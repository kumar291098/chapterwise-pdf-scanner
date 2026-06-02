package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.ui.components.BookPageFlip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    filePath: String,
    bookName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val file = remember(filePath) { File(filePath) }
    var pageCount by remember { mutableStateOf(0) }
    var isError by remember { mutableStateOf(false) }

    var isBookMode by remember { mutableStateOf(true) }
    var currentPage by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()
    
    // Remember the preloader across compositions of this screen
    val preloader = remember(file) { PdfPagePreloader(file, context, coroutineScope) }

    DisposableEffect(preloader) {
        onDispose {
            preloader.clear()
        }
    }

    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            try {
                if (file.exists()) {
                    val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(fd)
                    pageCount = renderer.pageCount
                    renderer.close()
                    fd.close()
                } else {
                    isError = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
                isError = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = bookName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp
                            ),
                            maxLines = 1
                        )
                        if (pageCount > 0) {
                            Text(
                                text = "$pageCount chapter pages assembled",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("pdf_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { isBookMode = !isBookMode },
                        modifier = Modifier.testTag("pdf_toggle_view_mode_button")
                    ) {
                        Icon(
                            imageVector = if (isBookMode) Icons.Default.List else Icons.Default.MenuBook,
                            contentDescription = if (isBookMode) "Switch to List View" else "Switch to 3D Book View"
                        )
                    }
                    IconButton(
                        onClick = {
                            if (file.exists()) {
                                try {
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "com.aistudio.chapterwisepdf.fileprovider",
                                        file
                                    )
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/pdf"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share Chapter PDF"))
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        },
                        enabled = file.exists(),
                        modifier = Modifier.testTag("pdf_share_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share PDF"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
    ) { paddingValues ->
        if (isError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Could not open compiled PDF. Please rebuild it.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else if (pageCount == 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (isBookMode) {
                    val configuration = LocalConfiguration.current
                    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

                    LaunchedEffect(currentPage, isLandscape) {
                        preloader.updateVisibleWindow(currentPage, isLandscape)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        BookPageFlip(
                            pageCount = pageCount,
                            currentPage = currentPage,
                            onPageChanged = { newPage ->
                                currentPage = newPage
                            },
                            modifier = Modifier.fillMaxSize()
                        ) { index ->
                            val bitmap = preloader.getBitmap(index)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Page ${index + 1}",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }
                    }

                    BookNavigationHud(
                        currentPage = currentPage,
                        pageCount = pageCount,
                        isLandscape = isLandscape,
                        onPageSelected = { newPage ->
                            currentPage = newPage
                        }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(pageCount) { index ->
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 600.dp)
                            ) {
                                Column {
                                    PdfPageItem(file = file, pageIndex = index)
                                    HorizontalDivider(color = Color(0xFFF1F1F1))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFFFCFCFC))
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Page ${index + 1} of $pageCount",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Color.Gray,
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PdfPageItem(file: File, pageIndex: Int, modifier: Modifier = Modifier) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(file, pageIndex) {
        withContext(Dispatchers.IO) {
            try {
                val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fd)
                if (pageIndex < renderer.pageCount) {
                    val page = renderer.openPage(pageIndex)
                    
                    // Render page at double density (upscaled x2 factor) for sharp readable text!
                    val upscaleFactor = 2
                    val renderedBitmap = Bitmap.createBitmap(
                        page.width * upscaleFactor,
                        page.height * upscaleFactor,
                        Bitmap.Config.ARGB_8888
                    )
                    
                    page.render(renderedBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    renderer.close()
                    fd.close()
                    
                    withContext(Dispatchers.Main) {
                        bitmap = renderedBitmap
                    }
                } else {
                    renderer.close()
                    fd.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    error = e.localizedMessage ?: "Failed to render page"
                }
            }
        }
    }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.707f) // A4 Aspect ratio helper
            .background(Color.White)
            .clipToBounds() // Keep zoomed contents inside bounds
            .pointerInput(Unit) {
                detectPdfZoomGestures(
                    onGesture = { pan, zoom ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1f) {
                            // Allow panning only when zoomed in
                            offset = Offset(
                                x = offset.x + pan.x,
                                y = offset.y + pan.y
                            )
                        } else {
                            offset = Offset.Zero
                        }
                    },
                    scaleProvider = { scale }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 3f
                            offset = Offset.Zero
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )
        } ?: if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        } else {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

suspend fun PointerInputScope.detectPdfZoomGestures(
    onGesture: (pan: Offset, zoom: Float) -> Unit,
    scaleProvider: () -> Float
) {
    awaitEachGesture {
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop
        
        awaitFirstDown(requireUnconsumed = false)
        
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            val currentScale = scaleProvider()
            val pointerCount = event.changes.size
            
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()
                
                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    pan += panChange
                    
                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = kotlin.math.abs(1f - zoom) * centroidSize
                    val panMotion = pan.getDistance()
                    
                    if (zoomMotion > touchSlop || panMotion > touchSlop) {
                        pastTouchSlop = true
                    }
                }
                
                if (pastTouchSlop) {
                    if (zoomChange != 1f || panChange != Offset.Zero) {
                        onGesture(panChange, zoomChange)
                    }
                    
                    // Consume pointer input to lock parent scroll when zoomed in OR when pinching with multiple fingers
                    if (currentScale > 1f || pointerCount > 1) {
                        event.changes.forEach {
                            if (it.positionChanged()) {
                                it.consume()
                            }
                        }
                    }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })
    }
}

class PdfPagePreloader(
    private val file: File,
    private val context: Context,
    private val scope: CoroutineScope
) {
    val cache = mutableStateMapOf<Int, Bitmap>()
    private val loadingPages = mutableSetOf<Int>()
    var pageCount by mutableStateOf(0)
        private set

    init {
        try {
            if (file.exists()) {
                val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fd)
                pageCount = renderer.pageCount
                renderer.close()
                fd.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getBitmap(pageIndex: Int): Bitmap? {
        if (pageIndex < 0 || pageIndex >= pageCount) return null
        val cached = cache[pageIndex]
        if (cached != null) return cached
        
        // Trigger load if not already loading
        if (!loadingPages.contains(pageIndex)) {
            loadPage(pageIndex)
        }
        return null
    }

    fun updateVisibleWindow(currentPage: Int, isLandscape: Boolean) {
        val range = if (isLandscape) {
            (currentPage - 3)..(currentPage + 4)
        } else {
            (currentPage - 2)..(currentPage + 2)
        }

        // Evict pages outside range
        val keysToRemove = cache.keys.filter { it !in range }
        keysToRemove.forEach { key ->
            cache.remove(key)?.recycle()
        }

        // Preload pages in range
        for (i in range) {
            if (i in 0 until pageCount && !cache.containsKey(i) && !loadingPages.contains(i)) {
                loadPage(i)
            }
        }
    }

    private fun loadPage(pageIndex: Int) {
        loadingPages.add(pageIndex)
        scope.launch(Dispatchers.IO) {
            try {
                val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fd)
                if (pageIndex < renderer.pageCount) {
                    val page = renderer.openPage(pageIndex)
                    val upscaleFactor = 2
                    val renderedBitmap = Bitmap.createBitmap(
                        page.width * upscaleFactor,
                        page.height * upscaleFactor,
                        Bitmap.Config.ARGB_8888
                    )
                    page.render(renderedBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    renderer.close()
                    fd.close()
                    withContext(Dispatchers.Main) {
                        cache[pageIndex] = renderedBitmap
                        loadingPages.remove(pageIndex)
                    }
                } else {
                    renderer.close()
                    fd.close()
                    withContext(Dispatchers.Main) {
                        loadingPages.remove(pageIndex)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    loadingPages.remove(pageIndex)
                }
            }
        }
    }

    fun clear() {
        cache.values.forEach { it.recycle() }
        cache.clear()
        loadingPages.clear()
    }
}

@Composable
fun BookNavigationHud(
    currentPage: Int,
    pageCount: Int,
    isLandscape: Boolean,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val pageText = if (isLandscape) {
                val leftPage = (currentPage / 2) * 2
                val rightPage = leftPage + 1
                if (rightPage < pageCount) {
                    "Pages ${leftPage + 1}-${rightPage + 1} of $pageCount"
                } else {
                    "Page ${leftPage + 1} of $pageCount"
                }
            } else {
                "Page ${currentPage + 1} of $pageCount"
            }

            Text(
                text = pageText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "1",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                
                Slider(
                    value = currentPage.toFloat(),
                    onValueChange = { value ->
                        onPageSelected(value.toInt().coerceIn(0, pageCount - 1))
                    },
                    valueRange = 0f..(pageCount - 1).toFloat(),
                    steps = if (pageCount > 2) pageCount - 2 else 0,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                )

                Text(
                    text = "$pageCount",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}


