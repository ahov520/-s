package com.readflow.app.data.local.datastore

import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object SettingsKeys {
    val FONT_SIZE = intPreferencesKey("font_size")
    val LINE_HEIGHT = floatPreferencesKey("line_height")
    val BG_COLOR_KEY = stringPreferencesKey("bg_color_key")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val PAGE_MODE = stringPreferencesKey("page_mode")
}
