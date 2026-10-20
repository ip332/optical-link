package com.automotivesystemsconsulting.opticallink.model

import androidx.compose.ui.graphics.Color

/**
 * Represents an optical symbol transmitted over the optical link display interface.
 *
 * [BLACK] represents binary 0 (low intensity/off).
 * [WHITE] represents binary 1 (high intensity/on).
 */
enum class OpticalSymbol(val rawBit: Int, val color: Color) {
    BLACK(0, Color.Black),
    WHITE(1, Color.White);

    companion object {
        fun fromBit(bit: Int): OpticalSymbol = when (bit) {
            0 -> BLACK
            1 -> WHITE
            else -> throw IllegalArgumentException("Invalid optical bit: $bit. Expected 0 or 1.")
        }
    }
}

/**
 * Converts a list of binary bits (0 or 1) into a list of corresponding [OpticalSymbol]s.
 */
fun List<Int>.toOpticalSymbols(): List<OpticalSymbol> = map { OpticalSymbol.fromBit(it) }
