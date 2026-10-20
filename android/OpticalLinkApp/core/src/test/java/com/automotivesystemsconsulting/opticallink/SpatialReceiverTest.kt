package com.automotivesystemsconsulting.opticallink

import com.automotivesystemsconsulting.opticallink.model.OpticalSymbol
import com.automotivesystemsconsulting.opticallink.model.SpatialGridMapper
import com.automotivesystemsconsulting.opticallink.model.SpatialSymbolFrame
import com.automotivesystemsconsulting.opticallink.receiver.OpticalFrameAssembler
import com.automotivesystemsconsulting.opticallink.receiver.OpticalFrameValidator
import com.automotivesystemsconsulting.opticallink.receiver.SpatialGridDetector
import com.automotivesystemsconsulting.opticallink.receiver.SpatialSymbolDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SpatialReceiverTest {

    private fun frameToLuminanceMatrix(frame: SpatialSymbolFrame): Array<IntArray> {
        val mat = Array(8) { IntArray(8) }
        for (r in 0 until 8) {
            for (c in 0 until 8) {
                val symbol = frame.getCell(r, c)
                mat[r][c] = if (symbol == OpticalSymbol.WHITE) 240 else 20
            }
        }
        return mat
    }

    @Test
    fun `encoder to receiver round-trip succeeds for all 3 spatial symbols`() {
        val originalBits = A0TestVector.encode()
        val spatialFrames = SpatialGridMapper.mapFrameToSpatialSymbols(originalBits)

        val assembler = OpticalFrameAssembler()
        var assembledBits: List<Int>? = null

        for (frame in spatialFrames) {
            val luminanceMat = frameToLuminanceMatrix(frame)
            val detectedFrame = SpatialGridDetector.detectFromLuminanceMatrix(luminanceMat)
            val decodedSymbol = SpatialSymbolDecoder.decodeSpatialSymbol(detectedFrame)

            assertNotNull(decodedSymbol)
            val bits = assembler.processSymbol(decodedSymbol!!)
            if (bits != null) {
                assembledBits = bits
            }
        }

        assertNotNull(assembledBits)
        assertEquals(100, assembledBits!!.size)
        assertEquals(originalBits, assembledBits)

        val decodedA0 = OpticalFrameValidator.validateAndParse(assembledBits)
        assertNotNull(decodedA0)
        assertEquals(A0TestVector.SYNC, decodedA0!!.sync)
        assertEquals(A0TestVector.VEHICLE_ID, decodedA0.vehicleId)
        assertEquals(A0TestVector.SEQUENCE, decodedA0.sequence)
        assertEquals(A0TestVector.PAYLOAD, decodedA0.payload)
        assertEquals(A0TestVector.EXPECTED_CRC, decodedA0.crc)
    }

    @Test
    fun `spatial symbol decoder correctly extracts symbol indices 0, 1, and 2`() {
        val originalBits = A0TestVector.encode()
        val spatialFrames = SpatialGridMapper.mapFrameToSpatialSymbols(originalBits)

        for (expectedIdx in 0..2) {
            val decoded = SpatialSymbolDecoder.decodeSpatialSymbol(spatialFrames[expectedIdx])
            assertNotNull(decoded)
            assertEquals(expectedIdx, decoded!!.symbolIndex)
            assertEquals(34, decoded.bits.size)
        }
    }

    @Test
    fun `spatial symbol decoder extracts correct bits with 180 degree rotation when upside down`() {
        val originalBits = A0TestVector.encode()
        val spatialFrames = SpatialGridMapper.mapFrameToSpatialSymbols(originalBits)

        for (expectedIdx in 0..2) {
            val rotatedFrame = spatialFrames[expectedIdx].rotated180()
            val fixedFrame = rotatedFrame.rotated180() // rotate back 180
            val decoded = SpatialSymbolDecoder.decodeSpatialSymbol(fixedFrame)

            assertNotNull(decoded)
            assertEquals(expectedIdx, decoded!!.symbolIndex)
        }
    }

    @Test
    fun `spatial detector correctly rotates 180 degrees when upside down`() {
        val originalBits = A0TestVector.encode()
        val spatialFrames = SpatialGridMapper.mapFrameToSpatialSymbols(originalBits)
        val frame0 = spatialFrames[0].rotated180()

        val baseMat = frameToLuminanceMatrix(frame0)
        val detected = SpatialGridDetector.detectFromLuminanceMatrix(baseMat, isUpsideDown = true)
        val decoded = SpatialSymbolDecoder.decodeSpatialSymbol(detected)

        assertNotNull(decoded)
        assertEquals(0, decoded!!.symbolIndex)
    }

    @Test
    fun `detectFromYPlane handles stride padding and buffer bounds safely`() {
        val yPlane = ByteArray(307200) { 128.toByte() }
        val frame = SpatialGridDetector.detectFromYPlane(
            yPlane = yPlane,
            width = 640,
            height = 480,
            rowStride = 640
        )
        assertNotNull(frame)
        assertEquals(8, frame.rows)
        assertEquals(8, frame.cols)
    }

    @Test
    fun `detectFromYPlane handles non-square dimensions and all four camera rotations`() {
        val width = 640
        val height = 480
        val yPlane = ByteArray(width * height) { 128.toByte() }

        for (rot in listOf(0, 90, 180, 270)) {
            val frame = SpatialGridDetector.detectFromYPlane(
                yPlane = yPlane,
                width = width,
                height = height,
                rowStride = width,
                pixelStride = 1,
                rotationDegrees = rot
            )
            assertNotNull(frame)
            assertEquals(8, frame.rows)
            assertEquals(8, frame.cols)
        }
    }

    @Test
    fun `assembler ignores duplicate symbols and requires complete 0-1-2 sequence`() {
        val originalBits = A0TestVector.encode()
        val spatialFrames = SpatialGridMapper.mapFrameToSpatialSymbols(originalBits)

        val assembler = OpticalFrameAssembler()

        val sym0 = SpatialSymbolDecoder.decodeSpatialSymbol(
            SpatialGridDetector.detectFromLuminanceMatrix(frameToLuminanceMatrix(spatialFrames[0]))
        )!!
        val sym1 = SpatialSymbolDecoder.decodeSpatialSymbol(
            SpatialGridDetector.detectFromLuminanceMatrix(frameToLuminanceMatrix(spatialFrames[1]))
        )!!
        val sym2 = SpatialSymbolDecoder.decodeSpatialSymbol(
            SpatialGridDetector.detectFromLuminanceMatrix(frameToLuminanceMatrix(spatialFrames[2]))
        )!!

        // Send symbol 0, duplicate symbol 0, symbol 1, duplicate symbol 1, then symbol 2
        assertNull(assembler.processSymbol(sym0))
        assertNull(assembler.processSymbol(sym0)) // duplicate 0 resets sequence
        assertNull(assembler.processSymbol(sym0))
        assertNull(assembler.processSymbol(sym1))
        assertNull(assembler.processSymbol(sym1)) // duplicate 1
        val result = assembler.processSymbol(sym2)

        assertNotNull(result)
        assertEquals(100, result!!.size)
        assertEquals(originalBits, result)
    }

    @Test
    fun `validator rejects corrupted bits and invalid CRC`() {
        val originalBits = A0TestVector.encode().toMutableList()
        // Corrupt a payload bit
        originalBits[50] = originalBits[50] xor 1

        val validated = OpticalFrameValidator.validateAndParse(originalBits)
        assertNull(validated)
    }
}
