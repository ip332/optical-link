package com.automotivesystemsconsulting.opticallink.receiver

import com.automotivesystemsconsulting.opticallink.model.DecodedA0Frame
import com.automotivesystemsconsulting.opticallink.model.SpatialSymbolFrame

/**
 * Telemetry and statistics for optical camera frame reception.
 */
data class ReceiverDiagnostics(
    val detectedSymbolsCount: Long = 0L,
    val validFramesCount: Long = 0L,
    val invalidFramesCount: Long = 0L,
    val lastDecodedFrame: DecodedA0Frame? = null,
    val lastDetectedFrame: SpatialSymbolFrame? = null,
    val lastLuminanceMatrix: Array<IntArray>? = null,
    val debugLog: String = "Ready. Tap 'Capture & Inspect Frame' to analyze current camera view.",
    val currentFps: Double = 0.0,
    val lastJitterMs: Double = 0.0,
    val errorMessage: String? = null
)
