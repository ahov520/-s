package com.readflow.app.data.repository

import com.readflow.app.data.local.db.dao.BookDao
import com.readflow.app.data.mapper.toDomain
import com.readflow.app.data.mapper.toEntity
import com.readflow.app.domain.model.Book
import com.readflow.app.domain.repository.BookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class BookRepositoryImpl @Inject constructor(
    private val bookDao: BookDao,
) : BookRepository {
    override fun observeBooks(): Flow<List<Book>> =
        bookDao.observeBooks().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getBook(bookId: String): Book? = bookDao.getBook(bookId)?.toDomain()

    override suspend fun getAllBooks(): List<Book> = bookDao.getAllBooks().map { it.toDomain() }

    override suspend fun upsertBook(book: Book) {
        bookDao.upsert(book.toEntity())
    }

    override suspend fun upsertBooks(books: List<Book>) {
        if (books.isEmpty()) return
        bookDao.upsertAll(books.map { it.toEntity() })
    }

    override suspend fun deleteBook(bookId: String) {
        bookDao.deleteById(bookId)
    }

    override suspend fun deleteAllBooks() {
        bookDao.deleteAll()
    }

    override suspend fun updateProgress(bookId: String, position: Int, progress: Float, totalChars: Int) {
        val now = System.currentTimeMillis()
        bookDao.updateProgress(
            bookId = bookId,
            position = position,
            progress = progress,
            totalChars = totalChars,
            lastReadAt = now,
            updatedAt = now,
        )
    }
}
