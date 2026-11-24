package com.medidiary.app.ui.diary

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medidiary.app.data.db.DiaryRecord

/**
 * Left panel from the wireframe: "CALENDAR" month grid on top, "DATES" list of saved entries
 * below (grouped by day, newest first, edit/delete on each), and a footer note about edits.
 * Used inside a ModalNavigationDrawer on phones and a permanent side panel on wide screens.
 */
@Composable
fun ColumnScope.CalendarPanel(vm: DiaryViewModel, onClose: (() -> Unit)?) {
    val month by vm.month.collectAsStateWithLifecycle()
    val marks by vm.dayMarks.collectAsStateWithLifecycle()
    val selected by vm.selectedDay.collectAsStateWithLifecycle()
    val type by vm.typeFilter.collectAsStateWithLifecycle()
    val days by vm.days.collectAsStateWithLifecycle()
    val family by vm.familyState.collectAsStateWithLifecycle()
    CalendarPanelContent(
        CalendarUi(month, marks, selected, type, days, myDeviceId = vm.myDeviceId, sharing = family.enabled),
        actions = remember(vm) {
            object : CalendarActions {
                override fun shiftMonth(delta: Int) = vm.shiftMonth(delta)
                override fun selectDay(day: Long?) = vm.selectDay(day)
                override fun setType(type: TypeFilter) { vm.typeFilter.value = type }
                override fun update(record: DiaryRecord) { vm.update(record) }
                override fun delete(record: DiaryRecord) { vm.delete(record) }
            }
        },
        onClose = onClose,
    )
}
