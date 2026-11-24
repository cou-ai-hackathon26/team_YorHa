package com.medidiary.app.ui.diary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medidiary.app.MediDiaryApp
import com.medidiary.app.data.db.DiaryRecord
import com.medidiary.app.domain.RecordType
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs the CALENDAR / DATES panel: month grid, per-day entries, edit and delete. */
class DiaryViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as MediDiaryApp).container

    val myDeviceId: String = c.familyPrefs.deviceId
    val familyState = c.familySync.state

    val typeFilter = MutableStateFlow(TypeFilter.ALL)
    /** Start-of-day millis of the tapped date, or null for "all dates". */
    val selectedDay = MutableStateFlow<Long?>(null)
    /** Start-of-month millis currently shown in the grid. */
    val month = MutableStateFlow(DateUtils.startOfMonth(System.currentTimeMillis()))

    private val all: StateFlow<List<DiaryRecord>> =
        c.diaryRepository.observe(0L, Long.MAX_VALUE)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dayMarks: StateFlow<Map<Long, DayMarks>> = all.map { list ->
        list.groupBy { DateUtils.startOfDay(it.loggedDate) }.mapValues { (_, rs) ->
            DayMarks(rs.any { it.type == RecordType.MEDICINE }, rs.any { it.type == RecordType.SYMPTOM })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Filtered entries grouped by day, newest day first. */
    val days: StateFlow<List<Pair<Long, List<DiaryRecord>>>> =
        combine(all, typeFilter, selectedDay) { list, type, day ->
            list.asSequence()
                .filter { r ->
                    when (type) {
                        TypeFilter.ALL -> true
                        TypeFilter.MEDICINES -> r.type == RecordType.MEDICINE
                        TypeFilter.SYMPTOMS -> r.type == RecordType.SYMPTOM
                    }
                }
                .filter { day == null || DateUtils.startOfDay(it.loggedDate) == day }
                .groupBy { DateUtils.startOfDay(it.loggedDate) }
                .toList()
                .sortedByDescending { it.first }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectDay(day: Long?) {
        selectedDay.value = if (selectedDay.value == day) null else day
    }

    fun shiftMonth(delta: Int) {
        month.value = Calendar.getInstance().apply { timeInMillis = month.value; add(Calendar.MONTH, delta) }.timeInMillis
    }

    fun update(record: DiaryRecord) = viewModelScope.launch { c.diaryRepository.update(record) }
    fun delete(record: DiaryRecord) = viewModelScope.launch { c.diaryRepository.delete(record) }
}
