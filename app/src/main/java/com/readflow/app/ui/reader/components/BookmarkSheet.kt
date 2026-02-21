package com.readflow.app.ui.reader.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.readflow.app.domain.model.Bookmark

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun BookmarkSheet(
    bookmarks: List<Bookmark>,
    onDismiss: () -> Unit,
    onAddBookmark: () -> Unit,
    onDeleteBookmark: (String) -> Unit,
    onJumpToBookmark: (Bookmark) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("书签", style = MaterialTheme.typography.titleLarge)
            Button(onClick = onAddBookmark, modifier = Modifier.fillMaxWidth()) {
                Text("添加当前书签")
            }
            LazyColumn {
                items(bookmarks, key = { it.id }) { bookmark ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = bookmark.label,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        RowActions(
                            onPrimary = { onJumpToBookmark(bookmark) },
                            primaryLabel = "跳转",
                            onSecondary = { onDeleteBookmark(bookmark.id) },
                            secondaryLabel = "删除",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowActions(
    onPrimary: () -> Unit,
    primaryLabel: String,
    onSecondary: () -> Unit,
    secondaryLabel: String,
) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onPrimary) { Text(primaryLabel) }
        Button(onClick = onSecondary) { Text(secondaryLabel) }
    }
}
