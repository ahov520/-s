package com.readflow.app.domain.model

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM,
}

enum class PageMode {
    SCROLL,
    PAGE,
}

enum class TtsProvider {
    SYSTEM,
    CLOUD,
}

data class ReadingSettings(
    val fontSize: Int = 18,
    val lineHeight: Float = 1.6f,
    val bgColorKey: String = "paper-sepia",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val pageMode: PageMode = PageMode.SCROLL,
    val ttsProvider: TtsProvider = TtsProvider.SYSTEM,
    val ttsRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val ttsVoiceId: String = "",
    val autoPageEnabled: Boolean = false,
    val autoPageIntervalMs: Int = 3500,
    val immersiveEnabled: Boolean = false,
    val brightnessLocked: Boolean = false,
    val mistouchGuardEnabled: Boolean = false,
    val dailyReadSeconds: Int = 0,
    val lastReadDate: String = "",
    val streakDays: Int = 0,
)
