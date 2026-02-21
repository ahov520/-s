package com.readflow.app.domain.usecase

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.readflow.app.domain.model.Book
import com.readflow.app.domain.model.BookSubscription
import com.readflow.app.domain.model.Bookmark
import com.readflow.app.domain.model.ChapterIndex
import com.readflow.app.domain.model.CloudSyncProvider
import com.readflow.app.domain.model.PageMode
import com.readflow.app.domain.model.ReadingNote
import com.readflow.app.domain.model.SyncConflict
import com.readflow.app.domain.model.ThemeMode
import com.readflow.app.domain.model.TtsProvider
import com.readflow.app.domain.model.VocabularyWord
import com.readflow.app.domain.repository.BookRepository
import com.readflow.app.domain.repository.BookmarkRepository
import com.readflow.app.domain.repository.ChapterIndexRepository
import com.readflow.app.domain.repository.ReaderSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.util.UUID
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
            if (token.isBlank()) error("请先填写云同步 Token")
            val payload = buildBackupPayload()
            val cloudRef = when (settings.cloudProvider) {
                CloudSyncProvider.GITHUB_GIST -> {
                    val gistId = if (settings.cloudGistId.isBlank()) {
                        val newId = createBackupGist(token, payload)
                        settingsRepository.updateCloudSyncConfig(token, newId)
                        newId
                    } else {
                        patchBackupGist(token, settings.cloudGistId.trim(), payload)
                        settings.cloudGistId.trim()
                    }
                    "https://gist.github.com/$gistId"
                }

                CloudSyncProvider.WEBDAV -> {
                    uploadToWebDav(settings, token, payload)
                }

                CloudSyncProvider.ONEDRIVE -> {
                    uploadToOneDrive(settings, token, payload)
                }

                CloudSyncProvider.DROPBOX -> {
                    uploadToDropbox(settings, token, payload)
                }
            }
            settingsRepository.updateLastSyncAt(System.currentTimeMillis())
            cloudRef
        }
    }

    suspend fun restoreFromCloud(mode: RestoreMode = RestoreMode.MERGE): Result<String> = runCatching {
        withContext(Dispatchers.IO) {
            ensureNetworkAvailable()
            val settings = settingsRepository.observeSettings().first()
            val token = settings.cloudSyncToken.trim()
            if (token.isBlank()) error("请先填写云同步 Token")
            val payload = when (settings.cloudProvider) {
                CloudSyncProvider.GITHUB_GIST -> {
                    val gistId = settings.cloudGistId.trim()
                    if (gistId.isBlank()) error("请先填写 Gist ID")
                    fetchBackupGistContent(token, gistId)
                }

                CloudSyncProvider.WEBDAV -> fetchFromWebDav(settings, token)
                CloudSyncProvider.ONEDRIVE -> fetchFromOneDrive(settings, token)
                CloudSyncProvider.DROPBOX -> fetchFromDropbox(settings, token)
            }
            restoreBackupPayload(payload, mode)
            settingsRepository.updateLastSyncAt(System.currentTimeMillis())
            settings.cloudProvider.name
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
                .put("vocabularyWords", settings.vocabularyWords.toVocabularyJsonArray())
                .put("syncConflicts", settings.syncConflicts.toSyncConflictsJsonArray())
                .put("bookSubscriptions", settings.bookSubscriptions.toSubscriptionsJsonArray())
                .put("offlineCachedBookIds", JSONArray(settings.offlineCachedBookIds.sorted()))
                .put("cloudProvider", settings.cloudProvider.name)
                .put("cloudWebDavEndpoint", settings.cloudWebDavEndpoint)
                .put("cloudWebDavUsername", settings.cloudWebDavUsername)
                .put("cloudRemotePath", settings.cloudRemotePath)
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
            settingsRepository.replaceSyncConflicts(emptyList())
        } else {
            val (mergedBooks, conflicts) = mergeBooksWithConflicts(bookRepository.getAllBooks(), books)
            val mergedBookmarks = mergeBookmarksById(bookmarkRepository.getAllBookmarks(), bookmarks)
            val mergedChapters = mergeChaptersById(chapterIndexRepository.getAllChapters(), chapters)

            if (mergedBooks.isNotEmpty()) bookRepository.upsertBooks(mergedBooks)
            if (mergedBookmarks.isNotEmpty()) bookmarkRepository.addBookmarks(mergedBookmarks)
            if (mergedChapters.isNotEmpty()) chapterIndexRepository.replaceAllChapters(mergedChapters)
            settingsRepository.replaceSyncConflicts(conflicts)
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
            settingsRepository.replaceVocabularyWords(settings.optJSONArray("vocabularyWords").toVocabularyWords())
            settingsRepository.replaceBookSubscriptions(settings.optJSONArray("bookSubscriptions").toSubscriptions())
            settingsRepository.replaceOfflineCachedBookIds(settings.optJSONArray("offlineCachedBookIds").toOfflineBookIds())
            settingsRepository.replaceSyncConflicts(settings.optJSONArray("syncConflicts").toSyncConflicts())
            settingsRepository.updateCloudProvider(settings.optString("cloudProvider").toCloudSyncProvider())
            settingsRepository.updateCloudWebDavConfig(
                endpoint = settings.optString("cloudWebDavEndpoint"),
                username = settings.optString("cloudWebDavUsername"),
                remotePath = settings.optString("cloudRemotePath", "readflow-backup.json"),
            )
        } else {
            val current = settingsRepository.observeSettings().first()
            val mergedGroups = mergeBookGroups(current.bookGroups, settings.optJSONObject("bookGroups").toBookGroups())
            val mergedNotes = mergeReadingNotes(current.readingNotes, settings.optJSONArray("readingNotes").toReadingNotes())
            val mergedWords = mergeVocabularyWords(current.vocabularyWords, settings.optJSONArray("vocabularyWords").toVocabularyWords())
            val mergedSubscriptions = mergeSubscriptions(
                current.bookSubscriptions,
                settings.optJSONArray("bookSubscriptions").toSubscriptions(),
            )
            val mergedOfflineIds = current.offlineCachedBookIds + settings.optJSONArray("offlineCachedBookIds").toOfflineBookIds()
            settingsRepository.replaceBookGroups(mergedGroups)
            settingsRepository.replaceReadingNotes(mergedNotes)
            settingsRepository.replaceVocabularyWords(mergedWords)
            settingsRepository.replaceBookSubscriptions(mergedSubscriptions)
            settingsRepository.replaceOfflineCachedBookIds(mergedOfflineIds)
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

    private suspend fun uploadToWebDav(settings: com.readflow.app.domain.model.ReadingSettings, token: String, payload: String): String {
        val endpoint = settings.cloudWebDavEndpoint.trim().trimEnd('/')
        if (endpoint.isBlank()) error("请填写 WebDAV 地址")
        val remotePath = settings.cloudRemotePath.trim().ifBlank { "readflow-backup.json" }
        val url = if (remotePath.startsWith("/")) "$endpoint$remotePath" else "$endpoint/$remotePath"
        requestWebDav(
            method = "PUT",
            url = url,
            username = settings.cloudWebDavUsername.trim(),
            token = token,
            body = payload,
        )
        return url
    }

    private suspend fun fetchFromWebDav(settings: com.readflow.app.domain.model.ReadingSettings, token: String): String {
        val endpoint = settings.cloudWebDavEndpoint.trim().trimEnd('/')
        if (endpoint.isBlank()) error("请填写 WebDAV 地址")
        val remotePath = settings.cloudRemotePath.trim().ifBlank { "readflow-backup.json" }
        val url = if (remotePath.startsWith("/")) "$endpoint$remotePath" else "$endpoint/$remotePath"
        return requestWebDav(
            method = "GET",
            url = url,
            username = settings.cloudWebDavUsername.trim(),
            token = token,
            body = null,
        )
    }

    private suspend fun uploadToOneDrive(settings: com.readflow.app.domain.model.ReadingSettings, token: String, payload: String): String {
        val remotePath = settings.cloudRemotePath.trim().ifBlank { "readflow-backup.json" }
        val encodedPath = remotePath.trimStart('/').replace(" ", "%20")
        val url = "https://graph.microsoft.com/v1.0/me/drive/special/approot:/$encodedPath:/content"
        requestBearerJson(
            method = "PUT",
            url = url,
            token = token,
            body = payload,
        )
        return "onedrive://$remotePath"
    }

    private suspend fun fetchFromOneDrive(settings: com.readflow.app.domain.model.ReadingSettings, token: String): String {
        val remotePath = settings.cloudRemotePath.trim().ifBlank { "readflow-backup.json" }
        val encodedPath = remotePath.trimStart('/').replace(" ", "%20")
        val url = "https://graph.microsoft.com/v1.0/me/drive/special/approot:/$encodedPath:/content"
        return requestBearerJson(
            method = "GET",
            url = url,
            token = token,
            body = null,
        )
    }

    private suspend fun uploadToDropbox(settings: com.readflow.app.domain.model.ReadingSettings, token: String, payload: String): String {
        val remotePath = settings.cloudRemotePath.trim().ifBlank { "/readflow-backup.json" }.let {
            if (it.startsWith("/")) it else "/$it"
        }
        requestDropboxUpload(token = token, path = remotePath, payload = payload)
        return "dropbox://$remotePath"
    }

    private suspend fun fetchFromDropbox(settings: com.readflow.app.domain.model.ReadingSettings, token: String): String {
        val remotePath = settings.cloudRemotePath.trim().ifBlank { "/readflow-backup.json" }.let {
            if (it.startsWith("/")) it else "/$it"
        }
        return requestDropboxDownload(token = token, path = remotePath)
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

    private suspend fun requestBearerJson(method: String, url: String, token: String, body: String?): String {
        var delayMs = 700L
        repeat(3) { attempt ->
            val result = runCatching { requestBearerJsonOnce(method, url, token, body) }
            if (result.isSuccess) return result.getOrThrow()
            val throwable = result.exceptionOrNull() ?: error("未知错误")
            val retryable = throwable is RetryableHttpException || throwable is IOException
            if (!retryable || attempt == 2) throw throwable
            delay(delayMs)
            delayMs *= 2
        }
        error("云端请求失败")
    }

    private fun requestBearerJsonOnce(method: String, url: String, token: String, body: String?): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json, text/plain, */*")
            connectTimeout = 15000
            readTimeout = 20000
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

        if (body != null) {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val code = conn.responseCode
        val payload = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            .orEmpty()
        conn.disconnect()
        if (code !in 200..299) {
            if (code == 429 || code >= 500) throw RetryableHttpException(code, payload)
            throw IOException("云端 API 失败($code): $payload")
        }
        return payload
    }

    private suspend fun requestWebDav(
        method: String,
        url: String,
        username: String,
        token: String,
        body: String?,
    ): String {
        var delayMs = 700L
        repeat(3) { attempt ->
            val result = runCatching {
                requestWebDavOnce(method, url, username, token, body)
            }
            if (result.isSuccess) return result.getOrThrow()
            val throwable = result.exceptionOrNull() ?: error("未知错误")
            val retryable = throwable is RetryableHttpException || throwable is IOException
            if (!retryable || attempt == 2) throw throwable
            delay(delayMs)
            delayMs *= 2
        }
        error("WebDAV 请求失败")
    }

    private fun requestWebDavOnce(
        method: String,
        url: String,
        username: String,
        token: String,
        body: String?,
    ): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            if (username.isNotBlank()) {
                val credentials = "$username:$token"
                val auth = Base64.getEncoder().encodeToString(credentials.toByteArray(Charsets.UTF_8))
                setRequestProperty("Authorization", "Basic $auth")
            } else {
                setRequestProperty("Authorization", "Bearer $token")
            }
            setRequestProperty("Accept", "application/json, text/plain, */*")
            connectTimeout = 15000
            readTimeout = 20000
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

        if (body != null) {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val code = conn.responseCode
        val payload = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            .orEmpty()
        conn.disconnect()
        if (code !in 200..299) {
            if (code == 429 || code >= 500) throw RetryableHttpException(code, payload)
            throw IOException("WebDAV 失败($code): $payload")
        }
        return payload
    }

    private suspend fun requestDropboxUpload(token: String, path: String, payload: String) {
        var delayMs = 700L
        repeat(3) { attempt ->
            val result = runCatching { requestDropboxUploadOnce(token, path, payload) }
            if (result.isSuccess) return
            val throwable = result.exceptionOrNull() ?: error("未知错误")
            val retryable = throwable is RetryableHttpException || throwable is IOException
            if (!retryable || attempt == 2) throw throwable
            delay(delayMs)
            delayMs *= 2
        }
    }

    private fun requestDropboxUploadOnce(token: String, path: String, payload: String) {
        val conn = (URL("https://content.dropboxapi.com/2/files/upload").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Dropbox-API-Arg", JSONObject()
                .put("path", path)
                .put("mode", "overwrite")
                .put("autorename", false)
                .put("mute", true)
                .toString()
            )
            setRequestProperty("Content-Type", "application/octet-stream")
            connectTimeout = 15000
            readTimeout = 20000
            doOutput = true
        }
        conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val response = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            .orEmpty()
        conn.disconnect()
        if (code !in 200..299) {
            if (code == 429 || code >= 500) throw RetryableHttpException(code, response)
            throw IOException("Dropbox 上传失败($code): $response")
        }
    }

    private suspend fun requestDropboxDownload(token: String, path: String): String {
        var delayMs = 700L
        repeat(3) { attempt ->
            val result = runCatching { requestDropboxDownloadOnce(token, path) }
            if (result.isSuccess) return result.getOrThrow()
            val throwable = result.exceptionOrNull() ?: error("未知错误")
            val retryable = throwable is RetryableHttpException || throwable is IOException
            if (!retryable || attempt == 2) throw throwable
            delay(delayMs)
            delayMs *= 2
        }
        error("Dropbox 下载失败")
    }

    private fun requestDropboxDownloadOnce(token: String, path: String): String {
        val conn = (URL("https://content.dropboxapi.com/2/files/download").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Dropbox-API-Arg", JSONObject().put("path", path).toString())
            connectTimeout = 15000
            readTimeout = 20000
        }
        val code = conn.responseCode
        val response = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            .orEmpty()
        conn.disconnect()
        if (code !in 200..299) {
            if (code == 429 || code >= 500) throw RetryableHttpException(code, response)
            throw IOException("Dropbox 下载失败($code): $response")
        }
        return response
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

private fun List<VocabularyWord>.toVocabularyJsonArray(): JSONArray = JSONArray().apply {
    forEach { word ->
        put(
            JSONObject()
                .put("id", word.id)
                .put("bookId", word.bookId)
                .put("word", word.word)
                .put("meaning", word.meaning)
                .put("sentence", word.sentence)
                .put("createdAt", word.createdAt)
        )
    }
}

private fun List<SyncConflict>.toSyncConflictsJsonArray(): JSONArray = JSONArray().apply {
    forEach { conflict ->
        put(
            JSONObject()
                .put("id", conflict.id)
                .put("bookId", conflict.bookId)
                .put("bookTitle", conflict.bookTitle)
                .put("localPosition", conflict.localPosition)
                .put("localProgress", conflict.localProgress.toDouble())
                .put("remotePosition", conflict.remotePosition)
                .put("remoteProgress", conflict.remoteProgress.toDouble())
                .put("createdAt", conflict.createdAt)
        )
    }
}

private fun List<BookSubscription>.toSubscriptionsJsonArray(): JSONArray = JSONArray().apply {
    forEach { subscription ->
        put(
            JSONObject()
                .put("bookId", subscription.bookId)
                .put("sourceUrl", subscription.sourceUrl)
                .put("etag", subscription.etag)
                .put("lastModified", subscription.lastModified)
                .put("hasUpdate", subscription.hasUpdate)
                .put("lastCheckedAt", subscription.lastCheckedAt)
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

private fun JSONArray?.toVocabularyWords(): List<VocabularyWord> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            val o = optJSONObject(i) ?: continue
            val id = o.optString("id")
            val word = o.optString("word")
            if (id.isBlank() || word.isBlank()) continue
            add(
                VocabularyWord(
                    id = id,
                    bookId = o.optString("bookId"),
                    word = word,
                    meaning = o.optString("meaning"),
                    sentence = o.optString("sentence"),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                )
            )
        }
    }
}

private fun JSONArray?.toSyncConflicts(): List<SyncConflict> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            val o = optJSONObject(i) ?: continue
            val bookId = o.optString("bookId")
            if (bookId.isBlank()) continue
            add(
                SyncConflict(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    bookId = bookId,
                    bookTitle = o.optString("bookTitle", "未知书籍"),
                    localPosition = o.optInt("localPosition", 0),
                    localProgress = o.optDouble("localProgress", 0.0).toFloat(),
                    remotePosition = o.optInt("remotePosition", 0),
                    remoteProgress = o.optDouble("remoteProgress", 0.0).toFloat(),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                )
            )
        }
    }
}

private fun JSONArray?.toSubscriptions(): List<BookSubscription> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            val o = optJSONObject(i) ?: continue
            val bookId = o.optString("bookId")
            val sourceUrl = o.optString("sourceUrl")
            if (bookId.isBlank() || sourceUrl.isBlank()) continue
            add(
                BookSubscription(
                    bookId = bookId,
                    sourceUrl = sourceUrl,
                    etag = o.optString("etag"),
                    lastModified = o.optString("lastModified"),
                    hasUpdate = o.optBoolean("hasUpdate", false),
                    lastCheckedAt = o.optLong("lastCheckedAt", 0L),
                )
            )
        }
    }
}

private fun JSONArray?.toOfflineBookIds(): Set<String> {
    if (this == null) return emptySet()
    return buildSet {
        for (i in 0 until length()) {
            val id = optString(i)
            if (id.isNotBlank()) add(id)
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

internal fun mergeBooksWithConflicts(
    existing: List<Book>,
    incoming: List<Book>,
): Pair<List<Book>, List<SyncConflict>> {
    if (incoming.isEmpty()) return existing to emptyList()
    val existingById = existing.associateBy { it.id }
    val merged = linkedMapOf<String, Book>()
    val conflicts = mutableListOf<SyncConflict>()

    existing.forEach { merged[it.id] = it }

    incoming.forEach { remote ->
        val local = existingById[remote.id]
        if (local == null) {
            merged[remote.id] = remote
            return@forEach
        }

        val hasProgressConflict =
            kotlin.math.abs(local.progress - remote.progress) >= 0.02f &&
                kotlin.math.abs(local.currentPosition - remote.currentPosition) >= 120

        if (hasProgressConflict) {
            // 合并模式默认保留本地进度，冲突进入待处理中心。
            merged[local.id] = local.copy(
                updatedAt = maxOf(local.updatedAt, remote.updatedAt),
                lastReadAt = maxOf(local.lastReadAt ?: 0L, remote.lastReadAt ?: 0L).takeIf { it > 0L },
            )
            conflicts += SyncConflict(
                id = "${local.id}-${remote.updatedAt}-${System.currentTimeMillis()}",
                bookId = local.id,
                bookTitle = if (local.title.isNotBlank()) local.title else remote.title,
                localPosition = local.currentPosition,
                localProgress = local.progress,
                remotePosition = remote.currentPosition,
                remoteProgress = remote.progress,
                createdAt = System.currentTimeMillis(),
            )
        } else {
            merged[local.id] = if (remote.updatedAt >= local.updatedAt) remote else local
        }
    }

    return merged.values.toList() to conflicts
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

internal fun mergeVocabularyWords(
    existing: List<VocabularyWord>,
    incoming: List<VocabularyWord>,
): List<VocabularyWord> {
    val merged = linkedMapOf<String, VocabularyWord>()
    existing.forEach { merged[it.id] = it }
    incoming.forEach { merged[it.id] = it }
    return merged.values.sortedByDescending { it.createdAt }
}

internal fun mergeSubscriptions(
    existing: List<BookSubscription>,
    incoming: List<BookSubscription>,
): List<BookSubscription> {
    val merged = linkedMapOf<String, BookSubscription>()
    existing.forEach { merged[it.bookId] = it }
    incoming.forEach { merged[it.bookId] = it }
    return merged.values.sortedByDescending { it.lastCheckedAt }
}

private fun String.toThemeMode(): ThemeMode =
    runCatching { ThemeMode.valueOf(this) }.getOrDefault(ThemeMode.SYSTEM)

private fun String.toPageMode(): PageMode =
    runCatching { PageMode.valueOf(this) }.getOrDefault(PageMode.SCROLL)

private fun String.toTtsProvider(): TtsProvider =
    runCatching { TtsProvider.valueOf(this) }.getOrDefault(TtsProvider.SYSTEM)

private fun String.toCloudSyncProvider(): CloudSyncProvider =
    runCatching { CloudSyncProvider.valueOf(this) }.getOrDefault(CloudSyncProvider.GITHUB_GIST)
