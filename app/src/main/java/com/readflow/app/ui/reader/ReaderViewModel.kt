package com.readflow.app.ui.reader

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.readflow.app.domain.model.Book
import com.readflow.app.domain.model.Bookmark
import com.readflow.app.domain.model.ChapterIndex
import com.readflow.app.domain.model.Highlight
import com.readflow.app.domain.model.PageMode
import com.readflow.app.domain.model.ReadingNote
import com.readflow.app.domain.model.ReadingSettings
import com.readflow.app.domain.model.TtsProvider
import com.readflow.app.domain.model.ThemeMode
import com.readflow.app.domain.model.VocabularyWord
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
import com.readflow.app.service.BackgroundAudioService
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

private const val PROGRESS_SAVE_DEBOUNCE = 3000L
private const val SEARCH_DEBOUNCE = 300L
private const val PAGINATION_RECALCULATE_DEBOUNCE = 180L
private const val SEARCH_LIMIT = 200
private const val FOCUS_TICK_SECONDS = 1
private const val TTS_PROGRESS_STEP = 35
private const val STATS_FLUSH_INTERVAL_SECONDS = 20
private const val AI_SNIPPET_WINDOW = 1200

data class SearchResultUi(
    val position: Int,
    val snippet: String,
    val chapterTitle: String?,
)

@Immutable
data class ReaderContentState(
    val book: Book? = null,
    val content: String = "",
    val paragraphs: List<String> = emptyList(),
    val pages: List<String> = listOf(""),
    val chapters: List<ChapterIndex> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val readingNotes: List<ReadingNote> = emptyList(),
    val highlights: List<Highlight> = emptyList(),
    val vocabularyWords: List<VocabularyWord> = emptyList(),
    val aiSummary: String = "",
    val aiKeywords: List<String> = emptyList(),
    val aiSourceLabel: String = "",
    val aiReviewQuestions: List<String> = emptyList(),
)

@Immutable
data class ReaderReadingState(
    val currentPage: Int = 0,
    val currentPosition: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@Immutable
data class ReaderSessionState(
    val ttsState: TtsPlaybackState = TtsPlaybackState.IDLE,
    val focusSessionSeconds: Int = 0,
    val isFocusTimerRunning: Boolean = false,
)

@Immutable
data class ReaderUiControlState(
    val isMenuVisible: Boolean = true,
    val isMistouchLocked: Boolean = false,
    val isSearchPanelVisible: Boolean = false,
    val isSearching: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<SearchResultUi> = emptyList(),
    val searchTruncated: Boolean = false,
    val isGeneratingAi: Boolean = false,
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
    val readingNotes: List<ReadingNote> = emptyList(),
    val highlights: List<Highlight> = emptyList(),
    val vocabularyWords: List<VocabularyWord> = emptyList(),
    val ttsState: TtsPlaybackState = TtsPlaybackState.IDLE,
    val focusSessionSeconds: Int = 0,
    val isFocusTimerRunning: Boolean = false,
    val isMistouchLocked: Boolean = false,
    val aiSummary: String = "",
    val aiKeywords: List<String> = emptyList(),
    val aiSourceLabel: String = "",
    val aiReviewQuestions: List<String> = emptyList(),
    val isGeneratingAi: Boolean = false,
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

    val contentState: StateFlow<ReaderContentState> = _uiState.map {
        ReaderContentState(
            book = it.book,
            content = it.content,
            paragraphs = it.paragraphs,
            pages = it.pages,
            chapters = it.chapters,
            bookmarks = it.bookmarks,
            readingNotes = it.readingNotes,
            highlights = it.highlights,
            vocabularyWords = it.vocabularyWords,
            aiSummary = it.aiSummary,
            aiKeywords = it.aiKeywords,
            aiSourceLabel = it.aiSourceLabel,
            aiReviewQuestions = it.aiReviewQuestions,
        )
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderContentState())

    val readingState: StateFlow<ReaderReadingState> = _uiState.map {
        ReaderReadingState(
            currentPage = it.currentPage,
            currentPosition = it.currentPosition,
            isLoading = it.isLoading,
            error = it.error,
        )
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderReadingState())

    val settingsState: StateFlow<ReadingSettings> = _uiState.map { it.settings }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReadingSettings())

    val sessionState: StateFlow<ReaderSessionState> = _uiState.map {
        ReaderSessionState(
            ttsState = it.ttsState,
            focusSessionSeconds = it.focusSessionSeconds,
            isFocusTimerRunning = it.isFocusTimerRunning,
        )
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderSessionState())

    val uiControlState: StateFlow<ReaderUiControlState> = _uiState.map {
        ReaderUiControlState(
            isMenuVisible = it.isMenuVisible,
            isMistouchLocked = it.isMistouchLocked,
            isSearchPanelVisible = it.isSearchPanelVisible,
            isSearching = it.isSearching,
            searchQuery = it.searchQuery,
            searchResults = it.searchResults,
            searchTruncated = it.searchTruncated,
            isGeneratingAi = it.isGeneratingAi,
        )
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderUiControlState())

    private var currentBookId: String? = null
    private var saveProgressJob: Job? = null
    private var settingsJob: Job? = null
    private var bookmarksJob: Job? = null
    private var chaptersJob: Job? = null
    private var currentLayout: PaginationLayout? = null
    private var searchJob: Job? = null
    private var paginationJob: Job? = null
    private var autoPageJob: Job? = null
    private var focusTimerJob: Job? = null
    private var ttsController: SystemTtsController? = null
    private var lastTtsPosition = -1
    private var pendingReadStatsSeconds = 0
    private var pendingReadStatsDate = ""
    private var shouldResumeFocusOnForeground = false
    private var notesIndexFingerprint = 0
    private var wordsIndexFingerprint = 0
    private var highlightsIndexFingerprint = 0
    private var notesByBookIndex: Map<String, List<ReadingNote>> = emptyMap()
    private var wordsByBookIndex: Map<String, List<VocabularyWord>> = emptyMap()
    private var highlightsByBookIndex: Map<String, List<Highlight>> = emptyMap()

    init {
        ttsController = SystemTtsController(
            context = appContext,
            onStateChanged = { state ->
                _uiState.update { it.copy(ttsState = state) }
                if (state != TtsPlaybackState.SPEAKING) {
                    viewModelScope.launch { settingsRepository.updateAutoPageEnabled(false) }
                }
                syncBackgroundPlayback(state)
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
                val activeBookId = currentBookId
                val notes = notesForBook(settings.readingNotes, activeBookId)
                val highlights = highlightsForBook(settings.highlights, activeBookId)
                val words = wordsForBook(settings.vocabularyWords, activeBookId)
                _uiState.update { state ->
                    state.copy(
                        settings = settings,
                        isMistouchLocked = if (settings.mistouchGuardEnabled) state.isMistouchLocked else false,
                        readingNotes = notes,
                        highlights = highlights,
                        vocabularyWords = words,
                    )
                }
                ttsController?.setRate(settings.ttsRate)
                ttsController?.setPitch(settings.ttsPitch)
                syncBackgroundPlayback()
                scheduleRecalculatePagination()
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
                    aiSummary = "",
                    aiKeywords = emptyList(),
                    aiSourceLabel = "",
                    aiReviewQuestions = emptyList(),
                    isGeneratingAi = false,
                )
            }

            val chapters = indexChaptersUseCase(book.id, content)
            chapterIndexRepository.replaceChapters(book.id, chapters)
            scheduleRecalculatePagination(immediate = true)
        }
    }

    fun updateLayout(widthPx: Int, heightPx: Int, paddingHorizontalPx: Int = 48) {
        val settings = _uiState.value.settings
        val nextLayout = PaginationLayout(
            width = widthPx,
            height = heightPx,
            fontSize = settings.fontSize,
            lineHeight = settings.lineHeight,
            paddingHorizontal = paddingHorizontalPx,
        )
        if (currentLayout == nextLayout) return
        currentLayout = nextLayout
        scheduleRecalculatePagination()
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

    fun updateBackgroundTtsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateBackgroundTtsEnabled(enabled) }
        syncBackgroundPlayback()
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

    fun addReadingNote(noteText: String) {
        val state = _uiState.value
        val bookId = currentBookId ?: return
        if (state.content.isBlank()) return
        val note = noteText.trim()
        if (note.isBlank()) return
        val start = state.currentPosition.coerceAtLeast(0)
        val end = (start + 160).coerceAtMost(state.content.length)
        val quote = state.content.substring(start, end).trim().ifBlank { "当前位置片段" }
        val item = ReadingNote(
            id = UUID.randomUUID().toString(),
            bookId = bookId,
            startChar = start,
            endChar = end,
            quote = quote,
            note = note,
            createdAt = System.currentTimeMillis(),
        )
        viewModelScope.launch { settingsRepository.upsertReadingNote(item) }
    }

    fun deleteReadingNote(noteId: String) {
        viewModelScope.launch { settingsRepository.deleteReadingNote(noteId) }
    }

    fun jumpToReadingNote(note: ReadingNote) {
        jumpToPosition(note.startChar)
    }

    fun addHighlight(colorKey: String, noteText: String = "") {
        val state = _uiState.value
        val bookId = currentBookId ?: return
        if (state.content.isBlank()) return
        val safeColor = colorKey.ifBlank { "amber" }
        val start = state.currentPosition.coerceAtLeast(0)
        val end = (start + 120).coerceAtMost(state.content.length)
        if (end <= start) return
        val quote = state.content.substring(start, end).trim().ifBlank { "当前位置片段" }
        val item = Highlight(
            id = UUID.randomUUID().toString(),
            bookId = bookId,
            startChar = start,
            endChar = end,
            quote = quote,
            colorKey = safeColor,
            note = noteText.trim(),
            createdAt = System.currentTimeMillis(),
        )
        viewModelScope.launch { settingsRepository.upsertHighlight(item) }
    }

    fun deleteHighlight(highlightId: String) {
        viewModelScope.launch { settingsRepository.deleteHighlight(highlightId) }
    }

    fun jumpToHighlight(highlight: Highlight) {
        jumpToPosition(highlight.startChar)
    }

    fun addVocabularyWord(wordText: String, meaningText: String) {
        val bookId = currentBookId ?: return
        val word = wordText.trim()
        val meaning = meaningText.trim()
        if (word.isBlank()) return
        val sentence = buildSnippetAroundCurrent()
        val item = VocabularyWord(
            id = UUID.randomUUID().toString(),
            bookId = bookId,
            word = word,
            meaning = meaning,
            sentence = sentence,
            createdAt = System.currentTimeMillis(),
        )
        viewModelScope.launch { settingsRepository.upsertVocabularyWord(item) }
    }

    fun deleteVocabularyWord(wordId: String) {
        viewModelScope.launch { settingsRepository.deleteVocabularyWord(wordId) }
    }

    fun generateLocalAiSummary() {
        val state = _uiState.value
        if (state.content.isBlank()) return
        _uiState.update { it.copy(isGeneratingAi = true) }
        viewModelScope.launch {
            val summary = withContext(Dispatchers.Default) {
                val (sourceLabel, snippet) = buildAiSourceText()
                val result = summarizeSnippet(snippet)
                Triple(sourceLabel, result.first, result.second)
            }
            _uiState.update {
                it.copy(
                    isGeneratingAi = false,
                    aiSourceLabel = summary.first,
                    aiSummary = summary.second.first,
                    aiReviewQuestions = summary.second.second,
                    aiKeywords = summary.third,
                )
            }
        }
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

    private fun scheduleRecalculatePagination(immediate: Boolean = false) {
        if (immediate) {
            paginationJob?.cancel()
            recalculatePaginationNow()
            return
        }
        paginationJob?.cancel()
        paginationJob = viewModelScope.launch {
            delay(PAGINATION_RECALCULATE_DEBOUNCE)
            recalculatePaginationNow()
        }
    }

    private fun recalculatePaginationNow() {
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

    private fun notesForBook(allNotes: List<ReadingNote>, bookId: String?): List<ReadingNote> {
        val safeBookId = bookId ?: return emptyList()
        val fingerprint = notesFingerprint(allNotes)
        if (fingerprint != notesIndexFingerprint) {
            notesIndexFingerprint = fingerprint
            notesByBookIndex = allNotes
                .groupBy { it.bookId }
                .mapValues { (_, list) -> list.sortedByDescending { it.createdAt } }
        }
        return notesByBookIndex[safeBookId].orEmpty()
    }

    private fun wordsForBook(allWords: List<VocabularyWord>, bookId: String?): List<VocabularyWord> {
        val safeBookId = bookId ?: return emptyList()
        val fingerprint = wordsFingerprint(allWords)
        if (fingerprint != wordsIndexFingerprint) {
            wordsIndexFingerprint = fingerprint
            wordsByBookIndex = allWords
                .groupBy { it.bookId }
                .mapValues { (_, list) -> list.sortedByDescending { it.createdAt } }
        }
        return wordsByBookIndex[safeBookId].orEmpty()
    }

    private fun highlightsForBook(allHighlights: List<Highlight>, bookId: String?): List<Highlight> {
        val safeBookId = bookId ?: return emptyList()
        val fingerprint = highlightsFingerprint(allHighlights)
        if (fingerprint != highlightsIndexFingerprint) {
            highlightsIndexFingerprint = fingerprint
            highlightsByBookIndex = allHighlights
                .groupBy { it.bookId }
                .mapValues { (_, list) -> list.sortedByDescending { it.createdAt } }
        }
        return highlightsByBookIndex[safeBookId].orEmpty()
    }

    private fun notesFingerprint(notes: List<ReadingNote>): Int {
        var fingerprint = notes.size
        for (note in notes) {
            fingerprint = 31 * fingerprint + note.id.hashCode()
            fingerprint = 31 * fingerprint + note.bookId.hashCode()
            fingerprint = 31 * fingerprint + note.createdAt.hashCode()
        }
        return fingerprint
    }

    private fun wordsFingerprint(words: List<VocabularyWord>): Int {
        var fingerprint = words.size
        for (word in words) {
            fingerprint = 31 * fingerprint + word.id.hashCode()
            fingerprint = 31 * fingerprint + word.bookId.hashCode()
            fingerprint = 31 * fingerprint + word.createdAt.hashCode()
        }
        return fingerprint
    }

    private fun highlightsFingerprint(highlights: List<Highlight>): Int {
        var fingerprint = highlights.size
        for (highlight in highlights) {
            fingerprint = 31 * fingerprint + highlight.id.hashCode()
            fingerprint = 31 * fingerprint + highlight.bookId.hashCode()
            fingerprint = 31 * fingerprint + highlight.createdAt.hashCode()
        }
        return fingerprint
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

    private fun syncBackgroundPlayback(forcedState: TtsPlaybackState? = null) {
        val state = _uiState.value
        val playbackState = forcedState ?: state.ttsState
        val shouldRun = state.settings.backgroundTtsEnabled && playbackState == TtsPlaybackState.SPEAKING
        if (shouldRun) {
            val intent = Intent(appContext, BackgroundAudioService::class.java).apply {
                putExtra(BackgroundAudioService.EXTRA_TITLE, state.book?.title ?: "后台听书中")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(appContext, intent)
            } else {
                appContext.startService(intent)
            }
        } else {
            appContext.stopService(Intent(appContext, BackgroundAudioService::class.java))
        }
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

    private fun buildSnippetAroundCurrent(): String {
        val state = _uiState.value
        if (state.content.isBlank()) return ""
        val center = state.currentPosition.coerceIn(0, state.content.lastIndex)
        val start = (center - AI_SNIPPET_WINDOW).coerceAtLeast(0)
        val end = (center + AI_SNIPPET_WINDOW).coerceAtMost(state.content.length)
        return state.content.substring(start, end).trim()
    }

    private fun buildAiSourceText(): Pair<String, String> {
        val state = _uiState.value
        val chapter = state.chapters.firstOrNull { state.currentPosition in it.startChar until it.endChar }
        if (chapter != null && state.content.isNotBlank()) {
            val start = chapter.startChar.coerceAtLeast(0).coerceAtMost(state.content.length)
            val end = chapter.endChar.coerceAtLeast(start).coerceAtMost(state.content.length)
            if (end > start) {
                val section = state.content.substring(start, end).trim()
                if (section.isNotBlank()) {
                    val trimmed = if (section.length > 6000) section.take(6000) else section
                    return (chapter.title.ifBlank { "当前章节" }) to trimmed
                }
            }
        }
        return "当前片段" to buildSnippetAroundCurrent()
    }

    private fun summarizeSnippet(snippet: String): Pair<Pair<String, List<String>>, List<String>> {
        if (snippet.isBlank()) return ("当前片段暂无可总结内容。" to emptyList()) to emptyList()
        val normalized = snippet
            .replace("\r", "")
            .replace(Regex("\\s+"), " ")
            .trim()
        val sentences = normalized
            .split(Regex("(?<=[。！？.!?])"))
            .map { it.trim() }
            .filter { it.length >= 8 }
        val keySentences = when {
            sentences.size >= 3 -> listOf(sentences.first(), sentences[sentences.size / 2], sentences.last())
            sentences.isNotEmpty() -> sentences
            else -> listOf(normalized.take(180))
        }
        val summary = buildString {
            append("本段要点：")
            keySentences.forEachIndexed { index, line ->
                append("\n${index + 1}. ")
                append(line.take(90))
            }
        }
        val review = listOf(
            "这一段最关键的冲突或转折是什么？",
            "主角在本段做出的决定会带来什么后果？",
            "你会如何用两句话复述本段核心内容？",
        )
        return (summary to review) to extractKeywords(normalized)
    }

    private fun extractKeywords(text: String, limit: Int = 8): List<String> {
        val stopWords = setOf("的", "了", "和", "是", "在", "与", "及", "或", "to", "the", "and", "of", "a", "an")
        val tokens = Regex("[\\p{IsHan}]{2,}|[A-Za-z]{3,}")
            .findAll(text.lowercase())
            .map { it.value }
            .filter { it !in stopWords }
            .toList()
        if (tokens.isEmpty()) return emptyList()
        return tokens
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
    }

    override fun onCleared() {
        ttsController?.stop()
        focusTimerJob?.cancel()
        _uiState.update { it.copy(isFocusTimerRunning = false) }
        runCatching { runBlocking { flushPendingReadStats() } }
        runCatching { syncBackgroundPlayback(TtsPlaybackState.IDLE) }
        autoPageJob?.cancel()
        paginationJob?.cancel()
        ttsController?.shutdown()
        super.onCleared()
    }
}
