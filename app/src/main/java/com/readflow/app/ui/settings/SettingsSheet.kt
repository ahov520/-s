package com.readflow.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.readflow.app.domain.model.PageMode
import com.readflow.app.domain.model.ReadingSettings
import com.readflow.app.domain.model.ThemeMode
import com.readflow.app.ui.theme.ReadingThemes
import com.readflow.app.ui.theme.ZenithAccent

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SettingsSheet(
    settings: ReadingSettings,
    onDismiss: () -> Unit,
    onPageModeChange: (PageMode) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBgColorChange: (String) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Reader Settings", style = MaterialTheme.typography.titleLarge)

            Text("Page Mode", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.pageMode == PageMode.SCROLL,
                    onClick = { onPageModeChange(PageMode.SCROLL) },
                    label = { Text("Scroll") },
                )
                FilterChip(
                    selected = settings.pageMode == PageMode.PAGE,
                    onClick = { onPageModeChange(PageMode.PAGE) },
                    label = { Text("Page") },
                )
            }

            Text("Theme", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadingThemes.forEach { theme ->
                    val selected = settings.bgColorKey == theme.key
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onBgColorChange(theme.key) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .background(
                                    color = if (selected) ZenithAccent else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .padding(2.dp)
                                .background(theme.bgColor, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Aa", color = theme.textColor, style = MaterialTheme.typography.labelMedium)
                        }
                        Text(
                            text = theme.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) ZenithAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Text("Text Size ${settings.fontSize}", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = settings.fontSize.toFloat(),
                onValueChange = { onFontSizeChange(it.toInt()) },
                valueRange = 14f..30f,
            )

            Text("Line Height ${"%.1f".format(settings.lineHeight)}", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = settings.lineHeight,
                onValueChange = onLineHeightChange,
                valueRange = 1.2f..2.0f,
            )

            Text("Theme Mode", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.themeMode == ThemeMode.LIGHT,
                    onClick = { onThemeModeChange(ThemeMode.LIGHT) },
                    label = { Text("Light") },
                )
                FilterChip(
                    selected = settings.themeMode == ThemeMode.DARK,
                    onClick = { onThemeModeChange(ThemeMode.DARK) },
                    label = { Text("Dark") },
                )
                FilterChip(
                    selected = settings.themeMode == ThemeMode.SYSTEM,
                    onClick = { onThemeModeChange(ThemeMode.SYSTEM) },
                    label = { Text("System") },
                )
            }
        }
    }
}
