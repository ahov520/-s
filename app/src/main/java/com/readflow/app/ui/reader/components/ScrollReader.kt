package com.readflow.app.ui.reader.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
fun ScrollReader(
    paragraphs: List<String>,
    fontSizeSp: Int,
    lineHeight: Float,
    textColor: androidx.compose.ui.graphics.Color,
    currentPosition: Int,
    onToggleMenu: () -> Unit,
    onPositionChanged: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    val starts = remember(paragraphs) {
        val offsets = IntArray(paragraphs.size)
        var sum = 0
        paragraphs.forEachIndexed { index, paragraph ->
            offsets[index] = sum
            sum += paragraph.length + 1
        }
        offsets
    }

    LaunchedEffect(paragraphs, currentPosition) {
        val targetIndex = starts.indexOfLast { it <= currentPosition }.coerceAtLeast(0)
        if (targetIndex in paragraphs.indices && listState.firstVisibleItemIndex != targetIndex) {
            listState.scrollToItem(targetIndex)
        }
    }

    LaunchedEffect(listState, paragraphs) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .map { index -> starts.getOrElse(index) { 0 } }
            .distinctUntilChanged()
            .collect { pos -> onPositionChanged(pos) }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onToggleMenu() })
            }
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        itemsIndexed(
            items = paragraphs,
            key = { index, _ -> index }
        ) { _, paragraph ->
            Text(
                text = paragraph,
                style = TextStyle(
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp * lineHeight).sp,
                    color = textColor,
                ),
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
    }
}
