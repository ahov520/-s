package com.readflow.app.notifications

import com.google.common.truth.Truth.assertThat
import java.util.Calendar
import java.util.TimeZone
import org.junit.Test

class ReadingReminderSchedulerTest {
    private val zone: TimeZone = TimeZone.getTimeZone("UTC")

    @Test
    fun calculateNextTriggerMillis_shouldUseTodayWhenTargetInFuture() {
        val now = utcMillis(2026, Calendar.FEBRUARY, 21, 8, 0, 0)

        val trigger = ReadingReminderScheduler.calculateNextTriggerMillis(
            nowMillis = now,
            hour = 21,
            minute = 0,
            timeZone = zone,
        )

        assertThat(trigger).isEqualTo(utcMillis(2026, Calendar.FEBRUARY, 21, 21, 0, 0))
    }

    @Test
    fun calculateNextTriggerMillis_shouldRollToTomorrowWhenTargetPassed() {
        val now = utcMillis(2026, Calendar.FEBRUARY, 21, 22, 15, 0)

        val trigger = ReadingReminderScheduler.calculateNextTriggerMillis(
            nowMillis = now,
            hour = 21,
            minute = 0,
            timeZone = zone,
        )

        assertThat(trigger).isEqualTo(utcMillis(2026, Calendar.FEBRUARY, 22, 21, 0, 0))
    }

    private fun utcMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
    ): Long {
        return Calendar.getInstance(zone).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, second)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
