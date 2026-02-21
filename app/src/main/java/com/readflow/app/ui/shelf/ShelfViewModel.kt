package com.readflow.app.ui.shelf

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.readflow.app.domain.model.Book
import com.readflow.app.domain.model.BookSubscription
import com.readflow.app.domain.model.CloudSyncProvider
import com.readflow.app.domain.model.SyncConflict
import com.readflow.app.domain.repository.BookRepository
import com.readflow.app.domain.repository.ReaderSettingsRepository
import com.readflow.app.domain.usecase.BackupAndSyncUseCase
import com.readflow.app.domain.usecase.GetBookContentUseCase
import com.readflow.app.domain.usecase.ImportBookUseCase
import com.readflow.app.notifications.ReadingReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ShelfUiState(
    val books: List<Book> = emptyList(),
    val dailyReadSeconds: Int = 0,
    val streakDays: Int = 0,
    val dailyGoalMinutes: Int = 60,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 21,
    val reminderMinute: Int = 0,
    val bookGroups: Map<String, String> = emptyMap(),
    val notesCount: Int = 0,
    val vocabularyCount: Int = 0,
    val syncConflicts: List<SyncConflict> = emptyList(),
    val subscriptions: List<BookSubscription> = emptyList(),
    val offlineCachedBookIds: Set<String> = emptySet(),
    val cloudProvider: CloudSyncProvider = CloudSyncProvider.GITHUB_GIST,
    val cloudWebDavEndpoint: String = "",
    val cloudWebDavUsername: String = "",
    val cloudRemotePath: String = "readflow-backup.json",
    val cloudSyncToken: String = "",
    val cloudGistId: String = "",
    val lastBackupPath: String = "",
    val lastSyncAt: Long = 0L,
    val restoreMode: BackupAndSyncUseCase.RestoreMode = BackupAndSyncUseCase.RestoreMode.MERGE,
    val isImporting: Boolean = false,
    val isWorking: Boolean = false,
    val workLabel: String? = null,
    val error: String? = null,
    val message: String? = null,
)

@HiltViewModel
class ShelfViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val settingsRepository: ReaderSettingsRepository,
    private val importBookUseCase: ImportBookUseCase,
    private val backupAndSyncUseCase: BackupAndSyncUseCase,
    private val getBookContentUseCase: GetBookContentUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ShelfUiState())
    val uiState: StateFlow<ShelfUiState> = _uiState.asStateFlow()

    private var reminderSignature: String = ""

    init {
        viewModelScope.launch {
            bookRepository.observeBooks().collect { books ->
                _uiState.update { it.copy(books = books) }
            }
        }
        viewModelScope.launch {
            settingsRepository.observeSettings().collect { settings ->
                _uiState.update {
                    it.copy(
                        dailyReadSeconds = settings.dailyReadSeconds,
                        streakDays = settings.streakDays,
                        dailyGoalMinutes = settings.dailyGoalMinutes,
                        reminderEnabled = settings.reminderEnabled,
                        reminderHour = settings.reminderHour,
                        reminderMinute = settings.reminderMinute,
                        bookGroups = settings.bookGroups,
                        notesCount = settings.readingNotes.size,
                        vocabularyCount = settings.vocabularyWords.size,
                        syncConflicts = settings.syncConflicts.sortedByDescending { it.createdAt },
                        subscriptions = settings.bookSubscriptions.sortedByDescending { it.lastCheckedAt },
                        offlineCachedBookIds = settings.offlineCachedBookIds,
                        cloudProvider = settings.cloudProvider,
                        cloudWebDavEndpoint = settings.cloudWebDavEndpoint,
                        cloudWebDavUsername = settings.cloudWebDavUsername,
                        cloudRemotePath = settings.cloudRemotePath,
                        cloudSyncToken = settings.cloudSyncToken,
                        cloudGistId = settings.cloudGistId,
                        lastBackupPath = settings.lastBackupPath,
                        lastSyncAt = settings.lastSyncAt,
                    )
                }
                ensureReminderScheduled(
                    enabled = settings.reminderEnabled,
                    hour = settings.reminderHour,
                    minute = settings.reminderMinute,
                )
            }
        }
    }

    fun importBook(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, error = null, message = null) }
            importBookUseCase(uri)
                .onFailure { throwable ->
                    _uiState.update { state ->
                        state.copy(
                            isImporting = false,
                            error = throwable.message ?: "导入失败",
                        )
                    }
                }
                .onSuccess {
                    _uiState.update { it.copy(isImporting = false, message = "导入完成") }
                }
        }
    }

    fun deleteBook(bookId: String) {
        viewModelScope.launch {
            bookRepository.deleteBook(bookId)
            _uiState.update { it.copy(message = "已删除书籍") }
        }
    }

    fun updateBookGroup(bookId: String, group: String) {
        viewModelScope.launch {
            settingsRepository.updateBookGroup(bookId, group)
            _uiState.update { it.copy(message = "分组已更新") }
        }
    }

    fun updateDailyGoalMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.updateDailyGoalMinutes(minutes)
        }
    }

    fun updateReminder(enabled: Boolean, hour: Int, minute: Int) {
        viewModelScope.launch {
            settingsRepository.updateReminder(enabled, hour, minute)
            ensureReminderScheduled(enabled, hour, minute)
        }
    }

    fun updateCloudSyncConfig(token: String, gistId: String) {
        viewModelScope.launch {
            settingsRepository.updateCloudSyncConfig(token, gistId)
            _uiState.update { it.copy(message = "云同步配置已保存") }
        }
    }

    fun updateCloudProvider(provider: CloudSyncProvider) {
        viewModelScope.launch {
            settingsRepository.updateCloudProvider(provider)
            _uiState.update { it.copy(message = "云同步通道已更新") }
        }
    }

    fun updateCloudWebDavConfig(endpoint: String, username: String, remotePath: String) {
        viewModelScope.launch {
            settingsRepository.updateCloudWebDavConfig(endpoint, username, remotePath)
            _uiState.update { it.copy(message = "WebDAV 配置已保存") }
        }
    }

    fun updateRestoreMode(mode: BackupAndSyncUseCase.RestoreMode) {
        _uiState.update { it.copy(restoreMode = mode) }
    }

    fun backupToLocal() {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, workLabel = "正在生成本地备份...", error = null, message = null) }
            val result = backupAndSyncUseCase.createLocalBackup()
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(
                        isWorking = false,
                        workLabel = null,
                        message = "本地备份完成：${result.getOrNull().orEmpty()}",
                    )
                } else {
                    it.copy(
                        isWorking = false,
                        workLabel = null,
                        error = result.exceptionOrNull()?.message ?: "本地备份失败",
                    )
                }
            }
        }
    }

    fun restoreFromLocalBackup(mode: BackupAndSyncUseCase.RestoreMode = _uiState.value.restoreMode) {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, workLabel = "正在执行本地恢复...", error = null, message = null) }
            val result = backupAndSyncUseCase.restoreFromLocalBackup(mode = mode)
            val label = if (mode == BackupAndSyncUseCase.RestoreMode.OVERWRITE) "覆盖恢复" else "合并恢复"
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(
                        isWorking = false,
                        workLabel = null,
                        message = "本地${label}完成：${result.getOrNull().orEmpty()}",
                    )
                } else {
                    it.copy(
                        isWorking = false,
                        workLabel = null,
                        error = result.exceptionOrNull()?.message ?: "本地恢复失败",
                    )
                }
            }
        }
    }

    fun syncToCloud() {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, workLabel = "正在上传云端备份...", error = null, message = null) }
            val result = backupAndSyncUseCase.uploadToCloud()
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(
                        isWorking = false,
                        workLabel = null,
                        message = "云同步成功：${result.getOrNull().orEmpty()}",
                    )
                } else {
                    it.copy(
                        isWorking = false,
                        workLabel = null,
                        error = result.exceptionOrNull()?.message ?: "云同步失败",
                    )
                }
            }
        }
    }

    fun restoreFromCloud(mode: BackupAndSyncUseCase.RestoreMode = _uiState.value.restoreMode) {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, workLabel = "正在从云端恢复...", error = null, message = null) }
            val result = backupAndSyncUseCase.restoreFromCloud(mode = mode)
            val label = if (mode == BackupAndSyncUseCase.RestoreMode.OVERWRITE) "覆盖恢复" else "合并恢复"
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(
                        isWorking = false,
                        workLabel = null,
                        message = "云端${label}成功：${result.getOrNull().orEmpty()}",
                    )
                } else {
                    it.copy(
                        isWorking = false,
                        workLabel = null,
                        error = result.exceptionOrNull()?.message ?: "云恢复失败",
                    )
                }
            }
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(error = null, message = null) }
    }

    fun resolveSyncConflict(conflictId: String, useRemote: Boolean) {
        viewModelScope.launch {
            val conflict = _uiState.value.syncConflicts.firstOrNull { it.id == conflictId } ?: return@launch
            if (useRemote) {
                val totalChars = _uiState.value.books.firstOrNull { it.id == conflict.bookId }?.totalChars ?: 0
                bookRepository.updateProgress(
                    bookId = conflict.bookId,
                    position = conflict.remotePosition,
                    progress = conflict.remoteProgress.coerceIn(0f, 1f),
                    totalChars = totalChars,
                )
            }
            settingsRepository.removeSyncConflict(conflictId)
            _uiState.update { it.copy(message = if (useRemote) "已采用云端进度" else "已保留本地进度") }
        }
    }

    fun clearSyncConflicts() {
        viewModelScope.launch {
            settingsRepository.replaceSyncConflicts(emptyList())
            _uiState.update { it.copy(message = "冲突列表已清空") }
        }
    }

    fun updateBookSubscription(bookId: String, sourceUrl: String) {
        val url = sourceUrl.trim()
        if (bookId.isBlank() || url.isBlank()) return
        viewModelScope.launch {
            val existing = _uiState.value.subscriptions.firstOrNull { it.bookId == bookId }
            settingsRepository.upsertBookSubscription(
                BookSubscription(
                    bookId = bookId,
                    sourceUrl = url,
                    etag = existing?.etag.orEmpty(),
                    lastModified = existing?.lastModified.orEmpty(),
                    hasUpdate = existing?.hasUpdate ?: false,
                    lastCheckedAt = existing?.lastCheckedAt ?: 0L,
                )
            )
            _uiState.update { it.copy(message = "追更订阅已保存") }
        }
    }

    fun checkSubscriptions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, workLabel = "正在检查订阅更新...", error = null, message = null) }
            val subscriptions = _uiState.value.subscriptions
            var updateCount = 0
            for (item in subscriptions) {
                val updated = withContext(Dispatchers.IO) { checkSubscriptionUpdate(item) }
                if (updated.hasUpdate) updateCount += 1
                settingsRepository.upsertBookSubscription(updated)
            }
            _uiState.update {
                it.copy(
                    isWorking = false,
                    workLabel = null,
                    message = if (subscriptions.isEmpty()) "当前没有订阅项" else "订阅检查完成：$updateCount 本有更新",
                )
            }
        }
    }

    fun cacheAllBooksOffline() {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, workLabel = "正在缓存离线包...", error = null, message = null) }
            val books = _uiState.value.books
            val folder = File(context.filesDir, "offline").apply { mkdirs() }
            var cachedCount = 0
            books.forEach { book ->
                val content = runCatching { getBookContentUseCase(book.id) }.getOrDefault("")
                if (content.isNotBlank()) {
                    runCatching {
                        File(folder, "${book.id}.txt").writeText(content, Charsets.UTF_8)
                        settingsRepository.markBookOfflineCached(book.id, true)
                        cachedCount += 1
                    }
                }
            }
            _uiState.update {
                it.copy(
                    isWorking = false,
                    workLabel = null,
                    message = "离线缓存完成：$cachedCount/${books.size}",
                )
            }
        }
    }

    fun clearOfflineCache() {
        viewModelScope.launch {
            val folder = File(context.filesDir, "offline")
            val cachedIds = _uiState.value.offlineCachedBookIds
            cachedIds.forEach { id ->
                runCatching { File(folder, "$id.txt").delete() }
                settingsRepository.markBookOfflineCached(id, false)
            }
            _uiState.update { it.copy(message = "离线缓存已清理") }
        }
    }

    private fun ensureReminderScheduled(enabled: Boolean, hour: Int, minute: Int) {
        val signature = "$enabled-$hour-$minute"
        if (signature == reminderSignature) return
        reminderSignature = signature
        ReadingReminderScheduler.schedule(
            context = context,
            enabled = enabled,
            hour = hour,
            minute = minute,
        )
    }

    private fun checkSubscriptionUpdate(item: BookSubscription): BookSubscription {
        return runCatching {
            val conn = (URL(item.sourceUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = 7000
                readTimeout = 7000
                setRequestProperty("User-Agent", "ReadFlow-Android")
            }
            val code = conn.responseCode
            val etag = conn.getHeaderField("ETag").orEmpty()
            val lastModified = conn.getHeaderField("Last-Modified").orEmpty()
            conn.disconnect()

            if (code !in 200..399) {
                item.copy(lastCheckedAt = System.currentTimeMillis())
            } else {
                val hasUpdate = when {
                    item.etag.isNotBlank() && etag.isNotBlank() -> item.etag != etag
                    item.lastModified.isNotBlank() && lastModified.isNotBlank() -> item.lastModified != lastModified
                    else -> false
                }
                item.copy(
                    etag = etag.ifBlank { item.etag },
                    lastModified = lastModified.ifBlank { item.lastModified },
                    hasUpdate = hasUpdate,
                    lastCheckedAt = System.currentTimeMillis(),
                )
            }
        }.getOrElse {
            item.copy(lastCheckedAt = System.currentTimeMillis())
        }
    }
}
