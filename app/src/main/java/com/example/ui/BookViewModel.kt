package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.BookDocument
import com.example.data.BookRepository
import com.example.data.Chapter
import com.example.utils.PdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

enum class SortType {
    NAME_ASC, NAME_DESC, DATE_NEWEST, DATE_OLDEST
}

sealed interface CompileUiState {
    object Idle : CompileUiState
    object Compiling : CompileUiState
    data class Success(val file: File) : CompileUiState
    data class Error(val message: String) : CompileUiState
}

class BookViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: BookRepository

    val searchQuery = MutableStateFlow("")
    val sortType = MutableStateFlow(SortType.DATE_NEWEST)

    private val _compileState = MutableStateFlow<CompileUiState>(CompileUiState.Idle)
    val compileState: StateFlow<CompileUiState> = _compileState.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = BookRepository(database.bookDao())
    }

    val booksState: StateFlow<List<BookDocument>> = combine(
        repository.allBooks,
        searchQuery,
        sortType
    ) { rawBooks, query, sort ->
        val filtered = if (query.isEmpty()) {
            rawBooks
        } else {
            rawBooks.filter { 
                it.name.contains(query, ignoreCase = true) || 
                it.description.contains(query, ignoreCase = true) 
            }
        }

        when (sort) {
            SortType.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
            SortType.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase() }
            SortType.DATE_NEWEST -> filtered.sortedByDescending { it.lastModifiedAt }
            SortType.DATE_OLDEST -> filtered.sortedBy { it.lastModifiedAt }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _selectedBookId = MutableStateFlow<Long?>(null)
    val selectedBookId: StateFlow<Long?> = _selectedBookId.asStateFlow()

    val activeBook: StateFlow<BookDocument?> = _selectedBookId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else repository.allBooks.map { list -> list.find { it.id == id } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeBookChapters: StateFlow<List<Chapter>> = _selectedBookId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repository.getChaptersForBook(id)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectBook(id: Long?) {
        _selectedBookId.value = id
        _compileState.value = CompileUiState.Idle
    }

    fun createBook(name: String, description: String, onComplete: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val newBook = BookDocument(
                name = name,
                description = description
            )
            val newId = repository.insertBook(newBook)
            onComplete(newId)
        }
    }

    fun deleteBook(book: BookDocument) {
        viewModelScope.launch {
            book.pdfPath?.let { path ->
                val f = File(path)
                if (f.exists()) f.delete()
            }
            repository.deleteBook(book)
            if (_selectedBookId.value == book.id) {
                _selectedBookId.value = null
            }
        }
    }

    fun updateBookDetails(book: BookDocument) {
        viewModelScope.launch {
            repository.updateBook(book.copy(lastModifiedAt = System.currentTimeMillis()))
        }
    }

    fun addChapter(bookId: Long, title: String, notes: String, orderIndex: Int, section: String) {
        viewModelScope.launch {
            val chapter = Chapter(
                bookId = bookId,
                title = title,
                notes = notes,
                orderIndex = orderIndex,
                section = section.ifBlank { "General" }
            )
            repository.insertChapter(chapter)
            updateBookModificationTime(bookId)
        }
    }

    fun updateChapter(chapter: Chapter) {
        viewModelScope.launch {
            repository.insertChapter(chapter)
            updateBookModificationTime(chapter.bookId)
        }
    }

    fun rotatePage(chapter: Chapter, imgPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(imgPath)
            if (!file.exists()) return@launch

            val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val bitmap = BitmapFactory.decodeFile(imgPath, options) ?: return@launch
            val matrix = android.graphics.Matrix().apply { postRotate(90f) }
            val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            
            val context = getApplication<Application>().applicationContext
            val scansDir = File(context.filesDir, "ScannedPages").apply {
                if (!exists()) mkdirs()
            }
            val newFile = File(scansDir, "pdf_scan_${System.currentTimeMillis()}_rotated.jpg")
            
            try {
                FileOutputStream(newFile).use { out ->
                    rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                file.delete()
                
                val updatedPaths = chapter.scannedImagePaths.map {
                    if (it == imgPath) newFile.absolutePath else it
                }
                
                withContext(Dispatchers.Main) {
                    updateChapter(chapter.copy(scannedImagePaths = updatedPaths))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                bitmap.recycle()
                rotatedBitmap.recycle()
            }
        }
    }

    fun reorderPages(chapter: Chapter, updatedPaths: List<String>) {
        updateChapter(chapter.copy(scannedImagePaths = updatedPaths))
    }

    fun compileChapterPdf(chapter: Chapter, onComplete: (File) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _compileState.value = CompileUiState.Compiling
            if (chapter.scannedImagePaths.isEmpty() && chapter.notes.isEmpty()) {
                val errorMsg = "Cannot generate PDF: Chapter has no notes or scan pages."
                _compileState.value = CompileUiState.Error(errorMsg)
                onError(errorMsg)
                return@launch
            }

            val context = getApplication<Application>().applicationContext
            val file = withContext(Dispatchers.IO) {
                PdfGenerator.generateChaptersPdf(context, chapter.title, "Chapter Study Notes & Scans", listOf(chapter))
            }

            if (file != null && file.exists()) {
                _compileState.value = CompileUiState.Idle
                onComplete(file)
            } else {
                val errorMsg = "Failed to compile Chapter PDF locally."
                _compileState.value = CompileUiState.Error(errorMsg)
                onError(errorMsg)
            }
        }
    }

    fun compileSectionPdf(book: BookDocument, sectionName: String, chapters: List<Chapter>, onComplete: (File) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _compileState.value = CompileUiState.Compiling
            if (chapters.isEmpty()) {
                val errorMsg = "Cannot generate PDF: Subsection has no chapters."
                _compileState.value = CompileUiState.Error(errorMsg)
                onError(errorMsg)
                return@launch
            }

            val context = getApplication<Application>().applicationContext
            val file = withContext(Dispatchers.IO) {
                PdfGenerator.generateChaptersPdf(context, "${book.name} - $sectionName", "Subsection Study Notes & Scans", chapters)
            }

            if (file != null && file.exists()) {
                _compileState.value = CompileUiState.Idle
                onComplete(file)
            } else {
                val errorMsg = "Failed to compile Subsection PDF locally."
                _compileState.value = CompileUiState.Error(errorMsg)
                onError(errorMsg)
            }
        }
    }

    fun deleteChapter(chapter: Chapter) {
        viewModelScope.launch {
            chapter.scannedImagePaths.forEach { path ->
                val f = File(path)
                if (f.exists()) f.delete()
            }
            repository.deleteChapter(chapter)
            updateBookModificationTime(chapter.bookId)
        }
    }

    private suspend fun updateBookModificationTime(bookId: Long) {
        val book = repository.getBookById(bookId)
        if (book != null) {
            repository.updateBook(book.copy(lastModifiedAt = System.currentTimeMillis()))
        }
    }

    fun compileBookPdf(book: BookDocument) {
        viewModelScope.launch {
            _compileState.value = CompileUiState.Compiling
            val chapters = repository.getChaptersListForBook(book.id)
            if (chapters.isEmpty()) {
                _compileState.value = CompileUiState.Error("Cannot generate PDF: Book has no chapters. Please add at least one chapter.")
                return@launch
            }

            val context = getApplication<Application>().applicationContext
            val file = withContext(Dispatchers.IO) {
                PdfGenerator.generateBookPdf(context, book, chapters)
            }

            if (file != null && file.exists()) {
                repository.updateBook(book.copy(
                    pdfPath = file.absolutePath,
                    lastModifiedAt = System.currentTimeMillis()
                ))
                _compileState.value = CompileUiState.Success(file)
            } else {
                _compileState.value = CompileUiState.Error("Failed to compile PDF locally. Please check your storage space.")
            }
        }
    }

    fun createSampleScanBook() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext
            
            val scansDir = File(context.filesDir, "SampleScans")
            if (!scansDir.exists()) scansDir.mkdirs()

            val bookId = repository.insertBook(BookDocument(
                name = "Physics Lectures Vol.1",
                description = "Chapter-wise compiled textbook scans & lecture notes for AP Physics.",
                createdAt = System.currentTimeMillis(),
                lastModifiedAt = System.currentTimeMillis()
            ))

            val scan1 = createMockScanBitmap("LAWS OF MOTION", "Newton's First, Second and Third laws of inertia, force, and reaction. Written by Prof. Kumara.")
            val path1 = saveBitmapToScans(scan1, scansDir, "p1")

            val scan2 = createMockScanBitmap("WORK & ENERGY", "Conservative forces, kinetic energy theorem, dynamic friction calculations. Hand-drawn diagrams.")
            val path2 = saveBitmapToScans(scan2, scansDir, "p2")

            val scan3 = createMockScanBitmap("THERMODYNAMICS", "First law of thermodynamics, entropy flow, Carnot cycle visual graph. Revision sheet.")
            val path3 = saveBitmapToScans(scan3, scansDir, "p3")

            repository.insertChapter(Chapter(
                bookId = bookId,
                title = "1. Classical Mechanics",
                notes = "Core Newtonian physics summary. Study closely for midterms. Pay attention to vectors and free-body diagram calculations.",
                scannedImagePaths = listOf(path1),
                orderIndex = 1,
                section = "Mechanics"
            ))

            repository.insertChapter(Chapter(
                bookId = bookId,
                title = "2. Work & Energy Theory",
                notes = "This chapter contains important equations for work-energy equivalence and spring potentials. Review key derivations.",
                scannedImagePaths = listOf(path2),
                orderIndex = 2,
                section = "Mechanics"
            ))

            repository.insertChapter(Chapter(
                bookId = bookId,
                title = "3. Intro to Thermodynamics",
                notes = "Thermal dynamics, heat engine formulas, and entropy equations. Scan shows classroom whiteboard calculations.",
                scannedImagePaths = listOf(path3),
                orderIndex = 3,
                section = "Thermodynamics"
            ))

            withContext(Dispatchers.Main) {
                selectBook(bookId)
            }
        }
    }

    private fun createMockScanBitmap(title: String, body: String): Bitmap {
        val bitmap = Bitmap.createBitmap(800, 1100, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#FAF8F5"))

        val paint = Paint().apply {
            isAntiAlias = true
        }

        paint.color = Color.parseColor("#E8DBC5")
        paint.strokeWidth = 3f
        canvas.drawLine(100f, 0f, 100f, 1100f, paint)

        paint.color = Color.parseColor("#EBF0F6")
        paint.strokeWidth = 1f
        var y = 150f
        while (y < 1100f) {
            canvas.drawLine(0f, y, 800f, y, paint)
            y += 45f
        }

        paint.color = Color.parseColor("#1C3144")
        paint.textSize = 36f
        paint.isFakeBoldText = true
        canvas.drawText(title, 130f, 115f, paint)

        paint.color = Color.parseColor("#2B2D42")
        paint.textSize = 20f
        paint.isFakeBoldText = false

        val words = body.split(" ")
        var currentLine = ""
        var currentY = 220f
        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            val width = paint.measureText(testLine)
            if (width > 600f) {
                canvas.drawText(currentLine, 130f, currentY, paint)
                currentY += 45f
                currentLine = word
            } else {
                currentLine = testLine
            }
        }
        if (currentLine.isNotEmpty()) {
            canvas.drawText(currentLine, 130f, currentY, paint)
        }

        paint.color = Color.parseColor("#30D32F2F")
        paint.textSize = 42f
        paint.isFakeBoldText = true
        canvas.save()
        canvas.rotate(-20f, 400f, 600f)
        canvas.drawText("LOCAL SCAN VERIFIED", 180f, 600f, paint)
        canvas.restore()

        return bitmap
    }

    private fun saveBitmapToScans(bitmap: Bitmap, dir: File, namePrefix: String): String {
        val file = File(dir, "${namePrefix}_${System.currentTimeMillis()}.jpg")
        try {
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.flush()
            out.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return file.absolutePath
    }
}
