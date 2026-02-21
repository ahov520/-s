package com.readflow.app.ui.reader.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.readflow.app.domain.model.ReadingNote

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingNotesSheet(
    notes: List<ReadingNote>,
    onDismiss: () -> Unit,
    onAddNote: (String) -> Unit,
    onDeleteNote: (String) -> Unit,
    onJumpToNote: (ReadingNote) -> Unit,
) {
    var noteInput by rememberSaveable { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("划线与笔记", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = noteInput,
                onValueChange = { noteInput = it },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                placeholder = { Text("输入当前片段笔记内容") },
            )
            Button(
                onClick = {
                    onAddNote(noteInput)
                    noteInput = ""
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("保存当前位置笔记")
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(notes, key = { it.id }) { note ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = note.quote,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(note.note, style = MaterialTheme.typography.bodyMedium)
                        NoteRowActions(
                            onPrimary = { onJumpToNote(note) },
                            primaryLabel = "跳转",
                            onSecondary = { onDeleteNote(note.id) },
                            secondaryLabel = "删除",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteRowActions(
    onPrimary: () -> Unit,
    primaryLabel: String,
    onSecondary: () -> Unit,
    secondaryLabel: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onPrimary) { Text(primaryLabel) }
        Button(onClick = onSecondary) { Text(secondaryLabel) }
    }
}
