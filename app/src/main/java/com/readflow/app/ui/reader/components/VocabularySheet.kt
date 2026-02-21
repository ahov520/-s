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
import androidx.compose.ui.unit.dp
import com.readflow.app.domain.model.VocabularyWord

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun VocabularySheet(
    words: List<VocabularyWord>,
    onDismiss: () -> Unit,
    onAddWord: (String, String) -> Unit,
    onDeleteWord: (String) -> Unit,
) {
    var wordDraft by rememberSaveable { mutableStateOf("") }
    var meaningDraft by rememberSaveable { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("生词本", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = wordDraft,
                onValueChange = { wordDraft = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("输入生词") },
            )
            OutlinedTextField(
                value = meaningDraft,
                onValueChange = { meaningDraft = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("输入释义（可选）") },
            )
            Text(
                text = "加入生词本",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onAddWord(wordDraft, meaningDraft)
                        wordDraft = ""
                        meaningDraft = ""
                    }
                    .background(Color(0xFFFFE7DE), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
            if (words.isEmpty()) {
                Text("还没有生词，阅读时可随时添加。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(words, key = { it.id }) { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF7F8FB), RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(item.word, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "删除",
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.clickable { onDeleteWord(item.id) },
                                )
                            }
                            if (item.meaning.isNotBlank()) {
                                Text(item.meaning, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (item.sentence.isNotBlank()) {
                                Text(
                                    item.sentence,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
