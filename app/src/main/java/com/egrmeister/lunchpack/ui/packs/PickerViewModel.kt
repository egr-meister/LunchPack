package com.egrmeister.lunchpack.ui.packs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.egrmeister.lunchpack.AppContainer
import com.egrmeister.lunchpack.domain.AssignResult
import com.egrmeister.lunchpack.domain.PackTemplate
import com.egrmeister.lunchpack.domain.PackingRules
import com.egrmeister.lunchpack.domain.PackingSession
import com.egrmeister.lunchpack.domain.StartResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class PendingReplace(val templateId: Long, val currentPackName: String)

data class PickerUiState(
    val loading: Boolean = true,
    val date: LocalDate? = null,
    val today: LocalDate = LocalDate.MIN,
    val templates: List<PackTemplate> = emptyList(),
    /** Existing plan for [date], or the unfinished unscheduled session when [date] is null. */
    val current: PackingSession? = null,
    val pendingReplace: PendingReplace? = null,
    val busy: Boolean = false,
    val message: String? = null,
    /** Set when done: the session to open (unscheduled) or -1 when a dated plan was assigned. */
    val doneSessionId: Long? = null,
)

/** Picks a template for a date (weekly plan) or for unscheduled packing. */
class PickerViewModel(private val container: AppContainer, private val date: LocalDate?) : ViewModel() {
    private val local = MutableStateFlow(PickerUiState(date = date))

    val state: StateFlow<PickerUiState> = combine(
        container.repository.templatesFlow,
        if (date == null) container.repository.unfinishedUnscheduledFlow else container.repository.sessionForDateFlow(date),
        container.dateTicker.today,
        local,
    ) { templates, current, today, l ->
        l.copy(loading = false, today = today, templates = PackingRules.sortForPicker(templates), current = current)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PickerUiState(date = date))

    fun choose(templateId: Long, confirmed: Boolean = false) {
        if (local.value.busy) return
        local.value = local.value.copy(busy = true, pendingReplace = null, message = null)
        viewModelScope.launch {
            if (date != null) {
                val replacedId = state.value.current?.id
                when (val r = container.service.assignPlan(date, templateId, confirmed)) {
                    is AssignResult.Assigned -> {
                        // Keep the lunchbox on this day if it was showing the plan that was replaced.
                        if (replacedId != null && container.settings.selectedSessionFlow.first() == replacedId) {
                            container.settings.setSelectedSession(r.sessionId)
                        }
                        done(-1L)
                    }
                    is AssignResult.NeedsConfirmation -> ask(templateId, r.currentPackName)
                    AssignResult.OutsideWindow -> fail("Plans can be made from today through the next 30 days.")
                    AssignResult.TemplateEmpty -> fail("This pack has no items yet.")
                    AssignResult.TemplateMissing -> fail("That pack was deleted.")
                }
            } else {
                when (val r = container.service.startUnscheduled(templateId, confirmed)) {
                    is StartResult.Started -> {
                        container.settings.setSelectedSession(r.sessionId)
                        done(r.sessionId)
                    }
                    is StartResult.NeedsConfirmation -> ask(templateId, r.currentPackName)
                    StartResult.TemplateEmpty -> fail("This pack has no items yet.")
                    StartResult.TemplateMissing -> fail("That pack was deleted.")
                }
            }
        }
    }

    fun resume(sessionId: Long) {
        viewModelScope.launch {
            container.settings.setSelectedSession(sessionId)
            done(sessionId)
        }
    }

    fun dismissReplace() {
        local.value = local.value.copy(pendingReplace = null)
    }

    fun clearMessage() {
        local.value = local.value.copy(message = null)
    }

    private fun done(id: Long) {
        local.value = local.value.copy(busy = false, doneSessionId = id)
    }

    private fun ask(templateId: Long, name: String) {
        local.value = local.value.copy(busy = false, pendingReplace = PendingReplace(templateId, name))
    }

    private fun fail(message: String) {
        local.value = local.value.copy(busy = false, message = message)
    }
}
