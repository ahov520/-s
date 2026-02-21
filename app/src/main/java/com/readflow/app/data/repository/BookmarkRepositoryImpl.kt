package com.readflow.app.data.repository

import com.readflow.app.data.local.db.dao.BookmarkDao
import com.readflow.app.data.mapper.toDomain
import com.readflow.app.data.mapper.toEntity
import com.readflow.app.domain.model.Bookmark
import com.readflow.app.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class BookmarkRepositoryImpl @Inject constructor(
    private val bookmarkDao: BookmarkDao,
) : BookmarkRepository {
    override fun observeBookmarks(bookId: String): Flow<List<Bookmark>> =
        bookmarkDao.observeByBook(bookId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getAllBookmarks(): List<Bookmark> = bookmarkDao.getAll().map { it.toDomain() }

    override suspend fun addBookmark(bookmark: Bookmark) {
        bookmarkDao.upsert(bookmark.toEntity())
    }

    override suspend fun addBookmarks(bookmarks: List<Bookmark>) {
        if (bookmarks.isEmpty()) return
        bookmarkDao.upsertAll(bookmarks.map { it.toEntity() })
    }

    override suspend fun deleteBookmark(bookmarkId: String) {
        bookmarkDao.deleteById(bookmarkId)
    }

    override suspend fun deleteAllBookmarks() {
        bookmarkDao.deleteAll()
    }
}
