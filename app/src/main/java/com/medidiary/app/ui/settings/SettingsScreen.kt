package com.medidiary.app.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medidiary.app.reminder.ReminderScheduler
import com.medidiary.app.ai.ModelKind
import com.medidiary.app.data.AppSettings
import com.medidiary.app.ui.components.NeuButton
import com.medidiary.app.ui.theme.Mocha
import java.io.File

/**
 * SETTINGS window from the wireframe: USER DETAILS · LOAD MODEL · DATA RETENTION DAYS
 * (30 / 60 / CUSTOM) · EXPORT TO (CSV / PDF), plus collapsible Advanced, Privacy and About.
 */
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val models by vm.models.collectAsStateWithLifecycle()
    val exportRange by vm.exportRange.collectAsStateWithLifecycle()
    val exporting by vm.exporting.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showModels by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.refreshModels() }

    // Notification permission for the daily reminder: re-checked every time the screen resumes
    // (the user may have changed it in system settings).
    var notificationsAllowed by remember { mutableStateOf(ReminderScheduler.notificationsAllowed(context)) }
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = ReminderScheduler.notificationsAllowed(context)
        onPauseOrDispose { }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsAllowed = ReminderScheduler.notificationsAllowed(context)
        if (!notificationsAllowed) context.startActivity(appNotificationSettings(context))
    }

    SettingsContent(
        s = s,
        ui = SettingsUi(
            message, models.whisper != null, models.gemma != null, exportRange, exporting, notificationsAllowed,
            familySharingAvailable = vm.familySync.supported,
        ),
        actions = remember(vm) {
            object : SettingsActions {
                override fun update(transform: (AppSettings) -> AppSettings) = vm.update(transform)
                override fun setRetention(days: Int) = vm.setRetention(days)
                override fun openModels() { showModels = true }
                override fun setExportRange(range: ExportRange) { vm.exportRange.value = range }
                override fun export(pdf: Boolean) { vm.export(pdf) { context.startActivity(it) } }
                override fun deleteAllData() { vm.deleteAllData() }
                override fun requestNotificationPermission() {
                    if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else context.startActivity(appNotificationSettings(context))
                }
            }
        },
        onBack = onBack,
        familySharing = { if (vm.familySync.supported) FamilySharingCard(vm.familySync, defaultName = s.patientName) },
    )
    if (showModels) ModelsDialog(vm, models.whisper, models.gemma) { showModels = false }
}

/** Opens MediDiary's page in the system notification settings (when the permission prompt can't be shown). */
private fun appNotificationSettings(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

@Composable
private fun ModelsDialog(vm: SettingsViewModel, whisper: File?, gemma: File?, onDismiss: () -> Unit) {
    val importing by vm.importing.collectAsStateWithLifecycle()
    var pendingKind by remember { mutableStateOf<ModelKind?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val k = pendingKind
        if (uri != null && k != null) vm.importModel(k, uri)
        pendingKind = null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Mocha.Base,
        title = { Text("On-device AI models", color = Mocha.Mauve) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ModelKind.entries.forEach { kind ->
                    val file = if (kind == ModelKind.WHISPER) whisper else gemma
                    Text(kind.label, color = Mocha.Text, style = MaterialTheme.typography.titleSmall)
                    Text(
                        file?.let { "Installed · %.0f MB".format(it.length() / 1_048_576.0) } ?: "Not installed — expects ${kind.fileName}",
                        color = if (file != null) Mocha.Green else Mocha.Yellow, style = MaterialTheme.typography.bodySmall,
                    )
                    if (importing?.first == kind) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Mocha.Mauve, trackColor = Mocha.Surface0)
                        Text("Copying… %.0f MB".format((importing?.second ?: 0L) / 1_048_576.0), color = Mocha.Subtext0,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NeuButton({ pendingKind = kind; picker.launch(arrayOf("*/*")) }, enabled = importing == null,
                            accent = Mocha.Blue, alwaysAccent = true, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                            Icon(Icons.Filled.Description, null); Text(if (file == null) "Import…" else "Replace…")
                        }
                        if (file != null) TextButton(onClick = { vm.deleteModel(kind) }) { Text("Remove", color = Mocha.Red) }
                    }
                }
                HorizontalDivider(color = Mocha.Surface1)
                Text("Developers: push over USB instead (then Re-scan):", color = Mocha.Subtext0, style = MaterialTheme.typography.bodySmall)
                SelectionContainer {
                    Text(
                        "adb push medidiary-whisper.bin ${vm.externalModelDir}/\nadb push medidiary-gemma.gguf ${vm.externalModelDir}/",
                        fontFamily = FontFamily.Monospace, color = Mocha.Rosewater, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.widthIn(max = 600.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = Mocha.Blue) } },
        dismissButton = { TextButton(onClick = vm::refreshModels) { Text("Re-scan", color = Mocha.Subtext0) } },
    )
}
