package com.egrmeister.lunchpack.ui.week

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.egrmeister.lunchpack.AppContainer
import com.egrmeister.lunchpack.domain.PackingSession
import com.egrmeister.lunchpack.domain.PlanningWindow
import com.egrmeister.lunchpack.domain.UpdateResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

data class DayRow(
    val date: LocalDate,
    val kind: PlanningWindow.DayKind,
    val plan: PackingSession?,
    val templateAvailable: Boolean,
)

data class WeekUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.MIN,
    val weekStart: LocalDate = LocalDate.MIN,
    val days: List<DayRow> = emptyList(),
    val canGoPrevious: Boolean = false,
    val canGoNext: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class WeekViewModel(private val container: AppContainer) : ViewModel() {
    private val firstDay: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    private val repository = container.repository

    /** Offset in weeks from the week containing today; clamped to the planning window. */
    private val weekOffset = MutableStateFlow(0L)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val state: StateFlow<WeekUiState> = combine(container.dateTicker.today, weekOffset) { today, offset ->
        today to PlanningWindow.clampWeekStart(
            PlanningWindow.weekStart(today, firstDay).plusWeeks(offset),
            today,
            firstDay,
        )
    }.flatMapLatest { (today, start) ->
        val days = PlanningWindow.weekDays(start)
        combine(
            repository.plansBetweenFlow(days.first(), days.last()),
            repository.templatesFlow.map { list -> list.map { it.id }.toSet() },
        ) { plans, templateIds ->
            val byDate = plans.associateBy { it.plannedDate }
            WeekUiState(
                loading = false,
                today = today,
                weekStart = start,
                days = days.map { d ->
                    val plan = byDate[d]
                    DayRow(
                        date = d,
                        kind = PlanningWindow.dayKind(d, today),
                        plan = plan,
                        templateAvailable = plan?.sourceTemplateId?.let { it in templateIds } ?: false,
                    )
                },
                canGoPrevious = PlanningWindow.canGoPrevious(start, today, firstDay),
                canGoNext = PlanningWindow.canGoNext(start, today, firstDay),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekUiState())

    fun previousWeek() {
        if (state.value.canGoPrevious) weekOffset.value = weekOffsetFor(state.value.weekStart.minusWeeks(1))
    }

    fun nextWeek() {
        if (state.value.canGoNext) weekOffset.value = weekOffsetFor(state.value.weekStart.plusWeeks(1))
    }

    private fun weekOffsetFor(start: LocalDate): Long {
        val base = PlanningWindow.weekStart(state.value.today, firstDay)
        return java.time.temporal.ChronoUnit.WEEKS.between(base, start)
    }

    fun open(plan: PackingSession, then: () -> Unit) {
        viewModelScope.launch {
            container.settings.setSelectedSession(plan.id)
            then()
        }
    }

    fun remove(date: LocalDate) {
        viewModelScope.launch {
            if (container.service.removePlan(date)) _messages.tryEmit("Plan removed. Saved history is kept.")
        }
    }

    fun updateFromTemplate(plan: PackingSession) {
        viewModelScope.launch {
            val message = when (container.service.updateFromTemplate(plan.id)) {
                UpdateResult.Updated -> "Plan updated from the current pack. Packing restarted."
                UpdateResult.TemplateMissing -> "The original pack was deleted, so this plan keeps its items."
                UpdateResult.TemplateEmpty -> "The original pack has no items."
                UpdateResult.SessionMissing -> "This plan no longer exists."
            }
            _messages.tryEmit(message)
        }
    }
}
