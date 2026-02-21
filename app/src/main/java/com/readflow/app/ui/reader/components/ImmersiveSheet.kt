package com.readflow.app.ui.reader.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.readflow.app.domain.model.ReadingSettings
import com.readflow.app.domain.model.TtsProvider
import com.readflow.app.ui.reader.TtsPlaybackState
import kotlin.math.roundToInt

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ImmersiveSheet(
    settings: ReadingSettings,
    ttsState: TtsPlaybackState,
    focusSessionSeconds: Int,
    isFocusTimerRunning: Boolean,
    isMistouchLocked: Boolean,
    onDismiss: () -> Unit,
    onStartTts: () -> Unit,
    onPauseTts: () -> Unit,
    onResumeTts: () -> Unit,
    onStopTts: () -> Unit,
    onTtsProviderChange: (TtsProvider) -> Unit,
    onTtsRateChange: (Float) -> Unit,
    onTtsPitchChange: (Float) -> Unit,
    onBackgroundTtsEnabledChange: (Boolean) -> Unit,
    onAutoPageEnabledChange: (Boolean) -> Unit,
    onAutoPageIntervalChange: (Int) -> Unit,
    onToggleFocusTimer: () -> Unit,
    onResetFocusSession: () -> Unit,
    onImmersiveEnabledChange: (Boolean) -> Unit,
    onBrightnessLockedChange: (Boolean) -> Unit,
    onMistouchGuardEnabledChange: (Boolean) -> Unit,
    onLockMistouch: () -> Unit,
    onUnlockMistouch: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("沉浸体验", style = MaterialTheme.typography.titleLarge)

            Text("听书", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStartTts) { Text("开始") }
                if (ttsState == TtsPlaybackState.SPEAKING) {
                    Button(onClick = onPauseTts) { Text("暂停") }
                }
                if (ttsState == TtsPlaybackState.PAUSED) {
                    Button(onClick = onResumeTts) { Text("继续") }
                }
                Button(onClick = onStopTts) { Text("停止") }
            }
            Text("状态：${ttsStateLabel(ttsState)}", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.ttsProvider == TtsProvider.SYSTEM,
                    onClick = { onTtsProviderChange(TtsProvider.SYSTEM) },
                    label = { Text("系统TTS") },
                )
                FilterChip(
                    selected = settings.ttsProvider == TtsProvider.CLOUD,
                    onClick = { onTtsProviderChange(TtsProvider.CLOUD) },
                    label = { Text("云端预留") },
                )
            }
            Text("语速 ${"%.2f".format(settings.ttsRate)}", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = settings.ttsRate,
                onValueChange = onTtsRateChange,
                valueRange = 0.5f..2.0f,
            )
            Text("音调 ${"%.2f".format(settings.ttsPitch)}", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = settings.ttsPitch,
                onValueChange = onTtsPitchChange,
                valueRange = 0.5f..2.0f,
            )
            ToggleRow(
                label = "后台听书",
                checked = settings.backgroundTtsEnabled,
                onCheckedChange = onBackgroundTtsEnabledChange,
            )

            ToggleRow(
                label = "自动翻页",
                checked = settings.autoPageEnabled,
                onCheckedChange = onAutoPageEnabledChange,
            )
            Text("翻页间隔 ${settings.autoPageIntervalMs}ms", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = settings.autoPageIntervalMs.toFloat(),
                onValueChange = { onAutoPageIntervalChange(it.roundToInt()) },
                valueRange = 1000f..10000f,
            )

            Text("专注计时", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "本次 ${formatDuration(focusSessionSeconds)} · 今日 ${formatDuration(settings.dailyReadSeconds)} · 连续 ${settings.streakDays} 天",
                style = MaterialTheme.typography.labelMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onToggleFocusTimer) {
                    Text(if (isFocusTimerRunning) "暂停计时" else "开始计时")
                }
                Button(onClick = onResetFocusSession) { Text("重置本次") }
            }

            ToggleRow(
                label = "全屏沉浸",
                checked = settings.immersiveEnabled,
                onCheckedChange = onImmersiveEnabledChange,
            )
            ToggleRow(
                label = "锁定亮度",
                checked = settings.brightnessLocked,
                onCheckedChange = onBrightnessLockedChange,
            )
            ToggleRow(
                label = "防误触",
                checked = settings.mistouchGuardEnabled,
                onCheckedChange = onMistouchGuardEnabledChange,
            )
            if (settings.mistouchGuardEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onLockMistouch) { Text("锁定触控") }
                    if (isMistouchLocked) {
                        Button(onClick = onUnlockMistouch) { Text("解除锁定") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private fun ttsStateLabel(state: TtsPlaybackState): String = when (state) {
    TtsPlaybackState.IDLE -> "空闲"
    TtsPlaybackState.SPEAKING -> "朗读中"
    TtsPlaybackState.PAUSED -> "已暂停"
    TtsPlaybackState.ERROR -> "错误"
}
