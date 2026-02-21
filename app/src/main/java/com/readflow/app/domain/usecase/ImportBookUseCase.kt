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
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ImportBookUseCase @Inject constructor(
    private val bookRepository: BookRepository,
    private val readerFileRepository: ReaderFileRepository,
    @ApplicationContext private val context: Context,
) {
    suspend operator fun invoke(uri: Uri): Result<Book> = runCatching {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        val now = System.currentTimeMillis()
        val meta = readerFileRepository.inspectBook(uri)
        val book = Book(
            id = UUID.randomUUID().toString(),
            title = meta.title,
            fileUri = uri.toString(),
            coverColor = randomCoverColor(meta.title),
            coverImageUrl = fetchCoverImageUrl(meta.title),
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
        book
    }

    private fun randomCoverColor(seed: String): String {
        val colors = listOf("#F35B2A", "#FF9B71", "#63A46C", "#4D7EA8", "#B26E63")
        return colors[kotlin.math.abs(seed.hashCode()) % colors.size]
    }

    private suspend fun fetchCoverImageUrl(title: String): String? = withContext(Dispatchers.IO) {
        val encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
        val endpoint = "https://openlibrary.org/search.json?title=$encodedTitle&limit=1&fields=cover_i"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
            requestMethod = "GET"
        }

        try {
            if (connection.responseCode !in 200..299) return@withContext null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val docs = JSONObject(body).optJSONArray("docs") ?: return@withContext null
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
}
