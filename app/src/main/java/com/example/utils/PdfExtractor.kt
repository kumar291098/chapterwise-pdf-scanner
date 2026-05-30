package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

object PdfExtractor {

    data class PdfMeta(
        val name: String,
        val pageCount: Int,
        val tempFile: File
    )

    /**
     * Copies a PDF from a content Uri to a safe cache file and returns metadata.
     */
    suspend fun getPdfMetadata(context: Context, uri: Uri): PdfMeta? = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        var fileName = "imported_document.pdf"
        
        // Get name from ContentResolver if possible
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                fileName = cursor.getString(nameIndex)
            }
        }

        val cacheFile = File(context.cacheDir, "temp_import_${System.currentTimeMillis()}.pdf")
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            // Open with PdfRenderer to ensure it's valid and get page count
            val pfd = ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val pageCount = renderer.pageCount
            renderer.close()
            pfd.close()

            return@withContext PdfMeta(fileName, pageCount, cacheFile)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    /**
     * Renders specific pages of a PDF into JPEG images, returns the files and bitmaps.
     */
    suspend fun renderPdfPages(
        context: Context,
        pdfFile: File,
        pageIndices: List<Int>
    ): List<RenderedPage> = withContext(Dispatchers.IO) {
        val result = mutableListOf<RenderedPage>()
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null

        try {
            pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)

            val scansDir = File(context.filesDir, "ScannedPages").apply {
                if (!exists()) mkdirs()
            }

            for (pageIndex in pageIndices) {
                if (pageIndex < 0 || pageIndex >= renderer.pageCount) continue

                val page = renderer.openPage(pageIndex)
                
                // Keep dimensions within a generous but memory-friendly boundary for display / AI analysis
                val maxDimension = 1500f
                val scale = if (page.width > page.height) {
                    if (page.width > maxDimension) maxDimension / page.width else 1.0f
                } else {
                    if (page.height > maxDimension) maxDimension / page.height else 1.0f
                }

                val width = (page.width * scale).toInt()
                val height = (page.height * scale).toInt()

                var bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE) // White backgrounds for transparent PDFs
                
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                // Automatically rotate landscape pages to portrait (vertical in page)
                if (width > height) {
                    val matrix = android.graphics.Matrix().apply { postRotate(90f) }
                    val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
                    bitmap.recycle()
                    bitmap = rotatedBitmap
                }

                // Save bitmap as JPEG file
                val imageFile = File(scansDir, "pdf_scan_${System.currentTimeMillis()}_p$pageIndex.jpg")
                FileOutputStream(imageFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }

                result.add(RenderedPage(pageIndex, imageFile.absolutePath, bitmap))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                renderer?.close()
                pfd?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return@withContext result
    }

    data class RenderedPage(
        val pageIndex: Int,
        val filePath: String,
        val bitmap: Bitmap
    )
}
