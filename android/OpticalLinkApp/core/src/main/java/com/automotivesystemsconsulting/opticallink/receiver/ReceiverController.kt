package com.automotivesystemsconsulting.opticallink.receiver

import androidx.camera.core.CameraControl
import com.automotivesystemsconsulting.opticallink.time.MonotonicClock
import com.automotivesystemsconsulting.opticallink.time.SystemMonotonicClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controller managing optical receiver state, camera analyzer frame processing,
 * symbol assembly, and live diagnostic telemetry.
 */
class ReceiverController(
    private val clock: MonotonicClock = SystemMonotonicClock
) {
    private val _state = MutableStateFlow(ReceiverState.IDLE)
    val state: StateFlow<ReceiverState> = _state.asStateFlow()

    private val _diagnostics = MutableStateFlow(ReceiverDiagnostics())
    val diagnostics: StateFlow<ReceiverDiagnostics> = _diagnostics.asStateFlow()

    private val _isUpsideDown = MutableStateFlow(false)
    val isUpsideDown: StateFlow<Boolean> = _isUpsideDown.asStateFlow()

    private val _zoomRatio = MutableStateFlow(1.0f)
    val zoomRatio: StateFlow<Float> = _zoomRatio.asStateFlow()

    private val assembler = OpticalFrameAssembler()

    private var detectedSymbols = 0L
    private var validFrames = 0L
    private var invalidFrames = 0L
    private var lastFrameTimeNanos = 0L

    // Cache last raw Y frame for single-frame debug inspection
    private var latestYPlane: ByteArray? = null
    private var latestWidth: Int = 0
    private var latestHeight: Int = 0
    private var latestRowStride: Int = 0
    private var latestPixelStride: Int = 1
    private var latestRotationDegrees: Int = 0

    private var cameraControl: CameraControl? = null

    /**
     * Registers active camera control handle from CameraX.
     */
    fun setCameraControl(control: CameraControl?) {
        cameraControl = control
        control?.setZoomRatio(_zoomRatio.value)
    }

    /**
     * Sets the camera optical zoom ratio (e.g. 1.0f, 2.0f, 3.0f).
     */
    fun setZoomRatio(ratio: Float) {
        val clamped = ratio.coerceIn(1.0f, 10.0f)
        _zoomRatio.value = clamped
        cameraControl?.setZoomRatio(clamped)
    }

    /**
     * Toggles 180-degree receiver frame rotation.
     */
    fun setUpsideDown(upsideDown: Boolean) {
        _isUpsideDown.value = upsideDown
    }

    /**
     * Starts the receiver engine.
     */
    fun start() {
        assembler.reset()
        detectedSymbols = 0L
        validFrames = 0L
        invalidFrames = 0L
        lastFrameTimeNanos = clock.nanoTime()

        _state.value = ReceiverState.RECEIVING
        _diagnostics.value = ReceiverDiagnostics(debugLog = "Receiver active. Streaming camera frames...")
    }

    /**
     * Stops the receiver engine.
     */
    fun stop() {
        assembler.reset()
        if (_state.value == ReceiverState.RECEIVING) {
            _state.value = ReceiverState.STOPPED
            _diagnostics.value = _diagnostics.value.copy(debugLog = "Receiver stopped.")
        }
    }

    /**
     * Resets the receiver to [ReceiverState.IDLE].
     */
    fun reset() {
        assembler.reset()
        _state.value = ReceiverState.IDLE
        _diagnostics.value = ReceiverDiagnostics()
    }

    /**
     * Analyzes a single camera Y-plane frame. Called on background executor thread.
     */
    fun processYPlane(
        yPlane: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int = 1,
        rotationDegrees: Int = 0
    ) {
        latestYPlane = yPlane
        latestWidth = width
        latestHeight = height
        latestRowStride = rowStride
        latestPixelStride = pixelStride
        latestRotationDegrees = rotationDegrees

        if (_state.value != ReceiverState.RECEIVING) return

        val now = clock.nanoTime()
        val frameDeltaNanos = if (lastFrameTimeNanos > 0) now - lastFrameTimeNanos else 0L
        lastFrameTimeNanos = now
        val currentFps = if (frameDeltaNanos > 0) 1_000_000_000.0 / frameDeltaNanos else 0.0

        try {
            val spatialFrame = SpatialGridDetector.detectFromYPlane(
                yPlane = yPlane,
                width = width,
                height = height,
                rowStride = rowStride,
                pixelStride = pixelStride,
                rotationDegrees = rotationDegrees,
                isUpsideDown = _isUpsideDown.value
            )

            val decodeResult = SpatialSymbolDecoder.decodeSpatialSymbolWithReason(spatialFrame)
            val decodedSymbol = decodeResult.symbol

            if (decodedSymbol != null) {
                detectedSymbols++

                val assembledBits = assembler.processSymbol(decodedSymbol)
                if (assembledBits != null) {
                    val decodedFrame = OpticalFrameValidator.validateAndParse(assembledBits, now)
                    if (decodedFrame != null) {
                        validFrames++
                        _diagnostics.value = _diagnostics.value.copy(
                            detectedSymbolsCount = detectedSymbols,
                            validFramesCount = validFrames,
                            invalidFramesCount = invalidFrames,
                            lastDecodedFrame = decodedFrame,
                            lastDetectedFrame = spatialFrame,
                            debugLog = "SUCCESS: Decoded valid frame #${decodedFrame.sequence}!",
                            currentFps = currentFps,
                            errorMessage = null
                        )
                    } else {
                        invalidFrames++
                        _diagnostics.value = _diagnostics.value.copy(
                            detectedSymbolsCount = detectedSymbols,
                            validFramesCount = validFrames,
                            invalidFramesCount = invalidFrames,
                            lastDetectedFrame = spatialFrame,
                            debugLog = "Symbol decoded, but CRC validation failed.",
                            currentFps = currentFps
                        )
                    }
                } else {
                    _diagnostics.value = _diagnostics.value.copy(
                        detectedSymbolsCount = detectedSymbols,
                        lastDetectedFrame = spatialFrame,
                        debugLog = "Symbol ${decodedSymbol.symbolIndex} detected and assembled.",
                        currentFps = currentFps
                    )
                }
            } else {
                _diagnostics.value = _diagnostics.value.copy(
                    lastDetectedFrame = spatialFrame,
                    debugLog = decodeResult.failureReason,
                    currentFps = currentFps
                )
            }
        } catch (e: Exception) {
            _diagnostics.value = _diagnostics.value.copy(
                errorMessage = e.message ?: "Camera frame processing error",
                debugLog = "Error: ${e.message}"
            )
        }
    }

    /**
     * Captures and inspects a single frame snapshot for debugging alignment and luminance.
     */
    fun debugCaptureSingleFrame() {
        val yPlane = latestYPlane
        if (yPlane == null) {
            _diagnostics.value = _diagnostics.value.copy(
                debugLog = "No camera frame available yet. Ensure receiver is running."
            )
            return
        }

        try {
            val spatialFrame = SpatialGridDetector.detectFromYPlane(
                yPlane = yPlane,
                width = latestWidth,
                height = latestHeight,
                rowStride = latestRowStride,
                pixelStride = latestPixelStride,
                rotationDegrees = latestRotationDegrees,
                isUpsideDown = _isUpsideDown.value
            )

            val decodeResult = SpatialSymbolDecoder.decodeSpatialSymbolWithReason(spatialFrame)
            val decodedSymbol = decodeResult.symbol
            val logMessage = if (decodedSymbol != null) {
                "Snapshot SUCCESS: Symbol index ${decodedSymbol.symbolIndex}, 34 bits extracted."
            } else {
                "Snapshot FAILED: ${decodeResult.failureReason}"
            }

            _diagnostics.value = _diagnostics.value.copy(
                lastDetectedFrame = spatialFrame,
                debugLog = logMessage
            )
        } catch (e: Exception) {
            _diagnostics.value = _diagnostics.value.copy(
                debugLog = "Snapshot Exception: ${e.message}"
            )
        }
    }
}
