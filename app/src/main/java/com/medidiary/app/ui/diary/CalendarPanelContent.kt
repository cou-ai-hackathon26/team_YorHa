package com.medidiary.app.ui.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.medidiary.app.data.db.DiaryRecord
import com.medidiary.app.domain.MatchStatus
import com.medidiary.app.domain.RecordType
import com.medidiary.app.ui.components.DiaryRecordEditDialog
import com.medidiary.app.ui.components.NeuSegmented
import com.medidiary.app.ui.components.SectionLabel
import com.medidiary.app.ui.theme.MedicineAccent
import com.medidiary.app.ui.theme.Mocha
import com.medidiary.app.ui.theme.SymptomAccent
import com.medidiary.app.ui.theme.neumorphic
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.map

/** Snapshot the calendar panel draws (stateless → previewable). */
data class CalendarUi(
    val month: Long,
    val marks: Map<Long, DayMarks>,
    val selectedDay: Long?,
    val type: TypeFilter,
    val days: List<Pair<Long, List<DiaryRecord>>>,
    /** Family sharing: entries written on another phone get a "by <name>" badge. */
    val myDeviceId: String? = null,
    /** Family sharing is on: every entry shows who added it ("by Sifat" / "by you"). */
    val sharing: Boolean = false,
)

interface CalendarActions {
    fun shiftMonth(delta: Int)
    fun selectDay(day: Long?)
    fun setType(type: TypeFilter)
    fun update(record: DiaryRecord)
    fun delete(record: DiaryRecord)
}

@Composable
fun ColumnScope.CalendarPanelContent(ui: CalendarUi, actions: CalendarActions, onClose: (() -> Unit)?) {
    val month = ui.month
    val marks = ui.marks
    val selected = ui.selectedDay
    val type = ui.type
    val days = ui.days
    var editing by remember { mutableStateOf<DiaryRecord?>(null) }
    var deleting by remember { mutableStateOf<DiaryRecord?>(null) }

    val monthFmt = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val dayFmt = remember { SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()) }
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val today = DateUtils.startOfDay(System.currentTimeMillis())

    // ---- Header ---------------------------------------------------------------------------
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Calendar", style = MaterialTheme.typography.headlineSmall, color = Mocha.Blue, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f))
        if (onClose != null) IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close calendar", tint = Mocha.Subtext0) }
    }

    // ---- Month grid -----------------------------------------------------------------------
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).neumorphic(20.dp, 6.dp).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { actions.shiftMonth(-1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month", tint = Mocha.Subtext1)
            }
            Text(monthFmt.format(Date(month)), Modifier.weight(1f), textAlign = TextAlign.Center,
                color = Mocha.Text, style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = { actions.shiftMonth(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month", tint = Mocha.Subtext1)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            DateUtils.weekdayInitials().forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, color = Mocha.Overlay1, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(4.dp))
        DateUtils.monthGrid(month).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (day != null) DayCell(day, marks[day], selected == day, day == today) { actions.selectDay(day) }
                    }
                }
            }
        }
    }

    // ---- Filters --------------------------------------------------------------------------
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(if (selected == null) "Dates" else dayFmt.format(Date(selected)), Modifier.weight(1f))
        if (selected != null) TextButton(onClick = { actions.selectDay(null) }) { Text("All dates", color = Mocha.Blue) }
    }
    NeuSegmented(
        options = TypeFilter.entries.map { it.name to it.label },
        selected = type.name,
        onSelect = { actions.setType(TypeFilter.valueOf(it)) },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )

    // ---- DATES list -----------------------------------------------------------------------
    LazyColumn(
        Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (days.isEmpty()) {
            item {
                Text(
                    if (selected == null) "No saved entries yet. Record a check-in to start your diary."
                    else "No entries recorded on this day. (A missed day means “no entry recorded”, not “no medicine taken”.)",
                    color = Mocha.Overlay1, style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        days.forEach { (day, records) ->
            item(key = "d$day") {
                Text(
                    if (day == today) "Today · ${dayFmt.format(Date(day))}" else dayFmt.format(Date(day)),
                    color = Mocha.Lavender, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp),
                )
            }
            items(records, key = { it.id }) { r ->
                EntryRow(
                    r, timeFmt.format(Date(r.loggedDate)), onEdit = { editing = r }, onDelete = { deleting = r },
                    author = when {
                        !ui.sharing -> null
                        r.authorDevice == null || r.authorDevice == ui.myDeviceId -> "you"
                        else -> r.authorName ?: "family"
                    },
                )
            }
        }
    }

    // ---- Footer: "necessary edits added" ----------------------------------------------------
    Row(
        Modifier.fillMaxWidth().padding(16.dp).neumorphic(14.dp, 4.dp, inset = true).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.EditNote, null, tint = Mocha.Green)
        Spacer(Modifier.width(8.dp))
        Text(
            if (ui.sharing) "Edits and deletions are saved instantly and synced to the family's phones."
            else "Edits and deletions are saved instantly on this phone.",
            color = Mocha.Subtext0, style = MaterialTheme.typography.bodySmall,
        )
    }

    editing?.let { r -> DiaryRecordEditDialog(r, onDismiss = { editing = null }, onSave = { actions.update(it); editing = null }) }
    deleting?.let { r ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = Mocha.Base,
            title = { Text("Delete entry?", color = Mocha.Text) },
            text = { Text("“${r.originalName}” will be deleted permanently (from every phone, if the diary is shared).", color = Mocha.Subtext1) },
            confirmButton = { TextButton(onClick = { actions.delete(r); deleting = null }) { Text("Delete", color = Mocha.Red) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel", color = Mocha.Subtext0) } },
        )
    }
}

@Composable
private fun DayCell(day: Long, marks: DayMarks?, isSelected: Boolean, isToday: Boolean, onClick: () -> Unit) {
    val dayNum = java.util.Calendar.getInstance().apply { timeInMillis = day }.get(java.util.Calendar.DAY_OF_MONTH)
    Column(
        Modifier
            .size(38.dp)
            .then(if (isSelected) Modifier.neumorphic(19.dp, 3.dp, inset = true) else Modifier)
            .then(if (isToday && !isSelected) Modifier.border(1.dp, Mocha.Mauve, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "$dayNum",
            color = when { isSelected -> Mocha.Blue; marks != null -> Mocha.Text; else -> Mocha.Overlay1 },
            fontWeight = if (marks != null || isSelected) FontWeight.SemiBold else FontWeight.Normal,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(5.dp)) {
            if (marks?.medicine == true) Box(Modifier.size(4.dp).background(MedicineAccent, CircleShape))
            if (marks?.symptom == true) Box(Modifier.size(4.dp).background(SymptomAccent, CircleShape))
        }
    }
}

@Composable
private fun EntryRow(r: DiaryRecord, time: String, onEdit: () -> Unit, onDelete: () -> Unit, author: String? = null) {
    val med = r.type == RecordType.MEDICINE
    val accent = if (med) MedicineAccent else SymptomAccent
    Row(Modifier.fillMaxWidth().neumorphic(16.dp, 5.dp).padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(accent, CircleShape))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(r.originalName, color = accent, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            val details = listOfNotNull(
                r.timePhrase,
                if (med) when {
                    r.genericSuggestion == null -> "Unmatched brand"
                    r.genericMatchStatus == MatchStatus.FUZZY -> "${r.genericSuggestion}?"
                    else -> r.genericSuggestion
                } else null,
                r.note,
            ).joinToString(" · ")
            if (details.isNotEmpty()) Text(details, color = Mocha.Subtext1, style = MaterialTheme.typography.bodySmall)
            Text(
                if (author != null) "Logged $time · by $author" else "Logged $time",
                color = if (author != null) Mocha.Teal else Mocha.Overlay0, style = MaterialTheme.typography.labelSmall,
            )
        }
        IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Edit", tint = Mocha.Subtext0) }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete", tint = Mocha.Red) }
    }
}

enum class TypeFilter(val label: String) { ALL("All"), MEDICINES("Medicines"), SYMPTOMS("Symptoms") }

/** Which kinds of entry a calendar day has (drives the dots under each date). */
data class DayMarks(val medicine: Boolean, val symptom: Boolean)
