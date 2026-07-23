package com.dev.scanlaptop.ui.components

import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Komponen kamera murni — hanya menangani preview dan deteksi barcode.
 * Tidak ada business logic (Supabase, navigasi) di sini.
 * Business logic ada di CameraScannerScreen di DashboardScreen.kt.
 */
@Composable
fun CameraPreviewView(
    onBarcodeDetected: (String) -> Unit,
    onCameraReady: (CameraControl) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val image = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees
                        )
                        BarcodeScanning.getClient().process(image)
                            .addOnSuccessListener { barcodes ->
                                barcodes.firstOrNull()?.rawValue?.let {
                                    onBarcodeDetected(it)
                                }
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    } else {
                        imageProxy.close()
                    }
                }

                try {
                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                    onCameraReady(camera.cameraControl)
                } catch (e: Exception) {
                    Log.e("CameraPreviewView", "Camera binding failed: ${e.message}")
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * Wrapper kamera dengan tap-to-focus indicator.
 * Dipakai di dalam CameraScannerScreen (DashboardScreen.kt).
 */
@Composable
fun CameraWithFocusIndicator(
    onBarcodeDetected: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var showFocusIndicator by remember { mutableStateOf(false) }
    var focusPosition by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { tapOffset ->
                    cameraControl?.let { control ->
                        scope.launch {
                            try {
                                val factory = SurfaceOrientedMeteringPointFactory(
                                    size.width.toFloat(),
                                    size.height.toFloat()
                                )
                                val focusPoint = factory.createPoint(tapOffset.x, tapOffset.y)
                                val action = FocusMeteringAction.Builder(
                                    focusPoint,
                                    FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                                ).setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                                    .build()
                                control.startFocusAndMetering(action)
                                focusPosition = tapOffset
                                showFocusIndicator = true
                                delay(1000)
                                showFocusIndicator = false
                            } catch (e: Exception) {
                                Log.e("CameraFocus", "Focus failed: ${e.message}")
                            }
                        }
                    }
                })
            }
    ) {
        CameraPreviewView(
            onBarcodeDetected = onBarcodeDetected,
            onCameraReady = { control -> cameraControl = control }
        )

        // Focus ring indicator
        AnimatedVisibility(
            visible = showFocusIndicator,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.offset {
                IntOffset(
                    (focusPosition.x - 60).roundToInt(),
                    (focusPosition.y - 60).roundToInt()
                )
            }
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape)
            )
        }
    }
}