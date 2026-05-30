package com.example.data

import kotlinx.coroutines.flow.Flow

class BookRepository(private val bookDao: BookDao) {
    val allBooks: Flow<List<BookDocument>> = bookDao.getAllBooks()

    suspend fun getBookById(id: Long): BookDocument? = bookDao.getBookById(id)

    fun getChaptersForBook(bookId: Long): Flow<List<Chapter>> = bookDao.getChaptersForBook(bookId)

    suspend fun getChaptersListForBook(bookId: Long): List<Chapter> = bookDao.getChaptersListForBook(bookId)

    suspend fun getChapterById(chapterId: Long): Chapter? = bookDao.getChapterById(chapterId)

    suspend fun insertBook(book: BookDocument): Long = bookDao.insertBook(book)

    suspend fun insertChapter(chapter: Chapter): Long = bookDao.insertChapter(chapter)

    suspend fun updateBook(book: BookDocument) = bookDao.updateBook(book)

    suspend fun deleteBook(book: BookDocument) = bookDao.deleteBook(book)

    suspend fun deleteChapter(chapter: Chapter) = bookDao.deleteChapter(chapter)
}
