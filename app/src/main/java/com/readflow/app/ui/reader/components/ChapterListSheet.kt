package com.readflow.app.ui.reader.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.readflow.app.domain.model.ChapterIndex

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ChapterListSheet(
    chapters: List<ChapterIndex>,
    currentPosition: Int,
    onDismiss: () -> Unit,
    onJumpToChapter: (ChapterIndex) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("章节目录", style = MaterialTheme.typography.titleLarge)
            LazyColumn {
                items(chapters, key = { it.id }) { chapter ->
                    val selected = currentPosition in chapter.startChar until chapter.endChar
                    Text(
                        text = chapter.title,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .pointerInput(Unit) {
                                detectTapGestures { onJumpToChapter(chapter) }
                            }
                    )
                }
            }
        }
    }
}
