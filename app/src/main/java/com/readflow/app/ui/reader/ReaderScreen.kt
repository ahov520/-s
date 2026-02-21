package com.readflow.app.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readflow.app.domain.model.PageMode
import com.readflow.app.ui.reader.components.BookmarkSheet
import com.readflow.app.ui.reader.components.ChapterListSheet
import com.readflow.app.ui.reader.components.PageReader
import com.readflow.app.ui.reader.components.ReaderToolbar
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

    LaunchedEffect(bookId) {
        viewModel.loadBook(bookId)
    }

    val readingTheme = readingThemeFor(state.settings.bgColorKey)
    val progress = if (state.content.isEmpty()) 0f else state.currentPosition.toFloat() / state.content.length

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
            onShowSettings = { showSettings = true },
            onProgressChange = { ratio ->
                val pos = (state.content.length * ratio).toInt()
                viewModel.jumpToPosition(pos)
            },
            onPrevPage = viewModel::previousPage,
            onNextPage = viewModel::nextPage,
        )
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
}
