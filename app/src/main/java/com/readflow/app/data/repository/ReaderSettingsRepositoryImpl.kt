package com.readflow.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.readflow.app.data.local.datastore.SettingsKeys
import com.readflow.app.domain.model.PageMode
import com.readflow.app.domain.model.ReadingSettings
import com.readflow.app.domain.model.TtsProvider
import com.readflow.app.domain.model.ThemeMode
import com.readflow.app.domain.repository.ReaderSettingsRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ReaderSettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ReaderSettingsRepository {
    override fun observeSettings(): Flow<ReadingSettings> = dataStore.data.map { pref ->
        ReadingSettings(
            fontSize = pref[SettingsKeys.FONT_SIZE] ?: 18,
            lineHeight = pref[SettingsKeys.LINE_HEIGHT] ?: 1.6f,
            bgColorKey = pref[SettingsKeys.BG_COLOR_KEY] ?: "paper-sepia",
            themeMode = pref[SettingsKeys.THEME_MODE]?.let { ThemeMode.valueOf(it) } ?: ThemeMode.SYSTEM,
            pageMode = pref[SettingsKeys.PAGE_MODE]?.let { PageMode.valueOf(it) } ?: PageMode.SCROLL,
            ttsProvider = pref[SettingsKeys.TTS_PROVIDER]?.let { TtsProvider.valueOf(it) } ?: TtsProvider.SYSTEM,
            ttsRate = pref[SettingsKeys.TTS_RATE] ?: 1.0f,
            ttsPitch = pref[SettingsKeys.TTS_PITCH] ?: 1.0f,
            ttsVoiceId = pref[SettingsKeys.TTS_VOICE_ID] ?: "",
            autoPageEnabled = pref[SettingsKeys.AUTO_PAGE_ENABLED] ?: false,
            autoPageIntervalMs = pref[SettingsKeys.AUTO_PAGE_INTERVAL_MS] ?: 3500,
            immersiveEnabled = pref[SettingsKeys.IMMERSIVE_ENABLED] ?: false,
            brightnessLocked = pref[SettingsKeys.BRIGHTNESS_LOCKED] ?: false,
            mistouchGuardEnabled = pref[SettingsKeys.MISTOUCH_GUARD_ENABLED] ?: false,
            dailyReadSeconds = pref[SettingsKeys.DAILY_READ_SECONDS] ?: 0,
            lastReadDate = pref[SettingsKeys.LAST_READ_DATE] ?: "",
            streakDays = pref[SettingsKeys.STREAK_DAYS] ?: 0,
        )
    }

    override suspend fun updateFontSize(value: Int) {
        dataStore.edit { it[SettingsKeys.FONT_SIZE] = value.coerceIn(14, 30) }
    }

    override suspend fun updateLineHeight(value: Float) {
        dataStore.edit { it[SettingsKeys.LINE_HEIGHT] = value }
    }

    override suspend fun updateBgColor(key: String) {
        dataStore.edit { it[SettingsKeys.BG_COLOR_KEY] = key }
    }

    override suspend fun updateThemeMode(mode: ThemeMode) {
        dataStore.edit { it[SettingsKeys.THEME_MODE] = mode.name }
    }

    override suspend fun updatePageMode(mode: PageMode) {
        dataStore.edit { it[SettingsKeys.PAGE_MODE] = mode.name }
    }

    override suspend fun updateTtsProvider(provider: TtsProvider) {
        dataStore.edit { it[SettingsKeys.TTS_PROVIDER] = provider.name }
    }

    override suspend fun updateTtsRate(value: Float) {
        dataStore.edit { it[SettingsKeys.TTS_RATE] = value.coerceIn(0.5f, 2.0f) }
    }

    override suspend fun updateTtsPitch(value: Float) {
        dataStore.edit { it[SettingsKeys.TTS_PITCH] = value.coerceIn(0.5f, 2.0f) }
    }

    override suspend fun updateTtsVoiceId(value: String) {
        dataStore.edit { it[SettingsKeys.TTS_VOICE_ID] = value }
    }

    override suspend fun updateAutoPageEnabled(enabled: Boolean) {
        dataStore.edit { it[SettingsKeys.AUTO_PAGE_ENABLED] = enabled }
    }

    override suspend fun updateAutoPageIntervalMs(value: Int) {
        dataStore.edit { it[SettingsKeys.AUTO_PAGE_INTERVAL_MS] = value.coerceIn(1000, 10000) }
    }

    override suspend fun updateImmersiveEnabled(enabled: Boolean) {
        dataStore.edit { it[SettingsKeys.IMMERSIVE_ENABLED] = enabled }
    }

    override suspend fun updateBrightnessLocked(enabled: Boolean) {
        dataStore.edit { it[SettingsKeys.BRIGHTNESS_LOCKED] = enabled }
    }

    override suspend fun updateMistouchGuardEnabled(enabled: Boolean) {
        dataStore.edit { it[SettingsKeys.MISTOUCH_GUARD_ENABLED] = enabled }
    }

    override suspend fun updateReadStats(secondsDelta: Int, today: String) {
        dataStore.edit { pref ->
            val delta = secondsDelta.coerceAtLeast(0)
            val prevDate = pref[SettingsKeys.LAST_READ_DATE].orEmpty()
            val prevDaily = pref[SettingsKeys.DAILY_READ_SECONDS] ?: 0
            val prevStreak = pref[SettingsKeys.STREAK_DAYS] ?: 0

            if (prevDate == today || prevDate.isBlank()) {
                pref[SettingsKeys.DAILY_READ_SECONDS] = if (prevDate == today) prevDaily + delta else delta
                pref[SettingsKeys.LAST_READ_DATE] = today
                pref[SettingsKeys.STREAK_DAYS] = if (prevDate.isBlank()) 1 else prevStreak.coerceAtLeast(1)
            } else {
                val nextStreak = if (isYesterday(prevDate, today)) prevStreak + 1 else 1
                pref[SettingsKeys.DAILY_READ_SECONDS] = delta
                pref[SettingsKeys.LAST_READ_DATE] = today
                pref[SettingsKeys.STREAK_DAYS] = nextStreak
            }
        }
    }

    override suspend fun resetDailyReadStats(today: String) {
        dataStore.edit { pref ->
            pref[SettingsKeys.LAST_READ_DATE] = today
            pref[SettingsKeys.DAILY_READ_SECONDS] = 0
            pref[SettingsKeys.STREAK_DAYS] = (pref[SettingsKeys.STREAK_DAYS] ?: 0).coerceAtLeast(1)
        }
    }

    private fun isYesterday(previous: String, today: String): Boolean {
        return runCatching {
            LocalDate.parse(previous).plusDays(1) == LocalDate.parse(today)
        }.getOrDefault(false)
    }
}
