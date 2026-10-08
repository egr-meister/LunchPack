@file:OptIn(ExperimentalMaterial3Api::class)

package com.egrmeister.lunchpack.ui.week

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.egrmeister.lunchpack.domain.PackingSession
import com.egrmeister.lunchpack.domain.PlanningWindow.DayKind
import com.egrmeister.lunchpack.ui.common.ConfirmDialog
import com.egrmeister.lunchpack.ui.common.Formats
import com.egrmeister.lunchpack.ui.common.InfoCard
import com.egrmeister.lunchpack.ui.common.StatusBadge
import com.egrmeister.lunchpack.ui.theme.LunchColors
import java.time.LocalDate

private sealed interface WeekDialog {
    data class Remove(val date: LocalDate, val name: String) : WeekDialog
    data class Update(val plan: PackingSession) : WeekDialog
}

@Composable
fun WeekScreen(
    viewModel: WeekViewModel,
    onChoosePack: (LocalDate) -> Unit,
    onOpenPack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<WeekDialog?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Week") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = viewModel::previousWeek, enabled = state.canGoPrevious) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous week")
                    }
                    Text(
                        text = if (state.days.isEmpty()) "" else
                            "${Formats.monthDay(state.days.first().date)} – ${Formats.monthDay(state.days.last().date)}",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    IconButton(onClick = viewModel::nextWeek, enabled = state.canGoNext) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next week")
                    }
                }
            }
            item {
                Text(
                    "Plan from today through the next 30 days. Each date holds one pack. Past days live in History.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LunchColors.Muted,
                )
            }
            items(state.days, key = { it.date.toString() }) { row ->
                DayCard(
                    row = row,
                    onPlan = { onChoosePack(row.date) },
                    onOpen = { plan -> viewModel.open(plan, onOpenPack) },
                    onRemove = { plan -> dialog = WeekDialog.Remove(row.date, plan.packName) },
                    onUpdate = { plan -> dialog = WeekDialog.Update(plan) },
                )
            }
            item {
                InfoCard(
                    "Plans are snapshots: editing a pack later does not change days you already planned. " +
                        "Use “Update from template” on a day to pull in the latest version.",
                )
            }
        }
    }

    when (val d = dialog) {
        is WeekDialog.Remove -> ConfirmDialog(
            title = "Remove plan?",
            message = "Remove “${d.name}” from ${Formats.longDay(d.date)}? Any pending reminder for this day is cancelled. Saved history is kept.",
            confirmLabel = "Remove",
            onConfirm = {
                dialog = null
                viewModel.remove(d.date)
            },
            onDismiss = { dialog = null },
        )
        is WeekDialog.Update -> ConfirmDialog(
            title = "Update from template?",
            message = "This replaces the items planned for ${Formats.longDay(d.plan.plannedDate ?: LocalDate.now())} " +
                "with the current version of the pack and restarts packing progress. Saved history is kept.",
            confirmLabel = "Update",
            onConfirm = {
                dialog = null
                viewModel.updateFromTemplate(d.plan)
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun DayCard(
    row: DayRow,
    onPlan: () -> Unit,
    onOpen: (PackingSession) -> Unit,
    onRemove: (PackingSession) -> Unit,
    onUpdate: (PackingSession) -> Unit,
) {
    val isToday = row.kind == DayKind.TODAY
    val plannable = row.kind == DayKind.TODAY || row.kind == DayKind.TOMORROW || row.kind == DayKind.UPCOMING
    Surface(
        color = if (plannable) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        border = if (isToday) BorderStroke(2.dp, LunchColors.Teal) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Formats.longDay(row.date), style = MaterialTheme.typography.titleSmall)
                    when (row.kind) {
                        DayKind.TODAY -> Text("Today", style = MaterialTheme.typography.labelMedium, color = LunchColors.TealDark)
                        DayKind.TOMORROW -> Text("Tomorrow", style = MaterialTheme.typography.labelMedium, color = LunchColors.TealDark)
                        else -> Unit
                    }
                }
                val plan = row.plan
                if (plan != null && plannable) {
                    PlanMenu(plan, row.templateAvailable, onRemove, onUpdate)
                }
            }
            val plan = row.plan
            when {
                !plannable && row.kind == DayKind.PAST ->
                    Text("Past day. Saved packs are in History.", style = MaterialTheme.typography.bodyMedium, color = LunchColors.Muted)
                !plannable ->
                    Text("Outside the 30-day planning window.", style = MaterialTheme.typography.bodyMedium, color = LunchColors.Muted)
                plan == null -> {
                    Text("No pack planned for this day.", style = MaterialTheme.typography.bodyMedium, color = LunchColors.Muted)
                    OutlinedButton(onClick = onPlan, modifier = Modifier.heightIn(min = 48.dp)) { Text("Plan a pack") }
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(plan.packName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        StatusBadge(plan.status, plan.savedComplete)
                    }
                    Text(
                        "${plan.packedCount} of ${plan.totalCount} packed",
                        style = MaterialTheme.typography.bodySmall,
                        color = LunchColors.Muted,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { onOpen(plan) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Open Pack") }
                        OutlinedButton(onClick = onPlan, modifier = Modifier.heightIn(min = 48.dp)) { Text("Change Pack") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanMenu(
    plan: PackingSession,
    templateAvailable: Boolean,
    onRemove: (PackingSession) -> Unit,
    onUpdate: (PackingSession) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More actions for ${plan.packName}")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(if (templateAvailable) "Update from template" else "Update from template (pack deleted)") },
                enabled = templateAvailable,
                onClick = {
                    open = false
                    onUpdate(plan)
                },
            )
            DropdownMenuItem(
                text = { Text("Remove plan") },
                onClick = {
                    open = false
                    onRemove(plan)
                },
            )
        }
    }
}
