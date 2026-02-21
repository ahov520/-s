package com.readflow.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.readflow.app.domain.model.PageMode
import com.readflow.app.domain.model.ReadingSettings
import com.readflow.app.domain.model.ThemeMode
import com.readflow.app.domain.repository.ReaderSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ReaderSettingsRepository,
) : ViewModel() {
    private val _settings = MutableStateFlow(ReadingSettings())
    val settings: StateFlow<ReadingSettings> = _settings.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeSettings().collect { value ->
                _settings.update { value }
            }
        }
    }

    fun updateFontSize(value: Int) {
        viewModelScope.launch { repository.updateFontSize(value) }
    }

    fun updateLineHeight(value: Float) {
        viewModelScope.launch { repository.updateLineHeight(value) }
    }

    fun updateThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.updateThemeMode(mode) }
    }

    fun updatePageMode(mode: PageMode) {
        viewModelScope.launch { repository.updatePageMode(mode) }
    }

    fun updateBgColor(key: String) {
        viewModelScope.launch { repository.updateBgColor(key) }
    }
}
