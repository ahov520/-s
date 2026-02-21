package com.readflow.app.domain.repository

import com.readflow.app.domain.model.PageMode
import com.readflow.app.domain.model.ReadingSettings
import com.readflow.app.domain.model.TtsProvider
import com.readflow.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface ReaderSettingsRepository {
    fun observeSettings(): Flow<ReadingSettings>
    suspend fun updateFontSize(value: Int)
    suspend fun updateLineHeight(value: Float)
    suspend fun updateBgColor(key: String)
    suspend fun updateThemeMode(mode: ThemeMode)
    suspend fun updatePageMode(mode: PageMode)
    suspend fun updateTtsProvider(provider: TtsProvider)
    suspend fun updateTtsRate(value: Float)
    suspend fun updateTtsPitch(value: Float)
    suspend fun updateTtsVoiceId(value: String)
    suspend fun updateAutoPageEnabled(enabled: Boolean)
    suspend fun updateAutoPageIntervalMs(value: Int)
    suspend fun updateImmersiveEnabled(enabled: Boolean)
    suspend fun updateBrightnessLocked(enabled: Boolean)
    suspend fun updateMistouchGuardEnabled(enabled: Boolean)
    suspend fun updateReadStats(secondsDelta: Int, today: String)
    suspend fun resetDailyReadStats(today: String)
}
