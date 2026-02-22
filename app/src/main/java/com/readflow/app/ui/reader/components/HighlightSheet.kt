package com.readflow.app.ui.reader.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.readflow.app.domain.model.Highlight

private val highlightPalettes = listOf(
    "amber" to Color(0xFFFFD166),
    "mint" to Color(0xFF95D5B2),
    "sky" to Color(0xFFA8DADC),
    "rose" to Color(0xFFFFAFCC),
    "lavender" to Color(0xFFCBC0FF),
)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HighlightSheet(
    highlights: List<Highlight>,
    onDismiss: () -> Unit,
    onAddHighlight: (String, String) -> Unit,
    onDeleteHighlight: (String) -> Unit,
    onJumpToHighlight: (Highlight) -> Unit,
) {
    var selectedColor by rememberSaveable { mutableStateOf("amber") }
    var noteDraft by rememberSaveable { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("高亮", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                highlightPalettes.forEach { (key, color) ->
                    val selected = selectedColor == key
                    Row(
                        modifier = Modifier
                            .background(
                                color = if (selected) color.copy(alpha = 0.55f) else color.copy(alpha = 0.25f),
                                shape = CircleShape,
                            )
                            .clickable { selectedColor = key }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = key,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = noteDraft,
                onValueChange = { noteDraft = it },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                placeholder = { Text("高亮备注（可选）") },
            )

            Text(
                text = "保存当前位置高亮",
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFE7DE), RoundedCornerShape(12.dp))
                    .clickable {
                        onAddHighlight(selectedColor, noteDraft)
                        noteDraft = ""
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )

            if (highlights.isEmpty()) {
                Text("还没有高亮内容。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(highlights, key = { it.id }) { item ->
                        val color = highlightPalettes.firstOrNull { it.first == item.colorKey }?.second ?: Color(0xFFFFD166)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(color.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = item.quote,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (item.note.isNotBlank()) {
                                Text(
                                    text = item.note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "跳转",
                                    modifier = Modifier.clickable { onJumpToHighlight(item) },
                                )
                                Text(
                                    text = "删除",
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.clickable { onDeleteHighlight(item.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
