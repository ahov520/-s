package com.readflow.app.domain.repository

import android.net.Uri
import kotlinx.coroutines.flow.Flow

data class ImportedBookMeta(
    val title: String,
    val encoding: String,
    val totalChars: Int,
    val fileSizeBytes: Long,
)

interface ReaderFileRepository {
    suspend fun inspectBook(uri: Uri): ImportedBookMeta
    suspend fun readBookContent(uri: Uri, encoding: String): String
    fun streamTextContent(uri: Uri, encoding: String, chunkSize: Int = 256 * 1024): Flow<String>
}
