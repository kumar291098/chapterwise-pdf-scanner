package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.PrimaryKey

@Entity(
    tableName = "books"
)
data class BookDocument(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastModifiedAt: Long = System.currentTimeMillis(),
    val pdfPath: String? = null
)

@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = BookDocument::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = CASCADE
        )
    ]
)
data class Chapter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val title: String,
    val notes: String = "",
    val scannedImagePaths: List<String> = emptyList(),
    val orderIndex: Int = 0,
    val section: String = "General"
)

