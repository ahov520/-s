package com.readflow.app.domain.usecase

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.readflow.app.domain.model.Book
import com.readflow.app.domain.model.Bookmark
import com.readflow.app.domain.model.ChapterIndex
import com.readflow.app.domain.model.PageMode
import com.readflow.app.domain.model.ReadingNote
import com.readflow.app.domain.model.ThemeMode
import com.readflow.app.domain.model.TtsProvider
import com.readflow.app.domain.repository.BookRepository
import com.readflow.app.domain.repository.BookmarkRepository
import com.readflow.app.domain.repository.ChapterIndexRepository
import com.readflow.app.domain.repository.ReaderSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class BackupAndSyncUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val chapterIndexRepository: ChapterIndexRepository,
    private val settingsRepository: ReaderSettingsRepository,
) {
    enum class RestoreMode {
        MERGE,
        OVERWRITE,
    }

    suspend fun createLocalBackup(): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            val payload = buildBackupPayload()
            val folder = File(context.filesDir, "backups").apply { mkdirs() }
            val file = File(folder, "readflow-backup-${System.currentTimeMillis()}.json")
            file.writeText(payload, Charsets.UTF_8)
            settingsRepository.updateLastBackupPath(file.absolutePath)
            file.absolutePath
        }
    }

    suspend fun restoreFromLocalBackup(
        path: String? = null,
        mode: RestoreMode = RestoreMode.MERGE,
    ): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            val resolved = resolveBackupPath(path)
            val file = File(resolved)
            if (!file.exists()) error("备份文件不存在")
            val payload = file.readText(Charsets.UTF_8)
            restoreBackupPayload(payload, mode)
            settingsRepository.updateLastBackupPath(file.absolutePath)
            file.absolutePath
        }
    }

    suspend fun uploadToCloud(): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            ensureNetworkAvailable()
            val settings = settingsRepository.observeSettings().first()
            val token = settings.cloudSyncToken.trim()
            if (token.isBlank()) error("请先填写 GitHub Token")
            val payload = buildBackupPayload()
            val gistId = if (settings.cloudGistId.isBlank()) {
                val newId = createBackupGist(token, payload)
                settingsRepository.updateCloudSyncConfig(token, newId)
                newId
            } else {
                patchBackupGist(token, settings.cloudGistId.trim(), payload)
                settings.cloudGistId.trim()
            }
            settingsRepository.updateLastSyncAt(System.currentTimeMillis())
            "https://gist.github.com/$gistId"
        }
    }

    suspend fun restoreFromCloud(mode: RestoreMode = RestoreMode.MERGE): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            ensureNetworkAvailable()
            val settings = settingsRepository.observeSettings().first()
            val token = settings.cloudSyncToken.trim()
            val gistId = settings.cloudGistId.trim()
            if (token.isBlank() || gistId.isBlank()) error("请先填写 GitHub Token 和 Gist ID")
            val payload = fetchBackupGistContent(token, gistId)
            restoreBackupPayload(payload, mode)
            settingsRepository.updateLastSyncAt(System.currentTimeMillis())
            gistId
        }
    }

    private suspend fun buildBackupPayload(): String {
        val books = bookRepository.getAllBooks()
        val bookmarks = bookmarkRepository.getAllBookmarks()
        val chapters = chapterIndexRepository.getAllChapters()
        val settings = settingsRepository.observeSettings().first()

        val root = JSONObject()
            .put("schema", 1)
            .put("createdAt", System.currentTimeMillis())
            .put("books", books.toBooksJsonArray())
            .put("bookmarks", bookmarks.toBookmarksJsonArray())
            .put("chapters", chapters.toChaptersJsonArray())
            .put("settings", JSONObject()
                .put("fontSize", settings.fontSize)
                .put("lineHeight", settings.lineHeight)
                .put("bgColorKey", settings.bgColorKey)
                .put("themeMode", settings.themeMode.name)
                .put("pageMode", settings.pageMode.name)
                .put("ttsProvider", settings.ttsProvider.name)
                .put("ttsRate", settings.ttsRate)
                .put("ttsPitch", settings.ttsPitch)
                .put("ttsVoiceId", settings.ttsVoiceId)
                .put("backgroundTtsEnabled", settings.backgroundTtsEnabled)
                .put("autoPageEnabled", settings.autoPageEnabled)
                .put("autoPageIntervalMs", settings.autoPageIntervalMs)
                .put("immersiveEnabled", settings.immersiveEnabled)
                .put("brightnessLocked", settings.brightnessLocked)
                .put("mistouchGuardEnabled", settings.mistouchGuardEnabled)
                .put("dailyReadSeconds", settings.dailyReadSeconds)
                .put("lastReadDate", settings.lastReadDate)
                .put("streakDays", settings.streakDays)
                .put("dailyGoalMinutes", settings.dailyGoalMinutes)
                .put("reminderEnabled", settings.reminderEnabled)
                .put("reminderHour", settings.reminderHour)
                .put("reminderMinute", settings.reminderMinute)
                .put("bookGroups", JSONObject(settings.bookGroups))
                .put("readingNotes", settings.readingNotes.toNotesJsonArray())
                .put("cloudGistId", settings.cloudGistId)
                .put("lastBackupPath", settings.lastBackupPath)
            )

        return root.toString()
    }

    private suspend fun restoreBackupPayload(payload: String, mode: RestoreMode) {
        val root = JSONObject(payload)
        val books = root.optJSONArray("books").toBooks()
        val bookmarks = root.optJSONArray("bookmarks").toBookmarks()
        val chapters = root.optJSONArray("chapters").toChapters()
        if (mode == RestoreMode.OVERWRITE) {
            bookRepository.deleteAllBooks()
            if (books.isNotEmpty()) bookRepository.upsertBooks(books)

            bookmarkRepository.deleteAllBookmarks()
            if (bookmarks.isNotEmpty()) bookmarkRepository.addBookmarks(bookmarks)

            chapterIndexRepository.deleteAllChapters()
            if (chapters.isNotEmpty()) chapterIndexRepository.replaceAllChapters(chapters)
        } else {
            val mergedBooks = mergeBooksById(bookRepository.getAllBooks(), books)
            val mergedBookmarks = mergeBookmarksById(bookmarkRepository.getAllBookmarks(), bookmarks)
            val mergedChapters = mergeChaptersById(chapterIndexRepository.getAllChapters(), chapters)

            if (mergedBooks.isNotEmpty()) bookRepository.upsertBooks(mergedBooks)
            if (mergedBookmarks.isNotEmpty()) bookmarkRepository.addBookmarks(mergedBookmarks)
            if (mergedChapters.isNotEmpty()) chapterIndexRepository.replaceAllChapters(mergedChapters)
        }

        val settingsJson = root.optJSONObject("settings") ?: JSONObject()
        applySettingsFromBackup(settingsJson, mode)
    }

    private suspend fun resolveBackupPath(path: String?): String {
        if (!path.isNullOrBlank()) return path
        val settings = settingsRepository.observeSettings().first()
        if (settings.lastBackupPath.isNotBlank()) return settings.lastBackupPath
        val folder = File(context.filesDir, "backups")
        val latest = folder.listFiles()?.maxByOrNull { it.lastModified() }
        return latest?.absolutePath ?: error("没有可恢复的本地备份")
    }

    private suspend fun applySettingsFromBackup(
        settings: JSONObject,
        mode: RestoreMode,
    ) {
        if (mode == RestoreMode.OVERWRITE) {
            settingsRepository.updateFontSize(settings.optInt("fontSize", 18))
            settingsRepository.updateLineHeight(settings.optDouble("lineHeight", 1.6).toFloat())
            settingsRepository.updateBgColor(settings.optString("bgColorKey", "paper-sepia"))
            settingsRepository.updateThemeMode(settings.optString("themeMode").toThemeMode())
            settingsRepository.updatePageMode(settings.optString("pageMode").toPageMode())
            settingsRepository.updateTtsProvider(settings.optString("ttsProvider").toTtsProvider())
            settingsRepository.updateTtsRate(settings.optDouble("ttsRate", 1.0).toFloat())
            settingsRepository.updateTtsPitch(settings.optDouble("ttsPitch", 1.0).toFloat())
            settingsRepository.updateTtsVoiceId(settings.optString("ttsVoiceId"))
            settingsRepository.updateBackgroundTtsEnabled(settings.optBoolean("backgroundTtsEnabled", true))
            settingsRepository.updateAutoPageEnabled(settings.optBoolean("autoPageEnabled", false))
            settingsRepository.updateAutoPageIntervalMs(settings.optInt("autoPageIntervalMs", 3500))
            settingsRepository.updateImmersiveEnabled(settings.optBoolean("immersiveEnabled", false))
            settingsRepository.updateBrightnessLocked(settings.optBoolean("brightnessLocked", false))
            settingsRepository.updateMistouchGuardEnabled(settings.optBoolean("mistouchGuardEnabled", false))
            settingsRepository.replaceReadStats(
                dailyReadSeconds = settings.optInt("dailyReadSeconds", 0),
                lastReadDate = settings.optString("lastReadDate"),
                streakDays = settings.optInt("streakDays", 0),
            )
            settingsRepository.updateDailyGoalMinutes(settings.optInt("dailyGoalMinutes", 60))
            settingsRepository.updateReminder(
                enabled = settings.optBoolean("reminderEnabled", false),
                hour = settings.optInt("reminderHour", 21),
                minute = settings.optInt("reminderMinute", 0),
            )
            settingsRepository.replaceBookGroups(settings.optJSONObject("bookGroups").toBookGroups())
            settingsRepository.replaceReadingNotes(settings.optJSONArray("readingNotes").toReadingNotes())
        } else {
            val current = settingsRepository.observeSettings().first()
            val mergedGroups = mergeBookGroups(current.bookGroups, settings.optJSONObject("bookGroups").toBookGroups())
            val mergedNotes = mergeReadingNotes(current.readingNotes, settings.optJSONArray("readingNotes").toReadingNotes())
            settingsRepository.replaceBookGroups(mergedGroups)
            settingsRepository.replaceReadingNotes(mergedNotes)
        }

        val currentSettings = settingsRepository.observeSettings().first()
        val currentToken = currentSettings.cloudSyncToken
        val backupGistId = settings.optString("cloudGistId")
        val gistToSave = if (mode == RestoreMode.OVERWRITE || backupGistId.isNotBlank()) {
            backupGistId
        } else {
            currentSettings.cloudGistId
        }
        settingsRepository.updateCloudSyncConfig(
            token = currentToken,
            gistId = gistToSave,
        )
        settingsRepository.updateLastBackupPath(settings.optString("lastBackupPath"))
    }

    private suspend fun createBackupGist(token: String, payload: String): String {
        val body = JSONObject()
            .put("description", "ReadFlow 云备份")
            .put("public", false)
            .put(
                "files",
                JSONObject().put(
                    "readflow-backup.json",
                    JSONObject().put("content", payload)
                )
            )
        val response = requestGitHub(
            method = "POST",
            url = "https://api.github.com/gists",
            token = token,
            body = body.toString(),
        )
        return JSONObject(response).optString("id").ifBlank { error("创建 Gist 失败") }
    }

    private suspend fun patchBackupGist(token: String, gistId: String, payload: String) {
        val body = JSONObject()
            .put(
                "files",
                JSONObject().put(
                    "readflow-backup.json",
                    JSONObject().put("content", payload)
                )
            )
        requestGitHub(
            method = "PATCH",
            url = "https://api.github.com/gists/$gistId",
            token = token,
            body = body.toString(),
        )
    }

    private suspend fun fetchBackupGistContent(token: String, gistId: String): String {
        val response = requestGitHub(
            method = "GET",
            url = "https://api.github.com/gists/$gistId",
            token = token,
            body = null,
        )
        val files = JSONObject(response).optJSONObject("files") ?: error("未找到备份文件")
        val preferred = files.optJSONObject("readflow-backup.json")
            ?: files.keys().asSequence().firstOrNull()?.let { files.optJSONObject(it) }
            ?: error("未找到可用备份内容")
        val content = preferred.optString("content")
        if (content.isBlank()) error("云端备份内容为空")
        return content
    }

    private suspend fun requestGitHub(method: String, url: String, token: String, body: String?): String {
        var delayMs = 700L
        repeat(3) { attempt ->
            val result = runCatching { requestGitHubOnce(method, url, token, body) }
            if (result.isSuccess) return result.getOrThrow()

            val throwable = result.exceptionOrNull() ?: error("未知错误")
            val retryable = throwable is RetryableHttpException || throwable is IOException
            if (!retryable || attempt == 2) throw throwable
            delay(delayMs)
            delayMs *= 2
        }
        error("网络请求失败")
    }

    private fun requestGitHubOnce(method: String, url: String, token: String, body: String?): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "ReadFlow-Android")
            connectTimeout = 15000
            readTimeout = 20000
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

        if (body != null) {
            conn.outputStream.use { stream ->
                stream.write(body.toByteArray(Charsets.UTF_8))
            }
        }

        val code = conn.responseCode
        val payload = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            .orEmpty()
        conn.disconnect()

        if (code !in 200..299) {
            if (code == 429 || code >= 500) {
                throw RetryableHttpException(code, payload)
            }
            throw IOException("GitHub API 失败($code): $payload")
        }
        return payload
    }

    private fun ensureNetworkAvailable() {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: error("网络服务不可用")
        val network = manager.activeNetwork ?: error("当前无可用网络")
        val caps = manager.getNetworkCapabilities(network) ?: error("当前网络不可用")
        val reachable = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        if (!reachable) error("当前网络不可用，请检查后重试")
    }

    private class RetryableHttpException(
        code: Int,
        payload: String,
    ) : IOException("可重试的 GitHub API 错误($code): $payload")
}

private fun List<Book>.toBooksJsonArray(): JSONArray = JSONArray().apply {
    forEach { book ->
        put(
            JSONObject()
                .put("id", book.id)
                .put("title", book.title)
                .put("fileUri", book.fileUri)
                .put("coverColor", book.coverColor)
                .put("coverImageUrl", book.coverImageUrl)
                .put("progress", book.progress)
                .put("currentPosition", book.currentPosition)
                .put("totalChars", book.totalChars)
                .put("encoding", book.encoding)
                .put("fileSizeBytes", book.fileSizeBytes)
                .put("lastReadAt", book.lastReadAt)
                .put("createdAt", book.createdAt)
                .put("updatedAt", book.updatedAt)
        )
    }
}

private fun List<Bookmark>.toBookmarksJsonArray(): JSONArray = JSONArray().apply {
    forEach { bookmark ->
        put(
            JSONObject()
                .put("id", bookmark.id)
                .put("bookId", bookmark.bookId)
                .put("position", bookmark.position)
                .put("label", bookmark.label)
                .put("createdAt", bookmark.createdAt)
        )
    }
}

private fun List<ChapterIndex>.toChaptersJsonArray(): JSONArray = JSONArray().apply {
    forEach { chapter ->
        put(
            JSONObject()
                .put("id", chapter.id)
                .put("bookId", chapter.bookId)
                .put("chapterOrder", chapter.chapterOrder)
                .put("title", chapter.title)
                .put("startChar", chapter.startChar)
                .put("endChar", chapter.endChar)
        )
    }
}

private fun List<ReadingNote>.toNotesJsonArray(): JSONArray = JSONArray().apply {
    forEach { note ->
        put(
            JSONObject()
                .put("id", note.id)
                .put("bookId", note.bookId)
                .put("startChar", note.startChar)
                .put("endChar", note.endChar)
                .put("quote", note.quote)
                .put("note", note.note)
                .put("createdAt", note.createdAt)
        )
    }
}

private fun JSONArray?.toBooks(): List<Book> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            val o = optJSONObject(i) ?: continue
            val id = o.optString("id")
            val fileUri = o.optString("fileUri")
            if (id.isBlank() || fileUri.isBlank()) continue
            add(
                Book(
                    id = id,
                    title = o.optString("title", "未命名书籍"),
                    fileUri = fileUri,
                    coverColor = o.optString("coverColor", "#2D2A26"),
                    coverImageUrl = o.optString("coverImageUrl").ifBlank { null },
                    progress = o.optDouble("progress", 0.0).toFloat().coerceIn(0f, 1f),
                    currentPosition = o.optInt("currentPosition", 0),
                    totalChars = o.optInt("totalChars", 0),
                    encoding = o.optString("encoding", "UTF-8"),
                    fileSizeBytes = o.optLong("fileSizeBytes", 0L),
                    lastReadAt = if (o.has("lastReadAt")) o.optLong("lastReadAt") else null,
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                )
            )
        }
    }
}

private fun JSONArray?.toBookmarks(): List<Bookmark> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            val o = optJSONObject(i) ?: continue
            val id = o.optString("id")
            val bookId = o.optString("bookId")
            if (id.isBlank() || bookId.isBlank()) continue
            add(
                Bookmark(
                    id = id,
                    bookId = bookId,
                    position = o.optInt("position", 0),
                    label = o.optString("label", "书签"),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                )
            )
        }
    }
}

private fun JSONArray?.toChapters(): List<ChapterIndex> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            val o = optJSONObject(i) ?: continue
            val id = o.optString("id")
            val bookId = o.optString("bookId")
            if (id.isBlank() || bookId.isBlank()) continue
            add(
                ChapterIndex(
                    id = id,
                    bookId = bookId,
                    chapterOrder = o.optInt("chapterOrder", 0),
                    title = o.optString("title", "章节"),
                    startChar = o.optInt("startChar", 0),
                    endChar = o.optInt("endChar", 0),
                )
            )
        }
    }
}

private fun JSONArray?.toReadingNotes(): List<ReadingNote> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            val o = optJSONObject(i) ?: continue
            val id = o.optString("id")
            val bookId = o.optString("bookId")
            if (id.isBlank() || bookId.isBlank()) continue
            add(
                ReadingNote(
                    id = id,
                    bookId = bookId,
                    startChar = o.optInt("startChar", 0),
                    endChar = o.optInt("endChar", 0),
                    quote = o.optString("quote"),
                    note = o.optString("note"),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                )
            )
        }
    }
}

private fun JSONObject?.toBookGroups(): Map<String, String> {
    if (this == null) return emptyMap()
    val out = linkedMapOf<String, String>()
    val keys = keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val value = optString(key)
        if (key.isNotBlank() && value.isNotBlank()) out[key] = value
    }
    return out
}

internal fun mergeBooksById(existing: List<Book>, incoming: List<Book>): List<Book> {
    val merged = linkedMapOf<String, Book>()
    existing.forEach { merged[it.id] = it }
    incoming.forEach { merged[it.id] = it }
    return merged.values.toList()
}

internal fun mergeBookmarksById(existing: List<Bookmark>, incoming: List<Bookmark>): List<Bookmark> {
    val merged = linkedMapOf<String, Bookmark>()
    existing.forEach { merged[it.id] = it }
    incoming.forEach { merged[it.id] = it }
    return merged.values.sortedByDescending { it.createdAt }
}

internal fun mergeChaptersById(existing: List<ChapterIndex>, incoming: List<ChapterIndex>): List<ChapterIndex> {
    val merged = linkedMapOf<String, ChapterIndex>()
    existing.forEach { merged[it.id] = it }
    incoming.forEach { merged[it.id] = it }
    return merged.values.sortedWith(compareBy<ChapterIndex> { it.bookId }.thenBy { it.chapterOrder })
}

internal fun mergeReadingNotes(existing: List<ReadingNote>, incoming: List<ReadingNote>): List<ReadingNote> {
    val merged = linkedMapOf<String, ReadingNote>()
    existing.forEach { merged[it.id] = it }
    incoming.forEach { merged[it.id] = it }
    return merged.values.sortedByDescending { it.createdAt }
}

internal fun mergeBookGroups(existing: Map<String, String>, incoming: Map<String, String>): Map<String, String> {
    if (incoming.isEmpty()) return existing
    val merged = existing.toMutableMap()
    incoming.forEach { (bookId, group) ->
        if (bookId.isNotBlank() && group.isNotBlank()) {
            merged[bookId] = group
        }
    }
    return merged
}

private fun String.toThemeMode(): ThemeMode =
    runCatching { ThemeMode.valueOf(this) }.getOrDefault(ThemeMode.SYSTEM)

private fun String.toPageMode(): PageMode =
    runCatching { PageMode.valueOf(this) }.getOrDefault(PageMode.SCROLL)

private fun String.toTtsProvider(): TtsProvider =
    runCatching { TtsProvider.valueOf(this) }.getOrDefault(TtsProvider.SYSTEM)
