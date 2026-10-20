package com.automotivesystemsconsulting.opticallink.model

/**
 * Data class containing the reconstructed fields of a validated 100-bit OL-A0-CV1 optical frame.
 */
data class DecodedA0Frame(
    val sync: Int,
    val version: Int,
    val frameType: Int,
    val faceId: Int,
    val vehicleId: Int,
    val sequence: Int,
    val payload: Long,
    val crc: Int,
    val rawBits: List<Int>,
    val timestampNanos: Long = 0L
)
