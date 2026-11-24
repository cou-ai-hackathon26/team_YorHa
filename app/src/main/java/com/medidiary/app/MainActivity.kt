package com.medidiary.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.medidiary.app.reminder.ReminderScheduler
import com.medidiary.app.sync.Invite
import com.medidiary.app.ui.MediDiaryNavHost
import com.medidiary.app.ui.theme.MediDiaryTheme

class MainActivity : ComponentActivity() {
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* Settings shows the result */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askForReminderPermissionOnce()
        handleInvite(intent)
        // Always-dark Catppuccin Mocha UI → light system-bar icons on transparent bars.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            MediDiaryTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MediDiaryNavHost()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleInvite(intent)
    }

    /** Family build: a medidiary://join… link or scanned QR opens Settings → Family sharing to confirm. */
    private fun handleInvite(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == Invite.SCHEME) {
            (application as MediDiaryApp).container.familySync.pendingInvite.value = data.toString()
        }
    }

    override fun onResume() {
        super.onResume()
        ReminderScheduler.dismiss(this) // opening the app = checking in
    }

    /** Android 13+: ask once on first launch so the daily reminder can be shown. Later: Settings → Daily reminder. */
    private fun askForReminderPermissionOnce() {
        if (Build.VERSION.SDK_INT < 33) return
        val prefs = getSharedPreferences("onboarding", Context.MODE_PRIVATE)
        val settings = (application as MediDiaryApp).container.settings.settings.value
        if (!settings.reminderEnabled || prefs.getBoolean("askedNotifications", false)) return
        prefs.edit().putBoolean("askedNotifications", true).apply()
        if (!ReminderScheduler.notificationsAllowed(this)) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
