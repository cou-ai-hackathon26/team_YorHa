package com.medidiary.app.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medidiary.app.audio.RecPhase
import com.medidiary.app.domain.ProposedRecord
import com.medidiary.app.ui.checkin.Busy
import com.medidiary.app.ui.checkin.CheckInViewModel
import com.medidiary.app.ui.diary.CalendarPanel
import com.medidiary.app.ui.diary.DiaryViewModel
import kotlinx.coroutines.launch

/**
 * The single main screen from the wireframe:
 *   ☰ CALENDAR (drawer / side panel)   ·   ⚙ Settings + SELECT LANGUAGE (EN | BN)
 *   VOICE MODEL card  →  TRANSCRIPTION AND EDITS card [Confirmation]  →  review cards [Confirm & Save]
 * On screens ≥ 840 dp wide the calendar is a permanent left panel, as in the wireframe.
 */
@Composable
fun HomeScreen(flowVm: CheckInViewModel, diaryVm: DiaryViewModel, onOpenSettings: () -> Unit) {
    val state by flowVm.state.collectAsStateWithLifecycle()
    val settings by flowVm.settings.collectAsStateWithLifecycle()
    val amplitude by flowVm.recorder.amplitude.collectAsStateWithLifecycle()
    val elapsed by flowVm.recorder.elapsedMs.collectAsStateWithLifecycle()
    val phase by flowVm.recorder.phase.collectAsStateWithLifecycle()
    val family by flowVm.familyState.collectAsStateWithLifecycle()
    val actions = rememberHomeActions(flowVm)
    LaunchedEffect(Unit) { flowVm.refreshMode() }
    // The recorder stops itself (10 s of silence / 60 s cap), so no timer-driven stop is needed here.
    HomeLayout(
        ui = HomeUi(state, settings.asrLanguage, amplitude, elapsed, listening = phase == RecPhase.LISTENING, sharing = family.enabled),
        actions = actions,
        onOpenSettings = onOpenSettings,
        calendar = { onClose -> CalendarPanel(diaryVm, onClose) },
    )
}

/** Adapts the ViewModel to [HomeActions]; owns the microphone-permission request. */
@Composable
private fun rememberHomeActions(vm: CheckInViewModel): HomeActions {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startRecording()
    }
    return remember(vm) {
        object : HomeActions {
            override fun setLanguage(code: String) = vm.setAsrLanguage(code)
            override fun onMicTapped() {
                val busy = vm.state.value.busy
                when {
                    busy == Busy.RECORDING -> vm.stopRecording()
                    busy != Busy.NONE -> Unit
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED -> vm.startRecording()
                    else -> permission.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
            override fun cancel() = vm.cancel()
            override fun setTranscript(text: String) = vm.setTranscript(text)
            override fun appendToTranscript(word: String) = vm.appendToTranscript(word)
            override fun extract() = vm.extract {}
            override fun updateProposal(record: ProposedRecord) = vm.updateProposal(record)
            override fun rematch(record: ProposedRecord) = vm.rematch(record)
            override fun removeProposal(localId: String) = vm.removeProposal(localId)
            override fun addManual(type: String) = vm.addManual(type)
            override fun save() = vm.save {}
            override fun discard() = vm.discard()
            override fun keepAsTyped() = vm.keepAsTyped()
            override fun editTranscript() = vm.dismissRejection()
            override fun reRecord() {
                vm.dismissRejection()
                vm.setTranscript("")
                onMicTapped()
            }
            override fun applySuggestion(record: ProposedRecord) = vm.applySuggestion(record)
        }
    }
}
