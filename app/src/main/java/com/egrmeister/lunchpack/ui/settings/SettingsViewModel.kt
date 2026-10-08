package com.egrmeister.lunchpack.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.egrmeister.lunchpack.AppContainer
import com.egrmeister.lunchpack.domain.ReminderConfig
import com.egrmeister.lunchpack.domain.ReminderPolicy
import com.egrmeister.lunchpack.domain.ReminderStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val loading: Boolean = true,
    val reminder: ReminderConfig = ReminderConfig.Default,
    val reminderStatus: ReminderStatus = ReminderStatus.OFF,
    val reducedMotion: Boolean = false,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val settings = container.settings

    val state: StateFlow<SettingsUiState> = combine(
        settings.reminderConfigFlow,
        settings.reducedMotionFlow,
        container.notificationsAllowed,
    ) { reminder, reduced, allowed ->
        SettingsUiState(
            loading = false,
            reminder = reminder,
            reminderStatus = ReminderPolicy.status(reminder.enabled, allowed),
            reducedMotion = reduced,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setReminderEnabled(enabled)
            container.engine.reschedule()
            if (!enabled) container.notifications.dismiss()
        }
    }

    fun setReminderTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            settings.setReminderTime(hour, minute)
            container.engine.reschedule()
        }
    }

    /** Called after the permission dialog closes or the user returns from system settings. */
    fun refreshPermission() {
        container.onForeground()
    }

    fun setReducedMotion(enabled: Boolean) {
        viewModelScope.launch { settings.setReducedMotion(enabled) }
    }

    fun restoreStarters() {
        viewModelScope.launch {
            val added = container.service.insertStarters()
            _messages.tryEmit(
                if (added == 0) "No room: you already have 30 packs." else "Added $added example ${if (added == 1) "pack" else "packs"}.",
            )
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            container.service.clearHistory()
            _messages.tryEmit("History cleared.")
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            container.clearAllData()
            _messages.tryEmit("All local data cleared. Example packs restored.")
        }
    }
}
