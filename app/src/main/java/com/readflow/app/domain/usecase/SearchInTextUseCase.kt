package com.readflow.app.domain.usecase

import kotlin.math.max
import kotlin.math.min
import javax.inject.Inject

data class SearchMatch(
    val position: Int,
    val snippet: String,
)

data class SearchBatch(
    val matches: List<SearchMatch>,
    val truncated: Boolean,
)

class SearchInTextUseCase @Inject constructor() {
    operator fun invoke(
        content: String,
        query: String,
        limit: Int = 200,
        contextWindow: Int = 20,
    ): SearchBatch {
        val normalized = query.trim()
        if (normalized.isEmpty() || content.isEmpty() || limit <= 0) {
            return SearchBatch(matches = emptyList(), truncated = false)
        }

        val matches = mutableListOf<SearchMatch>()
        var start = 0

        while (start < content.length) {
            val index = content.indexOf(normalized, startIndex = start, ignoreCase = true)
            if (index < 0) break

            if (matches.size < limit) {
                val left = max(0, index - contextWindow)
                val right = min(content.length, index + normalized.length + contextWindow)
                val prefix = if (left > 0) "…" else ""
                val suffix = if (right < content.length) "…" else ""
                matches += SearchMatch(
                    position = index,
                    snippet = prefix + content.substring(left, right).replace('\n', ' ') + suffix,
                )
            }

            start = index + normalized.length.coerceAtLeast(1)
        }

        val hasMore = matches.size == limit && content.indexOf(
            normalized,
            startIndex = (matches.lastOrNull()?.position ?: -1) + normalized.length.coerceAtLeast(1),
            ignoreCase = true,
        ) >= 0
        return SearchBatch(matches = matches, truncated = hasMore)
    }
}
