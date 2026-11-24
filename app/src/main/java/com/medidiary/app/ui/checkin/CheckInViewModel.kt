package com.medidiary.app.ui.checkin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medidiary.app.MediDiaryApp
import com.medidiary.app.audio.AudioRecorder
import com.medidiary.app.audio.StopReason
import com.medidiary.app.data.dgda.NameVerdict
import com.medidiary.app.domain.ExtractionSanitizer
import com.medidiary.app.domain.MatchStatus
import com.medidiary.app.domain.ModelResponseParser
import com.medidiary.app.domain.ProposedRecord
import com.medidiary.app.domain.RecordType
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Shared by CheckIn → Transcript → Review. Holds proposals in memory only; nothing is written
 * to Room until [save] is called from the Review screen (core rule #2).
 */
class CheckInViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as MediDiaryApp).container
    val recorder = AudioRecorder()

    private val _state = MutableStateFlow(CheckInState(mockMode = c.pipeline.isMockMode()))
    val state: StateFlow<CheckInState> = _state.asStateFlow()

    private var recordJob: Job? = null
    private var wavFile: File? = null

    /** App settings (the EN | BN toggle on the home screen reads/writes the speech language). */
    val settings = c.settings.settings

    /** For the "shared with family" hint on the home screen. */
    val familyState = c.familySync.state

    fun setAsrLanguage(code: String) = c.settings.update { it.copy(asrLanguage = code) }

    fun refreshMode() = _state.update { it.copy(mockMode = c.pipeline.isMockMode()) }

    /**
     * Opens the mic. The recorder ends by itself when nobody speaks in the first 10 s (no audio is
     * kept, Whisper never runs) or 60 s after the tap; the user can also tap stop any time.
     * Either way, if speech was captured it is transcribed straight away.
     */
    fun startRecording() {
        if (_state.value.busy != Busy.NONE) return
        val wav = File(getApplication<Application>().cacheDir, "checkin_${System.currentTimeMillis()}.wav")
        wavFile = wav
        _state.update { it.copy(busy = Busy.RECORDING, error = null, info = null) }
        recordJob = viewModelScope.launch {
            val result = runCatching { recorder.record(wav) }.getOrElse { e ->
                wav.delete()
                wavFile = null
                _state.update { it.copy(busy = Busy.NONE, error = e.message ?: "Recording failed") }
                return@launch
            }
            when {
                !result.speechDetected -> {
                    wav.delete()
                    wavFile = null
                    val msg = if (result.stopReason == StopReason.NO_SPEECH)
                        "Didn't hear anything in 10 seconds, so recording stopped. Tap the mic to try again."
                    else "No speech was recorded. Tap the mic and start speaking."
                    _state.update { it.copy(busy = Busy.NONE, info = msg) }
                }
                result.audioMs < 700 -> {
                    wav.delete()
                    wavFile = null
                    _state.update { it.copy(busy = Busy.NONE, error = "Recording too short. Hold on a little longer.") }
                }
                else -> transcribe(wav)
            }
        }
    }

    /** User tapped stop: the running recording job finishes and transcribes what it captured. */
    fun stopRecording() {
        recorder.requestStop()
    }

    private suspend fun transcribe(wav: File) {
        _state.update { it.copy(busy = Busy.TRANSCRIBING) }
        val t0 = System.currentTimeMillis()
        val result = runCatching { c.pipeline.transcribe(wav) }
        wav.delete() // privacy: raw audio never outlives transcription
        wavFile = null
        result.onSuccess { text ->
            _state.update {
                it.copy(
                    busy = Busy.NONE,
                    transcript = listOf(it.transcript.trim(), text.trim()).filter { s -> s.isNotEmpty() }.joinToString(" "),
                    asrMs = System.currentTimeMillis() - t0,
                    mockMode = c.pipeline.isMockMode(),
                )
            }
        }.onFailure { e ->
            _state.update { it.copy(busy = Busy.NONE, error = "Transcription failed: ${e.message}") }
        }
    }

    fun cancel() {
        recorder.requestStop()
        c.pipeline.cancel()
    }

    fun setTranscript(text: String) = _state.update { it.copy(transcript = text) }

    fun appendToTranscript(word: String) = _state.update {
        it.copy(transcript = (it.transcript.trimEnd() + " " + word).trim())
    }

    /** Phase 4 + 5: Gemma → parse → sanitize → deterministic DGDA lookup. */
    fun extract(onDone: () -> Unit) {
        val transcript = _state.value.transcript.trim()
        if (transcript.isEmpty() || _state.value.busy != Busy.NONE) return
        _state.update { it.copy(busy = Busy.EXTRACTING, error = null, info = null, rejected = emptyList(), held = emptyList()) }
        viewModelScope.launch {
            val t0 = System.currentTimeMillis()
            val raw = runCatching { c.pipeline.extract(transcript) }.getOrElse { e ->
                _state.update { it.copy(busy = Busy.NONE, error = "Extraction failed: ${e.message}") }
                return@launch
            }
            val llmMs = System.currentTimeMillis() - t0
            val parsed = ModelResponseParser.parse(raw)
            val clean = ExtractionSanitizer.sanitize(parsed.records)
            val matched = clean.map { rec ->
                if (rec.type != RecordType.MEDICINE) rec
                else {
                    val m = c.dgdaLookup.match(rec.name)
                    rec.copy(genericSuggestion = m.genericName, genericMatchStatus = m.status)
                }
            }
            // Task 2: stop before Review when a medicine name is nowhere near anything we know.
            val gate = c.dgdaLookup.nameGate(c.diaryRepository.medicineNames())
            val rejected = mutableListOf<RejectedName>()
            val gated = matched.map { rec ->
                if (rec.type != RecordType.MEDICINE) return@map rec
                when (val v = gate.check(rec.name, rec.genericMatchStatus)) {
                    is NameVerdict.Known -> rec
                    is NameVerdict.Close -> rec.copy(didYouMean = v.suggestion.takeUnless { it.equals(rec.name.trim(), ignoreCase = true) })
                    is NameVerdict.Reject -> { rejected += RejectedName(rec.name, v.closest); rec }
                }
            }
            if (rejected.isNotEmpty()) {
                _state.update {
                    it.copy(
                        busy = Busy.NONE, proposals = emptyList(), held = gated, rejected = rejected,
                        rawModelOutput = raw, llmMs = System.currentTimeMillis() - t0, mockMode = c.pipeline.isMockMode(),
                    )
                }
                return@launch
            }
            val dropped = parsed.records.size - clean.size + parsed.rejectedCount
            val info = buildList {
                parsed.error?.let { add("The AI output could not be read ($it). Add items manually below.") }
                if (dropped > 0) add("$dropped unclear item(s) were filtered out.")
                if (matched.isEmpty() && parsed.error == null) add("No medicines or symptoms were found. Add items manually if needed.")
            }.joinToString(" ").ifEmpty { null }
            _state.update {
                it.copy(
                    busy = Busy.NONE, proposals = gated, rawModelOutput = raw, llmMs = llmMs,
                    info = info, mockMode = c.pipeline.isMockMode(),
                )
            }
            onDone()
        }
    }

    /** "Keep as typed": the user vouches for the unrecognised name(s); they go to Review unmatched. */
    fun keepAsTyped() = _state.update { s ->
        s.copy(proposals = s.held, held = emptyList(), rejected = emptyList(), info = "Kept as typed. Check the names before saving.")
    }

    /** "Edit transcript": drop the held proposals so the user can fix the text and run Confirmation again. */
    fun dismissRejection() = _state.update { it.copy(held = emptyList(), rejected = emptyList(), rawModelOutput = null) }

    /** Accept a "Did you mean …?" suggestion. */
    fun applySuggestion(record: ProposedRecord) {
        val name = record.didYouMean ?: return
        val renamed = record.copy(name = name, didYouMean = null)
        updateProposal(renamed)
        rematch(renamed)
    }

    fun updateProposal(updated: ProposedRecord) = _state.update { s ->
        s.copy(proposals = s.proposals.map { if (it.localId == updated.localId) updated else it })
    }

    /** Re-run the deterministic matcher after the user edits a medicine name. */
    fun rematch(record: ProposedRecord) {
        if (record.type != RecordType.MEDICINE || record.genericMatchStatus == MatchStatus.MANUAL) return
        viewModelScope.launch {
            val m = c.dgdaLookup.match(record.name)
            updateProposal(record.copy(genericSuggestion = m.genericName, genericMatchStatus = m.status))
        }
    }

    fun removeProposal(id: String) = _state.update { s -> s.copy(proposals = s.proposals.filterNot { it.localId == id }) }

    fun addManual(type: String) = _state.update { s ->
        s.copy(proposals = s.proposals + ProposedRecord(type = type, name = "", genericMatchStatus = MatchStatus.UNMATCHED))
    }

    fun save(onSaved: () -> Unit) {
        val s = _state.value
        val toSave = s.proposals.filter { it.checked && it.name.isNotBlank() }
        if (toSave.isEmpty()) {
            _state.update { it.copy(error = "Tick at least one item to save.") }
            return
        }
        _state.update { it.copy(busy = Busy.SAVING, error = null) }
        viewModelScope.launch {
            runCatching { c.diaryRepository.saveVerified(s.transcript, toSave) }
                .onSuccess {
                    _state.value = CheckInState(info = "Saved ${toSave.size} item(s) to your diary.", mockMode = c.pipeline.isMockMode())
                    onSaved()
                }
                .onFailure { e -> _state.update { it.copy(busy = Busy.NONE, error = "Save failed: ${e.message}") } }
        }
    }

    fun discard() {
        _state.value = CheckInState(mockMode = c.pipeline.isMockMode())
    }

    fun clearMessages() = _state.update { it.copy(error = null, info = null) }

    override fun onCleared() {
        recorder.requestStop()
        wavFile?.delete()
    }
}
