package com.automotivesystemsconsulting.opticallink.receiver

/**
 * Assembler responsible for collecting spatial symbols (0, 1, and 2) in sequence
 * while robustly ignoring duplicate observations from high-framerate camera capture (30 FPS).
 */
class OpticalFrameAssembler {
    private var symbol0Bits: List<Int>? = null
    private var symbol1Bits: List<Int>? = null
    private var symbol2Bits: List<Int>? = null
    private var lastProcessedSymbolIndex: Int = -1

    /**
     * Ingests a [symbol] and returns a complete 100-bit frame when all 3 spatial symbols
     * (0, 1, 2) have been received in sequence.
     */
    fun processSymbol(symbol: DecodedSpatialSymbol): List<Int>? {
        // Ignore duplicate observations of the exact same symbol index
        if (symbol.symbolIndex == lastProcessedSymbolIndex) {
            return null
        }

        when (symbol.symbolIndex) {
            0 -> {
                symbol0Bits = symbol.bits
                symbol1Bits = null
                symbol2Bits = null
                lastProcessedSymbolIndex = 0
                return null
            }
            1 -> {
                if (symbol0Bits != null && lastProcessedSymbolIndex == 0) {
                    symbol1Bits = symbol.bits
                    lastProcessedSymbolIndex = 1
                }
                return null
            }
            2 -> {
                if (symbol0Bits != null && symbol1Bits != null && lastProcessedSymbolIndex == 1) {
                    symbol2Bits = symbol.bits
                    lastProcessedSymbolIndex = 2

                    val s0 = symbol0Bits!!
                    val s1 = symbol1Bits!!
                    val s2 = symbol2Bits!!

                    // Combine: s0 (34 bits) + s1 (34 bits) + s2 (first 32 bits, dropping 2 padding 0s)
                    val full100Bits = s0 + s1 + s2.take(32)
                    check(full100Bits.size == 100)

                    reset()
                    return full100Bits
                }
                return null
            }
            else -> {
                reset()
                return null
            }
        }
    }

    /**
     * Resets the assembler state.
     */
    fun reset() {
        symbol0Bits = null
        symbol1Bits = null
        symbol2Bits = null
        lastProcessedSymbolIndex = -1
    }
}
