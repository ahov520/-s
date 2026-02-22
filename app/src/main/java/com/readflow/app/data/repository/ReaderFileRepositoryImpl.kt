package com.readflow.app.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.text.HtmlCompat
import com.readflow.app.domain.repository.ImportedBookMeta
import com.readflow.app.domain.repository.ReaderFileRepository
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import org.mozilla.universalchardet.UniversalDetector
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.inject.Inject

class ReaderFileRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReaderFileRepository {
    @Volatile
    private var pdfBoxReady = false

    override suspend fun inspectBook(uri: Uri): ImportedBookMeta = withContext(Dispatchers.IO) {
        val bytes = readAllBytes(uri)
        val displayName = queryDisplayName(uri) ?: uri.lastPathSegment ?: "未命名书籍"
        val type = resolveType(displayName)
        val encoding = if (type == BookFileType.TXT) {
            detectEncoding(bytes.copyOfRange(0, minOf(bytes.size, 64 * 1024)))
        } else {
            "UTF-8"
        }
        val decoded = decodeContent(bytes, type, encoding)

        ImportedBookMeta(
            title = sanitizeTitle(displayName),
            encoding = encoding,
            totalChars = decoded.length,
            fileSizeBytes = bytes.size.toLong(),
        )
    }

    override suspend fun readBookContent(uri: Uri, encoding: String): String = withContext(Dispatchers.IO) {
        val data = readAllBytes(uri)
        val displayName = queryDisplayName(uri) ?: uri.lastPathSegment ?: ""
        val type = resolveType(displayName)
        decodeContent(data, type, encoding)
    }

    override fun streamTextContent(uri: Uri, encoding: String, chunkSize: Int): Flow<String> = channelFlow {
        val safeChunkSize = chunkSize.coerceIn(4 * 1024, 512 * 1024)
        val displayName = queryDisplayName(uri) ?: uri.lastPathSegment ?: ""
        val type = resolveType(displayName)
        val charset = runCatching { Charset.forName(encoding) }.getOrElse { Charsets.UTF_8 }

        val emitted = runCatching {
            if (type != BookFileType.TXT) {
                val fallback = withContext(Dispatchers.IO) { readBookContent(uri, encoding) }
                if (fallback.isNotBlank()) send(fallback)
                return@runCatching true
            }

            val stream = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri) }
                ?: return@runCatching false

            withContext(Dispatchers.IO) {
                BufferedInputStream(stream).use { input ->
                    InputStreamReader(input, charset).use { reader ->
                        val buffer = CharArray(safeChunkSize)
                        while (true) {
                            val read = reader.read(buffer)
                            if (read <= 0) break
                            send(String(buffer, 0, read))
                        }
                    }
                }
            }
            true
        }.getOrElse { false }

        if (!emitted) {
            val fallback = withContext(Dispatchers.IO) { readBookContent(uri, encoding) }
            if (fallback.isNotBlank()) send(fallback)
        }

    }

    private fun decodeContent(bytes: ByteArray, type: BookFileType, encoding: String): String {
        return when (type) {
            BookFileType.TXT -> runCatching { String(bytes, Charset.forName(encoding)) }
                .getOrElse { String(bytes, Charsets.UTF_8) }

            BookFileType.PDF -> extractPdfText(bytes)
            BookFileType.EPUB -> extractEpubText(bytes)
        }.ifBlank { "内容为空" }
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

    private fun resolveType(displayName: String): BookFileType {
        val name = displayName.lowercase(Locale.ROOT)
        return when {
            name.endsWith(".pdf") -> BookFileType.PDF
            name.endsWith(".epub") -> BookFileType.EPUB
            else -> BookFileType.TXT
        }
    }

    private fun sanitizeTitle(displayName: String): String {
        val normalized = displayName.replace(Regex("\\.(txt|epub|pdf)$", RegexOption.IGNORE_CASE), "")
        return normalized.trim().ifBlank { "未命名书籍" }
    }

    private fun extractPdfText(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        ensurePdfBoxReady()
        return runCatching {
            PDDocument.load(ByteArrayInputStream(bytes)).use { doc ->
                PDFTextStripper().getText(doc)
            }
        }.getOrDefault("")
    }

    private fun extractEpubText(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        return runCatching {
            val builder = StringBuilder()
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && entry.name.isEpubTextEntry()) {
                        val entryBytes = zip.readCurrentEntryBytes()
                        if (entryBytes.isNotEmpty()) {
                            val html = String(entryBytes, Charsets.UTF_8)
                            val plain = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
                                .toString()
                                .replace("\u00A0", " ")
                                .lines()
                                .joinToString("\n") { it.trim() }
                                .replace(Regex("\n{3,}"), "\n\n")
                                .trim()
                            if (plain.isNotBlank()) {
                                if (builder.isNotEmpty()) builder.append("\n\n")
                                builder.append(plain)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
            builder.toString()
        }.getOrDefault("")
    }

    private fun ensurePdfBoxReady() {
        if (pdfBoxReady) return
        synchronized(this) {
            if (!pdfBoxReady) {
                PDFBoxResourceLoader.init(context)
                pdfBoxReady = true
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

    private enum class BookFileType {
        TXT,
        PDF,
        EPUB,
    }
}

private fun String.isEpubTextEntry(): Boolean {
    val lower = lowercase(Locale.ROOT)
    return lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm") || lower.endsWith(".xml")
}

private fun ZipInputStream.readCurrentEntryBytes(): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(32 * 1024)
    while (true) {
        val read = read(buffer)
        if (read <= 0) break
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
