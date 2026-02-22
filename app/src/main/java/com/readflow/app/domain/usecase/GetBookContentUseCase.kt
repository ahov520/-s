package com.readflow.app.domain.usecase

import android.content.Context
import android.net.Uri
import com.readflow.app.data.local.offline.OfflineCacheMigrationStatus
import com.readflow.app.data.local.offline.OfflineChunkedCacheStore
import com.readflow.app.domain.model.Book
import com.readflow.app.domain.repository.BookRepository
import com.readflow.app.domain.repository.ReaderFileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

enum class MigrationStatus {
    NO_LEGACY_CACHE,
    ALREADY_MIGRATED,
    MIGRATED,
    FAILED,
}

data class MigrationResult(
    val status: MigrationStatus,
    val reason: String? = null,
)

class GetBookContentUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val readerFileRepository: ReaderFileRepository,
) {
    private val cacheStore: OfflineChunkedCacheStore by lazy {
        OfflineChunkedCacheStore(File(context.filesDir, "offline"))
    }

    suspend operator fun invoke(bookId: String): String {
        val book = bookRepository.getBook(bookId) ?: return ""
        val chunked = cacheStore.readChunked(bookId).orEmpty()
        if (chunked.isNotBlank()) return chunked

        val legacy = cacheStore.readLegacy(bookId).orEmpty()
        if (legacy.isNotBlank()) {
            runCatching {
                cacheStore.writeChunked(
                    bookId = book.id,
                    encoding = book.encoding,
                    content = legacy,
                )
            }
            return legacy
        }

        return loadFromSourceAndCache(book)
    }

    suspend fun readWindow(bookId: String, start: Int, length: Int): String {
        if (length <= 0) return ""
        val fromChunked = cacheStore.readWindow(bookId = bookId, start = start, length = length)
        if (fromChunked.isNotBlank()) return fromChunked

        val legacy = cacheStore.readLegacy(bookId)
        if (!legacy.isNullOrBlank()) {
            val safeStart = start.coerceAtLeast(0).coerceAtMost(legacy.length)
            val safeEnd = (safeStart + length).coerceAtMost(legacy.length)
            return if (safeEnd > safeStart) legacy.substring(safeStart, safeEnd) else ""
        }

        val all = invoke(bookId)
        if (all.isBlank()) return ""
        val safeStart = start.coerceAtLeast(0).coerceAtMost(all.length)
        val safeEnd = (safeStart + length).coerceAtMost(all.length)
        return if (safeEnd > safeStart) all.substring(safeStart, safeEnd) else ""
    }

    suspend fun ensureMigratedIfNeeded(bookId: String): MigrationResult {
        val encoding = bookRepository.getBook(bookId)?.encoding ?: "UTF-8"
        val result = cacheStore.migrateLegacyToChunked(
            bookId = bookId,
            encoding = encoding,
        )
        return when (result.status) {
            OfflineCacheMigrationStatus.NO_LEGACY_CACHE -> MigrationResult(MigrationStatus.NO_LEGACY_CACHE)
            OfflineCacheMigrationStatus.ALREADY_MIGRATED -> MigrationResult(MigrationStatus.ALREADY_MIGRATED)
            OfflineCacheMigrationStatus.MIGRATED -> MigrationResult(MigrationStatus.MIGRATED)
            OfflineCacheMigrationStatus.FAILED -> MigrationResult(
                status = MigrationStatus.FAILED,
                reason = result.reason,
            )
        }
    }

    suspend fun clearOfflineCache(bookId: String) {
        cacheStore.clearBookCache(bookId)
    }

    private suspend fun loadFromSourceAndCache(book: Book): String {
        val uri = Uri.parse(book.fileUri)
        val streamed = runCatching {
            cacheStore.writeChunkedFromFlow(
                bookId = book.id,
                encoding = book.encoding,
                chunks = readerFileRepository.streamTextContent(uri, book.encoding),
            ).content
        }.getOrDefault("")
        if (streamed.isNotBlank()) return streamed

        val fallback = readerFileRepository.readBookContent(uri, book.encoding)
        if (fallback.isNotBlank()) {
            runCatching {
                cacheStore.writeChunked(
                    bookId = book.id,
                    encoding = book.encoding,
                    content = fallback,
                )
            }
        }
        return fallback
    }
}
