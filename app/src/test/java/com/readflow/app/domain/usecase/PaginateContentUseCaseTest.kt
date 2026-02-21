package com.readflow.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PaginateContentUseCaseTest {
    private val useCase = PaginateContentUseCase()

    @Test
    fun `should split long content into multiple pages`() {
        val content = buildString {
            repeat(400) {
                append("这一段用于分页测试。\n")
            }
        }

        val result = useCase(
            content = content,
            layout = PaginationLayout(
                width = 1080,
                height = 1600,
                fontSize = 18,
                lineHeight = 1.6f,
                paddingHorizontal = 48,
            ),
        )

        assertThat(result.totalPages).isGreaterThan(1)
        assertThat(result.pages.joinToString(separator = "")).isEqualTo(content)
    }

    @Test
    fun `should map page and position correctly`() {
        val pages = listOf("abc", "defg", "hij")

        assertThat(useCase.pageIndexForPosition(0, pages)).isEqualTo(0)
        assertThat(useCase.pageIndexForPosition(3, pages)).isEqualTo(1)
        assertThat(useCase.pageIndexForPosition(8, pages)).isEqualTo(2)
        assertThat(useCase.positionForPageIndex(2, pages)).isEqualTo(7)
    }
}
