package com.medidiary.app.ui.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medidiary.app.MediDiaryApp
import com.medidiary.app.ai.ModelKind
import com.medidiary.app.data.AppSettings
import com.medidiary.app.domain.ReminderTime
import com.medidiary.app.reminder.ReminderScheduler
import com.medidiary.app.ui.diary.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as MediDiaryApp).container
    val settings = c.settings.settings
    val familySync = c.familySync
    val models = c.modelManager.status
    val importing = MutableStateFlow<Pair<ModelKind, Long>?>(null)
    val message = MutableStateFlow<String?>(null)
    val exportRange = MutableStateFlow(ExportRange.MONTH)
    val exporting = MutableStateFlow(false)

    val externalModelDir: String get() = c.modelManager.externalDirPath()

    fun update(transform: (AppSettings) -> AppSettings) {
        val before = c.settings.settings.value
        c.settings.update(transform)
        val after = c.settings.settings.value
        if (before.reminderEnabled != after.reminderEnabled || before.reminderHour != after.reminderHour ||
            before.reminderMinute != after.reminderMinute
        ) {
            ReminderScheduler.schedule(getApplication(), after)
            if (after.reminderEnabled) {
                message.value = "Daily reminder set for ${ReminderTime.label(after.reminderHour, after.reminderMinute)}."
            }
        }
    }

    /** 30, 60 or a custom number of days (1–365). */
    fun setRetention(days: Int) {
        val d = days.coerceIn(1, 365)
        c.settings.update { it.copy(retentionDays = d) }
        viewModelScope.launch {
            val removed = c.diaryRepository.applyRetention(d)
            message.value = if (removed > 0) "Removed $removed entries older than $d days." else "Entries are kept for $d days."
        }
    }

    fun refreshModels() {
        c.modelManager.refresh()
    }

    fun importModel(kind: ModelKind, uri: Uri) = viewModelScope.launch {
        importing.value = kind to 0L
        runCatching { c.modelManager.importFromUri(kind, uri) { importing.value = kind to it } }
            .onSuccess { message.value = "${kind.label} installed." }
            .onFailure { message.value = "Import failed: ${it.message}" }
        importing.value = null
    }

    fun deleteModel(kind: ModelKind) = c.modelManager.delete(kind)

    fun export(pdf: Boolean, onReady: (Intent) -> Unit) = viewModelScope.launch {
        val range = exportRange.value
        val now = System.currentTimeMillis()
        val from = range.days?.let { DateUtils.daysAgo(it, now) } ?: 0L
        val to = DateUtils.endOfDay(now)
        exporting.value = true
        runCatching {
            val rows = c.diaryRepository.between(from, to)
            if (rows.isEmpty()) error("No saved entries in ${range.label.lowercase()}.")
            val firstDay = if (range.days == null) rows.minOf { it.loggedDate } else from
            withContext(Dispatchers.IO) {
                val name = c.settings.settings.value.patientName
                if (pdf) c.exportManager.exportPdf(rows, name, firstDay, to) else c.exportManager.exportCsv(rows, name)
            }
        }.onSuccess { onReady(c.exportManager.shareIntent(it)) }
            .onFailure { message.value = it.message ?: "Export failed" }
        exporting.value = false
    }

    fun deleteAllData() = viewModelScope.launch {
        // While sharing, the other phone would just send everything back: stop sharing first.
        val wasSharing = c.familySync.state.value.enabled
        if (wasSharing) c.familySync.leave()
        c.diaryRepository.deleteAll()
        message.value = if (wasSharing) "Stopped family sharing and deleted all diary entries from this phone."
        else "All diary entries deleted from this phone."
    }
}
