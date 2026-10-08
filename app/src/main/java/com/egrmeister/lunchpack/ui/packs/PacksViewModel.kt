package com.egrmeister.lunchpack.ui.packs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.egrmeister.lunchpack.AppContainer
import com.egrmeister.lunchpack.domain.DuplicateResult
import com.egrmeister.lunchpack.domain.PackTemplate
import com.egrmeister.lunchpack.domain.PackingRules
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PacksUiState(val loading: Boolean = true, val templates: List<PackTemplate> = emptyList())

class PacksViewModel(private val container: AppContainer) : ViewModel() {
    val state: StateFlow<PacksUiState> = container.repository.templatesFlow
        .map { PacksUiState(loading = false, templates = PackingRules.sortForPicker(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PacksUiState())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun toggleFavorite(id: Long) {
        viewModelScope.launch { container.service.toggleFavorite(id) }
    }

    fun duplicate(id: Long) {
        viewModelScope.launch {
            val message = when (val result = container.service.duplicateTemplate(id)) {
                is DuplicateResult.Duplicated -> "Created “${result.name}”."
                DuplicateResult.LimitReached -> "You can keep up to 30 packs. Delete one to make room."
                DuplicateResult.Missing -> "That pack no longer exists."
            }
            _messages.tryEmit(message)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            container.service.deleteTemplate(id)
            _messages.tryEmit("Pack deleted. Existing plans and history are kept.")
        }
    }
}
