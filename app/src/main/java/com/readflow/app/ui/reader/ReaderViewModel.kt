package com.readflow.app.ui.reader

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.readflow.app.domain.model.Book
import com.readflow.app.domain.model.Bookmark
import com.readflow.app.domain.model.ChapterIndex
import com.readflow.app.domain.model.PageMode
import com.readflow.app.domain.model.ReadingSettings
import com.readflow.app.domain.model.TtsProvider
import com.readflow.app.domain.model.ThemeMode
import com.readflow.app.domain.repository.BookRepository
import com.readflow.app.domain.repository.BookmarkRepository
import com.readflow.app.domain.repository.ChapterIndexRepository
import com.readflow.app.domain.repository.ReaderSettingsRepository
import com.readflow.app.domain.usecase.AutoPageProgressPolicy
import com.readflow.app.domain.usecase.GetBookContentUseCase
import com.readflow.app.domain.usecase.IndexChaptersUseCase
import com.readflow.app.domain.usecase.PaginateContentUseCase
import com.readflow.app.domain.usecase.PaginationLayout
import com.readflow.app.domain.usecase.SearchInTextUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

private const val PROGRESS_SAVE_DEBOUNCE = 3000L
private const val SEARCH_DEBOUNCE = 300L
private const val SEARCH_LIMIT = 200
private const val FOCUS_TICK_SECONDS = 1
private const val TTS_PROGRESS_STEP = 35
private const val STATS_FLUSH_INTERVAL_SECONDS = 20

data class SearchResultUi(
    val position: Int,
    val snippet: String,
    val chapterTitle: String?,
)

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
    val isSearchPanelVisible: Boolean = false,
    val isSearching: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<SearchResultUi> = emptyList(),
    val searchTruncated: Boolean = false,
    val ttsState: TtsPlaybackState = TtsPlaybackState.IDLE,
    val focusSessionSeconds: Int = 0,
    val isFocusTimerRunning: Boolean = false,
    val isMistouchLocked: Boolean = false,
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val bookRepository: BookRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val chapterIndexRepository: ChapterIndexRepository,
    private val settingsRepository: ReaderSettingsRepository,
    private val getBookContentUseCase: GetBookContentUseCase,
    private val indexChaptersUseCase: IndexChaptersUseCase,
    private val paginateContentUseCase: PaginateContentUseCase,
    private val searchInTextUseCase: SearchInTextUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var currentBookId: String? = null
    private var saveProgressJob: Job? = null
    private var settingsJob: Job? = null
    private var bookmarksJob: Job? = null
    private var chaptersJob: Job? = null
    private var currentLayout: PaginationLayout? = null
    private var searchJob: Job? = null
    private var autoPageJob: Job? = null
    private var focusTimerJob: Job? = null
    private var ttsController: SystemTtsController? = null
    private var lastTtsPosition = -1
    private var pendingReadStatsSeconds = 0
    private var pendingReadStatsDate = ""
    private var shouldResumeFocusOnForeground = false

    init {
        ttsController = SystemTtsController(
            context = appContext,
            onStateChanged = { state ->
                _uiState.update { it.copy(ttsState = state) }
                if (state != TtsPlaybackState.SPEAKING) {
                    viewModelScope.launch { settingsRepository.updateAutoPageEnabled(false) }
                }
                ensureAutoPageLoop()
            },
            onProgress = { absolute ->
                if (kotlin.math.abs(absolute - lastTtsPosition) >= TTS_PROGRESS_STEP) {
                    lastTtsPosition = absolute
                    jumpToPosition(absolute)
                }
            },
        )
    }

    fun loadBook(bookId: String) {
        if (bookId.isBlank()) return
        if (currentBookId == bookId && _uiState.value.content.isNotBlank()) return
        currentBookId = bookId
        stopFocusTimer()
        ttsController?.stop()
        autoPageJob?.cancel()

        _uiState.update { it.copy(isLoading = true, error = null) }

        settingsJob?.cancel()
        settingsJob = viewModelScope.launch {
            settingsRepository.observeSettings().collect { settings ->
                _uiState.update { state ->
                    state.copy(
                        settings = settings,
                        isMistouchLocked = if (settings.mistouchGuardEnabled) state.isMistouchLocked else false,
                    )
                }
                ttsController?.setRate(settings.ttsRate)
                ttsController?.setPitch(settings.ttsPitch)
                recalculatePagination()
                ensureAutoPageLoop()
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
                    searchQuery = "",
                    searchResults = emptyList(),
                    searchTruncated = false,
                    isSearchPanelVisible = false,
                    isSearching = false,
                    ttsState = TtsPlaybackState.IDLE,
                    focusSessionSeconds = 0,
                    isFocusTimerRunning = false,
                    isMistouchLocked = _uiState.value.settings.mistouchGuardEnabled,
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

    fun showSearchPanel() {
        _uiState.update { it.copy(isSearchPanelVisible = true) }
    }

    fun hideSearchPanel() {
        _uiState.update { it.copy(isSearchPanelVisible = false) }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), searchTruncated = false, isSearching = false) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE)
            executeSearch(query)
        }
    }

    fun jumpToSearchResult(result: SearchResultUi) {
        jumpToPosition(result.position)
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

    fun startTts() {
        val state = _uiState.value
        if (state.content.isBlank()) return
        ttsController?.setRate(state.settings.ttsRate)
        ttsController?.setPitch(state.settings.ttsPitch)
        ttsController?.speak(state.content, state.currentPosition)
        viewModelScope.launch { settingsRepository.updateAutoPageEnabled(true) }
        if (!_uiState.value.isFocusTimerRunning) {
            startFocusTimer()
        }
    }

    fun pauseTts() {
        ttsController?.pause()
    }

    fun resumeTts() {
        ttsController?.resume()
        viewModelScope.launch { settingsRepository.updateAutoPageEnabled(true) }
    }

    fun stopTts() {
        ttsController?.stop()
        viewModelScope.launch { settingsRepository.updateAutoPageEnabled(false) }
    }

    fun updateTtsRate(value: Float) {
        ttsController?.setRate(value)
        viewModelScope.launch { settingsRepository.updateTtsRate(value) }
    }

    fun updateTtsProvider(provider: TtsProvider) {
        viewModelScope.launch { settingsRepository.updateTtsProvider(provider) }
    }

    fun updateTtsPitch(value: Float) {
        ttsController?.setPitch(value)
        viewModelScope.launch { settingsRepository.updateTtsPitch(value) }
    }

    fun updateAutoPageEnabled(enabled: Boolean) {
        val speaking = _uiState.value.ttsState == TtsPlaybackState.SPEAKING
        if (speaking && !enabled) {
            viewModelScope.launch { settingsRepository.updateAutoPageEnabled(true) }
            return
        }
        viewModelScope.launch { settingsRepository.updateAutoPageEnabled(enabled) }
        ensureAutoPageLoop()
    }

    fun updateAutoPageIntervalMs(value: Int) {
        viewModelScope.launch { settingsRepository.updateAutoPageIntervalMs(value) }
        ensureAutoPageLoop()
    }

    fun updateImmersiveEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateImmersiveEnabled(enabled) }
    }

    fun updateBrightnessLocked(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateBrightnessLocked(enabled) }
    }

    fun updateMistouchGuardEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateMistouchGuardEnabled(enabled) }
        _uiState.update { it.copy(isMistouchLocked = enabled) }
    }

    fun lockMistouch() {
        if (_uiState.value.settings.mistouchGuardEnabled) {
            _uiState.update { it.copy(isMistouchLocked = true) }
        }
    }

    fun unlockMistouch() {
        _uiState.update { it.copy(isMistouchLocked = false) }
    }

    fun toggleFocusTimer() {
        if (_uiState.value.isFocusTimerRunning) {
            stopFocusTimer()
        } else {
            startFocusTimer()
        }
    }

    fun resetFocusSession() {
        _uiState.update { it.copy(focusSessionSeconds = 0) }
    }

    fun onAppBackgrounded() {
        shouldResumeFocusOnForeground = _uiState.value.isFocusTimerRunning
        if (_uiState.value.isFocusTimerRunning) {
            stopFocusTimer()
        } else {
            flushPendingReadStatsAsync()
        }
        if (_uiState.value.settings.mistouchGuardEnabled) {
            lockMistouch()
        }
    }

    fun onAppResumed() {
        if (shouldResumeFocusOnForeground) {
            shouldResumeFocusOnForeground = false
            startFocusTimer()
        }
        ensureAutoPageLoop()
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

    private fun ensureAutoPageLoop() {
        autoPageJob?.cancel()
        val state = _uiState.value
        if (!state.settings.autoPageEnabled) return
        autoPageJob = viewModelScope.launch {
            while (isActive) {
                delay(_uiState.value.settings.autoPageIntervalMs.toLong())
                val snapshot = _uiState.value
                if (!snapshot.settings.autoPageEnabled) break
                if (snapshot.ttsState == TtsPlaybackState.SPEAKING) continue
                if (snapshot.content.isBlank() || snapshot.isMistouchLocked) continue
                if (snapshot.settings.pageMode == PageMode.PAGE) {
                    nextPage()
                } else {
                    val next = AutoPageProgressPolicy.nextScrollPosition(
                        content = snapshot.content,
                        currentPosition = snapshot.currentPosition,
                    )
                    if (next <= snapshot.currentPosition) {
                        settingsRepository.updateAutoPageEnabled(false)
                        break
                    }
                    jumpToPosition(next)
                }
                if (_uiState.value.currentPosition >= _uiState.value.content.length - 1) {
                    settingsRepository.updateAutoPageEnabled(false)
                    break
                }
            }
        }
    }

    private fun startFocusTimer() {
        if (_uiState.value.isFocusTimerRunning) return
        _uiState.update { it.copy(isFocusTimerRunning = true) }
        focusTimerJob?.cancel()
        focusTimerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                val today = currentDateString()
                if (pendingReadStatsDate.isNotBlank() && pendingReadStatsDate != today) {
                    flushPendingReadStats()
                }
                if (pendingReadStatsDate.isBlank()) {
                    pendingReadStatsDate = today
                }
                pendingReadStatsSeconds += FOCUS_TICK_SECONDS
                _uiState.update { it.copy(focusSessionSeconds = it.focusSessionSeconds + FOCUS_TICK_SECONDS) }
                if (pendingReadStatsSeconds >= STATS_FLUSH_INTERVAL_SECONDS) {
                    flushPendingReadStats()
                }
            }
        }
    }

    private fun stopFocusTimer() {
        focusTimerJob?.cancel()
        focusTimerJob = null
        _uiState.update { it.copy(isFocusTimerRunning = false) }
        flushPendingReadStatsAsync()
    }

    private fun currentDateString(): String = LocalDate.now().toString()

    private suspend fun flushPendingReadStats() {
        val delta = pendingReadStatsSeconds
        val day = pendingReadStatsDate
        if (delta <= 0 || day.isBlank()) return
        pendingReadStatsSeconds = 0
        pendingReadStatsDate = ""
        settingsRepository.updateReadStats(secondsDelta = delta, today = day)
    }

    private fun flushPendingReadStatsAsync() {
        viewModelScope.launch { flushPendingReadStats() }
    }

    private suspend fun executeSearch(query: String) {
        val state = _uiState.value
        if (state.content.isBlank()) return

        _uiState.update { it.copy(isSearching = true) }
        val batch = withContext(Dispatchers.Default) {
            searchInTextUseCase(
                content = state.content,
                query = query,
                limit = SEARCH_LIMIT,
            )
        }
        val chapters = _uiState.value.chapters
        val mapped = batch.matches.map { match ->
            SearchResultUi(
                position = match.position,
                snippet = match.snippet,
                chapterTitle = chapterTitleForPosition(chapters, match.position),
            )
        }
        _uiState.update {
            it.copy(
                isSearching = false,
                searchResults = mapped,
                searchTruncated = batch.truncated,
            )
        }
    }

    private fun chapterTitleForPosition(chapters: List<ChapterIndex>, position: Int): String? {
        return chapters.firstOrNull { position in it.startChar until it.endChar }?.title
    }

    override fun onCleared() {
        ttsController?.stop()
        focusTimerJob?.cancel()
        _uiState.update { it.copy(isFocusTimerRunning = false) }
        runCatching { runBlocking { flushPendingReadStats() } }
        autoPageJob?.cancel()
        ttsController?.shutdown()
        super.onCleared()
    }
}
