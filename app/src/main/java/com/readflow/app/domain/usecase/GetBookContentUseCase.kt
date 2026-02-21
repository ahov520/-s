package com.readflow.app.domain.usecase

import android.net.Uri
import com.readflow.app.domain.repository.BookRepository
import com.readflow.app.domain.repository.ReaderFileRepository
import javax.inject.Inject

class GetBookContentUseCase @Inject constructor(
    private val bookRepository: BookRepository,
    private val readerFileRepository: ReaderFileRepository,
) {
    suspend operator fun invoke(bookId: String): String {
        val book = bookRepository.getBook(bookId) ?: return ""
        return readerFileRepository.readBookContent(Uri.parse(book.fileUri), book.encoding)
    }
}
