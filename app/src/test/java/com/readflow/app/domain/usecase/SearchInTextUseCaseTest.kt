package com.readflow.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SearchInTextUseCaseTest {
    private val useCase = SearchInTextUseCase()

    @Test
    fun `should return empty when query is blank`() {
        val result = useCase(content = "abc", query = " ")

        assertThat(result.matches).isEmpty()
        assertThat(result.truncated).isFalse()
    }

    @Test
    fun `should search case insensitively and keep ascending order`() {
        val result = useCase(content = "Chapter one CHAPTER two chapter three", query = "chapter")

        assertThat(result.matches).hasSize(3)
        assertThat(result.matches.map { it.position }).containsExactly(0, 12, 24).inOrder()
    }

    @Test
    fun `should apply result limit and mark truncated`() {
        val result = useCase(content = "a a a a a", query = "a", limit = 3)

        assertThat(result.matches).hasSize(3)
        assertThat(result.truncated).isTrue()
    }

    @Test
    fun `should trim snippet around edges`() {
        val result = useCase(content = "hello world", query = "hello", contextWindow = 2)

        assertThat(result.matches).hasSize(1)
        assertThat(result.matches.first().snippet).isEqualTo("hello w…")
    }
}
