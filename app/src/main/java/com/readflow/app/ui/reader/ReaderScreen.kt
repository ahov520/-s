package com.readflow.app.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.readflow.app.domain.model.PageMode
import com.readflow.app.ui.reader.components.BookmarkSheet
import com.readflow.app.ui.reader.components.ChapterListSheet
import com.readflow.app.ui.reader.components.HighlightSheet
import com.readflow.app.ui.reader.components.ImmersiveSheet
import com.readflow.app.ui.reader.components.PageReader
import com.readflow.app.ui.reader.components.AiAssistantSheet
import com.readflow.app.ui.reader.components.ReadingNotesSheet
import com.readflow.app.ui.reader.components.ReaderToolbar
import com.readflow.app.ui.reader.components.SearchSheet
import com.readflow.app.ui.reader.components.ScrollReader
import com.readflow.app.ui.reader.components.VocabularySheet
import com.readflow.app.ui.settings.SettingsSheet
import com.readflow.app.ui.theme.readingThemeFor

@Composable
fun ReaderScreen(
    bookId: String,
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
) {
    val content by viewModel.contentState.collectAsStateWithLifecycle()
    val reading by viewModel.readingState.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val session by viewModel.sessionState.collectAsStateWithLifecycle()
    val uiControl by viewModel.uiControlState.collectAsStateWithLifecycle()

    var showSettings by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(false) }
    var showHighlights by remember { mutableStateOf(false) }
    var showVocabulary by remember { mutableStateOf(false) }
    var showAiAssistant by remember { mutableStateOf(false) }
    var showChapters by remember { mutableStateOf(false) }
    var showImmersive by remember { mutableStateOf(false) }
    val activity = LocalContext.current.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    val originalBrightness = remember(activity) { activity?.window?.attributes?.screenBrightness ?: -1f }

    LaunchedEffect(bookId) {
        viewModel.loadBook(bookId)
    }

    val readingTheme = readingThemeFor(settings.bgColorKey)
    val progress by remember {
        derivedStateOf {
            if (content.content.isEmpty()) 0f
            else reading.currentPosition.toFloat() / content.content.length
        }
    }

    DisposableEffect(
        settings.immersiveEnabled,
        settings.brightnessLocked,
        activity,
        lifecycleOwner,
        originalBrightness,
    ) {
        if (activity != null) {
            val window = activity.window
            val decor = window.decorView
            val controller = WindowInsetsControllerCompat(window, decor)

            fun applyImmersiveState() {
                if (settings.immersiveEnabled) {
                    WindowCompat.setDecorFitsSystemWindows(window, false)
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    WindowCompat.setDecorFitsSystemWindows(window, true)
                    controller.show(WindowInsetsCompat.Type.systemBars())
                }

                val attrs = window.attributes
                attrs.screenBrightness = if (settings.brightnessLocked) {
                    if (attrs.screenBrightness > 0f) attrs.screenBrightness else 0.55f
                } else {
                    -1f
                }
                window.attributes = attrs
            }

            fun restoreWindowState() {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                controller.show(WindowInsetsCompat.Type.systemBars())
                val attrs = window.attributes
                attrs.screenBrightness = originalBrightness
                window.attributes = attrs
            }

            applyImmersiveState()

            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        applyImmersiveState()
                        viewModel.onAppResumed()
                    }

                    Lifecycle.Event.ON_STOP -> {
                        restoreWindowState()
                        viewModel.onAppBackgrounded()
                    }

                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)

            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                restoreWindowState()
            }
        } else {
            onDispose { }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(readingTheme.bgColor)
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.roundToPx() }
        val heightPx = with(density) { maxHeight.roundToPx() }

        LaunchedEffect(widthPx, heightPx, settings.fontSize, settings.lineHeight) {
            viewModel.updateLayout(widthPx = widthPx, heightPx = heightPx)
        }

        if (reading.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (reading.error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = reading.error.orEmpty())
            }
        } else {
            when (settings.pageMode) {
                PageMode.SCROLL -> ScrollReader(
                    paragraphs = content.paragraphs,
                    fontSizeSp = settings.fontSize,
                    lineHeight = settings.lineHeight,
                    textColor = readingTheme.textColor,
                    currentPosition = reading.currentPosition,
                    onToggleMenu = viewModel::toggleMenu,
                    onPositionChanged = viewModel::onScrollPositionChanged,
                )

                PageMode.PAGE -> PageReader(
                    pages = content.pages,
                    currentPage = reading.currentPage,
                    fontSizeSp = settings.fontSize,
                    lineHeight = settings.lineHeight,
                    textColor = readingTheme.textColor,
                    onPageChanged = viewModel::onPageChanged,
                    onToggleMenu = viewModel::toggleMenu,
                    onPrevPage = viewModel::previousPage,
                    onNextPage = viewModel::nextPage,
                )
            }
        }

        AnimatedVisibility(
            visible = !uiControl.isMenuVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
            )
        }

        ReaderToolbar(
            visible = uiControl.isMenuVisible,
            title = content.book?.title.orEmpty(),
            progress = progress,
            showPageActions = settings.pageMode == PageMode.PAGE,
            onBack = onBack,
            onAddBookmark = viewModel::addBookmark,
            onShowBookmarks = { showBookmarks = true },
            onShowChapters = { showChapters = true },
            onShowSearch = viewModel::showSearchPanel,
            onShowHighlights = { showHighlights = true },
            onShowNotes = { showNotes = true },
            onShowVocabulary = { showVocabulary = true },
            onShowAi = { showAiAssistant = true },
            onShowSettings = { showSettings = true },
            onShowImmersive = { showImmersive = true },
            onProgressChange = { ratio ->
                val pos = (content.content.length * ratio).toInt()
                viewModel.jumpToPosition(pos)
            },
            onPrevPage = viewModel::previousPage,
            onNextPage = viewModel::nextPage,
        )

        if (settings.mistouchGuardEnabled && uiControl.isMistouchLocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.36f))
                    .pointerInput(Unit) {
                        detectTapGestures(onLongPress = { viewModel.unlockMistouch() })
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "防误触已锁定\n长按屏幕解除",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }

    if (showSettings) {
        SettingsSheet(
            settings = settings,
            onDismiss = { showSettings = false },
            onPageModeChange = viewModel::updatePageMode,
            onFontSizeChange = viewModel::updateFontSize,
            onLineHeightChange = viewModel::updateLineHeight,
            onThemeModeChange = viewModel::updateThemeMode,
            onBgColorChange = viewModel::updateBgColor,
        )
    }

    if (showBookmarks) {
        BookmarkSheet(
            bookmarks = content.bookmarks,
            onDismiss = { showBookmarks = false },
            onAddBookmark = viewModel::addBookmark,
            onDeleteBookmark = viewModel::deleteBookmark,
            onJumpToBookmark = {
                viewModel.jumpToPosition(it.position)
                showBookmarks = false
            },
        )
    }

    if (showNotes) {
        ReadingNotesSheet(
            notes = content.readingNotes,
            onDismiss = { showNotes = false },
            onAddNote = viewModel::addReadingNote,
            onDeleteNote = viewModel::deleteReadingNote,
            onJumpToNote = {
                viewModel.jumpToReadingNote(it)
                showNotes = false
            },
        )
    }

    if (showHighlights) {
        HighlightSheet(
            highlights = content.highlights,
            onDismiss = { showHighlights = false },
            onAddHighlight = viewModel::addHighlight,
            onDeleteHighlight = viewModel::deleteHighlight,
            onJumpToHighlight = {
                viewModel.jumpToHighlight(it)
                showHighlights = false
            },
        )
    }

    if (showVocabulary) {
        VocabularySheet(
            words = content.vocabularyWords,
            onDismiss = { showVocabulary = false },
            onAddWord = viewModel::addVocabularyWord,
            onDeleteWord = viewModel::deleteVocabularyWord,
        )
    }

    if (showChapters) {
        ChapterListSheet(
            chapters = content.chapters,
            currentPosition = reading.currentPosition,
            onDismiss = { showChapters = false },
            onJumpToChapter = {
                viewModel.jumpToChapter(it)
                showChapters = false
            },
        )
    }

    if (uiControl.isSearchPanelVisible) {
        SearchSheet(
            query = uiControl.searchQuery,
            isSearching = uiControl.isSearching,
            results = uiControl.searchResults,
            truncated = uiControl.searchTruncated,
            onDismiss = viewModel::hideSearchPanel,
            onQueryChange = viewModel::onSearchQueryChange,
            onJumpToResult = {
                viewModel.jumpToSearchResult(it)
                viewModel.hideSearchPanel()
            },
        )
    }

    if (showAiAssistant) {
        AiAssistantSheet(
            summary = content.aiSummary,
            sourceLabel = content.aiSourceLabel,
            keywords = content.aiKeywords,
            questions = content.aiReviewQuestions,
            isGenerating = uiControl.isGeneratingAi,
            onDismiss = { showAiAssistant = false },
            onGenerate = viewModel::generateLocalAiSummary,
        )
    }

    if (showImmersive) {
        ImmersiveSheet(
            settings = settings,
            ttsState = session.ttsState,
            focusSessionSeconds = session.focusSessionSeconds,
            isFocusTimerRunning = session.isFocusTimerRunning,
            isMistouchLocked = uiControl.isMistouchLocked,
            onDismiss = { showImmersive = false },
            onStartTts = viewModel::startTts,
            onPauseTts = viewModel::pauseTts,
            onResumeTts = viewModel::resumeTts,
            onStopTts = viewModel::stopTts,
            onTtsProviderChange = viewModel::updateTtsProvider,
            onTtsRateChange = viewModel::updateTtsRate,
            onTtsPitchChange = viewModel::updateTtsPitch,
            onBackgroundTtsEnabledChange = viewModel::updateBackgroundTtsEnabled,
            onAutoPageEnabledChange = viewModel::updateAutoPageEnabled,
            onAutoPageIntervalChange = viewModel::updateAutoPageIntervalMs,
            onToggleFocusTimer = viewModel::toggleFocusTimer,
            onResetFocusSession = viewModel::resetFocusSession,
            onImmersiveEnabledChange = viewModel::updateImmersiveEnabled,
            onBrightnessLockedChange = viewModel::updateBrightnessLocked,
            onMistouchGuardEnabledChange = viewModel::updateMistouchGuardEnabled,
            onLockMistouch = viewModel::lockMistouch,
            onUnlockMistouch = viewModel::unlockMistouch,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
