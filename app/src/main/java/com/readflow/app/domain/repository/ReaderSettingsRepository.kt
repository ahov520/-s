package com.readflow.app.domain.repository

import com.readflow.app.domain.model.PageMode
import com.readflow.app.domain.model.ReadingSettings
import com.readflow.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface ReaderSettingsRepository {
    fun observeSettings(): Flow<ReadingSettings>
    suspend fun updateFontSize(value: Int)
    suspend fun updateLineHeight(value: Float)
    suspend fun updateBgColor(key: String)
    suspend fun updateThemeMode(mode: ThemeMode)
    suspend fun updatePageMode(mode: PageMode)
}
