package com.automotivesystemsconsulting.opticallink.controller

import com.automotivesystemsconsulting.opticallink.A0TestVector
import com.automotivesystemsconsulting.opticallink.model.OpticalSymbol
import com.automotivesystemsconsulting.opticallink.model.SpatialGridMapper
import com.automotivesystemsconsulting.opticallink.model.SpatialSymbolFrame
import com.automotivesystemsconsulting.opticallink.model.TransmissionMode
import com.automotivesystemsconsulting.opticallink.model.toOpticalSymbols
import com.automotivesystemsconsulting.opticallink.receiver.OpticalFrameAssembler
import com.automotivesystemsconsulting.opticallink.receiver.OpticalFrameValidator
import com.automotivesystemsconsulting.opticallink.receiver.SpatialGridDetector
import com.automotivesystemsconsulting.opticallink.receiver.SpatialSymbolDecoder
import com.automotivesystemsconsulting.opticallink.time.MonotonicClock
import com.automotivesystemsconsulting.opticallink.time.SystemMonotonicClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Controller managing the optical transmission state machine, symbol sequencing,
 * and precise frame timing for Temporal OOK, Spatial Grid, and Still Frame modes.
 */
class TransmitterController(
    private val clock: MonotonicClock = SystemMonotonicClock,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val _state = MutableStateFlow(TransmitterState.IDLE)
    val state: StateFlow<TransmitterState> = _state.asStateFlow()

    private val _transmissionMode = MutableStateFlow(TransmissionMode.TEMPORAL_OOK)
    val transmissionMode: StateFlow<TransmissionMode> = _transmissionMode.asStateFlow()

    private val _currentSymbol = MutableStateFlow(OpticalSymbol.BLACK)
    val currentSymbol: StateFlow<OpticalSymbol> = _currentSymbol.asStateFlow()

    private val defaultSpatialFrame = SpatialSymbolFrame(
        cells = List(SpatialGridMapper.TOTAL_CELLS) { OpticalSymbol.BLACK }
    )
    private val _currentSpatialFrame = MutableStateFlow(defaultSpatialFrame)
    val currentSpatialFrame: StateFlow<SpatialSymbolFrame> = _currentSpatialFrame.asStateFlow()

    private val _diagnostics = MutableStateFlow(TransmitterDiagnostics())
    val diagnostics: StateFlow<TransmitterDiagnostics> = _diagnostics.asStateFlow()

    private val _nominalSymbolDurationMs = MutableStateFlow(100L)
    val nominalSymbolDurationMs: StateFlow<Long> = _nominalSymbolDurationMs.asStateFlow()

    private val _isUpsideDown = MutableStateFlow(false)
    val isUpsideDown: StateFlow<Boolean> = _isUpsideDown.asStateFlow()

    private var encodedBitsCache: List<Int> = emptyList()
    private var stillFrameIndex = 0

    private var transmissionJob: Job? = null

    /**
     * Updates the active transmission mode when idle or stopped.
     */
    fun setTransmissionMode(mode: TransmissionMode, symbolIndex: Int = 0) {
        if (_state.value != TransmitterState.TRANSMITTING) {
            _transmissionMode.value = mode
            stillFrameIndex = symbolIndex.coerceIn(0, 2)
        }
    }

    /**
     * Toggles 180-degree grid rotation (for phones mounted upside down).
     */
    fun setUpsideDown(upsideDown: Boolean) {
        _isUpsideDown.value = upsideDown
        if (_state.value == TransmitterState.TRANSMITTING && encodedBitsCache.isNotEmpty()) {
            start(encodedBitsCache, _nominalSymbolDurationMs.value, _transmissionMode.value, stillFrameIndex)
        }
    }

    /**
     * Runs an in-memory self-loopback test verifying mapper -> synthetic luminance matrix -> detector -> decoder -> assembler -> validator.
     */
    fun runSelfLoopbackTest(): String {
        return try {
            val originalBits = A0TestVector.encode()
            val frames = SpatialGridMapper.mapFrameToSpatialSymbols(originalBits).map { frame ->
                if (_isUpsideDown.value) frame.rotated180() else frame
            }
            val assembler = OpticalFrameAssembler()
            var assembled: List<Int>? = null

            for (frame in frames) {
                val mat = Array(8) { r ->
                    IntArray(8) { c ->
                        if (frame.getCell(r, c) == OpticalSymbol.WHITE) 240 else 20
                    }
                }
                val detected = SpatialGridDetector.detectFromLuminanceMatrix(mat)
                val decoded = SpatialSymbolDecoder.decodeSpatialSymbol(detected)
                    ?: return "Loopback FAILED: Could not decode symbol index ${frame.symbolIndex}"
                val bits = assembler.processSymbol(decoded)
                if (bits != null) {
                    assembled = bits
                }
            }

            if (assembled == null) return "Loopback FAILED: Frame assembly incomplete"
            val validated = OpticalFrameValidator.validateAndParse(assembled)
            if (validated != null) {
                "Loopback SUCCESS: 100-bit frame verified! VehicleID=0x${validated.vehicleId.toString(16).uppercase()}, CRC=0x${validated.crc.toString(16).uppercase()}"
            } else {
                "Loopback FAILED: CRC validation failed on assembled bits"
            }
        } catch (e: Exception) {
            "Loopback Exception: ${e.message}"
        }
    }

    /**
     * Starts continuous transmission of the provided [encodedBits].
     */
    fun start(
        encodedBits: List<Int>,
        symbolDurationMs: Long = 100L,
        mode: TransmissionMode = _transmissionMode.value,
        symbolIndex: Int = stillFrameIndex
    ) {
        stopTransmissionJob()

        if (mode != TransmissionMode.STILL_FRAME && symbolDurationMs <= 0) {
            _state.value = TransmitterState.ERROR
            _diagnostics.value = TransmitterDiagnostics(
                errorMessage = "Invalid symbol duration: $symbolDurationMs ms. Must be > 0."
            )
            return
        }

        if (encodedBits.isEmpty()) {
            _state.value = TransmitterState.ERROR
            _diagnostics.value = TransmitterDiagnostics(
                errorMessage = "Encoded bit sequence cannot be empty."
            )
            return
        }

        encodedBitsCache = encodedBits
        _transmissionMode.value = mode
        _nominalSymbolDurationMs.value = symbolDurationMs
        stillFrameIndex = symbolIndex.coerceIn(0, 2)

        when (mode) {
            TransmissionMode.TEMPORAL_OOK -> startTemporalOok(encodedBits, symbolDurationMs)
            TransmissionMode.SPATIAL_GRID -> startSpatialGrid(encodedBits, symbolDurationMs)
            TransmissionMode.STILL_FRAME -> startStillFrame(encodedBits, stillFrameIndex)
        }
    }

    private fun startTemporalOok(encodedBits: List<Int>, symbolDurationMs: Long) {
        val symbols = try {
            encodedBits.toOpticalSymbols()
        } catch (e: IllegalArgumentException) {
            _state.value = TransmitterState.ERROR
            _diagnostics.value = TransmitterDiagnostics(
                errorMessage = e.message ?: "Invalid bit stream input."
            )
            return
        }

        _state.value = TransmitterState.TRANSMITTING

        var completedMessages = 0L
        var totalSymbolsTransmitted = 0L
        var lateSymbolsCount = 0L

        var currentIdx = 0
        _currentSymbol.value = symbols[currentIdx]

        val initialTime = clock.nanoTime()
        _diagnostics.value = TransmitterDiagnostics(
            requestedTimeNanos = initialTime,
            actualTimeNanos = initialTime,
            symbolIndex = 0,
            completedMessages = 0L,
            lateSymbolsCount = 0L,
            lastJitterNanos = 0L,
            totalSymbolsTransmitted = 1L,
            errorMessage = null
        )

        transmissionJob = coroutineScope.launch {
            val durationNanos = symbolDurationMs * 1_000_000L
            var nextTargetTimeNanos = clock.nanoTime() + durationNanos

            while (_state.value == TransmitterState.TRANSMITTING) {
                val now = clock.nanoTime()
                val delayNeededNanos = nextTargetTimeNanos - now

                if (delayNeededNanos > 0) {
                    clock.delay(delayNeededNanos / 1_000_000L)
                }

                val actualTimeNanos = clock.nanoTime()
                val jitterNanos = actualTimeNanos - nextTargetTimeNanos

                val lateThresholdNanos = (durationNanos / 2).coerceAtMost(10_000_000L)
                if (jitterNanos > lateThresholdNanos) {
                    lateSymbolsCount++
                }

                currentIdx = (currentIdx + 1) % symbols.size
                totalSymbolsTransmitted++

                if (currentIdx == 0) {
                    completedMessages++
                }

                _currentSymbol.value = symbols[currentIdx]

                _diagnostics.value = TransmitterDiagnostics(
                    requestedTimeNanos = nextTargetTimeNanos,
                    actualTimeNanos = actualTimeNanos,
                    symbolIndex = currentIdx,
                    completedMessages = completedMessages,
                    lateSymbolsCount = lateSymbolsCount,
                    lastJitterNanos = jitterNanos,
                    totalSymbolsTransmitted = totalSymbolsTransmitted,
                    errorMessage = null
                )

                nextTargetTimeNanos += durationNanos
                if (actualTimeNanos > nextTargetTimeNanos + durationNanos) {
                    nextTargetTimeNanos = actualTimeNanos + durationNanos
                }
            }
        }
    }

    private fun startSpatialGrid(encodedBits: List<Int>, symbolDurationMs: Long) {
        val spatialFrames = try {
            SpatialGridMapper.mapFrameToSpatialSymbols(encodedBits).map { frame ->
                if (_isUpsideDown.value) frame.rotated180() else frame
            }
        } catch (e: IllegalArgumentException) {
            _state.value = TransmitterState.ERROR
            _diagnostics.value = TransmitterDiagnostics(
                errorMessage = e.message ?: "Invalid spatial bit stream input."
            )
            return
        }

        _state.value = TransmitterState.TRANSMITTING

        var completedMessages = 0L
        var totalSymbolsTransmitted = 0L
        var lateSymbolsCount = 0L

        var currentIdx = 0
        _currentSpatialFrame.value = spatialFrames[currentIdx]

        val initialTime = clock.nanoTime()
        _diagnostics.value = TransmitterDiagnostics(
            requestedTimeNanos = initialTime,
            actualTimeNanos = initialTime,
            symbolIndex = 0,
            completedMessages = 0L,
            lateSymbolsCount = 0L,
            lastJitterNanos = 0L,
            totalSymbolsTransmitted = 1L,
            errorMessage = null
        )

        transmissionJob = coroutineScope.launch {
            val durationNanos = symbolDurationMs * 1_000_000L
            var nextTargetTimeNanos = clock.nanoTime() + durationNanos

            while (_state.value == TransmitterState.TRANSMITTING) {
                val now = clock.nanoTime()
                val delayNeededNanos = nextTargetTimeNanos - now

                if (delayNeededNanos > 0) {
                    clock.delay(delayNeededNanos / 1_000_000L)
                }

                val actualTimeNanos = clock.nanoTime()
                val jitterNanos = actualTimeNanos - nextTargetTimeNanos

                val lateThresholdNanos = (durationNanos / 2).coerceAtMost(10_000_000L)
                if (jitterNanos > lateThresholdNanos) {
                    lateSymbolsCount++
                }

                currentIdx = (currentIdx + 1) % spatialFrames.size
                totalSymbolsTransmitted++

                if (currentIdx == 0) {
                    completedMessages++
                }

                _currentSpatialFrame.value = spatialFrames[currentIdx]

                _diagnostics.value = TransmitterDiagnostics(
                    requestedTimeNanos = nextTargetTimeNanos,
                    actualTimeNanos = actualTimeNanos,
                    symbolIndex = currentIdx,
                    completedMessages = completedMessages,
                    lateSymbolsCount = lateSymbolsCount,
                    lastJitterNanos = jitterNanos,
                    totalSymbolsTransmitted = totalSymbolsTransmitted,
                    errorMessage = null
                )

                nextTargetTimeNanos += durationNanos
                if (actualTimeNanos > nextTargetTimeNanos + durationNanos) {
                    nextTargetTimeNanos = actualTimeNanos + durationNanos
                }
            }
        }
    }

    private fun startStillFrame(encodedBits: List<Int>, symbolIndex: Int) {
        val spatialFrames = try {
            SpatialGridMapper.mapFrameToSpatialSymbols(encodedBits).map { frame ->
                if (_isUpsideDown.value) frame.rotated180() else frame
            }
        } catch (e: IllegalArgumentException) {
            _state.value = TransmitterState.ERROR
            _diagnostics.value = TransmitterDiagnostics(
                errorMessage = e.message ?: "Invalid spatial bit stream input."
            )
            return
        }

        _state.value = TransmitterState.TRANSMITTING
        val idx = symbolIndex.coerceIn(0, spatialFrames.size - 1)
        _currentSpatialFrame.value = spatialFrames[idx]
        _diagnostics.value = TransmitterDiagnostics(
            symbolIndex = idx,
            completedMessages = 0L,
            totalSymbolsTransmitted = 1L,
            errorMessage = null
        )
    }

    /**
     * Stops current optical transmission cleanly.
     */
    fun stop() {
        stopTransmissionJob()
        if (_state.value == TransmitterState.TRANSMITTING) {
            _state.value = TransmitterState.STOPPED
        }
        _currentSymbol.value = OpticalSymbol.BLACK
        _currentSpatialFrame.value = defaultSpatialFrame
    }

    /**
     * Resets the controller back to [TransmitterState.IDLE].
     */
    fun reset() {
        stopTransmissionJob()
        _state.value = TransmitterState.IDLE
        _currentSymbol.value = OpticalSymbol.BLACK
        _currentSpatialFrame.value = defaultSpatialFrame
        _diagnostics.value = TransmitterDiagnostics()
    }

    private fun stopTransmissionJob() {
        transmissionJob?.cancel()
        transmissionJob = null
    }
}
