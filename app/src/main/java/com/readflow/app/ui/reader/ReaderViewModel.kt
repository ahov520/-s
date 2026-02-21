package com.readflow.app.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.readflow.app.domain.model.Book
import com.readflow.app.domain.model.Bookmark
import com.readflow.app.domain.model.ChapterIndex
import com.readflow.app.domain.model.PageMode
import com.readflow.app.domain.model.ReadingSettings
import com.readflow.app.domain.model.ThemeMode
import com.readflow.app.domain.repository.BookRepository
import com.readflow.app.domain.repository.BookmarkRepository
import com.readflow.app.domain.repository.ChapterIndexRepository
import com.readflow.app.domain.repository.ReaderSettingsRepository
import com.readflow.app.domain.usecase.GetBookContentUseCase
import com.readflow.app.domain.usecase.IndexChaptersUseCase
import com.readflow.app.domain.usecase.PaginateContentUseCase
import com.readflow.app.domain.usecase.PaginationLayout
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

private const val PROGRESS_SAVE_DEBOUNCE = 3000L

data class ReaderUiState(
    val book: Book? = null,
    val content: String = "",
    val paragraphs: List<String> = emptyList(),
    val pages: List<String> = listOf(""),
    val currentPage: Int = 0,
    val currentPosition: Int = 0,
    val chapters: List<ChapterIndex> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val settings: ReadingSettings = ReadingSettings(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isMenuVisible: Boolean = true,
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val chapterIndexRepository: ChapterIndexRepository,
    private val settingsRepository: ReaderSettingsRepository,
    private val getBookContentUseCase: GetBookContentUseCase,
    private val indexChaptersUseCase: IndexChaptersUseCase,
    private val paginateContentUseCase: PaginateContentUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var currentBookId: String? = null
    private var saveProgressJob: Job? = null
    private var settingsJob: Job? = null
    private var bookmarksJob: Job? = null
    private var chaptersJob: Job? = null
    private var currentLayout: PaginationLayout? = null

    fun loadBook(bookId: String) {
        if (bookId.isBlank()) return
        if (currentBookId == bookId && _uiState.value.content.isNotBlank()) return
        currentBookId = bookId

        _uiState.update { it.copy(isLoading = true, error = null) }

        settingsJob?.cancel()
        settingsJob = viewModelScope.launch {
            settingsRepository.observeSettings().collect { settings ->
                _uiState.update { it.copy(settings = settings) }
                recalculatePagination()
            }
        }

        bookmarksJob?.cancel()
        bookmarksJob = viewModelScope.launch {
            bookmarkRepository.observeBookmarks(bookId).collect { bookmarks ->
                _uiState.update { it.copy(bookmarks = bookmarks) }
            }
        }

        chaptersJob?.cancel()
        chaptersJob = viewModelScope.launch {
            chapterIndexRepository.observeChapters(bookId).collect { chapters ->
                _uiState.update { it.copy(chapters = chapters) }
            }
        }

        viewModelScope.launch {
            val book = bookRepository.getBook(bookId)
            if (book == null) {
                _uiState.update { it.copy(isLoading = false, error = "书籍不存在") }
                return@launch
            }

            val content = runCatching { getBookContentUseCase(bookId) }
                .getOrElse { throwable ->
                    _uiState.update {
                        it.copy(isLoading = false, error = throwable.message ?: "读取失败")
                    }
                    return@launch
                }

            val paragraphs = content
                .split("\n")
                .map { it.trimEnd() }
                .filter { it.isNotBlank() }
                .ifEmpty { listOf(content) }

            _uiState.update {
                it.copy(
                    book = book,
                    content = content,
                    paragraphs = paragraphs,
                    currentPosition = book.currentPosition,
                    isLoading = false,
                )
            }

            val chapters = indexChaptersUseCase(book.id, content)
            chapterIndexRepository.replaceChapters(book.id, chapters)
            recalculatePagination()
        }
    }

    fun updateLayout(widthPx: Int, heightPx: Int, paddingHorizontalPx: Int = 48) {
        val settings = _uiState.value.settings
        currentLayout = PaginationLayout(
            width = widthPx,
            height = heightPx,
            fontSize = settings.fontSize,
            lineHeight = settings.lineHeight,
            paddingHorizontal = paddingHorizontalPx,
        )
        recalculatePagination()
    }

    fun onPageChanged(index: Int) {
        val pages = _uiState.value.pages
        if (pages.isEmpty()) return
        val safeIndex = index.coerceIn(0, pages.lastIndex)
        val position = paginateContentUseCase.positionForPageIndex(safeIndex, pages)
        updatePosition(position, safeIndex)
    }

    fun nextPage() {
        val state = _uiState.value
        onPageChanged((state.currentPage + 1).coerceAtMost(state.pages.lastIndex))
    }

    fun previousPage() {
        val state = _uiState.value
        onPageChanged((state.currentPage - 1).coerceAtLeast(0))
    }

    fun onScrollPositionChanged(position: Int) {
        updatePosition(position.coerceAtLeast(0), null)
    }

    fun jumpToPosition(position: Int) {
        val pages = _uiState.value.pages
        val page = paginateContentUseCase.pageIndexForPosition(position, pages)
        updatePosition(position, page)
    }

    fun jumpToChapter(chapter: ChapterIndex) {
        jumpToPosition(chapter.startChar)
    }

    fun addBookmark() {
        val state = _uiState.value
        val bookId = currentBookId ?: return
        val progress = ((state.currentPosition.toFloat() / state.content.length.coerceAtLeast(1)) * 100).toInt()
        val bookmark = Bookmark(
            id = UUID.randomUUID().toString(),
            bookId = bookId,
            position = state.currentPosition,
            label = "进度 $progress%",
            createdAt = System.currentTimeMillis(),
        )
        viewModelScope.launch {
            bookmarkRepository.addBookmark(bookmark)
        }
    }

    fun deleteBookmark(bookmarkId: String) {
        viewModelScope.launch {
            bookmarkRepository.deleteBookmark(bookmarkId)
        }
    }

    fun toggleMenu() {
        _uiState.update { it.copy(isMenuVisible = !it.isMenuVisible) }
    }

    fun setMenuVisible(visible: Boolean) {
        _uiState.update { it.copy(isMenuVisible = visible) }
    }

    fun updateFontSize(value: Int) {
        viewModelScope.launch { settingsRepository.updateFontSize(value) }
    }

    fun updateLineHeight(value: Float) {
        viewModelScope.launch { settingsRepository.updateLineHeight(value) }
    }

    fun updateThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.updateThemeMode(mode) }
    }

    fun updatePageMode(mode: PageMode) {
        viewModelScope.launch { settingsRepository.updatePageMode(mode) }
    }

    fun updateBgColor(key: String) {
        viewModelScope.launch { settingsRepository.updateBgColor(key) }
    }

    private fun recalculatePagination() {
        val state = _uiState.value
        if (state.content.isEmpty()) return

        val layout = currentLayout ?: PaginationLayout(
            width = 1080,
            height = 1920,
            fontSize = state.settings.fontSize,
            lineHeight = state.settings.lineHeight,
            paddingHorizontal = 48,
        )

        val result = paginateContentUseCase(
            content = state.content,
            layout = layout.copy(
                fontSize = state.settings.fontSize,
                lineHeight = state.settings.lineHeight,
            )
        )
        val pageIndex = paginateContentUseCase.pageIndexForPosition(state.currentPosition, result.pages)
        _uiState.update {
            it.copy(
                pages = result.pages,
                currentPage = pageIndex,
            )
        }
    }

    private fun updatePosition(position: Int, pageIndex: Int?) {
        val state = _uiState.value
        val contentLength = state.content.length.coerceAtLeast(1)
        val bounded = position.coerceIn(0, contentLength - 1)
        val progress = bounded.toFloat() / contentLength
        _uiState.update {
            it.copy(
                currentPosition = bounded,
                currentPage = pageIndex ?: paginateContentUseCase.pageIndexForPosition(bounded, state.pages),
                book = it.book?.copy(progress = progress),
            )
        }
        scheduleSaveProgress()
    }

    private fun scheduleSaveProgress() {
        val bookId = currentBookId ?: return
        val state = _uiState.value
        saveProgressJob?.cancel()
        saveProgressJob = viewModelScope.launch {
            delay(PROGRESS_SAVE_DEBOUNCE)
            bookRepository.updateProgress(
                bookId = bookId,
                position = state.currentPosition,
                progress = state.currentPosition.toFloat() / state.content.length.coerceAtLeast(1),
                totalChars = state.content.length,
            )
        }
    }
}
