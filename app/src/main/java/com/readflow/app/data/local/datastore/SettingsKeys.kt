package com.readflow.app.data.local.datastore

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object SettingsKeys {
    val FONT_SIZE = intPreferencesKey("font_size")
    val LINE_HEIGHT = floatPreferencesKey("line_height")
    val BG_COLOR_KEY = stringPreferencesKey("bg_color_key")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val PAGE_MODE = stringPreferencesKey("page_mode")
    val TTS_PROVIDER = stringPreferencesKey("tts_provider")
    val TTS_RATE = floatPreferencesKey("tts_rate")
    val TTS_PITCH = floatPreferencesKey("tts_pitch")
    val TTS_VOICE_ID = stringPreferencesKey("tts_voice_id")
    val AUTO_PAGE_ENABLED = booleanPreferencesKey("auto_page_enabled")
    val AUTO_PAGE_INTERVAL_MS = intPreferencesKey("auto_page_interval_ms")
    val IMMERSIVE_ENABLED = booleanPreferencesKey("immersive_enabled")
    val BRIGHTNESS_LOCKED = booleanPreferencesKey("brightness_locked")
    val MISTOUCH_GUARD_ENABLED = booleanPreferencesKey("mistouch_guard_enabled")
    val DAILY_READ_SECONDS = intPreferencesKey("daily_read_seconds")
    val LAST_READ_DATE = stringPreferencesKey("last_read_date")
    val STREAK_DAYS = intPreferencesKey("streak_days")
}
