package com.automotivesystemsconsulting.opticallink.receiver

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.automotivesystemsconsulting.opticallink.receiver.ui.ReceiverScreen
import com.automotivesystemsconsulting.opticallink.receiver.ui.theme.OpticalLinkAppTheme

class MainActivity : ComponentActivity() {
    private val controller by lazy {
        ReceiverController()
    }

    private val hasCameraPermissionState = mutableStateOf(false)

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermissionState.value = isGranted
        if (isGranted) {
            controller.start()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkCameraPermission()

        setContent {
            OpticalLinkAppTheme {
                val hasPermission by hasCameraPermissionState

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ReceiverScreen(
                        controller = controller,
                        hasCameraPermission = hasPermission,
                        onRequestCameraPermission = {
                            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkCameraPermission()
    }

    override fun onStop() {
        super.onStop()
        controller.stop()
    }

    private fun checkCameraPermission() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        hasCameraPermissionState.value = granted
        if (granted && controller.state.value == ReceiverState.IDLE) {
            controller.start()
        }
    }
}
