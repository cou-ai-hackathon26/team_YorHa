package com.medidiary.app.ui.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.medidiary.app.audio.AudioRecorder
import com.medidiary.app.domain.MatchStatus
import com.medidiary.app.domain.ProposedRecord
import com.medidiary.app.domain.RecordType
import com.medidiary.app.ui.checkin.Busy
import com.medidiary.app.ui.checkin.CheckInState
import com.medidiary.app.ui.components.BannerKind
import com.medidiary.app.ui.components.MOCK_MODE_TEXT
import com.medidiary.app.ui.components.MessageBanner
import com.medidiary.app.ui.components.NeuButton
import com.medidiary.app.ui.components.NeuCard
import com.medidiary.app.ui.components.NeuIconButton
import com.medidiary.app.ui.components.NeuSegmented
import com.medidiary.app.ui.components.NeuTextField
import com.medidiary.app.ui.components.ProposalEditDialog
import com.medidiary.app.ui.components.SectionLabel
import com.medidiary.app.ui.theme.MedicineAccent
import com.medidiary.app.ui.theme.Mocha
import com.medidiary.app.ui.theme.SymptomAccent
import com.medidiary.app.ui.theme.neumorphic
import kotlinx.coroutines.launch

private val QUICK_CHIPS = listOf("Napa", "Paracetamol", "Seclo", "Gas", "Headache", "জ্বর", "মাথা ব্যথা", "সকালে", "রাতে")

/** Snapshot of everything the home screen draws. Stateless composables take this, so they can be previewed. */
data class HomeUi(
    val state: CheckInState,
    val language: String,
    val amplitude: Float = 0f,
    val elapsedMs: Long = 0L,
    /** Mic is open but no speech yet (first 10 s silence gate). */
    val listening: Boolean = false,
    /** Family sharing is on for this phone. */
    val sharing: Boolean = false,
)

/** Everything the home screen can ask for. Implemented by [rememberHomeActions] over the ViewModel. */
interface HomeActions {
    fun setLanguage(code: String)
    fun onMicTapped()
    fun cancel()
    fun setTranscript(text: String)
    fun appendToTranscript(word: String)
    fun extract()
    fun updateProposal(record: ProposedRecord)
    fun rematch(record: ProposedRecord)
    fun removeProposal(localId: String)
    fun addManual(type: String)
    fun save()
    fun discard()
    // Task 2: unrecognised medicine name → check-in stopped.
    fun keepAsTyped() {}
    fun editTranscript() {}
    fun reRecord() {}
    fun applySuggestion(record: ProposedRecord) {}
}

/** Stateless layout: phone drawer vs. permanent side panel. [calendar] renders the CALENDAR panel. */
@Composable
fun HomeLayout(
    ui: HomeUi,
    actions: HomeActions,
    onOpenSettings: () -> Unit,
    calendar: @Composable ColumnScope.(onClose: (() -> Unit)?) -> Unit,
    forceWide: Boolean? = null,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Mocha.Base)) {
        if (forceWide ?: (maxWidth >= 840.dp)) {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(Modifier.width(340.dp).safeDrawingPadding(), drawerContainerColor = Mocha.Mantle) {
                        calendar(null)
                    }
                },
            ) { HomeContent(ui, actions, onOpenSettings, onMenu = null) }
        } else {
            val drawerState = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(Modifier.width(330.dp), drawerContainerColor = Mocha.Mantle) {
                        calendar { scope.launch { drawerState.close() } }
                    }
                },
            ) { HomeContent(ui, actions, onOpenSettings, onMenu = { scope.launch { drawerState.open() } }) }
        }
    }
}

/** Stateless main column. Preview this with fake [HomeUi] (see HomePreviews.kt). */
@Composable
fun HomeContent(ui: HomeUi, actions: HomeActions, onOpenSettings: () -> Unit, onMenu: (() -> Unit)?) {
    val state = ui.state
    var editing by remember { mutableStateOf<ProposedRecord?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Mocha.Base)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = 760.dp), // keep a readable column on tablets / landscape
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        // ---- Top bar: ☰ CALENDAR … ⚙ Settings ------------------------------------------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onMenu != null) {
                NeuIconButton(Icons.Filled.Menu, "Open calendar", onMenu, tint = Mocha.Blue)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("MediDiary", style = MaterialTheme.typography.titleLarge, color = Mocha.Text, fontWeight = FontWeight.Bold)
                Text(
                    if (ui.sharing) "On-device AI · shared with family, encrypted" else "Private · works offline",
                    style = MaterialTheme.typography.labelSmall, color = if (ui.sharing) Mocha.Teal else Mocha.Overlay1,
                )
            }
            NeuIconButton(Icons.Filled.Settings, "Settings", onOpenSettings, tint = Mocha.Mauve)
        }

        // ---- SELECT LANGUAGE (EN | BN) — speech recognition language -------------------------
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text("SELECT LANGUAGE", style = MaterialTheme.typography.labelSmall, color = Mocha.Subtext0, modifier = Modifier.padding(end = 12.dp))
            NeuSegmented(
                options = listOf("en" to "EN", "bn" to "BN"),
                selected = ui.language,
                onSelect = actions::setLanguage,
            )
        }

        if (state.mockMode) MessageBanner(MOCK_MODE_TEXT, BannerKind.MOCK)
        state.info?.let { MessageBanner(it, if (it.startsWith("Saved")) BannerKind.SUCCESS else BannerKind.INFO) }
        state.error?.let { MessageBanner(it, BannerKind.ERROR) }

        if (state.rejected.isNotEmpty()) RejectedNamesCard(state, actions)
        VoiceModelCard(ui, actions)
        TranscriptionCard(state, actions)
        // Task 2: while a name is rejected the check-in is stopped, so Review (and Save) stays hidden.
        if (state.rejected.isEmpty() && (state.proposals.isNotEmpty() || state.rawModelOutput != null)) {
            ReviewCard(state, actions, onEdit = { editing = it })
        }
        Spacer(Modifier.height(24.dp))
    }

    // Auto-open the editor for freshly added blank manual items.
    val dialogRecord = editing ?: state.proposals.firstOrNull { it.name.isBlank() }
    if (dialogRecord != null) {
        ProposalEditDialog(
            initial = dialogRecord,
            onDismiss = {
                if (dialogRecord.name.isBlank()) actions.removeProposal(dialogRecord.localId)
                editing = null
            },
            onSave = { updated ->
                actions.updateProposal(updated)
                if (updated.name != dialogRecord.name) actions.rematch(updated)
                editing = null
            },
        )
    }
}

// =============================================================================================
// VOICE MODEL
// =============================================================================================

@Composable
private fun VoiceModelCard(ui: HomeUi, actions: HomeActions) {
    val state = ui.state
    val amplitude = ui.amplitude
    val elapsed = ui.elapsedMs
    val recording = state.busy == Busy.RECORDING
    val transcribing = state.busy == Busy.TRANSCRIBING

    NeuCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Voice model", Modifier.weight(1f), color = Mocha.Blue)
            Text(
                if (state.mockMode) "demo" else "on-device",
                color = if (state.mockMode) Mocha.Yellow else Mocha.Green,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Text(
            "আজ কোন ওষুধ খেয়েছেন, কেমন লাগছে — বলুন।\nWhat did you take today, and how do you feel?",
            color = Mocha.Subtext1, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            MicButton(recording, transcribing, amplitude, onClick = actions::onMicTapped)
        }
        val secs = elapsed / 1000
        val listening = recording && ui.listening
        Text(
            when {
                listening -> "Listening… start speaking (%d s)".format(
                    ((AudioRecorder.SILENCE_TIMEOUT_MS - elapsed + 999) / 1000).coerceAtLeast(0),
                )
                recording -> "%d:%02d / 1:00 · tap to stop".format(secs / 60, secs % 60)
                transcribing -> "Transcribing on this phone…"
                else -> "Tap the microphone to speak"
            },
            color = when {
                listening -> Mocha.Yellow
                recording -> Mocha.Red
                else -> Mocha.Text
            },
            style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        AmplitudeBars(amplitude, recording)
        if (listening) {
            Text(
                "Stops by itself if nothing is heard in 10 seconds. Silence before you speak is not recorded.",
                color = Mocha.Overlay1, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (transcribing) {
            TextButton(onClick = actions::cancel, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Cancel", color = Mocha.Subtext0) }
        }
        Text(
            "Audio never leaves this phone and is deleted right after transcription.",
            color = Mocha.Overlay1, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MicButton(recording: Boolean, busy: Boolean, amplitude: Float, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val ring by pulse.animateFloat(1f, 1.25f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "ring")
    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
        if (recording) {
            Box(
                Modifier.size(150.dp).scale(ring * (1f + amplitude * 0.2f))
                    .background(Brush.radialGradient(listOf(Mocha.Red.copy(alpha = 0.35f), Color.Transparent)), CircleShape),
            )
        }
        NeuButton(
            onClick = onClick,
            modifier = Modifier.size(136.dp),
            selected = recording,
            enabled = !busy,
            accent = if (recording) Mocha.Red else Mocha.Blue,
            alwaysAccent = true,
            cornerRadius = 68.dp,
            contentPadding = PaddingValues(0.dp),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(44.dp), color = Mocha.Mauve, strokeWidth = 3.dp)
            else Icon(if (recording) Icons.Filled.Stop else Icons.Filled.Mic, if (recording) "Stop recording" else "Record", Modifier.size(60.dp))
        }
    }
}

@Composable
private fun AmplitudeBars(amplitude: Float, active: Boolean) {
    val history = remember { mutableStateListOf<Float>().apply { repeat(36) { add(0f) } } }
    LaunchedEffect(amplitude, active) {
        history.removeAt(0)
        history.add(if (active) amplitude else 0f)
    }
    Canvas(Modifier.fillMaxWidth().height(44.dp).neumorphic(14.dp, 3.dp, inset = true).padding(horizontal = 10.dp, vertical = 6.dp)) {
        val w = size.width / history.size
        history.forEachIndexed { i, a ->
            val h = a.coerceIn(0.05f, 1f) * size.height
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Mocha.Mauve, Mocha.Blue)),
                topLeft = Offset(i * w + w * 0.2f, (size.height - h) / 2),
                size = Size(w * 0.6f, h),
                cornerRadius = CornerRadius(w * 0.3f),
                alpha = if (active) 1f else 0.3f,
            )
        }
    }
}

// =============================================================================================
// TRANSCRIPTION AND EDITS
// =============================================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TranscriptionCard(state: CheckInState, actions: HomeActions) {
    val extracting = state.busy == Busy.EXTRACTING

    NeuCard(Modifier.fillMaxWidth()) {
        SectionLabel("Transcription and edits", color = Mocha.Blue)
        Text(
            "Fix any misheard medicine names here before AI extraction — or just type your check-in.",
            color = Mocha.Subtext0, style = MaterialTheme.typography.bodySmall,
        )
        NeuTextField(
            value = state.transcript,
            onValueChange = actions::setTranscript,
            minHeight = 140.dp,
            enabled = !extracting && state.busy != Busy.RECORDING,
            placeholder = "যেমন: সকালে নাস্তার পর একটা Napa খেয়েছি, মাথা ব্যথা করছে",
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            QUICK_CHIPS.forEach { chip ->
                NeuButton(
                    onClick = { actions.appendToTranscript(chip) },
                    enabled = state.busy == Busy.NONE,
                    cornerRadius = 12.dp,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) { Text(chip, style = MaterialTheme.typography.labelLarge) }
            }
        }
        state.asrMs?.let {
            Text("Transcribed on-device in %.1f s".format(it / 1000.0), color = Mocha.Overlay1, style = MaterialTheme.typography.labelSmall)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            if (extracting) TextButton(onClick = actions::cancel) { Text("Cancel", color = Mocha.Subtext0) }
            else if (state.transcript.isNotBlank()) TextButton(onClick = { actions.setTranscript("") }) { Text("Clear", color = Mocha.Subtext0) }
            NeuButton(
                onClick = actions::extract,
                enabled = !extracting && state.transcript.isNotBlank() && state.busy == Busy.NONE,
                accent = Mocha.Blue,
                alwaysAccent = true,
            ) {
                if (extracting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Mocha.Mauve)
                    Text("Extracting…")
                } else {
                    Icon(Icons.Filled.AutoAwesome, null)
                    Text("Confirmation", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// =============================================================================================
// REVIEW (human-in-the-loop — nothing is saved until "Confirm & Save")
// =============================================================================================

@Composable
private fun ReviewCard(state: CheckInState, actions: HomeActions, onEdit: (ProposedRecord) -> Unit) {
    var showRaw by remember { mutableStateOf(false) }
    val medicines = state.proposals.filter { it.type == RecordType.MEDICINE }
    val symptoms = state.proposals.filter { it.type == RecordType.SYMPTOM }
    val checked = state.proposals.count { it.checked && it.name.isNotBlank() }

    NeuCard(Modifier.fillMaxWidth()) {
        SectionLabel("Review before saving", color = Mocha.Green)
        Text("AI suggestions are not saved until you confirm. Untick or delete anything that is wrong.",
            color = Mocha.Subtext0, style = MaterialTheme.typography.bodySmall)

        GroupHeader("Medicines", MedicineAccent) { actions.addManual(RecordType.MEDICINE) }
        if (medicines.isEmpty()) EmptyHint("No medicines found.")
        medicines.forEach {
            ProposalRow(it, MedicineAccent, actions::updateProposal, { onEdit(it) }, { actions.removeProposal(it.localId) },
                onAcceptSuggestion = { actions.applySuggestion(it) })
        }

        GroupHeader("Symptoms", SymptomAccent) { actions.addManual(RecordType.SYMPTOM) }
        if (symptoms.isEmpty()) EmptyHint("No symptoms found.")
        symptoms.forEach { ProposalRow(it, SymptomAccent, actions::updateProposal, { onEdit(it) }, { actions.removeProposal(it.localId) }) }

        state.llmMs?.let {
            Text("Extracted on-device in %.1f s".format(it / 1000.0), color = Mocha.Overlay1, style = MaterialTheme.typography.labelSmall)
        }
        if (state.rawModelOutput != null) {
            TextButton(onClick = { showRaw = !showRaw }) {
                Text(if (showRaw) "Hide raw AI output" else "Show raw AI output", color = Mocha.Overlay1)
            }
            if (showRaw) Text(state.rawModelOutput.orEmpty(), color = Mocha.Overlay2, style = MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
            NeuButton(onClick = actions::discard, accent = Mocha.Red, alwaysAccent = true) { Text("Discard") }
            NeuButton(
                onClick = actions::save,
                enabled = checked > 0 && state.busy == Busy.NONE,
                accent = Mocha.Green,
                alwaysAccent = true,
            ) {
                Icon(Icons.Filled.CheckCircle, null)
                Text("Confirm & Save ($checked)", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Task 2: the check-in stopped because a medicine name matched nothing we know. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RejectedNamesCard(state: CheckInState, actions: HomeActions) {
    NeuCard(Modifier.fillMaxWidth()) {
        SectionLabel("Check-in stopped", color = Mocha.Red)
        val names = state.rejected.joinToString(", ") { "“${it.name}”" }
        Text(
            "We couldn't recognise $names as a medicine, so nothing was sent to review. " +
                "Say it again, fix the text, or keep it exactly as typed.",
            color = Mocha.Text, style = MaterialTheme.typography.bodyMedium,
        )
        state.rejected.mapNotNull { it.closest }.distinct().takeIf { it.isNotEmpty() }?.let {
            Text("Closest known: ${it.joinToString(", ")}", color = Mocha.Subtext0, style = MaterialTheme.typography.bodySmall)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            NeuButton(onClick = actions::reRecord, accent = Mocha.Blue, alwaysAccent = true) {
                Icon(Icons.Filled.Mic, null); Text("Say it again")
            }
            NeuButton(onClick = actions::editTranscript, accent = Mocha.Mauve, alwaysAccent = true) {
                Icon(Icons.Filled.Edit, null); Text("Edit transcript")
            }
            NeuButton(onClick = actions::keepAsTyped, accent = Mocha.Peach, alwaysAccent = true) {
                Text("Keep as typed")
            }
        }
    }
}

@Composable
private fun GroupHeader(title: String, accent: Color, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(accent, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(title, color = accent, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = onAdd) { Icon(Icons.Filled.Add, null, tint = accent); Text("Add", color = accent) }
    }
}

@Composable
private fun EmptyHint(text: String) = Text(text, color = Mocha.Overlay1, style = MaterialTheme.typography.bodySmall)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProposalRow(
    rec: ProposedRecord,
    accent: Color,
    onChange: (ProposedRecord) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAcceptSuggestion: () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().neumorphic(18.dp, if (rec.checked) 5.dp else 3.dp, inset = !rec.checked).padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(
            checked = rec.checked,
            onCheckedChange = { onChange(rec.copy(checked = it)) },
            colors = CheckboxDefaults.colors(checkedColor = accent, uncheckedColor = Mocha.Overlay1, checkmarkColor = Mocha.Crust),
        )
        Column(Modifier.weight(1f).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                rec.name.ifBlank { "(empty)" },
                color = if (rec.checked) accent else Mocha.Overlay1,
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rec.didYouMean?.let {
                    Text(
                        "Did you mean $it?",
                        color = Mocha.Crust,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.background(Mocha.Peach, CircleShape).clickable(onClick = onAcceptSuggestion)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                rec.timePhrase?.let { Pill("🕒 $it", Mocha.Subtext1) }
                rec.note?.let { Pill("📝 $it", Mocha.Subtext1) }
                if (rec.type == RecordType.MEDICINE) {
                    when {
                        rec.genericSuggestion == null -> Pill("Unmatched brand", Mocha.Overlay1)
                        rec.genericMatchStatus == MatchStatus.EXACT -> Pill("DGDA match: ${rec.genericSuggestion}", Mocha.Green)
                        rec.genericMatchStatus == MatchStatus.MANUAL -> Pill("Generic (you): ${rec.genericSuggestion}", Mocha.Green)
                        else -> Pill("Possible DGDA match: ${rec.genericSuggestion}?", Mocha.Yellow)
                    }
                }
            }
        }
        IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "Edit", tint = Mocha.Subtext0) }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete", tint = Mocha.Red) }
    }
}

@Composable
private fun Pill(text: String, color: Color) {
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.widthIn(max = 260.dp).background(Mocha.Surface0, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
