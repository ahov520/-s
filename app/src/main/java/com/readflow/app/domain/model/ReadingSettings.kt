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

data class ReadingSettings(
    val fontSize: Int = 18,
    val lineHeight: Float = 1.6f,
    val bgColorKey: String = "paper-sepia",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val pageMode: PageMode = PageMode.SCROLL,
)
