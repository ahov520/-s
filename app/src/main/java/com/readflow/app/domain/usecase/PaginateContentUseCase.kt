package com.readflow.app.domain.usecase

import javax.inject.Inject
import kotlin.math.floor
import kotlin.math.max

data class PaginationLayout(
    val width: Int,
    val height: Int,
    val fontSize: Int,
    val lineHeight: Float,
    val paddingHorizontal: Int,
)

data class PaginationResult(
    val pages: List<String>,
    val charsPerPage: Int,
    val totalPages: Int,
)

class PaginateContentUseCase @Inject constructor() {
    companion object {
        private const val CHAR_WIDTH_FACTOR = 0.6f
        private const val MIN_CHARS_PER_PAGE = 100
    }

    operator fun invoke(content: String, layout: PaginationLayout): PaginationResult {
        val charsPerPage = estimateCharsPerPage(layout)
        if (content.isEmpty()) {
            return PaginationResult(pages = listOf(""), charsPerPage = charsPerPage, totalPages = 1)
        }

        val pages = mutableListOf<String>()
        var offset = 0
        while (offset < content.length) {
            var end = minOf(offset + charsPerPage, content.length)
            if (end < content.length) {
                val lastNewline = content.lastIndexOf('\n', startIndex = end)
                if (lastNewline > offset) {
                    end = lastNewline + 1
                }
            }
            pages += content.substring(offset, end)
            offset = end
        }

        return PaginationResult(pages = pages, charsPerPage = charsPerPage, totalPages = pages.size)
    }

    private fun estimateCharsPerPage(layout: PaginationLayout): Int {
        val contentWidth = layout.width - layout.paddingHorizontal * 2
        val lineHeightPx = layout.fontSize * layout.lineHeight
        val charsPerLine = floor(contentWidth / (layout.fontSize * CHAR_WIDTH_FACTOR)).toInt()
        val linesPerPage = floor(layout.height / lineHeightPx).toInt()
        return max(MIN_CHARS_PER_PAGE, charsPerLine * linesPerPage)
    }

    fun pageIndexForPosition(charPos: Int, pages: List<String>): Int {
        if (pages.isEmpty()) return 0
        var accumulated = 0
        pages.forEachIndexed { index, page ->
            accumulated += page.length
            if (charPos < accumulated) return index
        }
        return pages.lastIndex
    }

    fun positionForPageIndex(pageIndex: Int, pages: List<String>): Int {
        var pos = 0
        for (i in 0 until pageIndex.coerceAtMost(pages.lastIndex)) {
            pos += pages[i].length
        }
        return pos
    }
}
