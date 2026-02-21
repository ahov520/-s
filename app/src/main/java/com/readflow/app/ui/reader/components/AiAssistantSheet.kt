package com.readflow.app.ui.reader.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AiAssistantSheet(
    summary: String,
    questions: List<String>,
    isGenerating: Boolean,
    onDismiss: () -> Unit,
    onGenerate: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("AI 阅读助手（本地模式）", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "生成当前片段总结",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isGenerating, onClick = onGenerate)
                    .background(
                        if (isGenerating) Color(0xFFE7EAF0) else Color(0xFFFFE7DE),
                        RoundedCornerShape(12.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                color = if (isGenerating) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            if (isGenerating) {
                CircularProgressIndicator()
            }
            Text(
                text = if (summary.isBlank()) "点击上方按钮生成总结。"
                else summary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (questions.isNotEmpty()) {
                Text("复盘问题", style = MaterialTheme.typography.titleMedium)
                questions.forEachIndexed { index, question ->
                    Text("${index + 1}. $question", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
