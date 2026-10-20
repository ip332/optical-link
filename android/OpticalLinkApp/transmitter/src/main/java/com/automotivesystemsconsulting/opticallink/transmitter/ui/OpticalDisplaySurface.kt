package com.automotivesystemsconsulting.opticallink.transmitter.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.automotivesystemsconsulting.opticallink.model.OpticalSymbol

/**
 * High-efficiency full-screen optical transmission display surface.
 *
 * Automatically keeps the device screen awake during active transmission
 * and renders direct color transitions without animations or interpolations.
 */
@Composable
fun OpticalDisplaySurface(
    symbol: OpticalSymbol,
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(symbol.color)
    )
}
