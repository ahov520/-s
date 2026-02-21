package com.readflow.app.domain.usecase

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReadStatsCalculatorTest {
    @Test
    fun `should initialize streak on first reading day`() {
        val initial = ReadStatsSnapshot(dailyReadSeconds = 0, lastReadDate = "", streakDays = 0)

        val updated = ReadStatsCalculator.applyDelta(
            current = initial,
            secondsDelta = 30,
            today = "2026-02-21",
        )

        assertThat(updated.dailyReadSeconds).isEqualTo(30)
        assertThat(updated.lastReadDate).isEqualTo("2026-02-21")
        assertThat(updated.streakDays).isEqualTo(1)
    }

    @Test
    fun `should accumulate on same day`() {
        val current = ReadStatsSnapshot(dailyReadSeconds = 120, lastReadDate = "2026-02-21", streakDays = 3)

        val updated = ReadStatsCalculator.applyDelta(
            current = current,
            secondsDelta = 45,
            today = "2026-02-21",
        )

        assertThat(updated.dailyReadSeconds).isEqualTo(165)
        assertThat(updated.lastReadDate).isEqualTo("2026-02-21")
        assertThat(updated.streakDays).isEqualTo(3)
    }

    @Test
    fun `should increase streak for consecutive day`() {
        val current = ReadStatsSnapshot(dailyReadSeconds = 300, lastReadDate = "2026-02-20", streakDays = 4)

        val updated = ReadStatsCalculator.applyDelta(
            current = current,
            secondsDelta = 10,
            today = "2026-02-21",
        )

        assertThat(updated.dailyReadSeconds).isEqualTo(10)
        assertThat(updated.streakDays).isEqualTo(5)
    }

    @Test
    fun `should reset streak after date gap`() {
        val current = ReadStatsSnapshot(dailyReadSeconds = 300, lastReadDate = "2026-02-18", streakDays = 6)

        val updated = ReadStatsCalculator.applyDelta(
            current = current,
            secondsDelta = 10,
            today = "2026-02-21",
        )

        assertThat(updated.dailyReadSeconds).isEqualTo(10)
        assertThat(updated.streakDays).isEqualTo(1)
    }

    @Test
    fun `should reset streak when previous date is invalid`() {
        val current = ReadStatsSnapshot(dailyReadSeconds = 100, lastReadDate = "bad-date", streakDays = 2)

        val updated = ReadStatsCalculator.applyDelta(
            current = current,
            secondsDelta = 15,
            today = "2026-02-21",
        )

        assertThat(updated.dailyReadSeconds).isEqualTo(15)
        assertThat(updated.streakDays).isEqualTo(1)
    }
}
