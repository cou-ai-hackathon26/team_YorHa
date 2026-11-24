package com.medidiary.app.ui.home

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.medidiary.app.data.AppSettings
import com.medidiary.app.data.db.DiaryRecord
import com.medidiary.app.domain.MatchStatus
import com.medidiary.app.domain.ProposedRecord
import com.medidiary.app.domain.RecordType
import com.medidiary.app.ui.checkin.CheckInState
import com.medidiary.app.ui.diary.CalendarActions
import com.medidiary.app.ui.diary.CalendarPanelContent
import com.medidiary.app.ui.diary.CalendarUi
import com.medidiary.app.ui.diary.DateUtils
import com.medidiary.app.ui.diary.DayMarks
import com.medidiary.app.ui.diary.TypeFilter
import com.medidiary.app.ui.settings.ExportRange
import com.medidiary.app.ui.settings.SettingsActions
import com.medidiary.app.ui.settings.SettingsContent
import com.medidiary.app.ui.settings.SettingsUi
import com.medidiary.app.ui.theme.MediDiaryTheme

/**
 * Sample data for Android Studio previews of the REAL screens (Split/Design view, no phone needed).
 * Everything here is fictional demo data.
 */
object PreviewData {
    private val now = System.currentTimeMillis()
    private const val DAY = 24L * 60 * 60 * 1000

    const val TRANSCRIPT = "সকালে নাস্তার আগে একটা Esonix 20 খেয়েছি, এখনো বুক জ্বালাপোড়া করছে আর একটু gas লাগছে।"

    val proposals = listOf(
        ProposedRecord(localId = "1", type = RecordType.MEDICINE, name = "Esonix", genericSuggestion = "Esomeprazole",
            genericMatchStatus = MatchStatus.EXACT, timePhrase = "সকালে নাস্তার আগে", note = "20"),
        ProposedRecord(localId = "2", type = RecordType.SYMPTOM, name = "বুক জ্বালাপোড়া", timePhrase = "এখনো"),
        ProposedRecord(localId = "3", type = RecordType.SYMPTOM, name = "gas", timePhrase = "এখনো", note = "একটু"),
    )

    val idle = HomeUi(CheckInState(transcript = TRANSCRIPT, asrMs = 6400), language = "bn")
    val reviewing = HomeUi(CheckInState(transcript = TRANSCRIPT, proposals = proposals, asrMs = 6400, llmMs = 4100), language = "bn")
    val recording = HomeUi(CheckInState(busy = com.medidiary.app.ui.checkin.Busy.RECORDING), language = "bn", amplitude = 0.6f, elapsedMs = 12_000)

    private val records = listOf(
        DiaryRecord(1, 1, RecordType.MEDICINE, "Napa", "Paracetamol", MatchStatus.EXACT, "সকালে", null, true, now - 2 * 3600_000),
        DiaryRecord(2, 1, RecordType.SYMPTOM, "মাথা ব্যথা", null, MatchStatus.UNMATCHED, "দুপুর থেকে", "একটু", true, now - 2 * 3600_000),
        DiaryRecord(3, 2, RecordType.MEDICINE, "Seclo", "Omeprazole", MatchStatus.EXACT, "রাতে", "20", true, now - DAY),
        DiaryRecord(4, 3, RecordType.SYMPTOM, "জ্বর", null, MatchStatus.UNMATCHED, "সকাল থেকে", "হালকা", true, now - 3 * DAY),
    )

    val calendar = CalendarUi(
        month = DateUtils.startOfMonth(now),
        marks = records.groupBy { DateUtils.startOfDay(it.loggedDate) }.mapValues { (_, rs) ->
            DayMarks(rs.any { it.type == RecordType.MEDICINE }, rs.any { it.type == RecordType.SYMPTOM })
        },
        selectedDay = null,
        type = TypeFilter.ALL,
        days = records.groupBy { DateUtils.startOfDay(it.loggedDate) }.toList().sortedByDescending { it.first },
    )

    val settingsUi = SettingsUi(message = null, whisperReady = true, gemmaReady = true, exportRange = ExportRange.MONTH, exporting = false)
    val settings = AppSettings(patientName = "Demo Patient", retentionDays = 30)
}

object NoopHomeActions : HomeActions {
    override fun setLanguage(code: String) {}
    override fun onMicTapped() {}
    override fun cancel() {}
    override fun setTranscript(text: String) {}
    override fun appendToTranscript(word: String) {}
    override fun extract() {}
    override fun updateProposal(record: ProposedRecord) {}
    override fun rematch(record: ProposedRecord) {}
    override fun removeProposal(localId: String) {}
    override fun addManual(type: String) {}
    override fun save() {}
    override fun discard() {}
}

object NoopCalendarActions : CalendarActions {
    override fun shiftMonth(delta: Int) {}
    override fun selectDay(day: Long?) {}
    override fun setType(type: TypeFilter) {}
    override fun update(record: DiaryRecord) {}
    override fun delete(record: DiaryRecord) {}
}

object NoopSettingsActions : SettingsActions {
    override fun update(transform: (AppSettings) -> AppSettings) {}
    override fun setRetention(days: Int) {}
    override fun openModels() {}
    override fun setExportRange(range: ExportRange) {}
    override fun export(pdf: Boolean) {}
    override fun deleteAllData() {}
}

private val previewCalendar: @Composable ColumnScope.((() -> Unit)?) -> Unit =
    { onClose -> CalendarPanelContent(PreviewData.calendar, NoopCalendarActions, onClose) }

@Composable
fun HomePhoneSample(ui: HomeUi = PreviewData.reviewing) = MediDiaryTheme {
    HomeLayout(ui, NoopHomeActions, onOpenSettings = {}, calendar = previewCalendar, forceWide = false)
}

@Composable
fun HomeTabletSample() = MediDiaryTheme {
    HomeLayout(PreviewData.reviewing, NoopHomeActions, onOpenSettings = {}, calendar = previewCalendar, forceWide = true)
}

@Composable
fun SettingsSample() = MediDiaryTheme {
    SettingsContent(PreviewData.settings, PreviewData.settingsUi, NoopSettingsActions, onBack = {})
}

@Preview(name = "Home · phone · review", widthDp = 400, heightDp = 1400)
@Composable private fun HomePhonePreview() = HomePhoneSample()

@Preview(name = "Home · phone · recording", widthDp = 400, heightDp = 900)
@Composable private fun HomeRecordingPreview() = HomePhoneSample(PreviewData.recording)

@Preview(name = "Home · tablet (permanent calendar)", widthDp = 1280, heightDp = 900)
@Composable private fun HomeTabletPreview() = HomeTabletSample()

@Preview(name = "Settings", widthDp = 400, heightDp = 1500)
@Composable private fun SettingsPreview() = SettingsSample()
