package com.readflow.app.ui.shelf

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.readflow.app.domain.model.Book
import com.readflow.app.domain.repository.BookRepository
import com.readflow.app.domain.repository.ReaderSettingsRepository
import com.readflow.app.domain.usecase.BackupAndSyncUseCase
import com.readflow.app.domain.usecase.ImportBookUseCase
import com.readflow.app.notifications.ReadingReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val cloudSyncToken: String = "",
    val cloudGistId: String = "",
    val lastBackupPath: String = "",
    val lastSyncAt: Long = 0L,
    val isImporting: Boolean = false,
    val isWorking: Boolean = false,
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
        }
    }

    fun backupToLocal() {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, error = null, message = null) }
            val result = backupAndSyncUseCase.createLocalBackup()
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(
                        isWorking = false,
                        message = "本地备份完成：${result.getOrNull().orEmpty()}",
                    )
                } else {
                    it.copy(
                        isWorking = false,
                        error = result.exceptionOrNull()?.message ?: "本地备份失败",
                    )
                }
            }
        }
    }

    fun restoreFromLocalBackup() {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, error = null, message = null) }
            val result = backupAndSyncUseCase.restoreFromLocalBackup()
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(
                        isWorking = false,
                        message = "本地恢复完成：${result.getOrNull().orEmpty()}",
                    )
                } else {
                    it.copy(
                        isWorking = false,
                        error = result.exceptionOrNull()?.message ?: "本地恢复失败",
                    )
                }
            }
        }
    }

    fun syncToCloud() {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, error = null, message = null) }
            val result = backupAndSyncUseCase.uploadToCloud()
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(
                        isWorking = false,
                        message = "云同步成功：${result.getOrNull().orEmpty()}",
                    )
                } else {
                    it.copy(
                        isWorking = false,
                        error = result.exceptionOrNull()?.message ?: "云同步失败",
                    )
                }
            }
        }
    }

    fun restoreFromCloud() {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, error = null, message = null) }
            val result = backupAndSyncUseCase.restoreFromCloud()
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(
                        isWorking = false,
                        message = "云恢复成功：${result.getOrNull().orEmpty()}",
                    )
                } else {
                    it.copy(
                        isWorking = false,
                        error = result.exceptionOrNull()?.message ?: "云恢复失败",
                    )
                }
            }
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(error = null, message = null) }
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
}
