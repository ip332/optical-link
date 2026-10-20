package com.automotivesystemsconsulting.opticallink.receiver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automotivesystemsconsulting.opticallink.receiver.ReceiverController
import com.automotivesystemsconsulting.opticallink.receiver.ReceiverState

@Composable
fun ReceiverScreen(
    controller: ReceiverController,
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by controller.state.collectAsState()
    val diagnostics by controller.diagnostics.collectAsState()
    val isUpsideDown by controller.isUpsideDown.collectAsState()
    val zoomRatio by controller.zoomRatio.collectAsState()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Text(
                    text = "Optical Link — A0 Receiver",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                // State Badge
                ReceiverStateBadge(state = state)

                // Orientation & Zoom Controls Card
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Receiver Orientation:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        FilterChip(
                            selected = isUpsideDown,
                            onClick = { controller.setUpsideDown(!isUpsideDown) },
                            label = { Text(if (isUpsideDown) "Rotate Camera 180° (Upside Down)" else "Normal Orientation (0°)") }
                        )

                        Text(
                            text = "Camera Optical Zoom:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (zoom in listOf(1.0f, 2.0f, 3.0f, 4.0f)) {
                                FilterChip(
                                    selected = zoomRatio == zoom,
                                    onClick = { controller.setZoomRatio(zoom) },
                                    label = { Text("${zoom.toInt()}x") }
                                )
                            }
                        }
                    }
                }

                // Permission Warning Card if permission is missing
                if (!hasCameraPermission) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Camera Permission Required",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "The optical receiver requires camera access to capture and decode optical symbols.",
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Button(
                                onClick = onRequestCameraPermission,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Grant Camera Permission")
                            }
                        }
                    }
                } else {
                    // 1. Single-Frame Decoder Diagnostics Card (At the TOP above camera view)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Single-Frame Decoder Diagnostics",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = diagnostics.debugLog,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Button(
                                onClick = { controller.debugCaptureSingleFrame() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Capture & Inspect Single Frame Snapshot")
                            }
                        }
                    }

                    // 2. Camera Preview Container with Grid Alignment ROI Box
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    ) {
                        CameraPreviewView(
                            controller = controller,
                            isReceiving = state == ReceiverState.RECEIVING
                        )
                    }
                }

                // Live Camera Detected Grid Preview Card
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Live Detected 8x8 Grid Preview",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        val frame = diagnostics.lastDetectedFrame
                        if (frame != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .background(Color.DarkGray, shape = RoundedCornerShape(8.dp))
                                    .padding(4.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                for (r in 0 until frame.rows) {
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        for (c in 0 until frame.cols) {
                                            val symbolColor = frame.getCell(r, c).color
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxSize()
                                                    .background(symbolColor, shape = RoundedCornerShape(2.dp))
                                            )
                                        }
                                    }
                                }
                            }
                            Text(
                                text = "Symbol Index: ${frame.symbolIndex} / 2",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        } else {
                            Text(
                                text = "Start receiver to view live sampled grid.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Error Alert if present
                if (diagnostics.errorMessage != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Error: ${diagnostics.errorMessage}",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Diagnostics Card
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Receiver Telemetry",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(text = "Target Grid: 8x8 Spatial Matrix (3 symbols/frame)")
                        Text(text = "Detected Symbols: ${diagnostics.detectedSymbolsCount}")
                        Text(text = "Valid Decoded Frames: ${diagnostics.validFramesCount}")
                        Text(text = "Invalid / Corrupted Frames: ${diagnostics.invalidFramesCount}")
                        Text(text = "Processing Speed: %.1f FPS".format(diagnostics.currentFps))
                    }
                }

                // Decoded Frame Payload Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Last Decoded Message",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        val frame = diagnostics.lastDecodedFrame
                        if (frame != null) {
                            Text(
                                text = "SYNC: 0x${frame.sync.toString(16).uppercase()} | " +
                                    "Ver: ${frame.version} | " +
                                    "Type: ${frame.frameType} | " +
                                    "FaceID: ${frame.faceId}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "VehicleID: 0x${frame.vehicleId.toString(16).uppercase()} | " +
                                    "Seq: 0x${frame.sequence.toString(16).uppercase()}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Payload: 0x${frame.payload.toString(16).uppercase()} | " +
                                    "CRC: 0x${frame.crc.toString(16).uppercase()}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = "No frame decoded yet. Align phone screen within the green 8x8 ROI box.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Sticky Bottom Action Controls Bar (Always visible without scrolling)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (state == ReceiverState.RECEIVING) {
                        Button(
                            onClick = { controller.stop() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Stop Receiver")
                        }
                    } else {
                        Button(
                            onClick = {
                                if (hasCameraPermission) {
                                    controller.start()
                                } else {
                                    onRequestCameraPermission()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Start Receiver")
                        }

                        if (state == ReceiverState.STOPPED || state == ReceiverState.ERROR) {
                            OutlinedButton(
                                onClick = { controller.reset() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Reset")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiverStateBadge(state: ReceiverState) {
    val (backgroundColor, textColor, label) = when (state) {
        ReceiverState.IDLE -> Triple(Color(0xFFE0E0E0), Color.DarkGray, "IDLE")
        ReceiverState.RECEIVING -> Triple(Color(0xFF4CAF50), Color.White, "RECEIVING")
        ReceiverState.STOPPED -> Triple(Color(0xFFFF9800), Color.White, "STOPPED")
        ReceiverState.ERROR -> Triple(Color(0xFFF44336), Color.White, "ERROR")
    }

    Box(
        modifier = Modifier
            .background(backgroundColor, shape = RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}
