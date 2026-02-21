package com.readflow.app.domain.usecase

object AutoPageProgressPolicy {
    private const val FALLBACK_STEP = 260
    private const val PARAGRAPH_WINDOW = 1500
    private const val LINE_BREAK_WINDOW = 900
    private const val SENTENCE_WINDOW = 720

    fun nextScrollPosition(content: String, currentPosition: Int): Int {
        if (content.isEmpty()) return 0
        val last = content.lastIndex
        val current = currentPosition.coerceIn(0, last)
        if (current >= last) return last

        val start = (current + 1).coerceAtMost(last)

        val paragraphBreak = content.indexOf("\n\n", start)
        if (paragraphBreak in start..(start + PARAGRAPH_WINDOW).coerceAtMost(last)) {
            return (paragraphBreak + 2).coerceAtMost(last)
        }

        val lineBreak = content.indexOf('\n', start)
        if (lineBreak in start..(start + LINE_BREAK_WINDOW).coerceAtMost(last)) {
            return (lineBreak + 1).coerceAtMost(last)
        }

        val sentenceBreak = findSentenceBreak(content, start, (start + SENTENCE_WINDOW).coerceAtMost(last))
        if (sentenceBreak != -1) return sentenceBreak

        return (current + FALLBACK_STEP).coerceAtMost(last)
    }

    private fun findSentenceBreak(content: String, start: Int, end: Int): Int {
        for (index in start..end) {
            when (content[index]) {
                '。', '！', '？', '!', '?', '；', ';' -> return (index + 1).coerceAtMost(content.lastIndex)
            }
        }
        return -1
    }
}
