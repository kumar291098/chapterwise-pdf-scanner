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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.ui.theme.DarkHerbalText
import com.example.ui.theme.DesertSand
import com.example.ui.theme.LightHerbalBg
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.OliveGreen
import com.example.ui.theme.SoftSage
import com.example.ui.theme.WarmDesertSand
import com.example.ui.theme.PureWhite
import com.example.ui.theme.DarkCharcoal
import com.example.ui.theme.CreamBackground
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
                containerColor = LightHerbalBg,
                contentColor = DarkHerbalText,
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
                            tint = SoftSage.copy(alpha = 0.5f),
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
                            color = SoftSage
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = onGenerateSample,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LightHerbalBg,
                                contentColor = DarkHerbalText
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

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Text(
                    text = "New Document Scan Folder",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_book_name_input")
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Short Description or Course Info") },
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
                    colors = ButtonDefaults.textButtonColors(contentColor = OliveGreen),
                    modifier = Modifier.testTag("dialog_confirm_create_book")
                ) {
                    Text("Create Folder", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = SoftSage)
                }
            },
            containerColor = CreamBackground,
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, DesertSand),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Document book icon styled with Natural Tones details
            Box(
                modifier = Modifier
                    .size(width = 60.dp, height = 76.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(WarmDesertSand),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .background(OliveGreen)
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
                        color = OliveGreen
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Icon(
                        imageVector = Icons.Default.Book,
                        contentDescription = null,
                        tint = SoftSage,
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
                        color = SoftSage,
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
                            tint = OliveGreen,
                            modifier = Modifier
                                .size(14.dp)
                                .testTag("pdf_badge_${book.id}")
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "PDF Compiled • ",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = OliveGreen
                        )
                    }

                    Text(
                        text = "Updated: $relativeTime",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 10.sp,
                            fontStyle = FontStyle.Italic
                        ),
                        color = SoftSage.copy(alpha = 0.8f)
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
                    tint = SoftSage
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
                    Text("Cancel", color = SoftSage)
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
                            style = MaterialTheme.typography.bodySmall.copy(color = SoftSage)
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
                                tint = OliveGreen
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
                colors = CardDefaults.cardColors(containerColor = WarmDesertSand),
                border = BorderStroke(1.dp, DesertSand.copy(alpha = 0.5f))
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
                                color = DarkCharcoal
                            )
                            Text(
                                text = "Generates A4 doc with custom layout and notes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SoftSage
                            )
                        }

                        if (compileState is CompileUiState.Compiling) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = OliveGreen
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
                                containerColor = OliveGreen,
                                contentColor = Color.White
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
                                    contentColor = OliveGreen
                                ),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.5.dp, OliveGreen),
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
                    colors = ButtonDefaults.textButtonColors(contentColor = OliveGreen),
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
                            tint = SoftSage.copy(alpha = 0.5f),
                            modifier = Modifier.size(54.dp)
                        )
                        Text(
                            text = "No chapters added yet.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = SoftSage
                        )
                        Text(
                            text = "Add chapters with unique notes and capture physical scan photos or document sheets now.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = SoftSage.copy(alpha = 0.7f),
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
                                    .background(OliveGreen.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
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
                                    color = OliveGreen
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
                                        tint = OliveGreen
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Read Section", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = OliveGreen)
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

        AlertDialog(
            onDismissRequest = { showAddChapterDialog = false },
            title = {
                Text(
                    text = "Add Document Chapter",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_chapter_title_input")
                    )

                    OutlinedTextField(
                        value = section,
                        onValueChange = { section = it },
                        label = { Text("Subsection (e.g. Kinematics, Mechanics)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Study notes, transcript or summary text") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = order,
                        onValueChange = { order = it },
                        label = { Text("Sequence Index (Priority)") },
                        singleLine = true,
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
                    Text("Add Chapter", fontWeight = FontWeight.Bold, color = OliveGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddChapterDialog = false }) {
                    Text("Cancel", color = SoftSage)
                }
            },
            containerColor = CreamBackground,
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

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success && tempPhotoFile != null) {
                val updatedPaths = chapter.scannedImagePaths + tempPhotoFile!!.absolutePath
                onUpdate(chapter.copy(scannedImagePaths = updatedPaths))
                Toast.makeText(context, "Page scan added locally!", Toast.LENGTH_SHORT).show()
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("chapter_item_${chapter.id}"),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, DesertSand.copy(alpha = 0.8f))
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
                            .background(WarmDesertSand),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CollectionsBookmark,
                            contentDescription = null,
                            tint = OliveGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = chapter.title,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = DarkCharcoal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${chapter.scannedImagePaths.size} local page scan(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = SoftSage
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
                        tint = SoftSage
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
                        .background(WarmDesertSand.copy(alpha = 0.5f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (chapter.notes.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "CHAPTER NOTES SUMMARY",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = OliveGreen
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = chapter.notes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = DarkCharcoal
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
                            color = SoftSage
                        )
                        if (chapter.scannedImagePaths.isNotEmpty()) {
                            TextButton(
                                onClick = { showManagePagesDialog = true },
                                colors = ButtonDefaults.textButtonColors(contentColor = OliveGreen),
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
                                .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No page snapshots captured yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SoftSage
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
                                    containerColor = OliveGreen,
                                    contentColor = Color.White
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
                                    containerColor = LightHerbalBg,
                                    contentColor = DarkHerbalText
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1.1f)
                                    .testTag("pdf_upload_button_${chapter.id}")
                            ) {
                                if (isPdfLoading) {
                                    CircularProgressIndicator(
                                        color = OliveGreen,
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.UploadFile, null, modifier = Modifier.size(16.dp), tint = OliveGreen)
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
                                    containerColor = OliveGreen,
                                    contentColor = Color.White
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
                                    contentColor = OliveGreen
                                ),
                                border = BorderStroke(1.dp, OliveGreen),
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

private fun createScanPhotoFile(context: Context): File? {
    val outputDir = File(context.filesDir, "SampleScans")
    if (!outputDir.exists()) outputDir.mkdirs()
    return try {
        File.createTempFile("SCAN_", ".jpg", outputDir)
    } catch (e: IOException) {
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
