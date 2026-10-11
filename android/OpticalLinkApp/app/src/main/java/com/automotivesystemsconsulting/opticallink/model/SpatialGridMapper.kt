package com.automotivesystemsconsulting.opticallink.model

/**
 * Mapper responsible for mapping encoded frame bitstreams into 2D spatial symbol frames
 * using an 8x8 grid layout with asymmetric corner anchors, index headers, and dark borders.
 */
object SpatialGridMapper {
    const val GRID_ROWS = 8
    const val GRID_COLS = 8
    const val TOTAL_CELLS = GRID_ROWS * GRID_COLS // 64
    const val DATA_CELLS_PER_SYMBOL = 34
    const val TOTAL_SPATIAL_SYMBOLS = 3

    /**
     * Maps a 100-bit OL-A0-CV1 encoded frame into a sequence of 3 [SpatialSymbolFrame]s.
     *
     * @param encodedBits The 100-bit frame (0s and 1s).
     * @return List of 3 [SpatialSymbolFrame]s.
     */
    fun mapFrameToSpatialSymbols(encodedBits: List<Int>): List<SpatialSymbolFrame> {
        require(encodedBits.size == 100) {
            "Expected 100 bits for OL-A0-CV1 frame, but got ${encodedBits.size}"
        }
        for (bit in encodedBits) {
            require(bit == 0 || bit == 1) { "Invalid bit value: $bit. Expected 0 or 1." }
        }

        // Partition 100 bits into 3 spatial slices of 34 bits each (last slice padded with two 0 bits)
        val slice0 = encodedBits.subList(0, 34)
        val slice1 = encodedBits.subList(34, 68)
        val slice2 = encodedBits.subList(68, 100) + listOf(0, 0)

        val slices = listOf(slice0, slice1, slice2)

        return slices.mapIndexed { symbolIdx, sliceBits ->
            createSpatialSymbolFrame(symbolIndex = symbolIdx, payloadBits = sliceBits)
        }
    }

    private fun createSpatialSymbolFrame(
        symbolIndex: Int,
        payloadBits: List<Int>
    ): SpatialSymbolFrame {
        require(payloadBits.size == DATA_CELLS_PER_SYMBOL)

        val cells = Array(TOTAL_CELLS) { OpticalSymbol.BLACK }

        // 1. Asymmetric Corner Anchors
        cells[0 * GRID_COLS + 0] = OpticalSymbol.WHITE // A0 (0,0) = 1
        cells[0 * GRID_COLS + 7] = OpticalSymbol.BLACK // A1 (0,7) = 0
        cells[7 * GRID_COLS + 0] = OpticalSymbol.BLACK // A2 (7,0) = 0
        cells[7 * GRID_COLS + 7] = OpticalSymbol.WHITE // A3 (7,7) = 1

        // 2. Symbol Index Header (I0, I1) at (1,1) and (1,2)
        val i0Bit = (symbolIndex shr 1) and 1
        val i1Bit = symbolIndex and 1
        cells[1 * GRID_COLS + 1] = OpticalSymbol.fromBit(i0Bit)
        cells[1 * GRID_COLS + 2] = OpticalSymbol.fromBit(i1Bit)

        // 3. Sync / Clock Toggle Cell (S) at (7,6)
        val syncBit = symbolIndex % 2
        cells[7 * GRID_COLS + 6] = OpticalSymbol.fromBit(syncBit)

        // 4. Data Payload Cells D0..D33
        val dataPositions = mutableListOf<Int>()
        // Row 1: cols 3..6
        for (col in 3..6) dataPositions.add(1 * GRID_COLS + col)
        // Rows 2..6: cols 1..6
        for (row in 2..6) {
            for (col in 1..6) {
                dataPositions.add(row * GRID_COLS + col)
            }
        }

        check(dataPositions.size == DATA_CELLS_PER_SYMBOL)

        for (i in payloadBits.indices) {
            val cellPos = dataPositions[i]
            cells[cellPos] = OpticalSymbol.fromBit(payloadBits[i])
        }

        return SpatialSymbolFrame(
            rows = GRID_ROWS,
            cols = GRID_COLS,
            cells = cells.toList(),
            symbolIndex = symbolIndex,
            totalSymbolsInSequence = TOTAL_SPATIAL_SYMBOLS
        )
    }
}
