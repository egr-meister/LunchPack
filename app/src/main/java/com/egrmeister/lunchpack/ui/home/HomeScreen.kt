@file:OptIn(ExperimentalLayoutApi::class)

package com.egrmeister.lunchpack.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.egrmeister.lunchpack.domain.Compartment
import com.egrmeister.lunchpack.domain.PackingRules
import com.egrmeister.lunchpack.domain.PackingSession
import com.egrmeister.lunchpack.domain.SessionItem
import com.egrmeister.lunchpack.ui.common.ConfirmDialog
import com.egrmeister.lunchpack.ui.common.EmptyState
import com.egrmeister.lunchpack.ui.common.Formats
import com.egrmeister.lunchpack.ui.common.ItemIconImage
import com.egrmeister.lunchpack.ui.common.SectionHeader
import com.egrmeister.lunchpack.ui.common.StatusBadge
import com.egrmeister.lunchpack.ui.theme.LunchColors

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onReview: (Long) -> Unit,
    onChoosePack: (PackingSession?) -> Unit,
    onOpenWeek: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmFresh by rememberSaveable { mutableStateOf(false) }
    val session = state.session

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val wide = maxWidth >= 600.dp
            if (state.loading) {
                Spacer(Modifier.fillMaxSize())
            } else if (session == null) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Text(
                        text = Formats.relativeDay(state.today, state.today),
                        style = MaterialTheme.typography.titleMedium,
                        color = LunchColors.Muted,
                    )
                    Spacer(Modifier.heightIn(min = 8.dp))
                    OpenLunchbox(items = emptyList(), selected = null, onSelect = {})
                    EmptyState("Choose a pack to get started.") {
                        Button(onClick = { onChoosePack(null) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Choose a pack")
                        }
                        OutlinedButton(onClick = onOpenWeek, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Plan the week")
                        }
                    }
                }
            } else {
            val header: @Composable () -> Unit = { PackHeader(session, state.today) { onChoosePack(session) } }
            val box: @Composable () -> Unit = {
                OpenLunchbox(
                    items = session.items,
                    selected = state.filter,
                    onSelect = { viewModel.selectFilter(it) },
                )
            }
            val progress: @Composable () -> Unit = {
                ProgressAndActions(
                    session = session,
                    onReview = { onReview(session.id) },
                    onStartFresh = { confirmFresh = true },
                )
            }
            val checklist: @Composable () -> Unit = {
                Checklist(
                    items = session.sortedItems,
                    filter = state.filter,
                    onClearFilter = { viewModel.selectFilter(null) },
                    onToggle = { viewModel.toggle(session.id, it.id) },
                )
            }

            if (wide) {
                Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        header()
                        box()
                        progress()
                    }
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) { checklist() }
                }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    header()
                    box()
                    progress()
                    checklist()
                }
            }
            }
        }
    }

    if (confirmFresh && session != null) {
        ConfirmDialog(
            title = "Start fresh?",
            message = "This unchecks every item in “${session.packName}”. Saved history and your pack templates stay as they are.",
            confirmLabel = "Start fresh",
            onConfirm = {
                confirmFresh = false
                viewModel.startFresh(session.id)
            },
            onDismiss = { confirmFresh = false },
        )
    }
}

@Composable
private fun PackHeader(session: PackingSession, today: java.time.LocalDate, onChange: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = session.plannedDate?.let { Formats.relativeDay(it, today) } ?: "Unscheduled pack",
            style = MaterialTheme.typography.titleMedium,
            color = LunchColors.Muted,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = session.packName,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            TextButton(onClick = onChange, modifier = Modifier.heightIn(min = 48.dp)) { Text("Change") }
        }
        StatusBadge(session.status, session.savedComplete)
    }
}

@Composable
private fun ProgressAndActions(session: PackingSession, onReview: () -> Unit, onStartFresh: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = PackingRules.progressLabel(session.packedCount, session.totalCount),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onReview, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Review Pack") }
            OutlinedButton(
                onClick = onStartFresh,
                enabled = session.packedCount > 0,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) { Text("Start Fresh") }
        }
    }
}

@Composable
private fun Checklist(
    items: List<SessionItem>,
    filter: Compartment?,
    onClearFilter: () -> Unit,
    onToggle: (SessionItem) -> Unit,
) {
    val visible = if (filter == null) items else items.filter { it.compartment == filter }
    val unpacked = visible.filter { !it.packed }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (filter != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Showing ${filter.label} only",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClearFilter, modifier = Modifier.heightIn(min = 48.dp)) { Text("Show all") }
            }
        }
        if (visible.isEmpty()) {
            EmptyState("No items in this compartment.")
        } else if (unpacked.isNotEmpty()) {
            SectionHeader("To pack")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                unpacked.forEach { item -> UnpackedChip(item) { onToggle(item) } }
            }
        }
        if (visible.isNotEmpty()) {
            SectionHeader("Checklist")
            visible.forEach { item -> ChecklistRow(item) { onToggle(item) } }
        }
    }
}

private fun SessionItem.detail(): String =
    if (note.isBlank()) compartment.label else "${compartment.label} · $note"

@Composable
private fun UnpackedChip(item: SessionItem, onClick: () -> Unit) {
    Surface(
        color = androidx.compose.ui.graphics.Color.White,
        shape = MaterialTheme.shapes.large,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = "Mark packed") { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ItemIconImage(item.icon, 26.dp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(item.name, style = MaterialTheme.typography.labelLarge)
                Text(item.compartment.label, style = MaterialTheme.typography.labelSmall, color = LunchColors.Muted)
            }
        }
    }
}

@Composable
private fun ChecklistRow(item: SessionItem, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = item.packed, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = item.packed, onCheckedChange = null)
        Spacer(Modifier.width(8.dp))
        ItemIconImage(item.icon, 28.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge)
            Text(item.detail(), style = MaterialTheme.typography.bodySmall, color = LunchColors.Muted)
        }
        Text(
            text = if (item.packed) "Packed" else "Not packed",
            style = MaterialTheme.typography.labelMedium,
            color = if (item.packed) LunchColors.TealDark else LunchColors.Muted,
        )
    }
}
