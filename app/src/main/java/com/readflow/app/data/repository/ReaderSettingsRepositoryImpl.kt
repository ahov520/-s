package com.readflow.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.readflow.app.data.local.datastore.SettingsKeys
import com.readflow.app.data.local.security.SecureTokenStore
import com.readflow.app.domain.model.BookSubscription
import com.readflow.app.domain.model.CloudSyncProvider
import com.readflow.app.domain.model.Highlight
import com.readflow.app.domain.model.PageMode
import com.readflow.app.domain.model.ReadingNote
import com.readflow.app.domain.model.ReadingSettings
import com.readflow.app.domain.model.SyncConflict
import com.readflow.app.domain.model.TtsProvider
import com.readflow.app.domain.model.ThemeMode
import com.readflow.app.domain.model.VocabularyWord
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
    private val secureTokenStore: SecureTokenStore,
) : ReaderSettingsRepository {
    override fun observeSettings(): Flow<ReadingSettings> = dataStore.data.map { pref ->
        val groupsJson = pref[SettingsKeys.BOOK_GROUPS_JSON].orEmpty()
        val notesJson = pref[SettingsKeys.READING_NOTES_JSON].orEmpty()
        val highlightsJson = pref[SettingsKeys.HIGHLIGHTS_JSON].orEmpty()
        val vocabularyJson = pref[SettingsKeys.VOCABULARY_JSON].orEmpty()
        val readHistoryJson = pref[SettingsKeys.READ_HISTORY_JSON].orEmpty()
        val conflictsJson = pref[SettingsKeys.SYNC_CONFLICTS_JSON].orEmpty()
        val subscriptionsJson = pref[SettingsKeys.BOOK_SUBSCRIPTIONS_JSON].orEmpty()
        val offlineJson = pref[SettingsKeys.OFFLINE_CACHED_BOOK_IDS_JSON].orEmpty()
        val legacyToken = pref[SettingsKeys.CLOUD_SYNC_TOKEN].orEmpty()
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
            highlights = decodeHighlights(highlightsJson),
            vocabularyWords = decodeVocabulary(vocabularyJson),
            readHistory = decodeReadHistory(readHistoryJson),
            syncConflicts = decodeSyncConflicts(conflictsJson),
            bookSubscriptions = decodeSubscriptions(subscriptionsJson),
            offlineCachedBookIds = decodeOfflineBookIds(offlineJson),
            cloudProvider = pref[SettingsKeys.CLOUD_PROVIDER]
                ?.let { decodeCloudProvider(it) }
                ?: CloudSyncProvider.GITHUB_GIST,
            cloudWebDavEndpoint = pref[SettingsKeys.CLOUD_WEBDAV_ENDPOINT].orEmpty(),
            cloudWebDavUsername = pref[SettingsKeys.CLOUD_WEBDAV_USERNAME].orEmpty(),
            cloudRemotePath = pref[SettingsKeys.CLOUD_REMOTE_PATH].orEmpty().ifBlank { "readflow-backup.json" },
            cloudSyncToken = secureTokenStore.readCloudSyncToken(legacyToken),
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

            if (today.isNotBlank() && secondsDelta > 0) {
                val history = decodeReadHistory(pref[SettingsKeys.READ_HISTORY_JSON].orEmpty()).toMutableMap()
                history[today] = (history[today] ?: 0) + secondsDelta
                pref[SettingsKeys.READ_HISTORY_JSON] = encodeReadHistory(trimReadHistory(history))
            }
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
            if (lastReadDate.isNotBlank() && dailyReadSeconds > 0) {
                val history = decodeReadHistory(pref[SettingsKeys.READ_HISTORY_JSON].orEmpty()).toMutableMap()
                history[lastReadDate] = dailyReadSeconds
                pref[SettingsKeys.READ_HISTORY_JSON] = encodeReadHistory(trimReadHistory(history))
            }
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

    override suspend fun upsertHighlight(highlight: Highlight) {
        if (highlight.id.isBlank() || highlight.bookId.isBlank()) return
        dataStore.edit { pref ->
            val highlights = decodeHighlights(pref[SettingsKeys.HIGHLIGHTS_JSON].orEmpty()).toMutableList()
            highlights.removeAll { it.id == highlight.id }
            highlights.add(highlight)
            pref[SettingsKeys.HIGHLIGHTS_JSON] = encodeHighlights(highlights.sortedByDescending { it.createdAt })
        }
    }

    override suspend fun replaceHighlights(highlights: List<Highlight>) {
        dataStore.edit { pref ->
            pref[SettingsKeys.HIGHLIGHTS_JSON] = encodeHighlights(highlights.sortedByDescending { it.createdAt })
        }
    }

    override suspend fun deleteHighlight(highlightId: String) {
        if (highlightId.isBlank()) return
        dataStore.edit { pref ->
            val highlights = decodeHighlights(pref[SettingsKeys.HIGHLIGHTS_JSON].orEmpty())
                .filterNot { it.id == highlightId }
            pref[SettingsKeys.HIGHLIGHTS_JSON] = encodeHighlights(highlights)
        }
    }

    override suspend fun upsertVocabularyWord(word: VocabularyWord) {
        if (word.id.isBlank()) return
        dataStore.edit { pref ->
            val words = decodeVocabulary(pref[SettingsKeys.VOCABULARY_JSON].orEmpty()).toMutableList()
            words.removeAll { it.id == word.id }
            words.add(word)
            pref[SettingsKeys.VOCABULARY_JSON] = encodeVocabulary(words.sortedByDescending { it.createdAt })
        }
    }

    override suspend fun deleteVocabularyWord(wordId: String) {
        if (wordId.isBlank()) return
        dataStore.edit { pref ->
            val words = decodeVocabulary(pref[SettingsKeys.VOCABULARY_JSON].orEmpty())
                .filterNot { it.id == wordId }
            pref[SettingsKeys.VOCABULARY_JSON] = encodeVocabulary(words)
        }
    }

    override suspend fun replaceVocabularyWords(words: List<VocabularyWord>) {
        dataStore.edit { pref ->
            pref[SettingsKeys.VOCABULARY_JSON] = encodeVocabulary(words.sortedByDescending { it.createdAt })
        }
    }

    override suspend fun replaceSyncConflicts(conflicts: List<SyncConflict>) {
        dataStore.edit { pref ->
            pref[SettingsKeys.SYNC_CONFLICTS_JSON] = encodeSyncConflicts(
                conflicts.sortedByDescending { it.createdAt }
            )
        }
    }

    override suspend fun removeSyncConflict(conflictId: String) {
        if (conflictId.isBlank()) return
        dataStore.edit { pref ->
            val conflicts = decodeSyncConflicts(pref[SettingsKeys.SYNC_CONFLICTS_JSON].orEmpty())
                .filterNot { it.id == conflictId }
            pref[SettingsKeys.SYNC_CONFLICTS_JSON] = encodeSyncConflicts(conflicts)
        }
    }

    override suspend fun upsertBookSubscription(subscription: BookSubscription) {
        if (subscription.bookId.isBlank() || subscription.sourceUrl.isBlank()) return
        dataStore.edit { pref ->
            val list = decodeSubscriptions(pref[SettingsKeys.BOOK_SUBSCRIPTIONS_JSON].orEmpty()).toMutableList()
            list.removeAll { it.bookId == subscription.bookId }
            list.add(subscription)
            pref[SettingsKeys.BOOK_SUBSCRIPTIONS_JSON] = encodeSubscriptions(list)
        }
    }

    override suspend fun replaceBookSubscriptions(subscriptions: List<BookSubscription>) {
        dataStore.edit { pref ->
            pref[SettingsKeys.BOOK_SUBSCRIPTIONS_JSON] = encodeSubscriptions(subscriptions)
        }
    }

    override suspend fun replaceOfflineCachedBookIds(bookIds: Set<String>) {
        dataStore.edit { pref ->
            pref[SettingsKeys.OFFLINE_CACHED_BOOK_IDS_JSON] = encodeOfflineBookIds(bookIds)
        }
    }

    override suspend fun markBookOfflineCached(bookId: String, cached: Boolean) {
        if (bookId.isBlank()) return
        dataStore.edit { pref ->
            val ids = decodeOfflineBookIds(pref[SettingsKeys.OFFLINE_CACHED_BOOK_IDS_JSON].orEmpty()).toMutableSet()
            if (cached) ids.add(bookId) else ids.remove(bookId)
            pref[SettingsKeys.OFFLINE_CACHED_BOOK_IDS_JSON] = encodeOfflineBookIds(ids)
        }
    }

    override suspend fun updateCloudProvider(provider: CloudSyncProvider) {
        dataStore.edit { pref ->
            pref[SettingsKeys.CLOUD_PROVIDER] = provider.name
        }
    }

    override suspend fun updateCloudWebDavConfig(endpoint: String, username: String, remotePath: String) {
        dataStore.edit { pref ->
            pref[SettingsKeys.CLOUD_WEBDAV_ENDPOINT] = endpoint.trim()
            pref[SettingsKeys.CLOUD_WEBDAV_USERNAME] = username.trim()
            pref[SettingsKeys.CLOUD_REMOTE_PATH] = remotePath.trim().ifBlank { "readflow-backup.json" }
        }
    }

    override suspend fun updateCloudSyncConfig(token: String, gistId: String) {
        secureTokenStore.saveCloudSyncToken(token)
        dataStore.edit {
            // 兼容旧版本字段：新版本不再明文保存 token。
            it[SettingsKeys.CLOUD_SYNC_TOKEN] = ""
            it[SettingsKeys.CLOUD_GIST_ID] = gistId.trim()
        }
    }

    override suspend fun replaceReadHistory(history: Map<String, Int>) {
        dataStore.edit { pref ->
            pref[SettingsKeys.READ_HISTORY_JSON] = encodeReadHistory(trimReadHistory(history))
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

    private fun decodeHighlights(raw: String): List<Highlight> {
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
                        Highlight(
                            id = id,
                            bookId = bookId,
                            startChar = item.optInt("startChar", 0),
                            endChar = item.optInt("endChar", 0),
                            quote = item.optString("quote"),
                            colorKey = item.optString("colorKey", "amber"),
                            note = item.optString("note"),
                            createdAt = item.optLong("createdAt", 0L),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeHighlights(highlights: List<Highlight>): String {
        val arr = JSONArray()
        highlights.forEach { highlight ->
            arr.put(
                JSONObject()
                    .put("id", highlight.id)
                    .put("bookId", highlight.bookId)
                    .put("startChar", highlight.startChar)
                    .put("endChar", highlight.endChar)
                    .put("quote", highlight.quote)
                    .put("colorKey", highlight.colorKey)
                    .put("note", highlight.note)
                    .put("createdAt", highlight.createdAt)
            )
        }
        return arr.toString()
    }

    private fun decodeVocabulary(raw: String): List<VocabularyWord> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    val word = item.optString("word")
                    if (id.isBlank() || word.isBlank()) continue
                    add(
                        VocabularyWord(
                            id = id,
                            bookId = item.optString("bookId"),
                            word = word,
                            meaning = item.optString("meaning"),
                            sentence = item.optString("sentence"),
                            createdAt = item.optLong("createdAt", 0L),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeVocabulary(words: List<VocabularyWord>): String {
        val arr = JSONArray()
        words.forEach { word ->
            arr.put(
                JSONObject()
                    .put("id", word.id)
                    .put("bookId", word.bookId)
                    .put("word", word.word)
                    .put("meaning", word.meaning)
                    .put("sentence", word.sentence)
                    .put("createdAt", word.createdAt)
            )
        }
        return arr.toString()
    }

    private fun decodeReadHistory(raw: String): Map<String, Int> {
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            val keys = obj.keys()
            val out = linkedMapOf<String, Int>()
            while (keys.hasNext()) {
                val date = keys.next()
                val seconds = obj.optInt(date, 0).coerceAtLeast(0)
                if (date.isNotBlank()) out[date] = seconds
            }
            out
        }.getOrDefault(emptyMap())
    }

    private fun encodeReadHistory(history: Map<String, Int>): String {
        val obj = JSONObject()
        history.toSortedMap().forEach { (date, seconds) ->
            if (date.isNotBlank() && seconds >= 0) {
                obj.put(date, seconds)
            }
        }
        return obj.toString()
    }

    private fun trimReadHistory(history: Map<String, Int>, keepDays: Int = 60): Map<String, Int> {
        if (history.size <= keepDays) return history
        return history.entries
            .sortedByDescending { it.key }
            .take(keepDays)
            .associate { it.toPair() }
    }

    private fun decodeSyncConflicts(raw: String): List<SyncConflict> {
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
                        SyncConflict(
                            id = id,
                            bookId = bookId,
                            bookTitle = item.optString("bookTitle", "未知书籍"),
                            localPosition = item.optInt("localPosition", 0),
                            localProgress = item.optDouble("localProgress", 0.0).toFloat(),
                            remotePosition = item.optInt("remotePosition", 0),
                            remoteProgress = item.optDouble("remoteProgress", 0.0).toFloat(),
                            createdAt = item.optLong("createdAt", 0L),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeSyncConflicts(conflicts: List<SyncConflict>): String {
        val arr = JSONArray()
        conflicts.forEach { conflict ->
            arr.put(
                JSONObject()
                    .put("id", conflict.id)
                    .put("bookId", conflict.bookId)
                    .put("bookTitle", conflict.bookTitle)
                    .put("localPosition", conflict.localPosition)
                    .put("localProgress", conflict.localProgress.toDouble())
                    .put("remotePosition", conflict.remotePosition)
                    .put("remoteProgress", conflict.remoteProgress.toDouble())
                    .put("createdAt", conflict.createdAt)
            )
        }
        return arr.toString()
    }

    private fun decodeSubscriptions(raw: String): List<BookSubscription> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val bookId = item.optString("bookId")
                    val sourceUrl = item.optString("sourceUrl")
                    if (bookId.isBlank() || sourceUrl.isBlank()) continue
                    add(
                        BookSubscription(
                            bookId = bookId,
                            sourceUrl = sourceUrl,
                            etag = item.optString("etag"),
                            lastModified = item.optString("lastModified"),
                            hasUpdate = item.optBoolean("hasUpdate", false),
                            lastCheckedAt = item.optLong("lastCheckedAt", 0L),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeSubscriptions(subscriptions: List<BookSubscription>): String {
        val arr = JSONArray()
        subscriptions.forEach { item ->
            arr.put(
                JSONObject()
                    .put("bookId", item.bookId)
                    .put("sourceUrl", item.sourceUrl)
                    .put("etag", item.etag)
                    .put("lastModified", item.lastModified)
                    .put("hasUpdate", item.hasUpdate)
                    .put("lastCheckedAt", item.lastCheckedAt)
            )
        }
        return arr.toString()
    }

    private fun decodeOfflineBookIds(raw: String): Set<String> {
        if (raw.isBlank()) return emptySet()
        return runCatching {
            val arr = JSONArray(raw)
            buildSet {
                for (i in 0 until arr.length()) {
                    val id = arr.optString(i)
                    if (id.isNotBlank()) add(id)
                }
            }
        }.getOrDefault(emptySet())
    }

    private fun encodeOfflineBookIds(bookIds: Set<String>): String {
        val arr = JSONArray()
        bookIds.filter { it.isNotBlank() }.sorted().forEach { arr.put(it) }
        return arr.toString()
    }

    private fun decodeCloudProvider(raw: String): CloudSyncProvider =
        runCatching { CloudSyncProvider.valueOf(raw) }.getOrDefault(CloudSyncProvider.GITHUB_GIST)
}
