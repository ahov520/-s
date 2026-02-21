package com.readflow.app.ui.reader.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.readflow.app.ui.reader.SearchResultUi

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SearchSheet(
    query: String,
    isSearching: Boolean,
    results: List<SearchResultUi>,
    truncated: Boolean,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onJumpToResult: (SearchResultUi) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("全文搜索", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("输入关键词") },
            )

            if (query.isBlank()) {
                Text(
                    text = "请输入关键词开始搜索",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (isSearching) {
                CircularProgressIndicator()
            } else if (results.isEmpty()) {
                Text(
                    text = "没有找到匹配内容",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                if (truncated) {
                    Text(
                        text = "结果较多，仅展示前 200 条",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(results, key = { "${it.position}-${it.snippet}" }) { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onJumpToResult(item) }
                                .padding(vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            item.chapterTitle?.let { chapter ->
                                Text(
                                    text = chapter,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                text = item.snippet,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "位置 ${item.position}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
