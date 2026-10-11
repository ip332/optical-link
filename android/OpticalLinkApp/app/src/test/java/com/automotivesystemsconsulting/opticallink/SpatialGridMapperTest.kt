package com.automotivesystemsconsulting.opticallink

import com.automotivesystemsconsulting.opticallink.model.OpticalSymbol
import com.automotivesystemsconsulting.opticallink.model.SpatialGridMapper
import org.junit.Assert.assertEquals
import org.junit.Test

class SpatialGridMapperTest {

    @Test
    fun `mapFrameToSpatialSymbols produces exactly 3 spatial frames of 64 cells`() {
        val bits = A0TestVector.encode()
        val frames = SpatialGridMapper.mapFrameToSpatialSymbols(bits)

        assertEquals(3, frames.size)
        for (frame in frames) {
            assertEquals(8, frame.rows)
            assertEquals(8, frame.cols)
            assertEquals(64, frame.cells.size)
        }
    }

    @Test
    fun `asymmetric corner anchors match specification A0=WHITE, A1=BLACK, A2=BLACK, A3=WHITE`() {
        val bits = A0TestVector.encode()
        val frames = SpatialGridMapper.mapFrameToSpatialSymbols(bits)

        for (frame in frames) {
            assertEquals("A0 top-left (0,0) must be WHITE", OpticalSymbol.WHITE, frame.getCell(0, 0))
            assertEquals("A1 top-right (0,7) must be BLACK", OpticalSymbol.BLACK, frame.getCell(0, 7))
            assertEquals("A2 bottom-left (7,0) must be BLACK", OpticalSymbol.BLACK, frame.getCell(7, 0))
            assertEquals("A3 bottom-right (7,7) must be WHITE", OpticalSymbol.WHITE, frame.getCell(7, 7))
        }
    }

    @Test
    fun `symbol index headers encode index 00, 01, and 10 correctly`() {
        val bits = A0TestVector.encode()
        val frames = SpatialGridMapper.mapFrameToSpatialSymbols(bits)

        // Symbol 0 (index 0 = 00_2)
        assertEquals(0, frames[0].symbolIndex)
        assertEquals(OpticalSymbol.BLACK, frames[0].getCell(1, 1)) // I0 = 0
        assertEquals(OpticalSymbol.BLACK, frames[0].getCell(1, 2)) // I1 = 0

        // Symbol 1 (index 1 = 01_2)
        assertEquals(1, frames[1].symbolIndex)
        assertEquals(OpticalSymbol.BLACK, frames[1].getCell(1, 1)) // I0 = 0
        assertEquals(OpticalSymbol.WHITE, frames[1].getCell(1, 2)) // I1 = 1

        // Symbol 2 (index 2 = 10_2)
        assertEquals(2, frames[2].symbolIndex)
        assertEquals(OpticalSymbol.WHITE, frames[2].getCell(1, 1)) // I0 = 1
        assertEquals(OpticalSymbol.BLACK, frames[2].getCell(1, 2)) // I1 = 0
    }

    @Test
    fun `sync toggle cell alternates between 0 and 1`() {
        val bits = A0TestVector.encode()
        val frames = SpatialGridMapper.mapFrameToSpatialSymbols(bits)

        assertEquals(OpticalSymbol.BLACK, frames[0].getCell(7, 6)) // 0 % 2 = 0
        assertEquals(OpticalSymbol.WHITE, frames[1].getCell(7, 6)) // 1 % 2 = 1
        assertEquals(OpticalSymbol.BLACK, frames[2].getCell(7, 6)) // 2 % 2 = 0
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid frame length throws IllegalArgumentException`() {
        SpatialGridMapper.mapFrameToSpatialSymbols(listOf(1, 0, 1))
    }
}
