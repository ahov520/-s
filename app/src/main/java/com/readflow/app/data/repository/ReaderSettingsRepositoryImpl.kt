package com.readflow.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.readflow.app.data.local.datastore.SettingsKeys
import com.readflow.app.domain.model.PageMode
import com.readflow.app.domain.model.ReadingSettings
import com.readflow.app.domain.model.ThemeMode
import com.readflow.app.domain.repository.ReaderSettingsRepository
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
}
