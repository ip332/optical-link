package com.automotivesystemsconsulting.opticallink.model

/**
 * Represents an [rows] x [cols] matrix of optical symbols for a single spatial symbol transmission frame.
 *
 * @property rows Number of rows in the spatial grid (default 8).
 * @property cols Number of columns in the spatial grid (default 8).
 * @property cells List of [OpticalSymbol] values of size [rows] * [cols] in row-major order.
 * @property symbolIndex Index of this spatial symbol frame within a multi-symbol sequence (0, 1, or 2).
 * @property totalSymbolsInSequence Total number of spatial symbols required for the frame (e.g. 3).
 */
data class SpatialSymbolFrame(
    val rows: Int = 8,
    val cols: Int = 8,
    val cells: List<OpticalSymbol>,
    val symbolIndex: Int = 0,
    val totalSymbolsInSequence: Int = 3
) {
    init {
        require(cells.size == rows * cols) {
            "Expected ${rows * cols} cells for an ${rows}x${cols} grid, but got ${cells.size}"
        }
    }

    /**
     * Returns the [OpticalSymbol] at the specified [row] and [col].
     */
    fun getCell(row: Int, col: Int): OpticalSymbol {
        require(row in 0 until rows && col in 0 until cols)
        return cells[row * cols + col]
    }

    /**
     * Returns a new [SpatialSymbolFrame] rotated by 180 degrees.
     */
    fun rotated180(): SpatialSymbolFrame {
        val newCells = List(rows * cols) { OpticalSymbol.BLACK }.toMutableList()
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val oldIdx = r * cols + c
                val newR = rows - 1 - r
                val newC = cols - 1 - c
                val newIdx = newR * cols + newC
                newCells[newIdx] = cells[oldIdx]
            }
        }
        return copy(cells = newCells)
    }
}
