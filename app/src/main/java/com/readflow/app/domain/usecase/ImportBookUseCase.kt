package com.readflow.app.domain.usecase

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.readflow.app.domain.model.Book
import com.readflow.app.domain.repository.BookRepository
import com.readflow.app.domain.repository.ReaderFileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject

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
}
