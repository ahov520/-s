package com.readflow.app.ui.shelf

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.readflow.app.domain.model.Book
import com.readflow.app.domain.repository.BookRepository
import com.readflow.app.domain.usecase.ImportBookUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShelfUiState(
    val books: List<Book> = emptyList(),
    val isImporting: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ShelfViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val importBookUseCase: ImportBookUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ShelfUiState())
    val uiState: StateFlow<ShelfUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            bookRepository.observeBooks().collect { books ->
                _uiState.update { it.copy(books = books) }
            }
        }
    }

    fun importBook(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, error = null) }
            importBookUseCase(uri)
                .onFailure { throwable ->
                    _uiState.update { state ->
                        state.copy(
                            isImporting = false,
                            error = throwable.message ?: "导入失败"
                        )
                    }
                }
                .onSuccess {
                    _uiState.update { it.copy(isImporting = false) }
                }
        }
    }

    fun deleteBook(bookId: String) {
        viewModelScope.launch {
            bookRepository.deleteBook(bookId)
        }
    }
}
