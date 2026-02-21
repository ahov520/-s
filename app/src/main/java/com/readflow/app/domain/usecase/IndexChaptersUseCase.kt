package com.readflow.app.domain.usecase

import com.readflow.app.domain.model.ChapterIndex
import java.util.UUID
import javax.inject.Inject

private val CHAPTER_PATTERNS = listOf(
    Regex("^\\s*第[0-9零一二三四五六七八九十百千两〇]+[章回节卷部篇].{0,30}$", RegexOption.MULTILINE),
    Regex("^\\s*Chapter\\s+\\d+.*$", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)),
    Regex("^\\s*卷[0-9零一二三四五六七八九十百千]+.{0,30}$", RegexOption.MULTILINE),
)

class IndexChaptersUseCase @Inject constructor() {
    operator fun invoke(bookId: String, content: String): List<ChapterIndex> {
        if (content.isBlank()) {
            return emptyList()
        }

        val hits = mutableListOf<Pair<Int, String>>()
        CHAPTER_PATTERNS.forEach { pattern ->
            pattern.findAll(content).forEach { match ->
                hits += match.range.first to match.value.trim()
            }
        }

        val sorted = hits.distinctBy { it.first }.sortedBy { it.first }
        if (sorted.isEmpty()) {
            return listOf(
                ChapterIndex(
                    id = UUID.randomUUID().toString(),
                    bookId = bookId,
                    chapterOrder = 0,
                    title = "开始",
                    startChar = 0,
                    endChar = content.length,
                )
            )
        }

        return sorted.mapIndexed { index, (start, title) ->
            val end = if (index < sorted.lastIndex) sorted[index + 1].first else content.length
            ChapterIndex(
                id = UUID.randomUUID().toString(),
                bookId = bookId,
                chapterOrder = index,
                title = title,
                startChar = start,
                endChar = end,
            )
        }
    }
}
