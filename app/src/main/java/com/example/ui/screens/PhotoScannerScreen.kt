package com.example.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executor

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PhotoScannerScreen(
    onBack: () -> Unit,
    onPhotoCapture: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var isCameraReady by remember { mutableStateOf(false) }
    var showCapturedImage by remember { mutableStateOf<Bitmap?>(null) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    if (showCapturedImage != null) {
        PageAdjustmentScreen(
            capturedImage = showCapturedImage!!,
            onBack = { showCapturedImage = null },
            onConfirm = { adjustedBitmap ->
                onPhotoCapture(adjustedBitmap)
                showCapturedImage = null
                onBack()
            }
        )
    } else if (cameraPermissionState.status.isGranted) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Camera Preview
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            try {
                                val cameraProvider = cameraProviderFuture.get()
                                
                                val preview = Preview.Builder().build().apply {
                                    setSurfaceProvider(surfaceProvider)
                                }
                                
                                imageCapture = ImageCapture.Builder()
                                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                    .build()
                                
                                val cameraSelector = CameraSelector.Builder()
                                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                                    .build()
                                
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageCapture
                                )
                                
                                isCameraReady = true
                            } catch (exc: Exception) {
                                exc.printStackTrace()
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Semi-transparent overlay with frame guide
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
            ) {
                // Frame guide rectangle
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(280.dp)
                        .height(380.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Transparent)
                        .border(
                            width = 3.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(16.dp)
                        )
                ) {
                    // Corner indicators
                    repeat(4) { corner ->
                        val (alignmentX, alignmentY) = when (corner) {
                            0 -> Alignment.TopStart to Alignment.TopStart
                            1 -> Alignment.TopEnd to Alignment.TopEnd
                            2 -> Alignment.BottomStart to Alignment.BottomStart
                            else -> Alignment.BottomEnd to Alignment.BottomEnd
                        }
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .align(alignmentX)
                        )
                    }
                }
            }

            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    "Scan Document",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(modifier = Modifier.size(48.dp))
            }

            // Bottom Control Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Align document with frame",
                    color = Color.White,
                    fontSize = 14.sp
                )
                
                Button(
                    onClick = {
                        if (isCameraReady && imageCapture != null) {
                            val outputDir = File(context.cacheDir, "camera_captures")
                            outputDir.mkdirs()
                            
                            val photoFile = File(
                                outputDir,
                                "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())}.jpg"
                            )
                            
                            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                            
                            imageCapture!!.takePicture(
                                outputOptions,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                        scope.launch {
                                            val bitmap = android.graphics.BitmapFactory.decodeFile(photoFile.absolutePath)
                                            if (bitmap != null) {
                                                // Rotate if necessary
                                                val rotatedBitmap = rotateBitmap(bitmap, 0f)
                                                showCapturedImage = rotatedBitmap
                                            }
                                        }
                                    }
                                    
                                    override fun onError(exception: ImageCaptureException) {
                                        exception.printStackTrace()
                                    }
                                }
                            )
                        }
                    },
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = CircleShape
                ) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Capture",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    } else {
        // Permission denied UI
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Text(
                    "Camera Permission Required",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "We need camera access to scan documents",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { cameraPermissionState.launchPermissionRequest() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Grant Permission")
                }
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface)
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun PageAdjustmentScreen(
    capturedImage: Bitmap,
    onBack: () -> Unit,
    onConfirm: (Bitmap) -> Unit
) {
    var pageScale by remember { mutableFloatStateOf(0.82f) }
    val previewBitmap by remember(capturedImage, pageScale) {
        mutableStateOf(createPortraitPageBitmap(capturedImage, pageScale, 720, 1018))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color.White.copy(alpha = 0.12f), CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Adjust Page",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Portrait mode by default",
                        color = Color.White.copy(alpha = 0.68f),
                        fontSize = 12.sp
                    )
                }

                Box(modifier = Modifier.size(42.dp))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF1E293B), RoundedCornerShape(22.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = previewBitmap.asImageBitmap(),
                    contentDescription = "Portrait page preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(0.707f)
                        .clip(RoundedCornerShape(10.dp))
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Page size", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("${(pageScale * 100).toInt()}%", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }

                Slider(
                    value = pageScale,
                    onValueChange = { pageScale = it },
                    valueRange = 0.55f..0.96f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.22f)
                    )
                )

                Button(
                    onClick = {
                        onConfirm(createPortraitPageBitmap(capturedImage, pageScale, 1240, 1754))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Portrait Page", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun createPortraitPageBitmap(sourceBitmap: Bitmap, pageScale: Float, pageWidth: Int, pageHeight: Int): Bitmap {
    val source = if (sourceBitmap.width > sourceBitmap.height) {
        rotateBitmap(sourceBitmap, 90f)
    } else {
        sourceBitmap
    }

    val page = Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(page)
    canvas.drawColor(android.graphics.Color.WHITE)

    val maxWidth = pageWidth * pageScale
    val maxHeight = pageHeight * pageScale
    val scale = minOf(maxWidth / source.width, maxHeight / source.height)
    val targetWidth = source.width * scale
    val targetHeight = source.height * scale
    val left = (pageWidth - targetWidth) / 2f
    val top = (pageHeight - targetHeight) / 2f

    canvas.drawBitmap(
        source,
        Rect(0, 0, source.width, source.height),
        Rect(left.toInt(), top.toInt(), (left + targetWidth).toInt(), (top + targetHeight).toInt()),
        Paint(Paint.FILTER_BITMAP_FLAG)
    )
    return page
}

private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
    if (degrees == 0f) return bitmap
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
