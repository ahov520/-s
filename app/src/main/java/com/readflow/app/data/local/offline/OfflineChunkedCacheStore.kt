package com.readflow.app.data.local.offline

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

data class OfflineChunkRef(
    val chunkNo: Int,
    val fileName: String,
    val startOffset: Int,
    val length: Int,
)

data class OfflineChunkIndex(
    val version: Int,
    val bookId: String,
    val encoding: String,
    val chunkChars: Int,
    val totalChars: Int,
    val chunkCount: Int,
    val contentHash: String,
    val createdAt: Long,
    val chunks: List<OfflineChunkRef>,
)

enum class OfflineCacheMigrationStatus {
    NO_LEGACY_CACHE,
    ALREADY_MIGRATED,
    MIGRATED,
    FAILED,
}

data class OfflineCacheMigrationResult(
    val status: OfflineCacheMigrationStatus,
    val reason: String? = null,
)

data class OfflineChunkWriteResult(
    val content: String,
    val index: OfflineChunkIndex,
)

class OfflineChunkedCacheStore(
    private val offlineRootDir: File,
) {
    companion object {
        const val INDEX_VERSION = 2
        const val DEFAULT_CHUNK_CHARS = 64 * 1024
    }

    private val bookLocks = ConcurrentHashMap<String, Mutex>()

    suspend fun hasLegacyCache(bookId: String): Boolean = withContext(Dispatchers.IO) {
        legacyFile(bookId).exists()
    }

    suspend fun hasChunkedCache(bookId: String): Boolean = withContext(Dispatchers.IO) {
        indexFile(bookId).exists()
    }

    suspend fun readLegacy(bookId: String): String? = withContext(Dispatchers.IO) {
        val file = legacyFile(bookId)
        if (!file.exists()) return@withContext null
        runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
    }

    suspend fun readChunked(bookId: String): String? = withContext(Dispatchers.IO) {
        val index = readIndexInternal(bookId) ?: return@withContext null
        val builder = StringBuilder(index.totalChars.coerceAtLeast(0))
        for (chunk in index.chunks.sortedBy { it.chunkNo }) {
            val file = File(bookDir(bookId), chunk.fileName)
            val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return@withContext null
            builder.append(text)
        }
        builder.toString()
    }

    suspend fun readWindow(bookId: String, start: Int, length: Int): String = withContext(Dispatchers.IO) {
        if (length <= 0) return@withContext ""
        val index = readIndexInternal(bookId) ?: return@withContext ""
        val safeStart = start.coerceAtLeast(0)
        if (safeStart >= index.totalChars) return@withContext ""

        val windowEnd = (safeStart + length).coerceAtMost(index.totalChars)
        if (windowEnd <= safeStart) return@withContext ""

        val builder = StringBuilder(windowEnd - safeStart)
        val chunks = index.chunks.sortedBy { it.chunkNo }
        for (chunk in chunks) {
            val chunkStart = chunk.startOffset
            val chunkEnd = chunk.startOffset + chunk.length
            if (chunkEnd <= safeStart) continue
            if (chunkStart >= windowEnd) break

            val overlapStart = maxOf(safeStart, chunkStart)
            val overlapEnd = minOf(windowEnd, chunkEnd)
            if (overlapEnd <= overlapStart) continue

            val text = runCatching {
                File(bookDir(bookId), chunk.fileName).readText(Charsets.UTF_8)
            }.getOrNull() ?: break

            val localStart = (overlapStart - chunkStart).coerceIn(0, text.length)
            val localEnd = (overlapEnd - chunkStart).coerceIn(localStart, text.length)
            if (localEnd > localStart) {
                builder.append(text.substring(localStart, localEnd))
            }
        }
        builder.toString()
    }

    suspend fun writeChunked(
        bookId: String,
        encoding: String,
        content: String,
        chunkChars: Int = DEFAULT_CHUNK_CHARS,
    ): OfflineChunkWriteResult = writeChunkedFromFlow(
        bookId = bookId,
        encoding = encoding,
        chunks = kotlinx.coroutines.flow.flowOf(content),
        chunkChars = chunkChars,
    )

    suspend fun writeChunkedFromFlow(
        bookId: String,
        encoding: String,
        chunks: Flow<String>,
        chunkChars: Int = DEFAULT_CHUNK_CHARS,
    ): OfflineChunkWriteResult {
        val safeChunkChars = chunkChars.coerceIn(4 * 1024, 512 * 1024)
        val lock = bookLocks.getOrPut(bookId) { Mutex() }
        return lock.withLock {
            withContext(Dispatchers.IO) {
                ensureOfflineRoot()

                val safeBookId = safeBookId(bookId)
                val bookTempDir = File(v2RootDir(), "$safeBookId.tmp-${System.nanoTime()}")
                if (!bookTempDir.mkdirs()) {
                    error("创建离线临时目录失败")
                }

                val refs = mutableListOf<OfflineChunkRef>()
                val digest = MessageDigest.getInstance("SHA-256")
                val fullContent = StringBuilder()
                val pending = StringBuilder()
                var totalChars = 0
                var chunkNo = 0
                var startOffset = 0

                fun flushChunk(text: String) {
                    if (text.isEmpty()) return
                    chunkNo += 1
                    val fileName = "chunk_%05d.txt".format(chunkNo)
                    File(bookTempDir, fileName).writeText(text, Charsets.UTF_8)
                    refs += OfflineChunkRef(
                        chunkNo = chunkNo,
                        fileName = fileName,
                        startOffset = startOffset,
                        length = text.length,
                    )
                    startOffset += text.length
                }

                chunks.collect { part ->
                    if (part.isEmpty()) return@collect
                    fullContent.append(part)
                    totalChars += part.length
                    digest.update(part.toByteArray(Charsets.UTF_8))
                    pending.append(part)

                    while (pending.length >= safeChunkChars) {
                        val chunkText = pending.substring(0, safeChunkChars)
                        flushChunk(chunkText)
                        pending.delete(0, safeChunkChars)
                    }
                }

                if (pending.isNotEmpty()) {
                    flushChunk(pending.toString())
                    pending.clear()
                }

                val index = OfflineChunkIndex(
                    version = INDEX_VERSION,
                    bookId = bookId,
                    encoding = encoding,
                    chunkChars = safeChunkChars,
                    totalChars = totalChars,
                    chunkCount = refs.size,
                    contentHash = digest.digest().joinToString("") { "%02x".format(it) },
                    createdAt = System.currentTimeMillis(),
                    chunks = refs,
                )
                File(bookTempDir, "index.json").writeText(index.toJson().toString(), Charsets.UTF_8)

                val targetDir = bookDir(bookId)
                runCatching { targetDir.deleteRecursively() }
                if (!bookTempDir.renameTo(targetDir)) {
                    runCatching { bookTempDir.deleteRecursively() }
                    error("替换离线缓存目录失败")
                }

                OfflineChunkWriteResult(content = fullContent.toString(), index = index)
            }
        }
    }

    suspend fun migrateLegacyToChunked(
        bookId: String,
        encoding: String,
        chunkChars: Int = DEFAULT_CHUNK_CHARS,
    ): OfflineCacheMigrationResult {
        return runCatching {
            if (hasChunkedCache(bookId)) {
                val chunked = readChunked(bookId)
                if (!chunked.isNullOrBlank()) {
                    return@runCatching OfflineCacheMigrationResult(OfflineCacheMigrationStatus.ALREADY_MIGRATED)
                }
            }

            val legacy = readLegacy(bookId)
            if (legacy.isNullOrBlank()) {
                return@runCatching OfflineCacheMigrationResult(OfflineCacheMigrationStatus.NO_LEGACY_CACHE)
            }

            writeChunked(
                bookId = bookId,
                encoding = encoding,
                content = legacy,
                chunkChars = chunkChars,
            )
            OfflineCacheMigrationResult(OfflineCacheMigrationStatus.MIGRATED)
        }.getOrElse { throwable ->
            OfflineCacheMigrationResult(
                status = OfflineCacheMigrationStatus.FAILED,
                reason = throwable.message,
            )
        }
    }

    suspend fun clearBookCache(bookId: String) = withContext(Dispatchers.IO) {
        runCatching { legacyFile(bookId).delete() }
        runCatching { bookDir(bookId).deleteRecursively() }
    }

    private fun ensureOfflineRoot() {
        if (!offlineRootDir.exists()) {
            offlineRootDir.mkdirs()
        }
        if (!v2RootDir().exists()) {
            v2RootDir().mkdirs()
        }
    }

    private fun v2RootDir(): File = File(offlineRootDir, "v2")

    private fun safeBookId(bookId: String): String =
        bookId.replace(Regex("[^a-zA-Z0-9._-]"), "_")

    private fun legacyFile(bookId: String): File = File(offlineRootDir, "${safeBookId(bookId)}.txt")

    private fun bookDir(bookId: String): File = File(v2RootDir(), safeBookId(bookId))

    private fun indexFile(bookId: String): File = File(bookDir(bookId), "index.json")

    private fun readIndexInternal(bookId: String): OfflineChunkIndex? {
        val file = indexFile(bookId)
        if (!file.exists()) return null
        val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return null
        return runCatching { text.toOfflineChunkIndex() }.getOrNull()
    }
}

private fun OfflineChunkIndex.toJson(): JSONObject {
    val chunksJson = JSONArray()
    chunks.forEach { chunk ->
        chunksJson.put(
            JSONObject()
                .put("chunkNo", chunk.chunkNo)
                .put("fileName", chunk.fileName)
                .put("startOffset", chunk.startOffset)
                .put("length", chunk.length)
        )
    }
    return JSONObject()
        .put("version", version)
        .put("bookId", bookId)
        .put("encoding", encoding)
        .put("chunkChars", chunkChars)
        .put("totalChars", totalChars)
        .put("chunkCount", chunkCount)
        .put("contentHash", contentHash)
        .put("createdAt", createdAt)
        .put("chunks", chunksJson)
}

private fun String.toOfflineChunkIndex(): OfflineChunkIndex {
    val json = JSONObject(this)
    val chunksArray = json.optJSONArray("chunks") ?: JSONArray()
    val chunks = buildList {
        for (i in 0 until chunksArray.length()) {
            val item = chunksArray.optJSONObject(i) ?: continue
            add(
                OfflineChunkRef(
                    chunkNo = item.optInt("chunkNo", i + 1),
                    fileName = item.optString("fileName"),
                    startOffset = item.optInt("startOffset", 0),
                    length = item.optInt("length", 0).coerceAtLeast(0),
                )
            )
        }
    }
    return OfflineChunkIndex(
        version = json.optInt("version", 0),
        bookId = json.optString("bookId"),
        encoding = json.optString("encoding", "UTF-8"),
        chunkChars = json.optInt("chunkChars", OfflineChunkedCacheStore.DEFAULT_CHUNK_CHARS),
        totalChars = json.optInt("totalChars", 0).coerceAtLeast(0),
        chunkCount = json.optInt("chunkCount", chunks.size),
        contentHash = json.optString("contentHash"),
        createdAt = json.optLong("createdAt", 0L),
        chunks = chunks,
    )
}
