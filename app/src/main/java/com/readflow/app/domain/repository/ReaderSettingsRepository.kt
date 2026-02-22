package com.readflow.app.domain.repository

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
    suspend fun updateBackgroundTtsEnabled(enabled: Boolean)
    suspend fun updateAutoPageEnabled(enabled: Boolean)
    suspend fun updateAutoPageIntervalMs(value: Int)
    suspend fun updateImmersiveEnabled(enabled: Boolean)
    suspend fun updateBrightnessLocked(enabled: Boolean)
    suspend fun updateMistouchGuardEnabled(enabled: Boolean)
    suspend fun updateReadStats(secondsDelta: Int, today: String)
    suspend fun resetDailyReadStats(today: String)
    suspend fun replaceReadStats(dailyReadSeconds: Int, lastReadDate: String, streakDays: Int)
    suspend fun updateDailyGoalMinutes(value: Int)
    suspend fun updateReminder(enabled: Boolean, hour: Int, minute: Int)
    suspend fun updateBookGroup(bookId: String, group: String)
    suspend fun replaceBookGroups(groups: Map<String, String>)
    suspend fun upsertReadingNote(note: ReadingNote)
    suspend fun replaceReadingNotes(notes: List<ReadingNote>)
    suspend fun deleteReadingNote(noteId: String)
    suspend fun upsertHighlight(highlight: Highlight)
    suspend fun replaceHighlights(highlights: List<Highlight>)
    suspend fun deleteHighlight(highlightId: String)
    suspend fun upsertVocabularyWord(word: VocabularyWord)
    suspend fun deleteVocabularyWord(wordId: String)
    suspend fun replaceVocabularyWords(words: List<VocabularyWord>)
    suspend fun replaceSyncConflicts(conflicts: List<SyncConflict>)
    suspend fun removeSyncConflict(conflictId: String)
    suspend fun upsertBookSubscription(subscription: BookSubscription)
    suspend fun replaceBookSubscriptions(subscriptions: List<BookSubscription>)
    suspend fun replaceOfflineCachedBookIds(bookIds: Set<String>)
    suspend fun markBookOfflineCached(bookId: String, cached: Boolean)
    suspend fun updateCloudProvider(provider: CloudSyncProvider)
    suspend fun updateCloudWebDavConfig(endpoint: String, username: String, remotePath: String)
    suspend fun updateCloudSyncConfig(token: String, gistId: String)
    suspend fun replaceReadHistory(history: Map<String, Int>)
    suspend fun updateLastBackupPath(path: String)
    suspend fun updateLastSyncAt(epochMillis: Long)
}
