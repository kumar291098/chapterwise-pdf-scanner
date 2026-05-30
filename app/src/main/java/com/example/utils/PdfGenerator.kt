package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.data.BookDocument
import com.example.data.Chapter
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfGenerator {

    fun generateBookPdf(
        context: Context,
        book: BookDocument,
        chapters: List<Chapter>
    ): File? {
        return generateChaptersPdf(context, book.name, book.description, chapters)
    }

    fun generateChaptersPdf(
        context: Context,
        title: String,
        subtitle: String,
        chapters: List<Chapter>
    ): File? {
        val pdfDocument = PdfDocument()
        var pageNumber = 1

        val termPrimaryColor = 0xFF2A4B7C.toInt()
        val textBodyColor = 0xFF2C3E50.toInt()
        val dividerColor = 0xFFBDC3C7.toInt()

        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f

        // 1. COVER PAGE
        val coverPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber++).create()
        val coverPage = pdfDocument.startPage(coverPageInfo)
        val coverCanvas = coverPage.canvas

        // Border
        val borderPaint = Paint().apply {
            color = termPrimaryColor
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        coverCanvas.drawRect(20f, 20f, pageWidth - 20f, pageHeight - 20f, borderPaint)
        
        borderPaint.strokeWidth = 1f
        coverCanvas.drawRect(25f, 25f, pageWidth - 25f, pageHeight - 25f, borderPaint)

        val titlePaint = TextPaint().apply {
            color = termPrimaryColor
            textSize = 28f
            isFakeBoldText = true
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val descPaint = TextPaint().apply {
            color = textBodyColor
            textSize = 14f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val textWidth = (pageWidth - 2 * margin).toInt()
        
        val titleLayoutCentered = StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 1.1f)
            .build()

        coverCanvas.save()
        coverCanvas.translate(pageWidth / 2f, 220f)
        titleLayoutCentered.draw(coverCanvas)
        coverCanvas.restore()

        val linePaint = Paint().apply {
            color = termPrimaryColor
            strokeWidth = 2f
        }
        coverCanvas.drawLine(pageWidth / 2f - 100f, 320f, pageWidth / 2f + 100f, 320f, linePaint)

        if (subtitle.isNotEmpty()) {
            val descLayout = StaticLayout.Builder.obtain(subtitle, 0, subtitle.length, descPaint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.1f)
                .build()
            coverCanvas.save()
            coverCanvas.translate(pageWidth / 2f, 360f)
            descLayout.draw(coverCanvas)
            coverCanvas.restore()
        }

        val footerPaint = Paint().apply {
            color = textBodyColor
            textSize = 10f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val formattedDate = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
        coverCanvas.drawText("Compiled on: $formattedDate", pageWidth / 2f, pageHeight - 100f, footerPaint)
        coverCanvas.drawText("Chapter-wise Local Scanner", pageWidth / 2f, pageHeight - 80f, footerPaint)

        pdfDocument.finishPage(coverPage)

        // 2. CHAPTERS
        val bodyPaint = TextPaint().apply {
            color = textBodyColor
            textSize = 12f
            isAntiAlias = true
        }

        val chapterTitlePaint = TextPaint().apply {
            color = termPrimaryColor
            textSize = 18f
            isFakeBoldText = true
            isAntiAlias = true
        }

        val footerPageNoPaint = Paint().apply {
            color = dividerColor
            textSize = 9f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        for ((index, chapter) in chapters.withIndex()) {
            val chPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber++).create()
            var chPage = pdfDocument.startPage(chPageInfo)
            var canvas = chPage.canvas

            val chHeader = "Chapter ${index + 1}: ${chapter.title}"
            canvas.drawText(chHeader, margin, margin + 20f, chapterTitlePaint)
            
            val divPaint = Paint().apply {
                color = termPrimaryColor
                strokeWidth = 1.5f
            }
            canvas.drawLine(margin, margin + 30f, pageWidth - margin, margin + 30f, divPaint)

            var currentY = margin + 60f

            if (chapter.notes.isNotEmpty()) {
                val notesLayout = StaticLayout.Builder.obtain(
                    chapter.notes, 
                    0, 
                    chapter.notes.length, 
                    bodyPaint, 
                    (pageWidth - 2 * margin).toInt()
                )
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.1f)
                .build()

                canvas.save()
                canvas.translate(margin, currentY)
                notesLayout.draw(canvas)
                canvas.restore()

                currentY += notesLayout.height + 30f
            }

            canvas.drawText("Document Page: ${pageNumber - 1}", pageWidth / 2f, pageHeight - 20f, footerPageNoPaint)
            pdfDocument.finishPage(chPage)

            for (imgPath in chapter.scannedImagePaths) {
                if (imgPath.isEmpty()) continue
                val imgFile = File(imgPath)
                if (imgFile.exists()) {
                    val scanPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber++).create()
                    val scanPage = pdfDocument.startPage(scanPageInfo)
                    val scanCanvas = scanPage.canvas

                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeFile(imgPath, options)
                    val imgW = options.outWidth
                    val imgH = options.outHeight

                    if (imgW > 0 && imgH > 0) {
                        val maxW = pageWidth - 2 * margin
                        val maxH = pageHeight - 2 * margin - 30f

                        var targetW = imgW.toFloat()
                        var targetH = imgH.toFloat()

                        if (targetW > maxW || targetH > maxH) {
                            val ratioW = maxW / targetW
                            val ratioH = maxH / targetH
                            val scale = Math.min(ratioW, ratioH)
                            targetW *= scale
                            targetH *= scale
                        }

                        val finalOptions = BitmapFactory.Options().apply {
                            inSampleSize = calculateInSampleSize(options, targetW.toInt(), targetH.toInt())
                        }
                        val bitmap = BitmapFactory.decodeFile(imgPath, finalOptions)
                        if (bitmap != null) {
                            val left = margin + (maxW - targetW) / 2f
                            val top = margin + (maxH - targetH) / 2f + 15f
                            
                            val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
                            val destRect = Rect(left.toInt(), top.toInt(), (left + targetW).toInt(), (top + targetH).toInt())
                            
                            scanCanvas.drawBitmap(bitmap, srcRect, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
                            bitmap.recycle()
                        }
                    }

                    val scanSubPaint = Paint().apply {
                        color = dividerColor
                        textSize = 9f
                        isAntiAlias = true
                    }
                    scanCanvas.drawText("Pages of $chHeader", margin, margin, scanSubPaint)
                    scanCanvas.drawText("Document Page: ${pageNumber - 1}", pageWidth / 2f, pageHeight - 20f, footerPageNoPaint)

                    pdfDocument.finishPage(scanPage)
                }
            }
        }

        return try {
            val outputDir = File(context.filesDir, "CompiledPDFs")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }
            val sanitizedName = title.replace("[^a-zA-Z0-9]".toRegex(), "_")
            val outputFile = File(outputDir, "${sanitizedName}_${System.currentTimeMillis()}.pdf")
            val outputStream = FileOutputStream(outputFile)
            pdfDocument.writeTo(outputStream)
            outputStream.flush()
            outputStream.close()
            pdfDocument.close()
            outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
