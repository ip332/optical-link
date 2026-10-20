package com.automotivesystemsconsulting.opticallink.receiver

import android.util.Log
import com.automotivesystemsconsulting.opticallink.model.SpatialGridMapper
import com.automotivesystemsconsulting.opticallink.model.SpatialSymbolFrame

/**
 * Decoded spatial symbol payload containing symbol index (0, 1, or 2) and extracted bits.
 */
data class DecodedSpatialSymbol(
    val symbolIndex: Int,
    val bits: List<Int>
)

/**
 * Decodes a canonical 0-degree [SpatialSymbolFrame] into a [DecodedSpatialSymbol].
 */
object SpatialSymbolDecoder {
    private const val TAG = "SpatialSymbolDecoder"

    private fun logDebug(message: String) {
        try {
            Log.d(TAG, message)
        } catch (e: Throwable) {
            println("[$TAG] $message")
        }
    }

    /**
     * Decodes [frame] into a [DecodedSpatialSymbol] containing extracted payload bits.
     */
    fun decodeSpatialSymbol(frame: SpatialSymbolFrame): DecodedSpatialSymbol? {
        return decodeSpatialSymbolWithReason(frame).symbol
    }

    data class DecodeResult(
        val symbol: DecodedSpatialSymbol?,
        val failureReason: String
    )

    /**
     * Decodes [frame] and returns both the symbol and the exact reason for failure if any.
     */
    fun decodeSpatialSymbolWithReason(frame: SpatialSymbolFrame): DecodeResult {
        if (frame.rows != SpatialGridMapper.GRID_ROWS || frame.cols != SpatialGridMapper.GRID_COLS) {
            return DecodeResult(null, "Invalid grid dimensions: ${frame.rows}x${frame.cols}")
        }

        // Extract Symbol Index Header (I0, I1) at (1,1) and (1,2)
        val i0Bit = frame.getCell(1, 1).rawBit
        val i1Bit = frame.getCell(1, 2).rawBit
        val symbolIndex = (i0Bit shl 1) or i1Bit

        logDebug("Header extracted: i0=(1,1)=$i0Bit, i1=(1,2)=$i1Bit -> symbolIndex=$symbolIndex")

        if (symbolIndex !in 0..2) {
            return DecodeResult(null, "Invalid symbol index: $symbolIndex (expected 0, 1, or 2)")
        }

        // Extract 34 payload bits D0..D33
        val dataPositions = mutableListOf<Pair<Int, Int>>()
        // Row 1: cols 3..6
        for (col in 3..6) dataPositions.add(1 to col)
        // Rows 2..6: cols 1..6
        for (row in 2..6) {
            for (col in 1..6) {
                dataPositions.add(row to col)
            }
        }

        check(dataPositions.size == SpatialGridMapper.DATA_CELLS_PER_SYMBOL)

        val bits = dataPositions.map { (row, col) ->
            frame.getCell(row, col).rawBit
        }

        return DecodeResult(
            DecodedSpatialSymbol(symbolIndex = symbolIndex, bits = bits),
            "SUCCESS"
        )
    }
}
