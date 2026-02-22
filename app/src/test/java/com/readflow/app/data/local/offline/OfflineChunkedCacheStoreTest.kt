package com.readflow.app.data.local.offline

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test

class OfflineChunkedCacheStoreTest {
    @Test
    fun `should write and read chunked content`() = runBlocking {
        val (dir, store) = createStore()
        try {
            val bookId = "book-1"
            val content = buildString {
                repeat(4000) { append("readflow-$it\n") }
            }

            val result = store.writeChunked(
                bookId = bookId,
                encoding = "UTF-8",
                content = content,
                chunkChars = 512,
            )

            val restored = store.readChunked(bookId)
            assertThat(result.index.chunkCount).isGreaterThan(1)
            assertThat(restored).isEqualTo(content)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `should support window read across chunks`() = runBlocking {
        val (dir, store) = createStore()
        try {
            val bookId = "book-2"
            val content = ("0123456789".repeat(2000))
            store.writeChunked(
                bookId = bookId,
                encoding = "UTF-8",
                content = content,
                chunkChars = 128,
            )

            val start = 123
            val length = 501
            val window = store.readWindow(bookId = bookId, start = start, length = length)

            assertThat(window).isEqualTo(content.substring(start, start + length))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `should migrate legacy cache to chunked`() = runBlocking {
        val (dir, store) = createStore()
        try {
            val bookId = "book-3"
            val content = "legacy text content".repeat(1000)
            File(dir, "$bookId.txt").writeText(content, Charsets.UTF_8)

            val migration = store.migrateLegacyToChunked(bookId = bookId, encoding = "UTF-8")
            val restored = store.readChunked(bookId)

            assertThat(migration.status).isEqualTo(OfflineCacheMigrationStatus.MIGRATED)
            assertThat(restored).isEqualTo(content)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `should clear both legacy and chunked cache`() = runBlocking {
        val (dir, store) = createStore()
        try {
            val bookId = "book-4"
            File(dir, "$bookId.txt").writeText("legacy", Charsets.UTF_8)
            store.writeChunkedFromFlow(
                bookId = bookId,
                encoding = "UTF-8",
                chunks = flowOf("chunked"),
                chunkChars = 4,
            )

            store.clearBookCache(bookId)

            assertThat(store.hasLegacyCache(bookId)).isFalse()
            assertThat(store.hasChunkedCache(bookId)).isFalse()
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun createStore(): Pair<File, OfflineChunkedCacheStore> {
        val directory = Files.createTempDirectory("readflow-offline-cache-test").toFile()
        return directory to OfflineChunkedCacheStore(directory)
    }
}
