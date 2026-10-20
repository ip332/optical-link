package com.automotivesystemsconsulting.opticallink.receiver

import android.util.Log
import com.automotivesystemsconsulting.opticallink.model.OpticalSymbol
import com.automotivesystemsconsulting.opticallink.model.SpatialSymbolFrame

/**
 * Detects and extracts the 8x8 spatial symbol grid from camera image luminance planes
 * using exact preview-aligned rotation mapping and anchor reference clustering.
 */
object SpatialGridDetector {
    const val GRID_SIZE = 8
    private const val TAG = "SpatialGridDetector"
    private const val MIN_CONTRAST_THRESHOLD = 15

    private fun logDebug(message: String) {
        try {
            Log.d(TAG, message)
        } catch (e: Throwable) {
            println("[$TAG] $message")
        }
    }

    /**
     * Rotates an 8x8 integer luminance matrix 90 degrees clockwise.
     */
    private fun rotateMatrix90(mat: Array<IntArray>): Array<IntArray> {
        val n = mat.size
        val rotated = Array(n) { IntArray(n) }
        for (r in 0 until n) {
            for (c in 0 until n) {
                rotated[c][n - 1 - r] = mat[r][c]
            }
        }
        return rotated
    }

    /**
     * Extracts an [8x8 SpatialSymbolFrame] from a synthetic [gridLuminance] matrix (8x8 integers 0..255).
     */
    fun detectFromLuminanceMatrix(gridLuminance: Array<IntArray>, isUpsideDown: Boolean = false): SpatialSymbolFrame {
        require(gridLuminance.size == GRID_SIZE && gridLuminance[0].size == GRID_SIZE)

        // Auto-Rotation Anchor Snap: test 0, 90, 180, 270 degree rotations to maximize anchor contrast
        var bestMat = gridLuminance
        var maxContrast = Int.MIN_VALUE

        var currentMat = gridLuminance
        repeat(4) {
            val c00 = currentMat[0][0]
            val c07 = currentMat[0][7]
            val c70 = currentMat[7][0]
            val c77 = currentMat[7][7]
            val whiteRef = (c00 + c77) / 2
            val blackRef = (c07 + c70) / 2
            val contrast = whiteRef - blackRef

            if (contrast > maxContrast) {
                maxContrast = contrast
                bestMat = currentMat
            }
            currentMat = rotateMatrix90(currentMat)
        }

        logDebug("Auto-rotation anchor snap selected rotation with max contrast: $maxContrast")

        val c00 = bestMat[0][0]
        val c07 = bestMat[0][7]
        val c70 = bestMat[7][0]
        val c77 = bestMat[7][7]
        val whiteRef = (c00 + c77) / 2
        val blackRef = (c07 + c70) / 2

        val threshold = if (maxContrast >= MIN_CONTRAST_THRESHOLD) {
            (whiteRef + blackRef) / 2
        } else {
            val allLums = ArrayList<Int>(GRID_SIZE * GRID_SIZE)
            for (r in 0 until GRID_SIZE) {
                for (c in 0 until GRID_SIZE) {
                    allLums.add(bestMat[r][c])
                }
            }
            allLums.sort()
            allLums[allLums.size / 2]
        }

        val cells = ArrayList<OpticalSymbol>(GRID_SIZE * GRID_SIZE)
        for (r in 0 until GRID_SIZE) {
            for (c in 0 until GRID_SIZE) {
                val lum = bestMat[r][c]
                val symbol = if (lum > threshold) OpticalSymbol.WHITE else OpticalSymbol.BLACK
                cells.add(symbol)
            }
        }

        var frame = SpatialSymbolFrame(
            rows = GRID_SIZE,
            cols = GRID_SIZE,
            cells = cells
        )

        if (isUpsideDown) {
            frame = frame.rotated180()
        }

        return frame
    }

    /**
     * Extracts an [8x8 SpatialSymbolFrame] from a raw Y-plane byte array using preview-aligned rotation mapping.
     */
    fun detectFromYPlane(
        yPlane: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int = 1,
        rotationDegrees: Int = 0,
        roiLeft: Float = 0.15f,
        roiTop: Float = 0.2f,
        roiRight: Float = 0.85f,
        roiBottom: Float = 0.8f,
        isUpsideDown: Boolean = false
    ): SpatialSymbolFrame {
        val bufW = width
        val bufH = height

        val (effectiveW, effectiveH) = if (rotationDegrees == 90 || rotationDegrees == 270) {
            bufH to bufW
        } else {
            bufW to bufH
        }

        val startX = (effectiveW * roiLeft).toInt().coerceIn(0, effectiveW - 1)
        val startY = (effectiveH * roiTop).toInt().coerceIn(0, effectiveH - 1)
        val endX = (effectiveW * roiRight).toInt().coerceIn(startX + 1, effectiveW)
        val endY = (effectiveH * roiBottom).toInt().coerceIn(startY + 1, effectiveH)

        val roiWidth = endX - startX
        val roiHeight = endY - startY

        val cellW = roiWidth / GRID_SIZE
        val cellH = roiHeight / GRID_SIZE

        val gridLuminance = Array(GRID_SIZE) { IntArray(GRID_SIZE) }

        for (r in 0 until GRID_SIZE) {
            for (c in 0 until GRID_SIZE) {
                val cellStartX = startX + c * cellW
                val cellStartY = startY + r * cellH
                var sum = 0L
                var count = 0

                val subStartX = cellStartX + cellW / 4
                val subEndX = cellStartX + (3 * cellW) / 4
                val subStartY = cellStartY + cellH / 4
                val subEndY = cellStartY + (3 * cellH) / 4

                for (y in subStartY until subEndY step 2) {
                    for (x in subStartX until subEndX step 2) {
                        // Map rotated display coordinates (x, y) to raw buffer coordinates (bufX, bufY)
                        val (bufX, bufY) = when (rotationDegrees) {
                            90 -> y to (bufH - 1 - x)
                            180 -> (bufW - 1 - x) to (bufH - 1 - y)
                            270 -> (bufW - 1 - y) to x
                            else -> x to y
                        }
                        val index = bufY * rowStride + bufX * pixelStride
                        if (index in yPlane.indices) {
                            val lum = yPlane[index].toInt() and 0xFF
                            sum += lum
                            count++
                        }
                    }
                }

                gridLuminance[r][c] = if (count > 0) (sum / count).toInt() else 128
            }
        }

        return detectFromLuminanceMatrix(gridLuminance, isUpsideDown)
    }
}
