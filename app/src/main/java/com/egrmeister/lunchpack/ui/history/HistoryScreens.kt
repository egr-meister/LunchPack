@file:OptIn(ExperimentalMaterial3Api::class)

package com.egrmeister.lunchpack.ui.history

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.egrmeister.lunchpack.AppContainer
import com.egrmeister.lunchpack.domain.Compartment
import com.egrmeister.lunchpack.domain.HistoryEntry
import com.egrmeister.lunchpack.ui.common.Badge
import com.egrmeister.lunchpack.ui.common.ConfirmDialog
import com.egrmeister.lunchpack.ui.common.EmptyState
import com.egrmeister.lunchpack.ui.common.Formats
import com.egrmeister.lunchpack.ui.common.ItemIconImage
import com.egrmeister.lunchpack.ui.common.SectionHeader
import com.egrmeister.lunchpack.ui.theme.LunchColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

data class HistoryUiState(val loading: Boolean = true, val entries: List<HistoryEntry> = emptyList())

class HistoryViewModel(private val container: AppContainer) : ViewModel() {
    val state: StateFlow<HistoryUiState> = container.repository.historyFlow
        .map { HistoryUiState(loading = false, entries = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun delete(id: Long) {
        viewModelScope.launch { container.service.deleteHistory(id) }
    }

    fun clear() {
        viewModelScope.launch { container.service.clearHistory() }
    }
}

data class HistoryDetailUiState(val loading: Boolean = true, val entry: HistoryEntry? = null)

class HistoryDetailViewModel(private val container: AppContainer, id: Long) : ViewModel() {
    val state: StateFlow<HistoryDetailUiState> = container.repository.historyEntryFlow(id)
        .map { HistoryDetailUiState(loading = false, entry = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryDetailUiState())

    /** After deletion the entry flow emits null and the screen closes itself. */
    fun delete(id: Long) {
        viewModelScope.launch { container.service.deleteHistory(id) }
    }
}

@Composable
private fun ResultBadge(entry: HistoryEntry) {
    if (entry.complete) Badge("Complete", LunchColors.TealSoft) else Badge("Partial", LunchColors.Peach)
}

private fun plannedLabel(entry: HistoryEntry): String =
    entry.plannedDate?.let { "Planned for ${Formats.day(it)}" } ?: "Unscheduled"

@Composable
fun HistoryScreen(viewModel: HistoryViewModel, onOpen: (Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by rememberSaveable { mutableStateOf<Long?>(null) }
    val zone = ZoneId.systemDefault()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                actions = {
                    if (state.entries.isNotEmpty()) {
                        TextButton(onClick = { confirmClear = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Clear")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (!state.loading && state.entries.isEmpty()) {
            EmptyState("Your saved packs will appear here.", Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        "The latest 100 saved results are kept. Results are snapshots — later pack edits don't change them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = LunchColors.Muted,
                    )
                }
                items(state.entries, key = { it.id }) { entry ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button, onClickLabel = "Show details") { onOpen(entry.id) },
                    ) {
                        Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(entry.packName, style = MaterialTheme.typography.titleMedium)
                                Text(plannedLabel(entry), style = MaterialTheme.typography.bodySmall, color = LunchColors.Muted)
                                Text(
                                    "Saved ${Formats.savedAt(entry.savedAt, zone)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LunchColors.Muted,
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${entry.packedItems} of ${entry.totalItems} packed",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    ResultBadge(entry)
                                }
                            }
                            IconButton(onClick = { pendingDelete = entry.id }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete this result")
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear history?",
            message = "All saved results will be deleted. Packs and plans are not affected.",
            confirmLabel = "Clear history",
            onConfirm = {
                confirmClear = false
                viewModel.clear()
            },
            onDismiss = { confirmClear = false },
        )
    }
    val deleting = pendingDelete
    if (deleting != null) {
        ConfirmDialog(
            title = "Delete this result?",
            message = "This saved result will be removed from History.",
            confirmLabel = "Delete",
            onConfirm = {
                pendingDelete = null
                viewModel.delete(deleting)
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
fun HistoryDetailScreen(viewModel: HistoryDetailViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val entry = state.entry

    LaunchedEffect(state.loading, entry) {
        if (!state.loading && entry == null) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved pack") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (entry != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete this result")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (entry != null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(entry.packName, style = MaterialTheme.typography.headlineSmall)
                        Text(plannedLabel(entry), color = LunchColors.Muted)
                        Text("Saved ${Formats.savedAt(entry.savedAt, zone)}", color = LunchColors.Muted)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${entry.packedItems} of ${entry.totalItems} packed", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(8.dp))
                            ResultBadge(entry)
                        }
                    }
                }
                Compartment.entries.forEach { compartment ->
                    val inCompartment = entry.items.filter { it.compartment == compartment }
                    if (inCompartment.isNotEmpty()) {
                        item(key = "h-${compartment.key}") { SectionHeader(compartment.label) }
                        items(inCompartment, key = { "i-${compartment.key}-${it.displayOrder}" }) { item ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                                ItemIconImage(item.icon, 26.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, style = MaterialTheme.typography.bodyLarge)
                                    if (item.note.isNotBlank()) {
                                        Text(item.note, style = MaterialTheme.typography.bodySmall, color = LunchColors.Muted)
                                    }
                                }
                                Text(
                                    if (item.packed) "Packed" else "Not packed",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (item.packed) LunchColors.TealDark else LunchColors.Muted,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete && entry != null) {
        ConfirmDialog(
            title = "Delete this result?",
            message = "This saved result will be removed from History.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmDelete = false
                viewModel.delete(entry.id)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
