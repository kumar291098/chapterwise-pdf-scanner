package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.data.BookDocument
import com.example.data.Chapter
import com.example.ui.BookViewModel
import com.example.ui.CompileUiState
import com.example.ui.SortType
import com.example.ui.screens.PdfViewerScreen
import com.example.ui.components.ManagePagesDialog
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.PrimaryIndigo
import com.example.ui.theme.PrimaryIndigoLight
import com.example.ui.theme.SecondaryViolet
import com.example.ui.theme.AccentTeal
import com.example.ui.theme.PureWhite
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RectF
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import com.example.utils.PdfExtractor
import com.example.utils.GeminiService
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppNavContent()
            }
        }
    }
}

@Composable
fun MainAppNavContent() {
    val viewModel: BookViewModel = viewModel()
    val books by viewModel.booksState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortType by viewModel.sortType.collectAsState()
    val activeBook by viewModel.activeBook.collectAsState()
    val chapters by viewModel.activeBookChapters.collectAsState()
    val compileState by viewModel.compileState.collectAsState()

    var showPdfPath by remember { mutableStateOf<String?>(null) }
    var showPdfName by remember { mutableStateOf("") }

    if (showPdfPath != null) {
        PdfViewerScreen(
            filePath = showPdfPath!!,
            bookName = showPdfName,
            onBack = { showPdfPath = null }
        )
    } else if (activeBook != null) {
        BookDetailsScreen(
            book = activeBook!!,
            chapters = chapters,
            compileState = compileState,
            onBack = { viewModel.selectBook(null) },
            onAddChapter = { title, notes, order, section ->
                viewModel.addChapter(activeBook!!.id, title, notes, order, section)
            },
            onDeleteChapter = { chapter ->
                viewModel.deleteChapter(chapter)
            },
            onUpdateChapter = { chapter ->
                viewModel.updateChapter(chapter)
            },
            onCompile = { viewModel.compileBookPdf(activeBook!!) },
            onViewPdf = { path, title ->
                showPdfName = title
                showPdfPath = path
            },
            viewModel = viewModel
        )
    } else {
        BookListScreen(
            books = books,
            searchQuery = searchQuery,
            sortType = sortType,
            onSearchChange = { viewModel.searchQuery.value = it },
            onSortChange = { viewModel.sortType.value = it },
            onBookSelect = { viewModel.selectBook(it) },
            onCreateBook = { name, desc ->
                viewModel.createBook(name, desc)
            },
            onDeleteBook = { viewModel.deleteBook(it) },
            onGenerateSample = {
                viewModel.createSampleScanBook()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookListScreen(
    books: List<BookDocument>,
    searchQuery: String,
    sortType: SortType,
    onSearchChange: (String) -> Unit,
    onSortChange: (SortType) -> Unit,
    onBookSelect: (Long) -> Unit,
    onCreateBook: (String, String) -> Unit,
    onDeleteBook: (BookDocument) -> Unit,
    onGenerateSample: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Text(
                            text = "My PDF Scans",
                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.5).sp
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(20.dp),
                elevation = FloatingActionButtonDefaults.elevation(6.dp),
                modifier = Modifier
                    .testTag("add_book_fab")
                    .padding(bottom = 16.dp, end = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New PDF Scan",
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Bar & Filter layout matching Natural Tones design Spec
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { onSearchChange(it) },
                    placeholder = { Text("Search scan documents...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("search_field")
                        .heightIn(min = 52.dp)
                )

                Box {
                    Button(
                        onClick = { showSortMenu = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 16.0.dp),
                        modifier = Modifier
                            .testTag("sort_button")
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = "Sort Options",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (sortType) {
                                SortType.NAME_ASC -> "Name A-Z"
                                SortType.NAME_DESC -> "Name Z-A"
                                SortType.DATE_NEWEST -> "Newest"
                                SortType.DATE_OLDEST -> "Oldest"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Alphabetical: A-Z") },
                            onClick = {
                                onSortChange(SortType.NAME_ASC)
                                showSortMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.SortByAlpha, null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.testTag("sort_alpha_asc")
                        )
                        DropdownMenuItem(
                            text = { Text("Alphabetical: Z-A") },
                            onClick = {
                                onSortChange(SortType.NAME_DESC)
                                showSortMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.SortByAlpha, null, modifier = Modifier.size(18.dp)) }
                        )
                        DropdownMenuItem(
                            text = { Text("Last Modified: Newest") },
                            onClick = {
                                onSortChange(SortType.DATE_NEWEST)
                                showSortMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.DateRange, null, modifier = Modifier.size(18.dp)) }
                        )
                        DropdownMenuItem(
                            text = { Text("Last Modified: Oldest") },
                            onClick = {
                                onSortChange(SortType.DATE_OLDEST)
                                showSortMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.DateRange, null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
            }

            if (books.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.widthIn(max = 320.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NoteAdd,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(72.dp)
                        )
                        Text(
                            text = "No Scanned Documents",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Store local documents chapter wise, organize notes, capture snapshots, and compile to print-ready PDF files instantly.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = onGenerateSample,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("generate_sample_button")
                        ) {
                            Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate Sample Document", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("book_list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(books, key = { it.id }) { book ->
                        BookListItem(
                            book = book,
                            onClick = { onBookSelect(book.id) },
                            onDelete = { onDeleteBook(book) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        val textFieldColors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor = MaterialTheme.colorScheme.primary
        )

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Text(
                    text = "New Document Scan Folder",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Document Name (e.g. Physics)") },
                        singleLine = true,
                        colors = textFieldColors,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_book_name_input")
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Short Description or Course Info") },
                        colors = textFieldColors,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isNotBlank()) {
                            onCreateBook(name, description)
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("dialog_confirm_create_book")
                ) {
                    Text("Create Folder", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(28.dp)
        )
    }
}

@Composable
fun BookListItem(
    book: BookDocument,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showConfirmDelete by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("book_item_${book.id}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Document book icon styled with modern theme details
            Box(
                modifier = Modifier
                    .size(width = 60.dp, height = 76.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .background(MaterialTheme.colorScheme.primary)
                        .align(Alignment.TopStart)
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "PDF",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Icon(
                        imageVector = Icons.Default.Book,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1.getFloatValue())) {
                Text(
                    text = book.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        letterSpacing = (-0.2).sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (book.description.isNotEmpty()) {
                    Text(
                        text = book.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                val relativeTime = remember(book.lastModifiedAt) {
                    val seconds = (System.currentTimeMillis() - book.lastModifiedAt) / 1000
                    when {
                        seconds < 60 -> "Just now"
                        seconds < 3600 -> "${seconds / 60}m ago"
                        seconds < 86400 -> "${seconds / 3600}h ago"
                        else -> {
                            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(book.lastModifiedAt))
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    if (book.pdfPath != null) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "PDF Ready",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(14.dp)
                                .testTag("pdf_badge_${book.id}")
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "PDF Compiled • ",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = "Updated: $relativeTime",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 10.sp,
                            fontStyle = FontStyle.Italic
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            IconButton(
                onClick = { showConfirmDelete = true },
                modifier = Modifier.testTag("delete_book_button_${book.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete document folder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showConfirmDelete) {
        AlertDialog(
            onDismissRequest = { showConfirmDelete = false },
            title = { Text("Delete Document Folder?") },
            text = { Text("This will permanently remove the folder '${book.name}' and all of its compiled PDF files and chapter scans.") },
            confirmButton = {
                    IconButton(
                        onClick = {
                            onDelete()
                            showConfirmDelete = false
                        },
                        modifier = Modifier.testTag("confirm_delete_dialog_button")
                    ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDelete = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailsScreen(
    book: BookDocument,
    chapters: List<Chapter>,
    compileState: CompileUiState,
    onBack: () -> Unit,
    onAddChapter: (String, String, Int, String) -> Unit,
    onDeleteChapter: (Chapter) -> Unit,
    onUpdateChapter: (Chapter) -> Unit,
    onCompile: () -> Unit,
    onViewPdf: (String, String) -> Unit,
    viewModel: BookViewModel
) {
    val context = LocalContext.current
    var showAddChapterDialog by remember { mutableStateOf(false) }

    LaunchedEffect(compileState) {
        if (compileState is CompileUiState.Success) {
            Toast.makeText(context, "PDF Compiled successfully!", Toast.LENGTH_SHORT).show()
        } else if (compileState is CompileUiState.Error) {
            Toast.makeText(context, compileState.message, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = book.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (chapters.isEmpty()) "0 Chapters" else "${chapters.size} local chapters",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("back_to_books_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (book.pdfPath != null) {
                        IconButton(
                            onClick = { onViewPdf(book.pdfPath, book.name) },
                            modifier = Modifier.testTag("view_existing_pdf_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = "View Compiled PDF",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // PDF Compiler State Controller
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Local PDF Assembler",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Generates A4 doc with custom layout and notes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (compileState is CompileUiState.Compiling) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onCompile,
                            enabled = chapters.isNotEmpty() && compileState !is CompileUiState.Compiling,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("compile_pdf_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (compileState is CompileUiState.Compiling) "Compiling..." else "Build PDF",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (book.pdfPath != null) {
                            OutlinedButton(
                                onClick = { onViewPdf(book.pdfPath, book.name) },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("preview_pdf_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Read Book", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Chapters Sequence",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )

                TextButton(
                    onClick = { showAddChapterDialog = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("add_chapter_text_button")
                ) {
                    Icon(Icons.Default.AddCircleOutline, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Chapter", fontWeight = FontWeight.Bold)
                }
            }

            if (chapters.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LayersClear,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(54.dp)
                        )
                        Text(
                            text = "No chapters added yet.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Add chapters with unique notes and capture physical scan photos or document sheets now.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.widthIn(max = 280.dp)
                        )
                    }
                }
            } else {
                val groupedChapters = remember(chapters) {
                    chapters.groupBy { it.section.ifBlank { "General" } }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("chapter_list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    groupedChapters.forEach { (sectionName, sectionChapters) ->
                        item(key = "section_header_$sectionName") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📂  $sectionName".uppercase(),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                
                                TextButton(
                                    onClick = {
                                        viewModel.compileSectionPdf(book, sectionName, sectionChapters,
                                            onComplete = { file ->
                                                onViewPdf(file.absolutePath, "${book.name} - $sectionName")
                                            },
                                            onError = { errorMsg ->
                                                Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PictureAsPdf,
                                        contentDescription = "Read Section",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Read Section", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        items(sectionChapters, key = { it.id }) { chapter ->
                            ChapterListItem(
                                chapter = chapter,
                                onDelete = { onDeleteChapter(chapter) },
                                onUpdate = onUpdateChapter,
                                viewModel = viewModel,
                                onViewPdf = onViewPdf
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddChapterDialog) {
        var title by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }
        var section by remember { mutableStateOf("") }
        var order by remember { mutableStateOf((chapters.size + 1).toString()) }
        val textFieldColors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor = MaterialTheme.colorScheme.primary
        )

        AlertDialog(
            onDismissRequest = { showAddChapterDialog = false },
            title = {
                Text(
                    text = "Add Document Chapter",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Chapter Title (e.g. 1. Introduction)") },
                        singleLine = true,
                        colors = textFieldColors,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_chapter_title_input")
                    )

                    OutlinedTextField(
                        value = section,
                        onValueChange = { section = it },
                        label = { Text("Subsection (e.g. Kinematics, Mechanics)") },
                        singleLine = true,
                        colors = textFieldColors,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Study notes, transcript or summary text") },
                        minLines = 3,
                        colors = textFieldColors,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = order,
                        onValueChange = { order = it },
                        label = { Text("Sequence Index (Priority)") },
                        singleLine = true,
                        colors = textFieldColors,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (title.isNotBlank()) {
                            val orderInt = order.toIntOrNull() ?: (chapters.size + 1)
                            onAddChapter(title, notes, orderInt, section)
                            showAddChapterDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_add_chapter_button")
                ) {
                    Text("Add Chapter", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddChapterDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(28.dp)
        )
    }
}

@Composable
fun ChapterListItem(
    chapter: Chapter,
    onDelete: () -> Unit,
    onUpdate: (Chapter) -> Unit,
    viewModel: BookViewModel,
    onViewPdf: (String, String) -> Unit
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var activeImportMeta by remember { mutableStateOf<com.example.utils.PdfExtractor.PdfMeta?>(null) }
    var isPdfLoading by remember { mutableStateOf(false) }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isPdfLoading = true
            coroutineScope.launch {
                try {
                    val meta = com.example.utils.PdfExtractor.getPdfMetadata(context, uri)
                    if (meta != null) {
                        activeImportMeta = meta
                    } else {
                        Toast.makeText(context, "Error: Could not copy or read selected PDF.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "Error processing PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                } finally {
                    isPdfLoading = false
                }
            }
        }
    }

    if (activeImportMeta != null) {
        PdfImportWizardDialog(
            meta = activeImportMeta!!,
            onDismiss = { activeImportMeta = null },
            onImportComplete = { imagePaths, genNotes ->
                var finalNotes = chapter.notes
                if (genNotes.isNotEmpty()) {
                    finalNotes = if (finalNotes.isNotEmpty()) {
                        "$finalNotes\n\n### AI SUMMARY NOTES FROM PDF\n$genNotes"
                    } else {
                        genNotes
                    }
                }
                val finalPaths = chapter.scannedImagePaths + imagePaths
                onUpdate(chapter.copy(notes = finalNotes, scannedImagePaths = finalPaths))
                activeImportMeta = null
                Toast.makeText(context, "PDF content processed and notes imported successfully!", Toast.LENGTH_LONG).show()
            }
        )
    }

    // Setup photo scanner launch intent
    val cameraPermission = Manifest.permission.CAMERA
    var tempPhotoFile by remember { mutableStateOf<File?>(null) }
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingAdjustmentFile by remember { mutableStateOf<File?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success && tempPhotoFile != null) {
                pendingAdjustmentFile = tempPhotoFile
            } else {
                tempPhotoFile?.delete()
            }
        }
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                val file = createScanPhotoFile(context)
                if (file != null) {
                    tempPhotoFile = file
                    val uri = FileProvider.getUriForFile(
                        context,
                        "com.aistudio.chapterwisepdf.fileprovider",
                        file
                    )
                    tempPhotoUri = uri
                    cameraLauncher.launch(uri)
                }
            } else {
                Toast.makeText(context, "Camera permission required to scan paper documents.", Toast.LENGTH_SHORT).show()
            }
        }
    )

    pendingAdjustmentFile?.let { scanFile ->
        ScanPageAdjustmentDialog(
            imageFile = scanFile,
            onDismiss = {
                scanFile.delete()
                pendingAdjustmentFile = null
                tempPhotoFile = null
                tempPhotoUri = null
            },
            onConfirm = { adjustedPath ->
                val updatedPaths = chapter.scannedImagePaths + adjustedPath
                onUpdate(chapter.copy(scannedImagePaths = updatedPaths))
                pendingAdjustmentFile = null
                tempPhotoFile = null
                tempPhotoUri = null
                Toast.makeText(context, "Portrait page scan added locally!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chapter_item_${chapter.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .clickable { isExpanded = !isExpanded }
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CollectionsBookmark,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = chapter.title,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${chapter.scannedImagePaths.size} local page scan(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.testTag("expand_arrow_${chapter.id}")
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand content details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (chapter.notes.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "CHAPTER NOTES SUMMARY",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = chapter.notes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Scanned page flow
                    var showManagePagesDialog by remember { mutableStateOf(false) }

                    if (showManagePagesDialog) {
                        ManagePagesDialog(
                            chapter = chapter,
                            onDismiss = { showManagePagesDialog = false },
                            viewModel = viewModel
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Scanned Page Thumbnails",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (chapter.scannedImagePaths.isNotEmpty()) {
                            TextButton(
                                onClick = { showManagePagesDialog = true },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(Icons.Default.Settings, null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Adjust Pages", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (chapter.scannedImagePaths.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No page snapshots captured yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 80.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(chapter.scannedImagePaths) { imgPath ->
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(0.707f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.LightGray)
                                ) {
                                    AsyncImage(
                                        model = imgPath,
                                        contentDescription = "Chapter page design",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    // Remove page button
                                    IconButton(
                                        onClick = {
                                            val updated = chapter.scannedImagePaths.filter { it != imgPath }
                                            onUpdate(chapter.copy(scannedImagePaths = updated))
                                            val f = File(imgPath)
                                            if (f.exists()) f.delete()
                                        },
                                        modifier = Modifier
                                            .size(24.dp)
                                            .align(Alignment.TopEnd)
                                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                            .padding(2.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove page picture",
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Scan, PDF, and Simulator Actions
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        cameraPermission
                                    ) == PackageManager.PERMISSION_GRANTED

                                    if (hasPermission) {
                                        val file = createScanPhotoFile(context)
                                        if (file != null) {
                                            tempPhotoFile = file
                                            val uri = FileProvider.getUriForFile(
                                                context,
                                                "com.aistudio.chapterwisepdf.fileprovider",
                                                file
                                            )
                                            tempPhotoUri = uri
                                            cameraLauncher.launch(uri)
                                        }
                                    } else {
                                        permissionLauncher.launch(cameraPermission)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1.0f)
                                    .testTag("camera_scan_button_${chapter.id}")
                            ) {
                                Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Camera Scan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    if (!isPdfLoading) {
                                        pdfLauncher.launch("application/pdf")
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1.1f)
                                    .testTag("pdf_upload_button_${chapter.id}")
                            ) {
                                if (isPdfLoading) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.onSecondary,
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.UploadFile, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSecondary)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Upload PDF Notes", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    viewModel.compileChapterPdf(chapter,
                                        onComplete = { file ->
                                            onViewPdf(file.absolutePath, chapter.title)
                                        },
                                        onError = { errorMsg ->
                                            Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("read_chapter_button_${chapter.id}")
                            ) {
                                Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Read Chapter", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    val mockFile = generateLocalMockScanImageFile(context, chapter.title)
                                    if (mockFile != null) {
                                        val updated = chapter.scannedImagePaths + mockFile.absolutePath
                                        onUpdate(chapter.copy(scannedImagePaths = updated))
                                        Toast.makeText(context, "Mock note sheet appended!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("mock_scan_button_${chapter.id}")
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Simulator Scan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier
                                    .height(40.dp)
                                    .testTag("delete_chapter_button_${chapter.id}")
                                    .background(Color(0xFFFFEEEE), RoundedCornerShape(12.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete chapter",
                                    tint = Color.Red,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Inline helper to avoid raw integer literal floats
private fun Int.getFloatValue(): Float {
    return this.toFloat()
}

private enum class HandleType {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
}

private data class NormalizedPoint(val x: Float, val y: Float)
private data class EraseStroke(val points: List<NormalizedPoint>, val brushWidthPercent: Float)

@Composable
private fun ScanPageAdjustmentDialog(
    imageFile: File,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var pageScale by remember { androidx.compose.runtime.mutableFloatStateOf(0.82f) }
    var previewBitmap by remember(imageFile.absolutePath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    var cropL by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var cropT by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var cropR by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    var cropB by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }

    var rotationAngle by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var selectedFilter by remember { mutableStateOf("Original") }
    val eraseStrokes = remember { androidx.compose.runtime.mutableStateListOf<EraseStroke>() }
    var brushSize by remember { androidx.compose.runtime.mutableFloatStateOf(30f) }
    var brightness by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var contrast by remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
    var editMode by remember { androidx.compose.runtime.mutableIntStateOf(0) } // 0: Crop/Rotate/Scale, 1: Filters/Contrast, 2: Eraser

    var previewSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var currentStroke by remember { mutableStateOf<List<NormalizedPoint>>(emptyList()) }
    var activeHandle by remember { mutableStateOf<HandleType?>(null) }

    LaunchedEffect(imageFile.absolutePath, rotationAngle, selectedFilter, brightness, contrast, editMode) {
        val edited = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val useCropL = if (editMode == 0) 0f else cropL
            val useCropT = if (editMode == 0) 0f else cropT
            val useCropR = if (editMode == 0) 1f else cropR
            val useCropB = if (editMode == 0) 1f else cropB
            buildPortraitPreviewBitmap(
                sourcePath = imageFile.absolutePath,
                rotation = rotationAngle,
                cropL = useCropL, cropT = useCropT, cropR = useCropR, cropB = useCropB,
                filter = selectedFilter,
                eraseStrokes = emptyList(), // Draw eraseStrokes on canvas overlay for UI fluidity
                brightness = brightness,
                contrast = contrast,
                pageScale = 1.0f,
                pageWidth = 720,
                pageHeight = 1018
            )
        }
        previewBitmap = edited
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = {
            if (!isSaving) onDismiss()
        },
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0F120E)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // 1. Top Control Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isSaving,
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
                    }

                    Text(
                        text = "Page Scan Optimizer",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    Button(
                        onClick = {
                            isSaving = true
                            coroutineScope.launch {
                                val adjustedFile = createPortraitAdjustedScanFile(
                                    context = context,
                                    sourcePath = imageFile.absolutePath,
                                    rotation = rotationAngle,
                                    cropL = cropL, cropT = cropT, cropR = cropR, cropB = cropB,
                                    filter = selectedFilter,
                                    eraseStrokes = eraseStrokes,
                                    brightness = brightness,
                                    contrast = contrast,
                                    pageScale = pageScale
                                )
                                isSaving = false
                                if (adjustedFile != null) {
                                    imageFile.delete()
                                    onConfirm(adjustedFile.absolutePath)
                                } else {
                                    Toast.makeText(context, "Could not adjust this page scan.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !isSaving,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.height(40.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Check, contentDescription = "Save", modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }

                // 2. Central Image Preview Viewport
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF0F172A))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(0.707f)
                            .clip(RoundedCornerShape(10.dp))
                    ) {
                        previewBitmap?.let { bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Scanned Page Preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .onGloballyPositioned { coordinates ->
                                        previewSize = coordinates.size
                                    }
                            )
                        } ?: CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)

                        if (previewSize.width > 0) {
                            val screenW = previewSize.width.toFloat()
                            val screenH = previewSize.height.toFloat()

                            val cropModifier = if (editMode == 0) {
                                Modifier.pointerInput(editMode) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            val w = size.width.toFloat()
                                            val h = size.height.toFloat()
                                            activeHandle = getActiveHandle(offset, cropL, cropT, cropR, cropB, w, h)
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val w = size.width.toFloat()
                                            val h = size.height.toFloat()
                                            if (w > 0 && h > 0) {
                                                val x = (change.position.x / w).coerceIn(0f, 1f)
                                                val y = (change.position.y / h).coerceIn(0f, 1f)
                                                when (activeHandle) {
                                                    HandleType.TOP_LEFT -> {
                                                        cropL = x.coerceAtMost(cropR - 0.1f)
                                                        cropT = y.coerceAtMost(cropB - 0.1f)
                                                    }
                                                    HandleType.TOP_RIGHT -> {
                                                        cropR = x.coerceAtLeast(cropL + 0.1f)
                                                        cropT = y.coerceAtMost(cropB - 0.1f)
                                                    }
                                                    HandleType.BOTTOM_LEFT -> {
                                                        cropL = x.coerceAtMost(cropR - 0.1f)
                                                        cropB = y.coerceAtLeast(cropT + 0.1f)
                                                    }
                                                    HandleType.BOTTOM_RIGHT -> {
                                                        cropR = x.coerceAtLeast(cropL + 0.1f)
                                                        cropB = y.coerceAtLeast(cropT + 0.1f)
                                                    }
                                                    null -> {}
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            activeHandle = null
                                        }
                                    )
                                }
                            } else Modifier

                            val eraseModifier = if (editMode == 2) {
                                Modifier.pointerInput(editMode) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            val w = size.width.toFloat()
                                            val h = size.height.toFloat()
                                            if (w > 0 && h > 0) {
                                                val xCrop = (offset.x / w).coerceIn(0f, 1f)
                                                val yCrop = (offset.y / h).coerceIn(0f, 1f)
                                                val xFull = cropL + xCrop * (cropR - cropL)
                                                val yFull = cropT + yCrop * (cropB - cropT)
                                                currentStroke = listOf(NormalizedPoint(xFull, yFull))
                                            }
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val w = size.width.toFloat()
                                            val h = size.height.toFloat()
                                            if (w > 0 && h > 0) {
                                                val xCrop = (change.position.x / w).coerceIn(0f, 1f)
                                                val yCrop = (change.position.y / h).coerceIn(0f, 1f)
                                                val xFull = cropL + xCrop * (cropR - cropL)
                                                val yFull = cropT + yCrop * (cropB - cropT)
                                                currentStroke = currentStroke + NormalizedPoint(xFull, yFull)
                                            }
                                        },
                                        onDragEnd = {
                                            val w = size.width.toFloat()
                                            if (w > 0 && currentStroke.isNotEmpty()) {
                                                eraseStrokes.add(EraseStroke(currentStroke, brushSize / w))
                                            }
                                            currentStroke = emptyList()
                                        }
                                    )
                                }
                            } else Modifier

                            androidx.compose.foundation.Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(cropModifier)
                                    .then(eraseModifier)
                            ) {
                                // Draw Crop Overlay Box & Handles
                                if (editMode == 0) {
                                    val rectL = cropL * screenW
                                    val rectT = cropT * screenH
                                    val rectR = cropR * screenW
                                    val rectB = cropB * screenH

                                    // Outer shaded areas
                                    drawRect(
                                        color = Color.Black.copy(alpha = 0.55f),
                                        topLeft = Offset(0f, 0f),
                                        size = androidx.compose.ui.geometry.Size(screenW, rectT)
                                    )
                                    drawRect(
                                        color = Color.Black.copy(alpha = 0.55f),
                                        topLeft = Offset(0f, rectB),
                                        size = androidx.compose.ui.geometry.Size(screenW, screenH - rectB)
                                    )
                                    drawRect(
                                        color = Color.Black.copy(alpha = 0.55f),
                                        topLeft = Offset(0f, rectT),
                                        size = androidx.compose.ui.geometry.Size(rectL, rectB - rectT)
                                    )
                                    drawRect(
                                        color = Color.Black.copy(alpha = 0.55f),
                                        topLeft = Offset(rectR, rectT),
                                        size = androidx.compose.ui.geometry.Size(screenW - rectR, rectB - rectT)
                                    )

                                    // White cropped borders
                                    drawRect(
                                        color = Color.White,
                                        topLeft = Offset(rectL, rectT),
                                        size = androidx.compose.ui.geometry.Size(rectR - rectL, rectB - rectT),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                                    )

                                    // Handle dots
                                    val handleRadius = 10.dp.toPx()
                                    drawCircle(color = PrimaryIndigo, radius = handleRadius, center = Offset(rectL, rectT))
                                    drawCircle(color = PrimaryIndigo, radius = handleRadius, center = Offset(rectR, rectT))
                                    drawCircle(color = PrimaryIndigo, radius = handleRadius, center = Offset(rectL, rectB))
                                    drawCircle(color = PrimaryIndigo, radius = handleRadius, center = Offset(rectR, rectB))
                                }

                                // Draw Erase Strokes
                                val useCropL = if (editMode == 0) 0f else cropL
                                val useCropT = if (editMode == 0) 0f else cropT
                                val useCropR = if (editMode == 0) 1f else cropR
                                val useCropB = if (editMode == 0) 1f else cropB
                                val divX = (useCropR - useCropL).coerceAtLeast(0.01f)
                                val divY = (useCropB - useCropT).coerceAtLeast(0.01f)

                                for (stroke in eraseStrokes) {
                                    if (stroke.points.isEmpty()) continue
                                    val path = androidx.compose.ui.graphics.Path()
                                    var isFirst = true
                                    for (pt in stroke.points) {
                                        val sx = (pt.x - useCropL) / divX * screenW
                                        val sy = (pt.y - useCropT) / divY * screenH
                                        if (isFirst) {
                                            path.moveTo(sx, sy)
                                            isFirst = false
                                        } else {
                                            path.lineTo(sx, sy)
                                        }
                                    }
                                    drawPath(
                                        path = path,
                                        color = Color.White,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                                            width = stroke.brushWidthPercent * screenW,
                                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                                        )
                                    )
                                }

                                // Draw Active Drag Stroke
                                if (currentStroke.isNotEmpty()) {
                                    val path = androidx.compose.ui.graphics.Path()
                                    var isFirst = true
                                    for (pt in currentStroke) {
                                        val sx = (pt.x - useCropL) / divX * screenW
                                        val sy = (pt.y - useCropT) / divY * screenH
                                        if (isFirst) {
                                            path.moveTo(sx, sy)
                                            isFirst = false
                                        } else {
                                            path.lineTo(sx, sy)
                                        }
                                    }
                                    drawPath(
                                        path = path,
                                        color = Color.White,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                                            width = brushSize,
                                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Bottom Controls Studio Panel
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        when (editMode) {
                            0 -> { // Format Tab
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Page Margins (Scale)",
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = "${(pageScale * 100).toInt()}%",
                                            color = PrimaryIndigoLight,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                    Slider(
                                        value = pageScale,
                                        onValueChange = { pageScale = it },
                                        valueRange = 0.55f..0.96f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = PrimaryIndigoLight,
                                            activeTrackColor = PrimaryIndigoLight,
                                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                rotationAngle = (rotationAngle + 90f) % 360f
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.RotateRight, contentDescription = null, tint = Color.White)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Rotate 90°", color = Color.White)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                cropL = 0f
                                                cropT = 0f
                                                cropR = 1f
                                                cropB = 1f
                                                rotationAngle = 0f
                                                pageScale = 0.82f
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Reset", color = Color.White)
                                        }
                                    }
                                }
                            }
                            1 -> { // Enhance Filters Tab
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val filters = listOf("Original", "B&W", "Grayscale", "Magic Color")
                                        filters.forEach { filterName ->
                                            val isSelected = selectedFilter == filterName
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSelected) PrimaryIndigo else Color.White.copy(alpha = 0.05f))
                                                    .clickable { selectedFilter = filterName }
                                                    .padding(vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = filterName,
                                                    color = Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Brightness", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                                            Text("${brightness.toInt()}", color = PrimaryIndigoLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Slider(
                                            value = brightness,
                                            onValueChange = { brightness = it },
                                            valueRange = -100f..100f,
                                            colors = SliderDefaults.colors(
                                                thumbColor = PrimaryIndigoLight,
                                                activeTrackColor = PrimaryIndigoLight,
                                                inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                                            )
                                        )
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Contrast", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                                            Text(String.format("%.1fx", contrast), color = PrimaryIndigoLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Slider(
                                            value = contrast,
                                            onValueChange = { contrast = it },
                                            valueRange = 0.5f..2.5f,
                                            colors = SliderDefaults.colors(
                                                thumbColor = PrimaryIndigoLight,
                                                activeTrackColor = PrimaryIndigoLight,
                                                inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                                            )
                                        )
                                    }
                                }
                            }
                            2 -> { // Eraser Brush Tab
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Brush Thickness",
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = "${brushSize.toInt()} px",
                                            color = PrimaryIndigoLight,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                    Slider(
                                        value = brushSize,
                                        onValueChange = { brushSize = it },
                                        valueRange = 10f..120f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = PrimaryIndigoLight,
                                            activeTrackColor = PrimaryIndigoLight,
                                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                if (eraseStrokes.isNotEmpty()) {
                                                    eraseStrokes.removeAt(eraseStrokes.size - 1)
                                                }
                                            },
                                            enabled = eraseStrokes.isNotEmpty(),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Undo, contentDescription = null, tint = Color.White)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Undo", color = Color.White)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                eraseStrokes.clear()
                                            },
                                            enabled = eraseStrokes.isNotEmpty(),
                                            shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color.White)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Clear", color = Color.White)
                                        }
                                    }
                                }
                            }
                        }

                        androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                        TabRow(
                            selectedTabIndex = editMode,
                            containerColor = Color.Transparent,
                            contentColor = PrimaryIndigoLight
                        ) {
                            Tab(
                                selected = editMode == 0,
                                onClick = { editMode = 0 },
                                text = { Text("Crop & Layout", fontWeight = FontWeight.Bold, color = if (editMode == 0) Color.White else Color.White.copy(alpha = 0.5f)) },
                                icon = { Icon(Icons.Default.Crop, contentDescription = null, tint = if (editMode == 0) PrimaryIndigoLight else Color.White.copy(alpha = 0.5f)) }
                            )
                            Tab(
                                selected = editMode == 1,
                                onClick = { editMode = 1 },
                                text = { Text("Enhancements", fontWeight = FontWeight.Bold, color = if (editMode == 1) Color.White else Color.White.copy(alpha = 0.5f)) },
                                icon = { Icon(Icons.Default.ColorLens, contentDescription = null, tint = if (editMode == 1) PrimaryIndigoLight else Color.White.copy(alpha = 0.5f)) }
                            )
                            Tab(
                                selected = editMode == 2,
                                onClick = { editMode = 2 },
                                text = { Text("Erase Part", fontWeight = FontWeight.Bold, color = if (editMode == 2) Color.White else Color.White.copy(alpha = 0.5f)) },
                                icon = { Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = if (editMode == 2) PrimaryIndigoLight else Color.White.copy(alpha = 0.5f)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getActiveHandle(
    offset: Offset,
    cropL: Float, cropT: Float, cropR: Float, cropB: Float,
    width: Float, height: Float
): HandleType? {
    val clickRadius = 40f * 3f
    val tl = Offset(cropL * width, cropT * height)
    if ((offset - tl).getDistance() < clickRadius) return HandleType.TOP_LEFT
    val tr = Offset(cropR * width, cropT * height)
    if ((offset - tr).getDistance() < clickRadius) return HandleType.TOP_RIGHT
    val bl = Offset(cropL * width, cropB * height)
    if ((offset - bl).getDistance() < clickRadius) return HandleType.BOTTOM_LEFT
    val br = Offset(cropR * width, cropB * height)
    if ((offset - br).getDistance() < clickRadius) return HandleType.BOTTOM_RIGHT
    return null
}

private fun processScannedBitmap(
    sourcePath: String,
    rotation: Float,
    cropL: Float, cropT: Float, cropR: Float, cropB: Float,
    filter: String,
    eraseStrokes: List<EraseStroke>,
    brightness: Float,
    contrast: Float
): android.graphics.Bitmap? {
    val original = android.graphics.BitmapFactory.decodeFile(sourcePath) ?: return null
    var workingBitmap = if (original.width > original.height) {
        val matrix = android.graphics.Matrix().apply { postRotate(90f) }
        android.graphics.Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true).also {
            original.recycle()
        }
    } else {
        original
    }

    // Copy to make it a mutable bitmap so we can draw on it using Canvas
    workingBitmap = workingBitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, true).also {
        if (it != workingBitmap) workingBitmap.recycle()
    }

    if (rotation != 0f) {
        val matrix = android.graphics.Matrix().apply { postRotate(rotation) }
        val rotated = android.graphics.Bitmap.createBitmap(workingBitmap, 0, 0, workingBitmap.width, workingBitmap.height, matrix, true).also {
            workingBitmap.recycle()
        }
        workingBitmap = rotated.copy(android.graphics.Bitmap.Config.ARGB_8888, true).also {
            if (it != rotated) rotated.recycle()
        }
    }

    // 1. Draw erase strokes on rotated bitmap before cropping (to simplify coordinate mapping)
    if (eraseStrokes.isNotEmpty()) {
        val eraseCanvas = android.graphics.Canvas(workingBitmap)
        val erasePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
            isAntiAlias = true
        }

        for (stroke in eraseStrokes) {
            if (stroke.points.isEmpty()) continue
            erasePaint.strokeWidth = stroke.brushWidthPercent * workingBitmap.width
            val path = android.graphics.Path()
            val first = stroke.points.first()
            path.moveTo(first.x * workingBitmap.width, first.y * workingBitmap.height)
            for (i in 1 until stroke.points.size) {
                val pt = stroke.points[i]
                path.lineTo(pt.x * workingBitmap.width, pt.y * workingBitmap.height)
            }
            eraseCanvas.drawPath(path, erasePaint)
        }
    }

    // 2. Crop rotated and erased bitmap
    val width = workingBitmap.width
    val height = workingBitmap.height
    val leftPx = (cropL * width).toInt().coerceIn(0, width - 10)
    val topPx = (cropT * height).toInt().coerceIn(0, height - 10)
    val rightPx = (cropR * width).toInt().coerceIn(leftPx + 10, width)
    val bottomPx = (cropB * height).toInt().coerceIn(topPx + 10, height)
    val cropW = rightPx - leftPx
    val cropH = bottomPx - topPx

    val croppedBitmap = android.graphics.Bitmap.createBitmap(workingBitmap, leftPx, topPx, cropW, cropH).also {
        workingBitmap.recycle()
    }

    // 3. Apply contrast, brightness and filter
    val filterBitmap = android.graphics.Bitmap.createBitmap(croppedBitmap.width, croppedBitmap.height, android.graphics.Bitmap.Config.ARGB_8888)
    val filterCanvas = android.graphics.Canvas(filterBitmap)
    val filterPaint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
        isAntiAlias = true
    }

    val cm = android.graphics.ColorMatrix()
    val contrastMatrix = floatArrayOf(
        contrast, 0f, 0f, 0f, brightness,
        0f, contrast, 0f, 0f, brightness,
        0f, 0f, contrast, 0f, brightness,
        0f, 0f, 0f, 1f, 0f
    )
    cm.set(contrastMatrix)

    when (filter) {
        "Grayscale" -> {
            val grayMatrix = android.graphics.ColorMatrix().apply { setSaturation(0f) }
            grayMatrix.postConcat(cm)
            filterPaint.colorFilter = android.graphics.ColorMatrixColorFilter(grayMatrix)
        }
        "B&W" -> {
            val satMatrix = android.graphics.ColorMatrix().apply { setSaturation(0f) }
            val bwMatrix = floatArrayOf(
                3.5f, 3.5f, 3.5f, 0f, -320f,
                3.5f, 3.5f, 3.5f, 0f, -320f,
                3.5f, 3.5f, 3.5f, 0f, -320f,
                0f, 0f, 0f, 1f, 0f
            )
            val combined = android.graphics.ColorMatrix(bwMatrix)
            combined.postConcat(cm)
            filterPaint.colorFilter = android.graphics.ColorMatrixColorFilter(combined)
        }
        "Magic Color" -> {
            val satMatrix = android.graphics.ColorMatrix().apply { setSaturation(1.4f) }
            val magicMatrix = floatArrayOf(
                1.3f, 0f, 0f, 0f, -15f,
                0f, 1.3f, 0f, 0f, -15f,
                0f, 0f, 1.3f, 0f, -15f,
                0f, 0f, 0f, 1f, 0f
            )
            val combined = android.graphics.ColorMatrix(magicMatrix)
            combined.preConcat(satMatrix)
            combined.postConcat(cm)
            filterPaint.colorFilter = android.graphics.ColorMatrixColorFilter(combined)
        }
        else -> {
            filterPaint.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
        }
    }

    filterCanvas.drawBitmap(croppedBitmap, 0f, 0f, filterPaint)
    croppedBitmap.recycle()
    return filterBitmap
}

private fun createPortraitAdjustedScanFile(
    context: android.content.Context,
    sourcePath: String,
    rotation: Float,
    cropL: Float, cropT: Float, cropR: Float, cropB: Float,
    filter: String,
    eraseStrokes: List<EraseStroke>,
    brightness: Float,
    contrast: Float,
    pageScale: Float
): File? {
    val edited = processScannedBitmap(sourcePath, rotation, cropL, cropT, cropR, cropB, filter, eraseStrokes, brightness, contrast) ?: return null
    val pageWidth = 1240
    val pageHeight = 1754
    val page = android.graphics.Bitmap.createBitmap(pageWidth, pageHeight, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(page)
    canvas.drawColor(android.graphics.Color.WHITE)

    val maxWidth = pageWidth * pageScale
    val maxHeight = pageHeight * pageScale
    val scale = minOf(maxWidth / edited.width, maxHeight / edited.height)
    val targetWidth = edited.width * scale
    val targetHeight = edited.height * scale
    val left = (pageWidth - targetWidth) / 2f
    val top = (pageHeight - targetHeight) / 2f
    val dest = android.graphics.Rect(left.toInt(), top.toInt(), (left + targetWidth).toInt(), (top + targetHeight).toInt())

    canvas.drawBitmap(edited, android.graphics.Rect(0, 0, edited.width, edited.height), dest, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
    edited.recycle()

    val outputDir = File(context.filesDir, "ScannedPages")
    if (!outputDir.exists()) outputDir.mkdirs()

    val outputFile = File(outputDir, "portrait_scan_${System.currentTimeMillis()}.jpg")
    return try {
        java.io.FileOutputStream(outputFile).use { output ->
            page.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, output)
        }
        page.recycle()
        outputFile
    } catch (e: java.io.IOException) {
        e.printStackTrace()
        page.recycle()
        null
    }
}

private fun buildPortraitPreviewBitmap(
    sourcePath: String,
    rotation: Float,
    cropL: Float, cropT: Float, cropR: Float, cropB: Float,
    filter: String,
    eraseStrokes: List<EraseStroke>,
    brightness: Float,
    contrast: Float,
    pageScale: Float,
    pageWidth: Int,
    pageHeight: Int
): android.graphics.Bitmap? {
    return processScannedBitmap(sourcePath, rotation, cropL, cropT, cropR, cropB, filter, eraseStrokes, brightness, contrast)
}

private fun createScanPhotoFile(context: Context): File? {
    val outputDir = File(context.filesDir, "SampleScans")
    if (!outputDir.exists()) outputDir.mkdirs()
    return try {
        File.createTempFile("SCAN_", ".jpg", outputDir)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun generateLocalMockScanImageFile(context: Context, chapterName: String): File? {
    val outputDir = File(context.filesDir, "SampleScans")
    if (!outputDir.exists()) outputDir.mkdirs()

    val file = File(outputDir, "mock_${System.currentTimeMillis()}.jpg")
    try {
        val bitmap = Bitmap.createBitmap(720, 960, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.parseColor("#FCFAF2"))

        val paint = Paint().apply {
            isAntiAlias = true
        }

        // Draw margins
        paint.color = android.graphics.Color.parseColor("#E6C5C5")
        paint.strokeWidth = 2f
        canvas.drawLine(80f, 0f, 80f, 960f, paint)

        // Draw note lines
        paint.color = android.graphics.Color.parseColor("#D9E3EB")
        paint.strokeWidth = 1f
        var y = 130f
        while (y < 960f) {
            canvas.drawLine(0f, y, 720f, y, paint)
            y += 40f
        }

        // Text title
        paint.color = android.graphics.Color.parseColor("#2B3E50")
        paint.textSize = 28f
        paint.isFakeBoldText = true
        canvas.drawText(chapterName, 100f, 95f, paint)

        // Subtext / Mock note derivations
        paint.color = android.graphics.Color.parseColor("#4A5568")
        paint.textSize = 16f
        paint.isFakeBoldText = false
        canvas.drawText("Equation:  f(x) = ∫ e^(-t) • dt (from 0 to ∞)", 100f, 190f, paint)
        canvas.drawText("Definition: Limit proof for continuous derivative arrays.", 100f, 230f, paint)
        canvas.drawText("Note: Study diagram in Chapter 3 appendix.", 100f, 270f, paint)

        // Red stamp
        paint.color = android.graphics.Color.parseColor("#40E53E3E")
        paint.textSize = 34f
        paint.isFakeBoldText = true
        canvas.save()
        canvas.rotate(-15f, 360f, 480f)
        canvas.drawText("CHAPTER SCAN COMPLETED", 140f, 480f, paint)
        canvas.restore()

        val output = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
        output.flush()
        output.close()
        bitmap.recycle()
        return file
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}
