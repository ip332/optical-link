package com.automotivesystemsconsulting.opticallink

import org.junit.Assert.*
import org.junit.Test

class A0ConformanceTest {

    companion object {
        private const val EXPECTED_BITS =
            "1010010100000001000001001000110100000000000000000101010101101010100011001111001100100001100000101000"

        private val EXPECTED_ROWS = listOf(
            "1010010100",
            "0000010000",
            "0100100011",
            "0100000000",
            "0000000001",
            "0101010110",
            "1010100011",
            "0011110011",
            "0010000110",
            "0000101000"
        )

        private val EXPECTED_PACKED = byteArrayOf(
            0xA5.toByte(),
            0x01,
            0x04,
            0x8D.toByte(),
            0x00,
            0x00,
            0x55,
            0x6A,
            0x8C.toByte(),
            0xF3.toByte(),
            0x21,
            0x82.toByte(),
            0x80.toByte()
        )
    }

    @Test
    fun `OL-A0-CV1 is exactly 100 bits`() {
        assertEquals(100, A0TestVector.encode().size)
    }

    @Test
    fun `OL-A0-CV1 matches canonical bit stream`() {
        val actual = A0TestVector.encode().joinToString("")

        assertEquals(EXPECTED_BITS, actual)
    }

    @Test
    fun `OL-A0-CV1 CRC is 0x860A`() {
        val bits = A0TestVector.encode()

        val crc = A0TestVector.readBits(
            bits,
            82,
            16
        ).toInt()

        assertEquals(0x860A, crc)
        assertTrue(A0TestVector.hasValidCrc(bits))
    }

    @Test
    fun `OL-A0-CV1 maps to canonical 10x10 rows`() {
        val bits = A0TestVector.encode()

        val rows = (0 until 10).map { row ->
            bits.subList(
                row * 10,
                row * 10 + 10
            ).joinToString("")
        }

        assertEquals(EXPECTED_ROWS, rows)
    }

    @Test
    fun `OL-A0-CV1 packs to canonical bytes`() {
        val packed =
            A0TestVector.packForStorage(A0TestVector.encode())

        assertArrayEquals(EXPECTED_PACKED, packed)
    }

    @Test
    fun `OL-A0-CV1 single protected bit corruption fails CRC`() {
        val corrupted =
            A0TestVector.encode().toMutableList()

        // Bit 50 is the first PAYLOAD bit and is CRC protected.
        corrupted[50] = corrupted[50] xor 1

        assertFalse(A0TestVector.hasValidCrc(corrupted))
    }
}
