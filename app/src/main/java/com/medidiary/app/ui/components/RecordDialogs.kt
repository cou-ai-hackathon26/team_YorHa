package com.medidiary.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.medidiary.app.data.db.DiaryRecord
import com.medidiary.app.domain.MatchStatus
import com.medidiary.app.domain.ProposedRecord
import com.medidiary.app.domain.RecordType
import com.medidiary.app.ui.theme.Mocha

private data class Fields(val name: String, val time: String, val note: String, val generic: String)

@Composable
private fun RecordFieldsDialog(
    title: String,
    isMedicine: Boolean,
    initial: Fields,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (Fields) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var time by remember { mutableStateOf(initial.time) }
    var note by remember { mutableStateOf(initial.note) }
    var generic by remember { mutableStateOf(initial.generic) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Mocha.Base,
        title = { Text(title, color = if (isMedicine) Mocha.Blue else Mocha.Peach) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Name (as you said it)", style = MaterialTheme.typography.labelMedium, color = Mocha.Subtext0)
                NeuTextField(name, { name = it }, singleLine = true)
                Text("When (e.g. সকালে, before lunch)", style = MaterialTheme.typography.labelMedium, color = Mocha.Subtext0)
                NeuTextField(time, { time = it }, singleLine = true)
                Text("Note (optional)", style = MaterialTheme.typography.labelMedium, color = Mocha.Subtext0)
                NeuTextField(note, { note = it })
                if (isMedicine) {
                    Text("Generic name (optional)", style = MaterialTheme.typography.labelMedium, color = Mocha.Subtext0)
                    NeuTextField(generic, { generic = it }, singleLine = true)
                    Text(
                        "Generic names come from the DGDA list or from you — never invented by AI.",
                        style = MaterialTheme.typography.bodySmall, color = Mocha.Overlay1,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(Fields(name.trim(), time.trim(), note.trim(), generic.trim())) }) {
                Text(confirmLabel, color = Mocha.Blue)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Mocha.Subtext0) } },
    )
}

/** Edit an unsaved AI proposal on the home screen. */
@Composable
fun ProposalEditDialog(initial: ProposedRecord, onDismiss: () -> Unit, onSave: (ProposedRecord) -> Unit) {
    val isMed = initial.type == RecordType.MEDICINE
    RecordFieldsDialog(
        title = if (isMed) "Medicine" else "Symptom",
        isMedicine = isMed,
        initial = Fields(initial.name, initial.timePhrase.orEmpty(), initial.note.orEmpty(), initial.genericSuggestion.orEmpty()),
        confirmLabel = "Done",
        onDismiss = onDismiss,
    ) { f ->
        val genericChanged = isMed && f.generic != initial.genericSuggestion.orEmpty()
        onSave(
            initial.copy(
                name = f.name,
                timePhrase = f.time.ifEmpty { null },
                note = f.note.ifEmpty { null },
                genericSuggestion = if (isMed) f.generic.ifEmpty { null } else null,
                genericMatchStatus = when {
                    !isMed -> MatchStatus.UNMATCHED
                    genericChanged && f.generic.isNotEmpty() -> MatchStatus.MANUAL
                    genericChanged -> MatchStatus.UNMATCHED
                    f.name != initial.name -> MatchStatus.UNMATCHED // triggers DGDA re-match
                    else -> initial.genericMatchStatus
                },
                checked = true,
            ),
        )
    }
}

/** Edit a saved diary entry from the calendar panel. */
@Composable
fun DiaryRecordEditDialog(record: DiaryRecord, onDismiss: () -> Unit, onSave: (DiaryRecord) -> Unit) {
    val isMed = record.type == RecordType.MEDICINE
    RecordFieldsDialog(
        title = "Edit entry",
        isMedicine = isMed,
        initial = Fields(record.originalName, record.timePhrase.orEmpty(), record.note.orEmpty(), record.genericSuggestion.orEmpty()),
        confirmLabel = "Save",
        onDismiss = onDismiss,
    ) { f ->
        val g = f.generic.ifEmpty { null }
        onSave(
            record.copy(
                originalName = f.name,
                timePhrase = f.time.ifEmpty { null },
                note = f.note.ifEmpty { null },
                genericSuggestion = if (isMed) g else null,
                genericMatchStatus = when {
                    !isMed -> record.genericMatchStatus
                    g != record.genericSuggestion -> if (g == null) MatchStatus.UNMATCHED else MatchStatus.MANUAL
                    else -> record.genericMatchStatus
                },
            ),
        )
    }
}
