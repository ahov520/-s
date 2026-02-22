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
    val backgroundTtsEnabled: Boolean = true,
    val autoPageEnabled: Boolean = false,
    val autoPageIntervalMs: Int = 3500,
    val immersiveEnabled: Boolean = false,
    val brightnessLocked: Boolean = false,
    val mistouchGuardEnabled: Boolean = false,
    val dailyReadSeconds: Int = 0,
    val lastReadDate: String = "",
    val streakDays: Int = 0,
    val dailyGoalMinutes: Int = 60,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 21,
    val reminderMinute: Int = 0,
    val bookGroups: Map<String, String> = emptyMap(),
    val readingNotes: List<ReadingNote> = emptyList(),
    val highlights: List<Highlight> = emptyList(),
    val vocabularyWords: List<VocabularyWord> = emptyList(),
    val readHistory: Map<String, Int> = emptyMap(),
    val syncConflicts: List<SyncConflict> = emptyList(),
    val bookSubscriptions: List<BookSubscription> = emptyList(),
    val offlineCachedBookIds: Set<String> = emptySet(),
    val cloudProvider: CloudSyncProvider = CloudSyncProvider.GITHUB_GIST,
    val cloudWebDavEndpoint: String = "",
    val cloudWebDavUsername: String = "",
    val cloudRemotePath: String = "readflow-backup.json",
    val cloudSyncToken: String = "",
    val cloudGistId: String = "",
    val lastBackupPath: String = "",
    val lastSyncAt: Long = 0L,
)
