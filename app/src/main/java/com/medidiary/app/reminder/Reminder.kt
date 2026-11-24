package com.medidiary.app.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.medidiary.app.MainActivity
import com.medidiary.app.MediDiaryApp
import com.medidiary.app.R
import com.medidiary.app.data.AppSettings
import com.medidiary.app.domain.ReminderTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Daily check-in reminder (proposal §3 step 1). One inexact, Doze-friendly alarm per day via
 * AlarmManager.setAndAllowWhileIdle — needs no "exact alarm" permission and may arrive a few
 * minutes late, which is fine for a diary prompt. Each firing schedules the next one.
 * Fully offline: no push service, no network.
 */
object ReminderScheduler {
    const val CHANNEL_ID = "daily_checkin"
    private const val REQUEST_CODE = 2101
    private const val NOTIFICATION_ID = 2101
    const val ACTION_FIRE = "com.medidiary.app.action.DAILY_REMINDER"

    fun schedule(context: Context, settings: AppSettings, now: Long = System.currentTimeMillis()) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = alarmIntent(context)
        am.cancel(pi)
        if (!settings.reminderEnabled) return
        val at = ReminderTime.nextTrigger(now, settings.reminderHour, settings.reminderMinute)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST_CODE,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Daily check-in", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A once-a-day reminder to record today's medicines and symptoms."
            },
        )
    }

    /** True when the OS will actually show our notifications (permission on 13+, and not switched off). */
    fun notificationsAllowed(context: Context): Boolean {
        val permissionOk = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permissionOk && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun show(context: Context) {
        if (!notificationsAllowed(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, REQUEST_CODE,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_checkin)
            .setContentTitle(ReminderTime.TITLE)
            .setContentText(ReminderTime.BODY)
            .setStyle(NotificationCompat.BigTextStyle().bigText(ReminderTime.BODY))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // contains no health data
            .setContentIntent(open)
            .addAction(0, "Check in now", open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n)
        } catch (_: SecurityException) {
            // Permission revoked between the check and notify — nothing to do.
        }
    }

    /** Clears the reminder once the user has checked in (opened the app / saved). */
    fun dismiss(context: Context) = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
}

/** Fires at the reminder time: show the notification (unless already logged today), then schedule tomorrow. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_FIRE) return
        val app = context.applicationContext as MediDiaryApp
        val settings = app.container.settings.settings.value
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (settings.reminderEnabled) {
                    val now = System.currentTimeMillis()
                    val loggedToday = settings.reminderSkipIfLogged &&
                        app.container.diaryRepository.between(ReminderTime.startOfDay(now), now).isNotEmpty()
                    if (!loggedToday) ReminderScheduler.show(context)
                }
                ReminderScheduler.schedule(context, settings)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Alarms are wiped on reboot and shift with clock/timezone changes: re-arm the reminder. */
class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                val app = context.applicationContext as MediDiaryApp
                ReminderScheduler.schedule(context, app.container.settings.settings.value)
            }
        }
    }
}
