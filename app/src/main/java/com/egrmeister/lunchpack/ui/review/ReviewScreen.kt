@file:OptIn(ExperimentalMaterial3Api::class)

package com.egrmeister.lunchpack.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.egrmeister.lunchpack.AppContainer
import com.egrmeister.lunchpack.domain.PackingRules
import com.egrmeister.lunchpack.domain.PackingSession
import com.egrmeister.lunchpack.domain.SaveResult
import com.egrmeister.lunchpack.domain.SessionItem
import com.egrmeister.lunchpack.domain.SessionStatus
import com.egrmeister.lunchpack.ui.common.ConfirmDialog
import com.egrmeister.lunchpack.ui.common.EmptyState
import com.egrmeister.lunchpack.ui.common.Formats
import com.egrmeister.lunchpack.ui.common.InfoCard
import com.egrmeister.lunchpack.ui.common.ItemIconImage
import com.egrmeister.lunchpack.ui.common.SectionHeader
import com.egrmeister.lunchpack.ui.common.StatusBadge
import com.egrmeister.lunchpack.ui.theme.LunchColors
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReviewUiState(val loading: Boolean = true, val session: PackingSession? = null)

class ReviewViewModel(private val container: AppContainer, sessionId: Long) : ViewModel() {
    val state: StateFlow<ReviewUiState> = container.repository.sessionFlow(sessionId)
        .map { ReviewUiState(loading = false, session = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewUiState())

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _partialPrompt = MutableStateFlow<Pair<Int, Int>?>(null)
    val partialPrompt: StateFlow<Pair<Int, Int>?> = _partialPrompt.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Guarded against duplicate taps here and, atomically, again inside PackingService. */
    fun save(sessionId: Long, allowPartial: Boolean) {
        if (_saving.value) return
        _saving.value = true
        _partialPrompt.value = null
        viewModelScope.launch {
            try {
                handle(container.service.save(sessionId, allowPartial))
            } finally {
                _saving.value = false
            }
        }
    }

    private fun handle(result: SaveResult) {
        if (result is SaveResult.NeedsPartialConfirmation) {
            _partialPrompt.value = result.packed to result.total
            return
        }
        val message = when (result) {
            is SaveResult.Saved -> if (result.complete) "Saved to History." else "Partial pack saved to History."
            is SaveResult.Unchanged -> "Already saved — nothing changed since then."
            SaveResult.Empty -> "This pack has no items."
            SaveResult.NotFound -> "This pack no longer exists."
            is SaveResult.NeedsPartialConfirmation -> null
        }
        if (message != null) _messages.tryEmit(message)
    }

    fun dismissPartial() {
        _partialPrompt.value = null
    }
}

@Composable
fun ReviewScreen(viewModel: ReviewViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val partial by viewModel.partialPrompt.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Pack") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val session = state.session
        if (!state.loading && session == null) {
            EmptyState("This pack no longer exists.", Modifier.padding(padding))
        } else if (session != null) {
            val items = session.sortedItems
            val packed = items.filter { it.packed }
            val remaining = items.filter { !it.packed }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            session.plannedDate?.let { Formats.longDay(it) } ?: "Unscheduled pack",
                            style = MaterialTheme.typography.titleSmall,
                            color = LunchColors.Muted,
                        )
                        Text(session.packName, style = MaterialTheme.typography.headlineSmall)
                        StatusBadge(session.status, session.savedComplete)
                        Text(
                            PackingRules.progressLabel(session.packedCount, session.totalCount),
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        if (session.allPacked) {
                            InfoCard("Everything on your list is packed.", color = LunchColors.TealSoft)
                        }
                        when (session.status) {
                            SessionStatus.NEEDS_REVIEW -> InfoCard(
                                "This pack changed after it was saved. Save a new revision to mark it saved again.",
                                color = LunchColors.Peach,
                            )
                            SessionStatus.SAVED -> InfoCard("Saved. Nothing has changed since the last save.")
                            else -> Unit
                        }
                        Text(
                            "LunchPack keeps your checklist; it can't check what is physically in the bag.",
                            style = MaterialTheme.typography.bodySmall,
                            color = LunchColors.Muted,
                        )
                    }
                }
                item { SectionHeader("Packed (${packed.size})") }
                if (packed.isEmpty()) item { Text("Nothing packed yet.", color = LunchColors.Muted) }
                items(packed, key = { "p-" + it.id }) { ReviewRow(it) }
                item { SectionHeader("Remaining (${remaining.size})") }
                if (remaining.isEmpty()) item { Text("Nothing left to pack.", color = LunchColors.Muted) }
                items(remaining, key = { "r-" + it.id }) { ReviewRow(it) }
                item {
                    val alreadySaved = session.status == SessionStatus.SAVED
                    Column(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = { viewModel.save(session.id, allowPartial = false) },
                            enabled = !saving && !alreadySaved && items.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) { Text(if (session.allPacked) "Save Pack" else "Save partial pack") }
                        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("Continue Packing")
                        }
                    }
                }
            }
        }
    }

    val prompt = partial
    val session = state.session
    if (prompt != null && session != null) {
        ConfirmDialog(
            title = "Save partial pack?",
            message = "${prompt.first} of ${prompt.second} items are packed. The result is saved to History as partial; " +
                "you can keep packing and save again later.",
            confirmLabel = "Save partial pack",
            onConfirm = { viewModel.save(session.id, allowPartial = true) },
            onDismiss = viewModel::dismissPartial,
        )
    }
}

@Composable
private fun ReviewRow(item: SessionItem) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        ItemIconImage(item.icon, 28.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (item.note.isBlank()) item.compartment.label else "${item.compartment.label} · ${item.note}",
                style = MaterialTheme.typography.bodySmall,
                color = LunchColors.Muted,
            )
        }
    }
}
