package com.readflow.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.readflow.app.data.local.datastore.SettingsKeys
import com.readflow.app.domain.model.PageMode
import com.readflow.app.domain.model.ReadingNote
import com.readflow.app.domain.model.ReadingSettings
import com.readflow.app.domain.model.TtsProvider
import com.readflow.app.domain.model.ThemeMode
import com.readflow.app.domain.repository.ReaderSettingsRepository
import com.readflow.app.domain.usecase.ReadStatsCalculator
import com.readflow.app.domain.usecase.ReadStatsSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class ReaderSettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ReaderSettingsRepository {
    override fun observeSettings(): Flow<ReadingSettings> = dataStore.data.map { pref ->
        val groupsJson = pref[SettingsKeys.BOOK_GROUPS_JSON].orEmpty()
        val notesJson = pref[SettingsKeys.READING_NOTES_JSON].orEmpty()
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
            backgroundTtsEnabled = pref[SettingsKeys.BACKGROUND_TTS_ENABLED] ?: true,
            autoPageEnabled = pref[SettingsKeys.AUTO_PAGE_ENABLED] ?: false,
            autoPageIntervalMs = pref[SettingsKeys.AUTO_PAGE_INTERVAL_MS] ?: 3500,
            immersiveEnabled = pref[SettingsKeys.IMMERSIVE_ENABLED] ?: false,
            brightnessLocked = pref[SettingsKeys.BRIGHTNESS_LOCKED] ?: false,
            mistouchGuardEnabled = pref[SettingsKeys.MISTOUCH_GUARD_ENABLED] ?: false,
            dailyReadSeconds = pref[SettingsKeys.DAILY_READ_SECONDS] ?: 0,
            lastReadDate = pref[SettingsKeys.LAST_READ_DATE] ?: "",
            streakDays = pref[SettingsKeys.STREAK_DAYS] ?: 0,
            dailyGoalMinutes = pref[SettingsKeys.DAILY_GOAL_MINUTES] ?: 60,
            reminderEnabled = pref[SettingsKeys.REMINDER_ENABLED] ?: false,
            reminderHour = pref[SettingsKeys.REMINDER_HOUR] ?: 21,
            reminderMinute = pref[SettingsKeys.REMINDER_MINUTE] ?: 0,
            bookGroups = decodeBookGroups(groupsJson),
            readingNotes = decodeNotes(notesJson),
            cloudSyncToken = pref[SettingsKeys.CLOUD_SYNC_TOKEN] ?: "",
            cloudGistId = pref[SettingsKeys.CLOUD_GIST_ID] ?: "",
            lastBackupPath = pref[SettingsKeys.LAST_BACKUP_PATH] ?: "",
            lastSyncAt = pref[SettingsKeys.LAST_SYNC_AT]?.toLongOrNull() ?: 0L,
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

    override suspend fun updateBackgroundTtsEnabled(enabled: Boolean) {
        dataStore.edit { it[SettingsKeys.BACKGROUND_TTS_ENABLED] = enabled }
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
            val current = ReadStatsSnapshot(
                dailyReadSeconds = pref[SettingsKeys.DAILY_READ_SECONDS] ?: 0,
                lastReadDate = pref[SettingsKeys.LAST_READ_DATE].orEmpty(),
                streakDays = pref[SettingsKeys.STREAK_DAYS] ?: 0,
            )
            val updated = ReadStatsCalculator.applyDelta(
                current = current,
                secondsDelta = secondsDelta,
                today = today,
            )
            pref[SettingsKeys.DAILY_READ_SECONDS] = updated.dailyReadSeconds
            pref[SettingsKeys.LAST_READ_DATE] = updated.lastReadDate
            pref[SettingsKeys.STREAK_DAYS] = updated.streakDays
        }
    }

    override suspend fun resetDailyReadStats(today: String) {
        dataStore.edit { pref ->
            val current = ReadStatsSnapshot(
                dailyReadSeconds = pref[SettingsKeys.DAILY_READ_SECONDS] ?: 0,
                lastReadDate = pref[SettingsKeys.LAST_READ_DATE].orEmpty(),
                streakDays = pref[SettingsKeys.STREAK_DAYS] ?: 0,
            )
            val updated = ReadStatsCalculator.resetDaily(current, today)
            pref[SettingsKeys.DAILY_READ_SECONDS] = updated.dailyReadSeconds
            pref[SettingsKeys.LAST_READ_DATE] = updated.lastReadDate
            pref[SettingsKeys.STREAK_DAYS] = updated.streakDays
        }
    }

    override suspend fun replaceReadStats(dailyReadSeconds: Int, lastReadDate: String, streakDays: Int) {
        dataStore.edit { pref ->
            pref[SettingsKeys.DAILY_READ_SECONDS] = dailyReadSeconds.coerceAtLeast(0)
            pref[SettingsKeys.LAST_READ_DATE] = lastReadDate
            pref[SettingsKeys.STREAK_DAYS] = streakDays.coerceAtLeast(0)
        }
    }

    override suspend fun updateDailyGoalMinutes(value: Int) {
        dataStore.edit { it[SettingsKeys.DAILY_GOAL_MINUTES] = value.coerceIn(10, 360) }
    }

    override suspend fun updateReminder(enabled: Boolean, hour: Int, minute: Int) {
        dataStore.edit {
            it[SettingsKeys.REMINDER_ENABLED] = enabled
            it[SettingsKeys.REMINDER_HOUR] = hour.coerceIn(0, 23)
            it[SettingsKeys.REMINDER_MINUTE] = minute.coerceIn(0, 59)
        }
    }

    override suspend fun updateBookGroup(bookId: String, group: String) {
        if (bookId.isBlank()) return
        dataStore.edit { pref ->
            val map = decodeBookGroups(pref[SettingsKeys.BOOK_GROUPS_JSON].orEmpty()).toMutableMap()
            val normalized = group.trim()
            if (normalized.isBlank() || normalized == "未分组") {
                map.remove(bookId)
            } else {
                map[bookId] = normalized
            }
            pref[SettingsKeys.BOOK_GROUPS_JSON] = encodeBookGroups(map)
        }
    }

    override suspend fun replaceBookGroups(groups: Map<String, String>) {
        dataStore.edit { pref ->
            pref[SettingsKeys.BOOK_GROUPS_JSON] = encodeBookGroups(groups)
        }
    }

    override suspend fun upsertReadingNote(note: ReadingNote) {
        if (note.bookId.isBlank() || note.id.isBlank()) return
        dataStore.edit { pref ->
            val notes = decodeNotes(pref[SettingsKeys.READING_NOTES_JSON].orEmpty()).toMutableList()
            notes.removeAll { it.id == note.id }
            notes.add(note)
            pref[SettingsKeys.READING_NOTES_JSON] = encodeNotes(notes.sortedByDescending { it.createdAt })
        }
    }

    override suspend fun replaceReadingNotes(notes: List<ReadingNote>) {
        dataStore.edit { pref ->
            pref[SettingsKeys.READING_NOTES_JSON] = encodeNotes(notes.sortedByDescending { it.createdAt })
        }
    }

    override suspend fun deleteReadingNote(noteId: String) {
        if (noteId.isBlank()) return
        dataStore.edit { pref ->
            val notes = decodeNotes(pref[SettingsKeys.READING_NOTES_JSON].orEmpty())
                .filterNot { it.id == noteId }
            pref[SettingsKeys.READING_NOTES_JSON] = encodeNotes(notes)
        }
    }

    override suspend fun updateCloudSyncConfig(token: String, gistId: String) {
        dataStore.edit {
            it[SettingsKeys.CLOUD_SYNC_TOKEN] = token.trim()
            it[SettingsKeys.CLOUD_GIST_ID] = gistId.trim()
        }
    }

    override suspend fun updateLastBackupPath(path: String) {
        dataStore.edit { it[SettingsKeys.LAST_BACKUP_PATH] = path }
    }

    override suspend fun updateLastSyncAt(epochMillis: Long) {
        dataStore.edit { it[SettingsKeys.LAST_SYNC_AT] = epochMillis.coerceAtLeast(0L).toString() }
    }

    private fun decodeBookGroups(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            val keys = obj.keys()
            val out = linkedMapOf<String, String>()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = obj.optString(key)
                if (key.isNotBlank() && value.isNotBlank()) out[key] = value
            }
            out
        }.getOrDefault(emptyMap())
    }

    private fun encodeBookGroups(groups: Map<String, String>): String {
        val obj = JSONObject()
        groups.forEach { (bookId, group) ->
            if (bookId.isNotBlank() && group.isNotBlank()) {
                obj.put(bookId, group)
            }
        }
        return obj.toString()
    }

    private fun decodeNotes(raw: String): List<ReadingNote> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    val bookId = item.optString("bookId")
                    if (id.isBlank() || bookId.isBlank()) continue
                    add(
                        ReadingNote(
                            id = id,
                            bookId = bookId,
                            startChar = item.optInt("startChar", 0),
                            endChar = item.optInt("endChar", 0),
                            quote = item.optString("quote"),
                            note = item.optString("note"),
                            createdAt = item.optLong("createdAt", 0L),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeNotes(notes: List<ReadingNote>): String {
        val arr = JSONArray()
        notes.forEach { note ->
            arr.put(
                JSONObject()
                    .put("id", note.id)
                    .put("bookId", note.bookId)
                    .put("startChar", note.startChar)
                    .put("endChar", note.endChar)
                    .put("quote", note.quote)
                    .put("note", note.note)
                    .put("createdAt", note.createdAt)
            )
        }
        return arr.toString()
    }
}
