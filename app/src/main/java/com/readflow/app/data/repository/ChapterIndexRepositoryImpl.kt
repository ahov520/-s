package com.readflow.app.data.repository

import com.readflow.app.data.local.db.dao.ChapterIndexDao
import com.readflow.app.data.mapper.toDomain
import com.readflow.app.data.mapper.toEntity
import com.readflow.app.domain.model.ChapterIndex
import com.readflow.app.domain.repository.ChapterIndexRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ChapterIndexRepositoryImpl @Inject constructor(
    private val chapterIndexDao: ChapterIndexDao,
) : ChapterIndexRepository {
    override fun observeChapters(bookId: String): Flow<List<ChapterIndex>> =
        chapterIndexDao.observeByBook(bookId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun replaceChapters(bookId: String, chapters: List<ChapterIndex>) {
        chapterIndexDao.deleteByBookId(bookId)
        if (chapters.isNotEmpty()) {
            chapterIndexDao.insertAll(chapters.map { it.toEntity() })
        }
    }
}
