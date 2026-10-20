package com.automotivesystemsconsulting.opticallink.transmitter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.automotivesystemsconsulting.opticallink.controller.TransmitterController
import com.automotivesystemsconsulting.opticallink.transmitter.ui.TransmitterScreen
import com.automotivesystemsconsulting.opticallink.transmitter.ui.theme.OpticalLinkAppTheme

class MainActivity : ComponentActivity() {
    private val controller by lazy {
        TransmitterController(coroutineScope = lifecycleScope)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OpticalLinkAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TransmitterScreen(
                        controller = controller,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        controller.stop()
    }
}
