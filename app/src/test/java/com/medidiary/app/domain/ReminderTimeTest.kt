package com.medidiary.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ReminderTimeTest {
    private val dhaka = TimeZone.getTimeZone("Asia/Dhaka")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int, tz: TimeZone = dhaka) =
        Calendar.getInstance(tz).apply { clear(); set(y, m - 1, d, h, min) }.timeInMillis

    @Test fun laterTodayWhenStillAhead() =
        assertEquals(at(2026, 11, 24, 21, 0), ReminderTime.nextTrigger(at(2026, 11, 24, 9, 30), 21, 0, dhaka))

    @Test fun tomorrowWhenAlreadyPassed() =
        assertEquals(at(2026, 11, 25, 21, 0), ReminderTime.nextTrigger(at(2026, 11, 24, 21, 30), 21, 0, dhaka))

    @Test fun exactlyNowSchedulesTomorrow() =
        assertEquals(at(2026, 11, 25, 21, 0), ReminderTime.nextTrigger(at(2026, 11, 24, 21, 0), 21, 0, dhaka))

    @Test fun crossesMonthEnd() =
        assertEquals(at(2026, 12, 1, 8, 15), ReminderTime.nextTrigger(at(2026, 11, 30, 23, 0), 8, 15, dhaka))

    @Test fun keepsWallClockTimeAcrossDst() {
        val ny = TimeZone.getTimeZone("America/New_York") // DST ends 1 Nov 2026
        val next = ReminderTime.nextTrigger(at(2026, 10, 31, 22, 0, ny), 21, 0, ny)
        assertEquals(at(2026, 11, 1, 21, 0, ny), next)
    }

    @Test fun labelUsesAsciiDigitsOnBanglaPhones() {
        val saved = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("bn-BD"))
            assertEquals("9:00 PM", ReminderTime.label(21, 0))
        } finally {
            java.util.Locale.setDefault(saved)
        }
    }

    @Test fun labels() {
        assertEquals("9:00 PM", ReminderTime.label(21, 0))
        assertEquals("12:05 AM", ReminderTime.label(0, 5))
        assertEquals("12:30 PM", ReminderTime.label(12, 30))
    }
}
