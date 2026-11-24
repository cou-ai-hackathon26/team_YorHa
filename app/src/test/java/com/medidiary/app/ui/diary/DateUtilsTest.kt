package com.medidiary.app.ui.diary

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class DateUtilsTest {
    private lateinit var savedLocale: Locale
    private lateinit var savedTz: TimeZone

    @Before fun setUp() {
        savedLocale = Locale.getDefault(); savedTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Dhaka"))
    }
    @After fun tearDown() { Locale.setDefault(savedLocale); TimeZone.setDefault(savedTz) }

    private fun millis(y: Int, m: Int, d: Int, h: Int = 0) =
        Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, 0) }.timeInMillis

    @Test fun startAndEndOfDay() {
        val t = millis(2026, 11, 24, 15)
        assertEquals(millis(2026, 11, 24), DateUtils.startOfDay(t))
        assertEquals(millis(2026, 11, 25) - 1, DateUtils.endOfDay(t))
    }

    @Test fun lastSevenDaysIncludesToday() {
        assertEquals(millis(2026, 11, 18), DateUtils.daysAgo(7, millis(2026, 11, 24, 9)))
    }

    @Test fun monthGridSundayFirst() {
        Locale.setDefault(Locale.US) // week starts Sunday
        val grid = DateUtils.monthGrid(DateUtils.startOfMonth(millis(2026, 11, 24)))
        // 1 Nov 2026 is a Sunday → no leading blanks; 30 days → 35 cells.
        assertEquals(millis(2026, 11, 1), grid[0])
        assertEquals(35, grid.size)
        assertEquals(30, grid.count { it != null })
        assertEquals("Su", DateUtils.weekdayInitials()[0])
    }

    @Test fun monthGridMondayFirstPadsCorrectly() {
        Locale.setDefault(Locale.UK) // week starts Monday
        val grid = DateUtils.monthGrid(DateUtils.startOfMonth(millis(2026, 11, 24)))
        assertEquals(6, grid.takeWhile { it == null }.size) // Sunday is the 7th column
        assertEquals(millis(2026, 11, 1), grid[6])
        assertTrue(grid.size % 7 == 0)
        assertNull(grid.last())
    }
}
