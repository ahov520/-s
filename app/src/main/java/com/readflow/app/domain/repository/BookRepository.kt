package com.readflow.app.domain.repository

import com.readflow.app.domain.model.Book
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun observeBooks(): Flow<List<Book>>
    suspend fun getBook(bookId: String): Book?
    suspend fun upsertBook(book: Book)
    suspend fun deleteBook(bookId: String)
    suspend fun updateProgress(bookId: String, position: Int, progress: Float, totalChars: Int)
}
