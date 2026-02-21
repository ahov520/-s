package com.readflow.app.domain.repository

import com.readflow.app.domain.model.ChapterIndex
import kotlinx.coroutines.flow.Flow

interface ChapterIndexRepository {
    fun observeChapters(bookId: String): Flow<List<ChapterIndex>>
    suspend fun replaceChapters(bookId: String, chapters: List<ChapterIndex>)
}
