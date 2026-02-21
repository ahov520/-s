package com.readflow.app.ui.reader.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.readflow.app.ui.theme.ZenithAccent

@Composable
fun ProgressSlider(
    progress: Float,
    onProgressChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "${(progress.coerceIn(0f, 1f) * 100).toInt()}%",
            modifier = Modifier.width(36.dp),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = progress.coerceIn(0f, 1f),
            onValueChange = { onProgressChange(it.coerceIn(0f, 1f)) },
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = ZenithAccent,
                activeTrackColor = ZenithAccent,
            )
        )
        Text(
            text = "100%",
            modifier = Modifier.width(36.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
