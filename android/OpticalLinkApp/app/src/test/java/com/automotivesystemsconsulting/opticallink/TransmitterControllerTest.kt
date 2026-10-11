package com.automotivesystemsconsulting.opticallink

import com.automotivesystemsconsulting.opticallink.controller.TransmitterController
import com.automotivesystemsconsulting.opticallink.controller.TransmitterState
import com.automotivesystemsconsulting.opticallink.model.OpticalSymbol
import com.automotivesystemsconsulting.opticallink.model.TransmissionMode
import com.automotivesystemsconsulting.opticallink.model.toOpticalSymbols
import com.automotivesystemsconsulting.opticallink.time.TestMonotonicClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransmitterControllerTest {

    @Test
    fun `bit sequence to optical symbol mapping preserves bits and order`() {
        val bits = listOf(1, 0, 1, 0, 0, 1)
        val symbols = bits.toOpticalSymbols()

        assertEquals(6, symbols.size)
        assertEquals(OpticalSymbol.WHITE, symbols[0])
        assertEquals(OpticalSymbol.BLACK, symbols[1])
        assertEquals(OpticalSymbol.WHITE, symbols[2])
        assertEquals(OpticalSymbol.BLACK, symbols[3])
        assertEquals(OpticalSymbol.BLACK, symbols[4])
        assertEquals(OpticalSymbol.WHITE, symbols[5])
    }

    @Test
    fun `A0TestVector encodes exactly to 100 optical symbols`() {
        val bits = A0TestVector.encode()
        val symbols = bits.toOpticalSymbols()

        assertEquals(100, symbols.size)
        assertEquals(OpticalSymbol.WHITE, symbols[0]) // First bit of SYNC (0xA5 = 10100101)
        assertEquals(OpticalSymbol.BLACK, symbols[1])
    }

    @Test
    fun `start transitions to TRANSMITTING state and initial symbol`() = runTest {
        val clock = TestMonotonicClock(1000L)
        val controller = TransmitterController(
            clock = clock,
            coroutineScope = backgroundScope
        )

        val bits = listOf(1, 0, 1)
        controller.start(bits, symbolDurationMs = 100L)

        assertEquals(TransmitterState.TRANSMITTING, controller.state.value)
        assertEquals(OpticalSymbol.WHITE, controller.currentSymbol.value)
        assertEquals(0, controller.diagnostics.value.symbolIndex)
        assertEquals(0L, controller.diagnostics.value.completedMessages)

        controller.stop()
    }

    @Test
    fun `transmission advances symbols and wraps around repeating messages`() = runTest {
        val clock = TestMonotonicClock(1000L)
        val controller = TransmitterController(
            clock = clock,
            coroutineScope = backgroundScope
        )

        val bits = listOf(1, 0)
        controller.start(bits, symbolDurationMs = 100L)

        // Initial symbol index 0 (WHITE)
        assertEquals(OpticalSymbol.WHITE, controller.currentSymbol.value)
        assertEquals(0, controller.diagnostics.value.symbolIndex)

        // Advance past 1 symbol duration (100ms)
        advanceTimeBy(100)
        runCurrent()

        // Next symbol index 1 (BLACK)
        assertEquals(OpticalSymbol.BLACK, controller.currentSymbol.value)
        assertEquals(1, controller.diagnostics.value.symbolIndex)

        // Advance past another symbol duration (100ms) - should wrap around to index 0 and increment completedMessages
        advanceTimeBy(100)
        runCurrent()

        assertEquals(OpticalSymbol.WHITE, controller.currentSymbol.value)
        assertEquals(0, controller.diagnostics.value.symbolIndex)
        assertEquals(1L, controller.diagnostics.value.completedMessages)

        controller.stop()
    }

    @Test
    fun `spatial grid transmission advances through 3 spatial symbols and wraps around`() = runTest {
        val clock = TestMonotonicClock(1000L)
        val controller = TransmitterController(
            clock = clock,
            coroutineScope = backgroundScope
        )

        val bits = A0TestVector.encode()
        controller.start(bits, symbolDurationMs = 100L, mode = TransmissionMode.SPATIAL_GRID)

        assertEquals(TransmitterState.TRANSMITTING, controller.state.value)
        assertEquals(TransmissionMode.SPATIAL_GRID, controller.transmissionMode.value)
        assertEquals(0, controller.currentSpatialFrame.value.symbolIndex)

        // Advance 100ms -> Symbol 1
        advanceTimeBy(100)
        runCurrent()
        assertEquals(1, controller.currentSpatialFrame.value.symbolIndex)

        // Advance 100ms -> Symbol 2
        advanceTimeBy(100)
        runCurrent()
        assertEquals(2, controller.currentSpatialFrame.value.symbolIndex)

        // Advance 100ms -> Wrap to Symbol 0 and increment completedMessages
        advanceTimeBy(100)
        runCurrent()
        assertEquals(0, controller.currentSpatialFrame.value.symbolIndex)
        assertEquals(1L, controller.diagnostics.value.completedMessages)

        controller.stop()
    }

    @Test
    fun `stop transitions to STOPPED state and resets symbol to BLACK`() = runTest {
        val clock = TestMonotonicClock(1000L)
        val controller = TransmitterController(
            clock = clock,
            coroutineScope = backgroundScope
        )

        controller.start(listOf(1, 1, 1), symbolDurationMs = 100L)
        assertEquals(TransmitterState.TRANSMITTING, controller.state.value)

        controller.stop()

        assertEquals(TransmitterState.STOPPED, controller.state.value)
        assertEquals(OpticalSymbol.BLACK, controller.currentSymbol.value)
    }

    @Test
    fun `restart after stopping resumes transmission cleanly`() = runTest {
        val clock = TestMonotonicClock(1000L)
        val controller = TransmitterController(
            clock = clock,
            coroutineScope = backgroundScope
        )

        val bits = listOf(0, 1)
        controller.start(bits, symbolDurationMs = 100L)
        controller.stop()

        assertEquals(TransmitterState.STOPPED, controller.state.value)

        controller.start(bits, symbolDurationMs = 50L)

        assertEquals(TransmitterState.TRANSMITTING, controller.state.value)
        assertEquals(OpticalSymbol.BLACK, controller.currentSymbol.value)
        assertEquals(50L, controller.nominalSymbolDurationMs.value)

        controller.stop()
    }

    @Test
    fun `invalid bit stream transitions to ERROR state`() = runTest {
        val clock = TestMonotonicClock(1000L)
        val controller = TransmitterController(
            clock = clock,
            coroutineScope = backgroundScope
        )

        // Invalid bit value 2
        controller.start(listOf(1, 2, 0), symbolDurationMs = 100L)

        assertEquals(TransmitterState.ERROR, controller.state.value)
        assertNotNull(controller.diagnostics.value.errorMessage)

        // Empty bit stream
        controller.start(emptyList(), symbolDurationMs = 100L)
        assertEquals(TransmitterState.ERROR, controller.state.value)

        // Invalid duration <= 0
        controller.start(listOf(1, 0), symbolDurationMs = 0L)
        assertEquals(TransmitterState.ERROR, controller.state.value)
    }

    @Test
    fun `simulated clock jitter records late symbol count`() = runTest {
        val clock = TestMonotonicClock(1000L)
        val controller = TransmitterController(
            clock = clock,
            coroutineScope = backgroundScope
        )

        controller.start(listOf(1, 0), symbolDurationMs = 100L)

        // Advance time by 100ms and simulate external clock jitter/delay
        advanceTimeBy(100)
        clock.advanceByMillis(50)
        runCurrent()

        // Check that late symbols counter incremented
        assertEquals(1L, controller.diagnostics.value.lateSymbolsCount)

        controller.stop()
    }
}
