package com.readflow.app.domain.usecase

import android.content.Context
import android.net.Uri
import com.readflow.app.domain.repository.BookRepository
import com.readflow.app.domain.repository.ReaderFileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class GetBookContentUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val readerFileRepository: ReaderFileRepository,
) {
    suspend operator fun invoke(bookId: String): String {
        val book = bookRepository.getBook(bookId) ?: return ""
        val cached = File(File(context.filesDir, "offline"), "$bookId.txt")
        if (cached.exists()) {
            val local = runCatching { cached.readText(Charsets.UTF_8) }.getOrDefault("")
            if (local.isNotBlank()) return local
        }
        return readerFileRepository.readBookContent(Uri.parse(book.fileUri), book.encoding)
    }
}
