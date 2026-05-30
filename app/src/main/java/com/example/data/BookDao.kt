package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastModifiedAt DESC")
    fun getAllBooks(): Flow<List<BookDocument>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: Long): BookDocument?

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY orderIndex ASC")
    fun getChaptersForBook(bookId: Long): Flow<List<Chapter>>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY orderIndex ASC")
    suspend fun getChaptersListForBook(bookId: Long): List<Chapter>

    @Query("SELECT * FROM chapters WHERE id = :chapterId")
    suspend fun getChapterById(chapterId: Long): Chapter?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookDocument): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapter: Chapter): Long

    @Update
    suspend fun updateBook(book: BookDocument)

    @Delete
    suspend fun deleteBook(book: BookDocument)

    @Delete
    suspend fun deleteChapter(chapter: Chapter)
}
