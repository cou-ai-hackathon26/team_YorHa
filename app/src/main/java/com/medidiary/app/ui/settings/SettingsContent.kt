package com.medidiary.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import com.medidiary.app.domain.ReminderTime
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medidiary.app.data.AppSettings
import com.medidiary.app.ui.components.BannerKind
import com.medidiary.app.ui.components.MessageBanner
import com.medidiary.app.ui.components.NeuButton
import com.medidiary.app.ui.components.NeuCard
import com.medidiary.app.ui.components.NeuIconButton
import com.medidiary.app.ui.components.NeuSegmented
import com.medidiary.app.ui.components.NeuTextField
import com.medidiary.app.ui.components.SectionLabel
import com.medidiary.app.ui.theme.Mocha

/** Snapshot the settings window draws (stateless → previewable). */
data class SettingsUi(
    val message: String?,
    val whisperReady: Boolean,
    val gemmaReady: Boolean,
    val exportRange: ExportRange,
    val exporting: Boolean,
    /** False when the OS blocks MediDiary's notifications (Android 13+ permission, or switched off). */
    val notificationsAllowed: Boolean = true,
    /** Family build: the app may sync verified entries (encrypted) when the user turns sharing on. */
    val familySharingAvailable: Boolean = false,
)

interface SettingsActions {
    fun update(transform: (AppSettings) -> AppSettings)
    fun setRetention(days: Int)
    fun openModels()
    fun setExportRange(range: ExportRange)
    fun export(pdf: Boolean)
    fun deleteAllData()
    /** Ask the OS to allow reminder notifications (Android 13+ permission prompt / app notification settings). */
    fun requestNotificationPermission() {}
}

@Composable
fun SettingsContent(
    s: AppSettings,
    ui: SettingsUi,
    actions: SettingsActions,
    onBack: () -> Unit,
    /** Task 1: the FAMILY SHARING card (stateful, so it is passed in; empty in previews / offline build). */
    familySharing: @Composable () -> Unit = {},
) {
    val message = ui.message
    val exportRange = ui.exportRange
    val exporting = ui.exporting
    var name by remember { mutableStateOf(s.patientName) }
    var showCustom by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Mocha.Base)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeuIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, tint = Mocha.Blue)
            Spacer(Modifier.width(14.dp))
            Text("Settings", style = MaterialTheme.typography.headlineSmall, color = Mocha.Text, fontWeight = FontWeight.SemiBold)
        }
        message?.let { MessageBanner(it, BannerKind.INFO) }

        // ---- USER DETAILS ---------------------------------------------------------------------
        NeuCard(Modifier.fillMaxWidth()) {
            SectionLabel("User details")
            Text("Name shown on PDF exports (optional)", color = Mocha.Subtext0, style = MaterialTheme.typography.bodySmall)
            NeuTextField(
                value = name,
                onValueChange = { name = it; actions.update { st -> st.copy(patientName = it) } },
                singleLine = true,
                placeholder = "Patient name",
            )
        }

        familySharing()

        // ---- LOAD MODEL -----------------------------------------------------------------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeuButton(onClick = actions::openModels, accent = Mocha.Mauve, alwaysAccent = true) {
                Icon(Icons.Filled.Memory, null)
                Text("Load model", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                ModelStatusLine("Whisper", ui.whisperReady)
                ModelStatusLine("Gemma", ui.gemmaReady)
            }
        }

        // ---- DAILY REMINDER -------------------------------------------------------------------
        NeuCard(Modifier.fillMaxWidth()) {
            SectionLabel("Daily reminder")
            ToggleRow("Remind me to check in every day", s.reminderEnabled) { v -> actions.update { it.copy(reminderEnabled = v) } }
            AnimatedVisibility(s.reminderEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Time", color = Mocha.Text, modifier = Modifier.weight(1f))
                        NeuButton({ showTimePicker = true }, accent = Mocha.Blue, alwaysAccent = true) {
                            Icon(Icons.Filled.Alarm, null)
                            Text(ReminderTime.label(s.reminderHour, s.reminderMinute), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    ToggleRow("Skip if I already checked in today", s.reminderSkipIfLogged) { v ->
                        actions.update { it.copy(reminderSkipIfLogged = v) }
                    }
                    if (!ui.notificationsAllowed) {
                        MessageBanner("Notifications are blocked, so the reminder can't appear.", BannerKind.ERROR)
                        NeuButton(actions::requestNotificationPermission, accent = Mocha.Yellow, alwaysAccent = true) {
                            Text("Allow notifications")
                        }
                    }
                    Text(
                        "A notification asks “আজ কোন ওষুধ খেয়েছেন, কেমন লাগছে?” at this time each day. Tapping it opens " +
                            "today's check-in. It never shows your health data.",
                        color = Mocha.Overlay1, style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        // ---- DATA RETENTION DAYS --------------------------------------------------------------
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionLabel("Data retention days")
            val custom = s.retentionDays != 30 && s.retentionDays != 60
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    NeuButton({ actions.setRetention(30) }, Modifier.fillMaxWidth(), selected = s.retentionDays == 30) { Text("30 days") }
                    NeuButton({ actions.setRetention(60) }, Modifier.fillMaxWidth(), selected = s.retentionDays == 60) { Text("60 days") }
                }
                NeuButton({ showCustom = true }, Modifier.weight(1f), selected = custom) {
                    Text(if (custom) "Custom · ${s.retentionDays} d" else "Custom")
                }
            }
            Text("Entries older than this are deleted automatically from this phone.", color = Mocha.Overlay1, style = MaterialTheme.typography.bodySmall)
        }

        // ---- EXPORT TO ------------------------------------------------------------------------
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionLabel("Export to")
            NeuSegmented(
                options = ExportRange.entries.map { it.name to it.label },
                selected = exportRange.name,
                onSelect = { actions.setExportRange(ExportRange.valueOf(it)) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                NeuButton({ actions.export(pdf = false) }, Modifier.weight(1f), enabled = !exporting,
                    accent = Mocha.Green, alwaysAccent = true) {
                    Icon(Icons.Filled.TableChart, null); Text("CSV")
                }
                NeuButton({ actions.export(pdf = true) }, Modifier.weight(1f), enabled = !exporting,
                    accent = Mocha.Peach, alwaysAccent = true) {
                    if (exporting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Mocha.Peach)
                    else Icon(Icons.Filled.PictureAsPdf, null)
                    Text("PDF")
                }
            }
            Text("Exports are labelled “Patient-Reported & AI-Assisted, Not Medically Verified”. Nothing is shared unless you choose an app.",
                color = Mocha.Overlay1, style = MaterialTheme.typography.bodySmall)
        }

        // ---- ADVANCED (collapsible) -----------------------------------------------------------
        NeuCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Advanced", Modifier.weight(1f), color = Mocha.Overlay2)
                TextButton(onClick = { showAdvanced = !showAdvanced }) {
                    Icon(if (showAdvanced) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = Mocha.Overlay2)
                }
            }
            AnimatedVisibility(showAdvanced) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToggleRow("Use GGUF chat template (Gemma turns)", s.useChatTemplate) { v -> actions.update { it.copy(useChatTemplate = v) } }
                    Text("Keep ON for the MediDiary fine-tune (trained with the Gemma 3 chat template).",
                        color = Mocha.Overlay1, style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Context", color = Mocha.Text, modifier = Modifier.padding(end = 12.dp))
                        NeuSegmented(
                            options = listOf("512", "1024", "2048").map { it to it },
                            selected = s.llmContextSize.toString(),
                            onSelect = { v -> actions.update { it.copy(llmContextSize = v.toInt()) } },
                        )
                    }
                    ToggleRow("Allow demo (mock) AI when models are missing", s.allowMockEngines) { v -> actions.update { it.copy(allowMockEngines = v) } }
                }
            }
        }

        // ---- PRIVACY + ABOUT ------------------------------------------------------------------
        NeuCard(Modifier.fillMaxWidth()) {
            SectionLabel("Privacy", color = Mocha.Green)
            Text(
                if (ui.familySharingAvailable) {
                    "Speech recognition and AI extraction run only on this phone. Recordings and transcripts never leave it, and raw " +
                        "audio is deleted right after transcription. If you turn on Family sharing, only the entries you confirm are " +
                        "sent, end-to-end encrypted, to the phones in your family diary."
                } else {
                    "MediDiary has no internet permission. Recordings, transcripts and diary entries never leave this phone " +
                        "unless you export and share them. Raw audio is deleted right after transcription."
                },
                color = Mocha.Subtext1, style = MaterialTheme.typography.bodySmall,
            )
            NeuButton({ confirmWipe = true }, accent = Mocha.Red, alwaysAccent = true) { Text("Delete all diary data") }
            HorizontalDivider(color = Mocha.Surface1)
            SectionLabel("About", color = Mocha.Overlay2)
            Text(
                "Patient-reported & AI-assisted, not medically verified. MediDiary gives no diagnoses, dosages or interaction advice.\n\n" +
                    "Speech: OpenAI Whisper via whisper.cpp (MIT); Bengali model SayedShaun/bengali-whisper-medium " +
                    "(Apache-2.0, fine-tuned from openai/whisper-medium). Extraction: Gemma 3 1B via llama.cpp (MIT); Gemma is " +
                    "provided under and subject to the Gemma Terms of Use. Brand/generic reference: subset of the Bangladesh DGDA " +
                    "registered-drug list (DGHS Bangladesh Core FHIR IG); suggestions only. Theme: Catppuccin Mocha (MIT).",
                color = Mocha.Overlay2, style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showTimePicker) {
        ReminderTimeDialog(s.reminderHour, s.reminderMinute, onDismiss = { showTimePicker = false }) { h, m ->
            actions.update { it.copy(reminderHour = h, reminderMinute = m, reminderEnabled = true) }
            showTimePicker = false
        }
    }
    if (showCustom) CustomRetentionDialog(s.retentionDays, onDismiss = { showCustom = false }) { actions.setRetention(it); showCustom = false }
    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            containerColor = Mocha.Base,
            title = { Text("Delete everything?", color = Mocha.Text) },
            text = { Text("All diary entries on this phone will be permanently deleted. This cannot be undone.", color = Mocha.Subtext1) },
            confirmButton = { TextButton(onClick = { actions.deleteAllData(); confirmWipe = false }) { Text("Delete", color = Mocha.Red) } },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancel", color = Mocha.Subtext0) } },
        )
    }
}

@Composable
private fun ModelStatusLine(label: String, ok: Boolean) {
    Text(
        (if (ok) "● " else "○ ") + label + if (ok) " ready" else " missing",
        color = if (ok) Mocha.Green else Mocha.Yellow,
        style = MaterialTheme.typography.labelMedium,
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = Mocha.Text, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Mocha.Crust, checkedTrackColor = Mocha.Blue,
                uncheckedThumbColor = Mocha.Overlay1, uncheckedTrackColor = Mocha.Surface0, uncheckedBorderColor = Mocha.Surface2,
            ),
        )
    }
}

@Composable
private fun CustomRetentionDialog(current: Int, onDismiss: () -> Unit, onSet: (Int) -> Unit) {
    var text by remember { mutableStateOf(if (current != 30 && current != 60) current.toString() else "") }
    val days = text.trim().toIntOrNull()
    val valid = days != null && days in 1..365
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Mocha.Base,
        title = { Text("Custom retention", color = Mocha.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Keep entries for how many days? (1–365)", color = Mocha.Subtext1)
                NeuTextField(text, { v -> text = v.filter { it.isDigit() }.take(3) }, singleLine = true, placeholder = "e.g. 90")
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSet(days!!) }) { Text("Set", color = Mocha.Blue) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Mocha.Subtext0) } },
    )
}

/** Export ranges offered under "EXPORT TO". days = null means every saved entry. */
enum class ExportRange(val label: String, val days: Int?) { WEEK("Last 7 days", 7), MONTH("Last 30 days", 30), ALL("All", null) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(hour: Int, minute: Int, onDismiss: () -> Unit, onSet: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Mocha.Base,
        title = { Text("Reminder time", color = Mocha.Text) },
        text = {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = Mocha.Mantle,
                    selectorColor = Mocha.Blue,
                    timeSelectorSelectedContainerColor = Mocha.Surface0,
                    timeSelectorUnselectedContainerColor = Mocha.Mantle,
                    timeSelectorSelectedContentColor = Mocha.Blue,
                    timeSelectorUnselectedContentColor = Mocha.Subtext0,
                    periodSelectorSelectedContainerColor = Mocha.Surface0,
                    periodSelectorSelectedContentColor = Mocha.Blue,
                    periodSelectorUnselectedContentColor = Mocha.Subtext0,
                    clockDialSelectedContentColor = Mocha.Crust,
                    clockDialUnselectedContentColor = Mocha.Text,
                ),
            )
        },
        confirmButton = { TextButton(onClick = { onSet(state.hour, state.minute) }) { Text("Set", color = Mocha.Blue) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Mocha.Subtext0) } },
    )
}
