package com.readflow.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AutoPageProgressPolicyTest {
    @Test
    fun `should jump to next paragraph break first`() {
        val content = "第一段文字\n\n第二段文字\n\n第三段"

        val next = AutoPageProgressPolicy.nextScrollPosition(content = content, currentPosition = 0)

        assertThat(next).isEqualTo(content.indexOf("\n\n") + 2)
    }

    @Test
    fun `should fallback to line break when paragraph break is absent`() {
        val content = "第一行\n第二行\n第三行"

        val next = AutoPageProgressPolicy.nextScrollPosition(content = content, currentPosition = 0)

        assertThat(next).isEqualTo(content.indexOf('\n') + 1)
    }

    @Test
    fun `should fallback to sentence boundary before fixed step`() {
        val content = "这是一个很长的句子但是中间没有换行。下一句接着来"

        val next = AutoPageProgressPolicy.nextScrollPosition(content = content, currentPosition = 0)

        assertThat(next).isEqualTo(content.indexOf('。') + 1)
    }

    @Test
    fun `should use fixed step when no natural break found`() {
        val content = "a".repeat(1000)

        val next = AutoPageProgressPolicy.nextScrollPosition(content = content, currentPosition = 0)

        assertThat(next).isEqualTo(260)
    }

    @Test
    fun `should stay at end when already at last position`() {
        val content = "结尾"

        val next = AutoPageProgressPolicy.nextScrollPosition(
            content = content,
            currentPosition = content.lastIndex,
        )

        assertThat(next).isEqualTo(content.lastIndex)
    }
}
