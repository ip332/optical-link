package com.automotivesystemsconsulting.opticallink.receiver

import com.automotivesystemsconsulting.opticallink.A0TestVector
import com.automotivesystemsconsulting.opticallink.model.DecodedA0Frame

/**
 * Validator responsible for verifying SYNC header and CRC-16 checksums on assembled 100-bit frames,
 * converting valid frames into [DecodedA0Frame] instances.
 */
object OpticalFrameValidator {

    /**
     * Validates [bits] and returns a [DecodedA0Frame] if valid, or `null` if invalid.
     */
    fun validateAndParse(bits: List<Int>, timestampNanos: Long = 0L): DecodedA0Frame? {
        if (bits.size != 100) {
            return null
        }

        // 1. Verify SYNC byte (bits 0..7 == 0xA5 / 10100101)
        val sync = A0TestVector.readBits(bits, 0, 8).toInt()
        if (sync != A0TestVector.SYNC) {
            return null
        }

        // 2. Verify CRC-16/CCITT-FALSE checksum
        if (!A0TestVector.hasValidCrc(bits)) {
            return null
        }

        // 3. Parse fields
        val version = A0TestVector.readBits(bits, 8, 3).toInt()
        val frameType = A0TestVector.readBits(bits, 11, 3).toInt()
        val faceId = A0TestVector.readBits(bits, 14, 4).toInt()
        val vehicleId = A0TestVector.readBits(bits, 18, 16).toInt()
        val sequence = A0TestVector.readBits(bits, 34, 16).toInt()
        val payload = A0TestVector.readBits(bits, 50, 32)
        val crc = A0TestVector.readBits(bits, 82, 16).toInt()

        return DecodedA0Frame(
            sync = sync,
            version = version,
            frameType = frameType,
            faceId = faceId,
            vehicleId = vehicleId,
            sequence = sequence,
            payload = payload,
            crc = crc,
            rawBits = bits,
            timestampNanos = timestampNanos
        )
    }
}
