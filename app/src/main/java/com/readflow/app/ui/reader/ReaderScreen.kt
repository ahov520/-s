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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readflow.app.domain.model.PageMode
import com.readflow.app.ui.reader.components.BookmarkSheet
import com.readflow.app.ui.reader.components.ChapterListSheet
import com.readflow.app.ui.reader.components.ImmersiveSheet
import com.readflow.app.ui.reader.components.PageReader
import com.readflow.app.ui.reader.components.ReaderToolbar
import com.readflow.app.ui.reader.components.SearchSheet
import com.readflow.app.ui.reader.components.ScrollReader
import com.readflow.app.ui.settings.SettingsSheet
import com.readflow.app.ui.theme.readingThemeFor

@Composable
fun ReaderScreen(
    bookId: String,
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showChapters by remember { mutableStateOf(false) }
    var showImmersive by remember { mutableStateOf(false) }
    val activity = LocalContext.current.findActivity()

    LaunchedEffect(bookId) {
        viewModel.loadBook(bookId)
    }

    val readingTheme = readingThemeFor(state.settings.bgColorKey)
    val progress = if (state.content.isEmpty()) 0f else state.currentPosition.toFloat() / state.content.length

    DisposableEffect(state.settings.immersiveEnabled, state.settings.brightnessLocked, activity) {
        if (activity != null) {
            val window = activity.window
            val decor = window.decorView
            val controller = WindowInsetsControllerCompat(window, decor)
            val previousBrightness = window.attributes.screenBrightness

            if (state.settings.immersiveEnabled) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }

            val attrs = window.attributes
            attrs.screenBrightness = if (state.settings.brightnessLocked) {
                if (attrs.screenBrightness > 0f) attrs.screenBrightness else 0.55f
            } else {
                -1f
            }
            window.attributes = attrs

            onDispose {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                controller.show(WindowInsetsCompat.Type.systemBars())
                val resetAttrs = window.attributes
                resetAttrs.screenBrightness = previousBrightness
                window.attributes = resetAttrs
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

        LaunchedEffect(widthPx, heightPx, state.settings.fontSize, state.settings.lineHeight) {
            viewModel.updateLayout(widthPx = widthPx, heightPx = heightPx)
        }

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.error != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = state.error.orEmpty())
            }
        } else {
            when (state.settings.pageMode) {
                PageMode.SCROLL -> ScrollReader(
                    paragraphs = state.paragraphs,
                    fontSizeSp = state.settings.fontSize,
                    lineHeight = state.settings.lineHeight,
                    textColor = readingTheme.textColor,
                    currentPosition = state.currentPosition,
                    onToggleMenu = viewModel::toggleMenu,
                    onPositionChanged = viewModel::onScrollPositionChanged,
                )

                PageMode.PAGE -> PageReader(
                    pages = state.pages,
                    currentPage = state.currentPage,
                    fontSizeSp = state.settings.fontSize,
                    lineHeight = state.settings.lineHeight,
                    textColor = readingTheme.textColor,
                    onPageChanged = viewModel::onPageChanged,
                    onToggleMenu = viewModel::toggleMenu,
                    onPrevPage = viewModel::previousPage,
                    onNextPage = viewModel::nextPage,
                )
            }
        }

        AnimatedVisibility(
            visible = !state.isMenuVisible,
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
            visible = state.isMenuVisible,
            title = state.book?.title.orEmpty(),
            progress = progress,
            showPageActions = state.settings.pageMode == PageMode.PAGE,
            onBack = onBack,
            onAddBookmark = viewModel::addBookmark,
            onShowBookmarks = { showBookmarks = true },
            onShowChapters = { showChapters = true },
            onShowSearch = viewModel::showSearchPanel,
            onShowSettings = { showSettings = true },
            onShowImmersive = { showImmersive = true },
            onProgressChange = { ratio ->
                val pos = (state.content.length * ratio).toInt()
                viewModel.jumpToPosition(pos)
            },
            onPrevPage = viewModel::previousPage,
            onNextPage = viewModel::nextPage,
        )

        if (state.settings.mistouchGuardEnabled && state.isMistouchLocked) {
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
            settings = state.settings,
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
            bookmarks = state.bookmarks,
            onDismiss = { showBookmarks = false },
            onAddBookmark = viewModel::addBookmark,
            onDeleteBookmark = viewModel::deleteBookmark,
            onJumpToBookmark = {
                viewModel.jumpToPosition(it.position)
                showBookmarks = false
            },
        )
    }

    if (showChapters) {
        ChapterListSheet(
            chapters = state.chapters,
            currentPosition = state.currentPosition,
            onDismiss = { showChapters = false },
            onJumpToChapter = {
                viewModel.jumpToChapter(it)
                showChapters = false
            },
        )
    }

    if (state.isSearchPanelVisible) {
        SearchSheet(
            query = state.searchQuery,
            isSearching = state.isSearching,
            results = state.searchResults,
            truncated = state.searchTruncated,
            onDismiss = viewModel::hideSearchPanel,
            onQueryChange = viewModel::onSearchQueryChange,
            onJumpToResult = {
                viewModel.jumpToSearchResult(it)
                viewModel.hideSearchPanel()
            },
        )
    }

    if (showImmersive) {
        ImmersiveSheet(
            settings = state.settings,
            ttsState = state.ttsState,
            focusSessionSeconds = state.focusSessionSeconds,
            isFocusTimerRunning = state.isFocusTimerRunning,
            isMistouchLocked = state.isMistouchLocked,
            onDismiss = { showImmersive = false },
            onStartTts = viewModel::startTts,
            onPauseTts = viewModel::pauseTts,
            onResumeTts = viewModel::resumeTts,
            onStopTts = viewModel::stopTts,
            onTtsProviderChange = viewModel::updateTtsProvider,
            onTtsRateChange = viewModel::updateTtsRate,
            onTtsPitchChange = viewModel::updateTtsPitch,
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
