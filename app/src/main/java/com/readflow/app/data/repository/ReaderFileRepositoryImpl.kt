package com.readflow.app.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.readflow.app.domain.repository.ImportedBookMeta
import com.readflow.app.domain.repository.ReaderFileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mozilla.universalchardet.UniversalDetector
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import javax.inject.Inject

class ReaderFileRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReaderFileRepository {
    override suspend fun inspectBook(uri: Uri): ImportedBookMeta = withContext(Dispatchers.IO) {
        val bytes = readAllBytes(uri)
        val encoding = detectEncoding(bytes.copyOfRange(0, minOf(bytes.size, 64 * 1024)))
        val title = queryDisplayName(uri) ?: "未命名书籍"
        val decoded = runCatching { String(bytes, Charset.forName(encoding)) }
            .getOrElse { String(bytes, Charsets.UTF_8) }

        ImportedBookMeta(
            title = title.removeSuffix(".txt"),
            encoding = encoding,
            totalChars = decoded.length,
            fileSizeBytes = bytes.size.toLong(),
        )
    }

    override suspend fun readBookContent(uri: Uri, encoding: String): String = withContext(Dispatchers.IO) {
        val data = readAllBytes(uri)
        runCatching { String(data, Charset.forName(encoding)) }
            .getOrElse { String(data, Charsets.UTF_8) }
    }

    private fun detectEncoding(bytes: ByteArray): String {
        if (bytes.isEmpty()) return "UTF-8"
        val detector = UniversalDetector(null)
        detector.handleData(bytes, 0, bytes.size)
        detector.dataEnd()
        return detector.detectedCharset ?: "UTF-8"
    }

    private fun queryDisplayName(uri: Uri): String? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            } else {
                null
            }
        }
    }

    private fun readAllBytes(uri: Uri): ByteArray {
        val stream = context.contentResolver.openInputStream(uri) ?: return ByteArray(0)
        BufferedInputStream(stream).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        }
    }
}
