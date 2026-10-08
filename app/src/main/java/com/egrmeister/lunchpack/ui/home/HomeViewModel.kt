package com.egrmeister.lunchpack.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.egrmeister.lunchpack.AppContainer
import com.egrmeister.lunchpack.domain.Compartment
import com.egrmeister.lunchpack.domain.PackingSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HomeUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.MIN,
    val session: PackingSession? = null,
    val filter: Compartment? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val repository = container.repository
    private val filter = MutableStateFlow<Compartment?>(null)

    /**
     * The session shown on the lunchbox: the explicitly selected one (if it still exists and is
     * not a past plan), otherwise today's plan, otherwise the unfinished unscheduled session.
     */
    private val session: Flow<Pair<LocalDate, PackingSession?>> =
        combine(container.settings.selectedSessionFlow, container.dateTicker.today) { id, today -> id to today }
            .flatMapLatest { (selectedId, today) ->
                val fallback = combine(
                    repository.sessionForDateFlow(today),
                    repository.unfinishedUnscheduledFlow,
                ) { planned, unscheduled -> today to (planned ?: unscheduled) }
                if (selectedId == null) {
                    fallback
                } else {
                    repository.sessionFlow(selectedId).flatMapLatest { selected ->
                        val usable = selected != null &&
                            (selected.plannedDate == null || !selected.plannedDate.isBefore(today))
                        if (usable) flowOf(today to selected) else fallback
                    }
                }
            }

    val state: StateFlow<HomeUiState> = combine(session, filter) { (today, s), f ->
        HomeUiState(loading = false, today = today, session = s, filter = f)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun selectFilter(compartment: Compartment?) {
        filter.value = if (filter.value == compartment) null else compartment
    }

    fun toggle(sessionId: Long, itemId: String) {
        viewModelScope.launch { container.service.toggleItem(sessionId, itemId) }
    }

    fun startFresh(sessionId: Long) {
        viewModelScope.launch { container.service.startFresh(sessionId) }
    }
}
