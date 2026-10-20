package com.automotivesystemsconsulting.opticallink.transmitter.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automotivesystemsconsulting.opticallink.A0TestVector
import com.automotivesystemsconsulting.opticallink.controller.TransmitterController
import com.automotivesystemsconsulting.opticallink.controller.TransmitterState
import com.automotivesystemsconsulting.opticallink.model.TransmissionMode
import kotlin.math.roundToLong

@Composable
fun TransmitterScreen(
    controller: TransmitterController,
    modifier: Modifier = Modifier
) {
    val state by controller.state.collectAsState()
    val mode by controller.transmissionMode.collectAsState()
    val symbol by controller.currentSymbol.collectAsState()
    val spatialFrame by controller.currentSpatialFrame.collectAsState()
    val diagnostics by controller.diagnostics.collectAsState()
    val isUpsideDown by controller.isUpsideDown.collectAsState()

    val encodedBits = remember { A0TestVector.encode() }
    var durationSliderValue by remember { mutableFloatStateOf(500f) }
    val durationMs = durationSliderValue.roundToLong()

    Box(modifier = modifier.fillMaxSize()) {
        // Control Dashboard (Visible when idle, stopped, error, or as main screen)
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Text(
                    text = "Optical Link — A0 Transmitter",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                // State Badge
                StateBadge(state = state)

                // Error alert if present
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

                // Transmission Mode Selector
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Transmission Mode:",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = mode == TransmissionMode.TEMPORAL_OOK,
                                    onClick = { controller.setTransmissionMode(TransmissionMode.TEMPORAL_OOK) },
                                    label = { Text("Temporal OOK") },
                                    enabled = state != TransmitterState.TRANSMITTING
                                )
                                FilterChip(
                                    selected = mode == TransmissionMode.SPATIAL_GRID,
                                    onClick = { controller.setTransmissionMode(TransmissionMode.SPATIAL_GRID) },
                                    label = { Text("Spatial Grid (8x8)") },
                                    enabled = state != TransmitterState.TRANSMITTING
                                )
                            }
                            Text(
                                text = "Still Frame Debug Modes:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = mode == TransmissionMode.STILL_FRAME && spatialFrame.symbolIndex == 0,
                                    onClick = { controller.setTransmissionMode(TransmissionMode.STILL_FRAME, 0) },
                                    label = { Text("Still Sym 0") },
                                    enabled = state != TransmitterState.TRANSMITTING
                                )
                                FilterChip(
                                    selected = mode == TransmissionMode.STILL_FRAME && spatialFrame.symbolIndex == 1,
                                    onClick = { controller.setTransmissionMode(TransmissionMode.STILL_FRAME, 1) },
                                    label = { Text("Still Sym 1") },
                                    enabled = state != TransmitterState.TRANSMITTING
                                )
                                FilterChip(
                                    selected = mode == TransmissionMode.STILL_FRAME && spatialFrame.symbolIndex == 2,
                                    onClick = { controller.setTransmissionMode(TransmissionMode.STILL_FRAME, 2) },
                                    label = { Text("Still Sym 2") },
                                    enabled = state != TransmitterState.TRANSMITTING
                                )
                            }

                            Text(
                                text = "Holder Orientation:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            FilterChip(
                                selected = isUpsideDown,
                                onClick = { controller.setUpsideDown(!isUpsideDown) },
                                label = { Text(if (isUpsideDown) "Rotate 180° (Upside Down)" else "Normal Orientation (0°)") }
                            )
                        }
                    }
                }

                // Software Decoder Self-Loopback Test Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Software Decoder Self-Loopback Test",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        var loopbackResult by remember { mutableStateOf("Tap below to test encoder -> synthetic luminance -> detector -> decoder -> validator in memory.") }
                        Text(
                            text = loopbackResult,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Button(
                            onClick = { loopbackResult = controller.runSelfLoopbackTest() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Run In-Memory Decoder Loopback Test")
                        }
                    }
                }

                // Payload Summary Card
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
                            text = "Test Vector: OL-A0-CV1",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Frame Size: ${encodedBits.size} bits | " +
                                when (mode) {
                                    TransmissionMode.SPATIAL_GRID -> "3 Spatial Symbols (8x8)"
                                    TransmissionMode.STILL_FRAME -> "Still Frame (Symbol ${spatialFrame.symbolIndex})"
                                    TransmissionMode.TEMPORAL_OOK -> "100 Temporal Symbols"
                                },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "SYNC: 0x${A0TestVector.SYNC.toString(16).uppercase()} | " +
                                "VehicleID: 0x${A0TestVector.VEHICLE_ID.toString(16).uppercase()} | " +
                                "Seq: 0x${A0TestVector.SEQUENCE.toString(16).uppercase()}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Payload: 0x${A0TestVector.PAYLOAD.toString(16).uppercase()} | " +
                                "CRC: 0x${A0TestVector.EXPECTED_CRC.toString(16).uppercase()}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }

                // Configurable Symbol Duration (Hidden in Still Frame mode)
                if (mode != TransmissionMode.STILL_FRAME) {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Nominal Symbol Duration:",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "$durationMs ms",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Slider(
                                value = durationSliderValue,
                                onValueChange = { durationSliderValue = it },
                                valueRange = 50f..500f,
                                steps = 45,
                                enabled = state != TransmitterState.TRANSMITTING
                            )
                        }
                    }
                }

                // Telemetry & Diagnostics Card
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Transmission Diagnostics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(text = "Active Mode: $mode (UpsideDown: $isUpsideDown)")
                        Text(text = "Completed Repetitions: ${diagnostics.completedMessages}")
                        val maxSymbolIdx = when (mode) {
                            TransmissionMode.SPATIAL_GRID -> 2
                            TransmissionMode.STILL_FRAME -> spatialFrame.symbolIndex
                            TransmissionMode.TEMPORAL_OOK -> encodedBits.size - 1
                        }
                        Text(text = "Active Symbol Index: ${diagnostics.symbolIndex} / $maxSymbolIdx")
                        Text(text = "Total Symbols Transmitted: ${diagnostics.totalSymbolsTransmitted}")
                        Text(
                            text = "Last Jitter: %.2f ms".format(diagnostics.lastJitterNanos / 1_000_000.0)
                        )
                        Text(text = "Late / Skipped Frames: ${diagnostics.lateSymbolsCount}")
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
                if (state == TransmitterState.TRANSMITTING) {
                    Button(
                        onClick = { controller.stop() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Stop Transmission")
                    }
                } else {
                    Button(
                        onClick = {
                            val idx = spatialFrame.symbolIndex
                            controller.start(encodedBits, durationMs, mode, idx)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            if (state == TransmitterState.STOPPED) "Restart Transmission"
                            else "Start Transmission"
                        )
                    }

                    if (state == TransmitterState.STOPPED || state == TransmitterState.ERROR) {
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

        // Full Screen Transmission Surface (Active during TRANSMITTING)
        AnimatedVisibility(
            visible = state == TransmitterState.TRANSMITTING,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Background optical display surface rendering symbols according to mode
                when (mode) {
                    TransmissionMode.TEMPORAL_OOK -> {
                        OpticalDisplaySurface(
                            symbol = symbol,
                            isTransmitting = state == TransmitterState.TRANSMITTING
                        )
                    }
                    TransmissionMode.SPATIAL_GRID, TransmissionMode.STILL_FRAME -> {
                        SpatialDisplaySurface(
                            frame = spatialFrame,
                            isTransmitting = state == TransmitterState.TRANSMITTING
                        )
                    }
                }

                // Compact top overlay banner with stop control
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val modeLabel = when (mode) {
                                TransmissionMode.SPATIAL_GRID -> "SPATIAL 8x8"
                                TransmissionMode.STILL_FRAME -> "STILL FRAME (Sym ${spatialFrame.symbolIndex})"
                                TransmissionMode.TEMPORAL_OOK -> "TEMPORAL OOK"
                            }
                            val maxIdx = when (mode) {
                                TransmissionMode.SPATIAL_GRID -> 2
                                TransmissionMode.STILL_FRAME -> spatialFrame.symbolIndex
                                TransmissionMode.TEMPORAL_OOK -> encodedBits.size - 1
                            }
                            Text(
                                text = "TRANSMITTING OL-A0-CV1 ($modeLabel)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Reps: ${diagnostics.completedMessages} | " +
                                    "Symbol: ${diagnostics.symbolIndex}/$maxIdx | " +
                                    "$durationMs ms",
                                color = Color.LightGray,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = { controller.stop() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("Stop", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StateBadge(state: TransmitterState) {
    val (backgroundColor, textColor, label) = when (state) {
        TransmitterState.IDLE -> Triple(Color(0xFFE0E0E0), Color.DarkGray, "IDLE")
        TransmitterState.TRANSMITTING -> Triple(Color(0xFF4CAF50), Color.White, "TRANSMITTING")
        TransmitterState.STOPPED -> Triple(Color(0xFFFF9800), Color.White, "STOPPED")
        TransmitterState.ERROR -> Triple(Color(0xFFF44336), Color.White, "ERROR")
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
