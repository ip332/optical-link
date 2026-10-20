package com.automotivesystemsconsulting.opticallink

object A0TestVector {
    const val SYNC = 0xA5
    const val VERSION = 0
    const val FRAME_TYPE = 0
    const val FACE_ID = 4
    const val VEHICLE_ID = 0x1234
    const val SEQUENCE = 0x0001
    const val PAYLOAD = 0x55AA33CCL

    const val EXPECTED_CRC = 0x860A

    private fun appendBits(
        output: MutableList<Int>,
        value: Long,
        width: Int
    ) {
        for (shift in width - 1 downTo 0) {
            output += ((value shr shift) and 1L).toInt()
        }
    }

    fun crc16CcittFalse(bits: List<Int>): Int {
        var crc = 0xFFFF

        for (bit in bits) {
            require(bit == 0 || bit == 1)

            val feedback = ((crc ushr 15) and 1) xor bit
            crc = (crc shl 1) and 0xFFFF

            if (feedback != 0) {
                crc = crc xor 0x1021
            }
        }

        return crc
    }

    fun encode(): List<Int> {
        val frame = mutableListOf<Int>()

        appendBits(frame, SYNC.toLong(), 8)

        val protectedBits = mutableListOf<Int>()

        appendBits(protectedBits, VERSION.toLong(), 3)
        appendBits(protectedBits, FRAME_TYPE.toLong(), 3)
        appendBits(protectedBits, FACE_ID.toLong(), 4)
        appendBits(protectedBits, VEHICLE_ID.toLong(), 16)
        appendBits(protectedBits, SEQUENCE.toLong(), 16)
        appendBits(protectedBits, PAYLOAD, 32)

        check(protectedBits.size == 74)

        frame += protectedBits

        val crc = crc16CcittFalse(protectedBits)

        appendBits(frame, crc.toLong(), 16)

        // RESERVED_MBZ
        appendBits(frame, 0L, 2)

        check(frame.size == 100)

        return frame
    }

    fun readBits(
        bits: List<Int>,
        offset: Int,
        width: Int
    ): Long {
        require(offset >= 0)
        require(width >= 0)
        require(offset + width <= bits.size)

        var value = 0L

        for (i in 0 until width) {
            value = (value shl 1) or bits[offset + i].toLong()
        }

        return value
    }

    fun hasValidCrc(bits: List<Int>): Boolean {
        if (bits.size != 100) {
            return false
        }

        val receivedCrc = readBits(bits, 82, 16).toInt()
        val calculatedCrc = crc16CcittFalse(bits.subList(8, 82))

        return receivedCrc == calculatedCrc
    }

    fun packForStorage(bits: List<Int>): ByteArray {
        require(bits.size == 100)

        // 100 optical bits require 13 storage bytes.
        // The final four bits are zero padding and are not part
        // of the optical frame.
        val packed = ByteArray(13)

        for (bitIndex in bits.indices) {
            require(bits[bitIndex] == 0 || bits[bitIndex] == 1)

            if (bits[bitIndex] != 0) {
                val byteIndex = bitIndex / 8
                val bitWithinByte = 7 - (bitIndex % 8)

                packed[byteIndex] =
                    (packed[byteIndex].toInt() or
                        (1 shl bitWithinByte)).toByte()
            }
        }

        return packed
    }
}
