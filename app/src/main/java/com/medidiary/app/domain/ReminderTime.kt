package com.medidiary.app.domain

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Pure scheduling maths for the daily check-in reminder (no Android dependencies, unit-tested). */
object ReminderTime {
    /** Next time [hour]:[minute] occurs strictly after [now] in [tz] (today if still ahead, else tomorrow). DST-safe. */
    fun nextTrigger(now: Long, hour: Int, minute: Int, tz: TimeZone = TimeZone.getDefault()): Long {
        val cal = Calendar.getInstance(tz).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    /** "9:00 PM" style label (the app UI is English; the notification itself is bilingual). */
    fun label(hour: Int, minute: Int): String {
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        // Locale.US: ASCII digits like the rest of the UI (a Bangla-locale phone would otherwise print "৯:০০").
        return String.format(Locale.US, "%d:%02d %s", h12, minute, if (hour < 12) "AM" else "PM")
    }

    /** Start of the local day containing [now] — used to check "already logged today". */
    fun startOfDay(now: Long, tz: TimeZone = TimeZone.getDefault()): Long = Calendar.getInstance(tz).apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Notification copy (bilingual, contains no health data — safe on a lock screen). */
    const val TITLE = "আজকের চেক-ইন · Daily check-in"
    const val BODY = "আজ কোন ওষুধ খেয়েছেন, কেমন লাগছে? Tap to record today's medicines and symptoms."
}
