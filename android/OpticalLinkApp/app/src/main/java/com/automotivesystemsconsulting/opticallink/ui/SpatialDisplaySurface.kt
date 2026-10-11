package com.automotivesystemsconsulting.opticallink.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import com.automotivesystemsconsulting.opticallink.model.SpatialSymbolFrame

/**
 * High-performance full-screen spatial optical transmission surface.
 *
 * Renders an R x C grid matrix defined by [SpatialSymbolFrame] on a single
 * Compose [Canvas], preventing display sleep during active transmission.
 */
@Composable
fun SpatialDisplaySurface(
    frame: SpatialSymbolFrame,
    isTransmitting: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    DisposableEffect(isTransmitting) {
        val activity = context as? Activity
        if (isTransmitting && activity != null) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            val activity = context as? Activity
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val cellWidth = size.width / frame.cols
        val cellHeight = size.height / frame.rows

        for (row in 0 until frame.rows) {
            for (col in 0 until frame.cols) {
                val symbol = frame.getCell(row, col)
                drawRect(
                    color = symbol.color,
                    topLeft = Offset(col * cellWidth, row * cellHeight),
                    size = Size(cellWidth, cellHeight)
                )
            }
        }
    }
}
