package com.automotivesystemsconsulting.opticallink.controller

/**
 * Lightweight telemetry metrics for optical symbol scheduling and transmission.
 */
data class TransmitterDiagnostics(
    val requestedTimeNanos: Long = 0L,
    val actualTimeNanos: Long = 0L,
    val symbolIndex: Int = 0,
    val completedMessages: Long = 0L,
    val lateSymbolsCount: Long = 0L,
    val lastJitterNanos: Long = 0L,
    val totalSymbolsTransmitted: Long = 0L,
    val errorMessage: String? = null
)
