package com.readflow.app.domain.usecase

import java.time.LocalDate

data class ReadStatsSnapshot(
    val dailyReadSeconds: Int,
    val lastReadDate: String,
    val streakDays: Int,
)

object ReadStatsCalculator {
    fun applyDelta(
        current: ReadStatsSnapshot,
        secondsDelta: Int,
        today: String,
    ): ReadStatsSnapshot {
        if (today.isBlank()) return current
        val delta = secondsDelta.coerceAtLeast(0)
        val lastDate = current.lastReadDate
        val safeStreak = current.streakDays.coerceAtLeast(0)

        if (lastDate.isBlank()) {
            return ReadStatsSnapshot(
                dailyReadSeconds = delta,
                lastReadDate = today,
                streakDays = 1,
            )
        }

        if (lastDate == today) {
            return ReadStatsSnapshot(
                dailyReadSeconds = current.dailyReadSeconds.coerceAtLeast(0) + delta,
                lastReadDate = today,
                streakDays = safeStreak.coerceAtLeast(1),
            )
        }

        val nextStreak = if (isYesterday(lastDate, today)) {
            safeStreak.coerceAtLeast(1) + 1
        } else {
            1
        }

        return ReadStatsSnapshot(
            dailyReadSeconds = delta,
            lastReadDate = today,
            streakDays = nextStreak,
        )
    }

    fun resetDaily(
        current: ReadStatsSnapshot,
        today: String,
    ): ReadStatsSnapshot {
        if (today.isBlank()) return current
        return ReadStatsSnapshot(
            dailyReadSeconds = 0,
            lastReadDate = today,
            streakDays = current.streakDays.coerceAtLeast(1),
        )
    }

    private fun isYesterday(previous: String, today: String): Boolean {
        return runCatching {
            LocalDate.parse(previous).plusDays(1) == LocalDate.parse(today)
        }.getOrDefault(false)
    }
}
