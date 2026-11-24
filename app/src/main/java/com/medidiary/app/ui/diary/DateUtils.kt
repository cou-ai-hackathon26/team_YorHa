package com.medidiary.app.ui.diary

import java.util.Calendar

/** Pure date helpers for the calendar panel and exports (JVM-testable). */
object DateUtils {
    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun startOfDay(t: Long): Long = Calendar.getInstance().apply {
        timeInMillis = t
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun endOfDay(t: Long): Long = startOfDay(t) + DAY_MS - 1

    fun startOfMonth(t: Long): Long = Calendar.getInstance().apply {
        timeInMillis = startOfDay(t); set(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis

    /** Start of day [days] - 1 days ago, so "last 7 days" includes today. */
    fun daysAgo(days: Int, now: Long = System.currentTimeMillis()): Long = Calendar.getInstance().apply {
        timeInMillis = startOfDay(now); add(Calendar.DAY_OF_YEAR, -(days - 1))
    }.timeInMillis

    /**
     * 6×7 grid of start-of-day millis for the month containing [monthStart]; null = padding cell.
     * Honours the locale's first day of week (Saturday/Sunday/Monday).
     */
    fun monthGrid(monthStart: Long): List<Long?> {
        val cal = Calendar.getInstance().apply { timeInMillis = monthStart }
        val first = cal.firstDayOfWeek
        val lead = (cal.get(Calendar.DAY_OF_WEEK) - first + 7) % 7
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val cells = MutableList<Long?>(lead) { null }
        repeat(daysInMonth) { i ->
            cells += Calendar.getInstance().apply { timeInMillis = monthStart; add(Calendar.DAY_OF_MONTH, i) }.timeInMillis
        }
        while (cells.size % 7 != 0) cells += null
        return cells
    }

    /** Weekday initials in grid order for the current locale. */
    fun weekdayInitials(): List<String> {
        val cal = Calendar.getInstance()
        val names = java.text.DateFormatSymbols.getInstance().shortWeekdays // index 1..7 = SUNDAY..SATURDAY
        return (0 until 7).map { names[((cal.firstDayOfWeek - 1 + it) % 7) + 1].take(2) }
    }
}
