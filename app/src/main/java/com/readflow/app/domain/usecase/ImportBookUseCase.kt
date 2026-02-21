package com.readflow.app.domain.usecase

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.readflow.app.domain.model.Book
import com.readflow.app.domain.repository.BookRepository
import com.readflow.app.domain.repository.ReaderFileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ImportBookUseCase @Inject constructor(
    private val bookRepository: BookRepository,
    private val readerFileRepository: ReaderFileRepository,
    @ApplicationContext private val context: Context,
) {
    private val coverUpdateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val coverCache by lazy {
        context.getSharedPreferences("cover_cache", Context.MODE_PRIVATE)
    }

    suspend operator fun invoke(uri: Uri): Result<Book> = runCatching {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        val now = System.currentTimeMillis()
        val meta = readerFileRepository.inspectBook(uri)
        val cachedCover = getCachedCover(meta.title)
        val book = Book(
            id = UUID.randomUUID().toString(),
            title = meta.title,
            fileUri = uri.toString(),
            coverColor = randomCoverColor(meta.title),
            coverImageUrl = cachedCover,
            progress = 0f,
            currentPosition = 0,
            totalChars = meta.totalChars,
            encoding = meta.encoding,
            fileSizeBytes = meta.fileSizeBytes,
            lastReadAt = null,
            createdAt = now,
            updatedAt = now,
        )
        bookRepository.upsertBook(book)
        if (cachedCover.isNullOrBlank()) {
            scheduleCoverBackfill(book)
        }
        book
    }

    private fun randomCoverColor(seed: String): String {
        val colors = listOf("#F35B2A", "#FF9B71", "#63A46C", "#4D7EA8", "#B26E63")
        return colors[kotlin.math.abs(seed.hashCode()) % colors.size]
    }

    private fun scheduleCoverBackfill(book: Book) {
        coverUpdateScope.launch {
            val coverUrl = fetchCoverImageUrl(book.title) ?: return@launch
            saveCachedCover(book.title, coverUrl)
            bookRepository.upsertBook(
                book.copy(
                    coverImageUrl = coverUrl,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    private fun getCachedCover(title: String): String? {
        val key = coverCacheKey(title)
        return coverCache.getString(key, null).orEmpty().ifBlank { null }
    }

    private fun saveCachedCover(title: String, imageUrl: String) {
        val key = coverCacheKey(title)
        coverCache.edit().putString(key, imageUrl).apply()
    }

    private fun coverCacheKey(title: String): String =
        title.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").take(120)

    private suspend fun fetchCoverImageUrl(title: String): String? = withContext(Dispatchers.IO) {
        fetchFromOpenLibrary(title) ?: fetchFromGoogleBooks(title)
    }

    private fun fetchFromOpenLibrary(title: String): String? {
        val encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
        val endpoint = "https://openlibrary.org/search.json?title=$encodedTitle&limit=1&fields=cover_i"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            connectTimeout = 3500
            readTimeout = 3500
            requestMethod = "GET"
        }

        try {
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val docs = JSONObject(body).optJSONArray("docs") ?: return null
            val coverId = docs.optJSONObject(0)?.optInt("cover_i", 0) ?: 0
            if (coverId > 0) {
                "https://covers.openlibrary.org/b/id/$coverId-L.jpg"
            } else {
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchFromGoogleBooks(title: String): String? {
        val encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
        val endpoint = "https://www.googleapis.com/books/v1/volumes?q=intitle:$encodedTitle&maxResults=1"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            connectTimeout = 3500
            readTimeout = 3500
            requestMethod = "GET"
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val item = JSONObject(body).optJSONArray("items")?.optJSONObject(0) ?: return null
            val image = item
                .optJSONObject("volumeInfo")
                ?.optJSONObject("imageLinks")
                ?.optString("thumbnail")
                .orEmpty()
            image
                .replace("http://", "https://")
                .ifBlank { null }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
