package com.automotivesystemsconsulting.opticallink.receiver.ui

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.automotivesystemsconsulting.opticallink.receiver.ReceiverController
import java.util.concurrent.Executors

/**
 * CameraX preview and frame analysis view hosting an ROI alignment box overlay.
 * Uses FIT_CENTER scaleType to guarantee zero cropping between preview and frame analysis.
 */
@Composable
fun CameraPreviewView(
    controller: ReceiverController,
    isReceiving: Boolean,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(this.surfaceProvider)
                        }

                        val analysisExecutor = Executors.newSingleThreadExecutor()
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                            try {
                                val plane = imageProxy.planes[0]
                                val buffer = plane.buffer
                                val bytes = ByteArray(buffer.remaining())
                                buffer.get(bytes)

                                val rotation = imageProxy.imageInfo.rotationDegrees
                                val width = imageProxy.width
                                val height = imageProxy.height
                                val rowStride = plane.rowStride
                                val pixelStride = plane.pixelStride

                                controller.processYPlane(
                                    yPlane = bytes,
                                    width = width,
                                    height = height,
                                    rowStride = rowStride,
                                    pixelStride = pixelStride,
                                    rotationDegrees = rotation
                                )
                            } finally {
                                imageProxy.close()
                            }
                        }

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        try {
                            cameraProvider.unbindAll()
                            val camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalysis
                            )
                            controller.setCameraControl(camera.cameraControl)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // ROI Alignment Overlay Box (Green target grid box in center)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val roiLeft = size.width * 0.15f
            val roiTop = size.height * 0.2f
            val roiWidth = size.width * 0.7f
            val roiHeight = size.height * 0.6f

            drawRect(
                color = Color.Green,
                topLeft = Offset(roiLeft, roiTop),
                size = Size(roiWidth, roiHeight),
                style = Stroke(width = 4f)
            )

            // Draw inner 8x8 grid guidelines
            val cellW = roiWidth / 8f
            val cellH = roiHeight / 8f
            for (i in 1..7) {
                drawLine(
                    color = Color.Green.copy(alpha = 0.35f),
                    start = Offset(roiLeft + i * cellW, roiTop),
                    end = Offset(roiLeft + i * cellW, roiTop + roiHeight),
                    strokeWidth = 1f
                )
                drawLine(
                    color = Color.Green.copy(alpha = 0.35f),
                    start = Offset(roiLeft, roiTop + i * cellH),
                    end = Offset(roiLeft + roiWidth, roiTop + i * cellH),
                    strokeWidth = 1f
                )
            }
        }
    }
}
