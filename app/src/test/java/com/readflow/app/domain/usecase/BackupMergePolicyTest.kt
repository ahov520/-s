package com.readflow.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.readflow.app.domain.model.Book
import com.readflow.app.domain.model.Bookmark
import com.readflow.app.domain.model.ChapterIndex
import com.readflow.app.domain.model.Highlight
import com.readflow.app.domain.model.ReadingNote
import org.junit.Test

class BackupMergePolicyTest {
    @Test
    fun mergeBooksById_incomingShouldOverrideSameId() {
        val existing = listOf(book(id = "a", title = "旧书A"), book(id = "b", title = "旧书B"))
        val incoming = listOf(book(id = "b", title = "新书B"), book(id = "c", title = "新书C"))

        val merged = mergeBooksById(existing, incoming)

        assertThat(merged.map { it.id }).containsExactly("a", "b", "c").inOrder()
        assertThat(merged.first { it.id == "b" }.title).isEqualTo("新书B")
    }

    @Test
    fun mergeBookmarksById_shouldDeduplicateAndSortByCreatedAtDesc() {
        val existing = listOf(
            bookmark(id = "m1", createdAt = 10),
            bookmark(id = "m2", createdAt = 20),
        )
        val incoming = listOf(
            bookmark(id = "m2", createdAt = 30),
            bookmark(id = "m3", createdAt = 25),
        )

        val merged = mergeBookmarksById(existing, incoming)

        assertThat(merged.map { it.id }).containsExactly("m2", "m3", "m1").inOrder()
        assertThat(merged.first().createdAt).isEqualTo(30)
    }

    @Test
    fun mergeChaptersById_shouldSortByBookAndOrder() {
        val existing = listOf(
            chapter(id = "c1", bookId = "b1", order = 2),
            chapter(id = "c2", bookId = "b1", order = 1),
        )
        val incoming = listOf(
            chapter(id = "c3", bookId = "b2", order = 1),
            chapter(id = "c2", bookId = "b1", order = 3),
        )

        val merged = mergeChaptersById(existing, incoming)

        assertThat(merged.map { it.id }).containsExactly("c1", "c2", "c3").inOrder()
        assertThat(merged.first { it.id == "c2" }.chapterOrder).isEqualTo(3)
    }

    @Test
    fun mergeReadingNotes_shouldDeduplicateAndSortByCreatedAtDesc() {
        val existing = listOf(note(id = "n1", createdAt = 100), note(id = "n2", createdAt = 200))
        val incoming = listOf(note(id = "n2", createdAt = 400), note(id = "n3", createdAt = 300))

        val merged = mergeReadingNotes(existing, incoming)

        assertThat(merged.map { it.id }).containsExactly("n2", "n3", "n1").inOrder()
        assertThat(merged.first().createdAt).isEqualTo(400)
    }

    @Test
    fun mergeBookGroups_shouldKeepExistingAndOverrideIncoming() {
        val existing = mapOf("book-a" to "收藏", "book-b" to "追更")
        val incoming = mapOf("book-b" to "完结", "book-c" to "收藏")

        val merged = mergeBookGroups(existing, incoming)

        assertThat(merged["book-a"]).isEqualTo("收藏")
        assertThat(merged["book-b"]).isEqualTo("完结")
        assertThat(merged["book-c"]).isEqualTo("收藏")
    }

    @Test
    fun mergeHighlights_shouldDeduplicateAndSortByCreatedAtDesc() {
        val existing = listOf(highlight(id = "h1", createdAt = 100), highlight(id = "h2", createdAt = 200))
        val incoming = listOf(highlight(id = "h2", createdAt = 400), highlight(id = "h3", createdAt = 300))

        val merged = mergeHighlights(existing, incoming)

        assertThat(merged.map { it.id }).containsExactly("h2", "h3", "h1").inOrder()
        assertThat(merged.first().createdAt).isEqualTo(400)
    }

    @Test
    fun mergeReadHistory_shouldKeepLatestAndTrim() {
        val existing = mapOf(
            "2026-02-20" to 1000,
            "2026-02-21" to 800,
        )
        val incoming = mapOf(
            "2026-02-21" to 1200,
            "2026-02-22" to 600,
        )

        val merged = mergeReadHistory(existing, incoming, keepDays = 3)

        assertThat(merged["2026-02-21"]).isEqualTo(1200)
        assertThat(merged["2026-02-22"]).isEqualTo(600)
        assertThat(merged["2026-02-20"]).isEqualTo(1000)
    }

    private fun book(id: String, title: String): Book = Book(
        id = id,
        title = title,
        fileUri = "file://$id.txt",
        coverColor = "#2D2A26",
        coverImageUrl = null,
        progress = 0f,
        currentPosition = 0,
        totalChars = 1000,
        encoding = "UTF-8",
        fileSizeBytes = 100,
        lastReadAt = null,
        createdAt = 1,
        updatedAt = 1,
    )

    private fun bookmark(id: String, createdAt: Long): Bookmark = Bookmark(
        id = id,
        bookId = "book",
        position = 10,
        label = "标记",
        createdAt = createdAt,
    )

    private fun chapter(id: String, bookId: String, order: Int): ChapterIndex = ChapterIndex(
        id = id,
        bookId = bookId,
        chapterOrder = order,
        title = "章节$order",
        startChar = order * 100,
        endChar = order * 100 + 99,
    )

    private fun note(id: String, createdAt: Long): ReadingNote = ReadingNote(
        id = id,
        bookId = "book",
        startChar = 0,
        endChar = 30,
        quote = "quote",
        note = "note",
        createdAt = createdAt,
    )

    private fun highlight(id: String, createdAt: Long): Highlight = Highlight(
        id = id,
        bookId = "book",
        startChar = 0,
        endChar = 120,
        quote = "quote",
        colorKey = "amber",
        note = "",
        createdAt = createdAt,
    )
}
