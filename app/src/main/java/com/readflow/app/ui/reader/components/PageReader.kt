package com.readflow.app.ui.reader.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun PageReader(
    pages: List<String>,
    currentPage: Int,
    fontSizeSp: Int,
    lineHeight: Float,
    textColor: androidx.compose.ui.graphics.Color,
    onPageChanged: (Int) -> Unit,
    onToggleMenu: () -> Unit,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = currentPage.coerceAtLeast(0),
        pageCount = { pages.size.coerceAtLeast(1) }
    )

    LaunchedEffect(currentPage, pages.size) {
        if (pages.isNotEmpty() && pagerState.currentPage != currentPage) {
            pagerState.animateScrollToPage(currentPage.coerceIn(0, pages.lastIndex))
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page -> onPageChanged(page) }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val ratio = offset.x / size.width
                    when {
                        ratio < 0.2f -> onPrevPage()
                        ratio > 0.8f -> onNextPage()
                        else -> onToggleMenu()
                    }
                }
            }
    ) { page ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Text(
                text = pages.getOrElse(page) { "" },
                style = TextStyle(
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp * lineHeight).sp,
                    color = textColor,
                ),
            )
        }
    }
}
