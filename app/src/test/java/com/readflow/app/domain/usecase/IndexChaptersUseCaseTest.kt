package com.readflow.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IndexChaptersUseCaseTest {
    private val useCase = IndexChaptersUseCase()

    @Test
    fun `should detect chinese chapters`() {
        val content = """
            序章
            第1章 初识
            内容 A
            第2章 再会
            内容 B
        """.trimIndent()

        val result = useCase(bookId = "book-1", content = content)

        assertThat(result).hasSize(2)
        assertThat(result[0].title).contains("第1章")
        assertThat(result[1].title).contains("第2章")
        assertThat(result[0].startChar).isLessThan(result[1].startChar)
    }

    @Test
    fun `should fallback to default chapter when no chapter title`() {
        val content = "这是没有章节标题的正文"

        val result = useCase(bookId = "book-2", content = content)

        assertThat(result).hasSize(1)
        assertThat(result[0].title).isEqualTo("开始")
        assertThat(result[0].startChar).isEqualTo(0)
        assertThat(result[0].endChar).isEqualTo(content.length)
    }
}
