package com.readflow.app.ui.theme

import androidx.compose.ui.graphics.Color

data class ReadingTheme(
    val key: String,
    val label: String,
    val bgColor: Color,
    val textColor: Color,
    val accentColor: Color,
)

val ReadingThemes = listOf(
    ReadingTheme(
        key = "paper-light",
        label = "晨光纸",
        bgColor = Color(0xFFFFF8F0),
        textColor = Color(0xFF2A201C),
        accentColor = ZenithAccent,
    ),
    ReadingTheme(
        key = "paper-sepia",
        label = "琥珀纸",
        bgColor = Color(0xFFF5E7D0),
        textColor = Color(0xFF3B2A1E),
        accentColor = Color(0xFFE37B43),
    ),
    ReadingTheme(
        key = "paper-green",
        label = "青柚护眼",
        bgColor = Color(0xFFEAF2DF),
        textColor = Color(0xFF1D2B1F),
        accentColor = Color(0xFF63A46C),
    ),
    ReadingTheme(
        key = "paper-night",
        label = "极夜",
        bgColor = Color(0xFF121110),
        textColor = Color(0xFFCFC9C4),
        accentColor = DarkPrimary,
    ),
)

fun readingThemeFor(key: String): ReadingTheme =
    ReadingThemes.firstOrNull { it.key == key } ?: ReadingThemes[1]
