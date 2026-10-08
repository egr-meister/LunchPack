@file:OptIn(ExperimentalMaterial3Api::class)

package com.egrmeister.lunchpack.ui.packs

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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.egrmeister.lunchpack.domain.PackTemplate
import com.egrmeister.lunchpack.domain.PackingRules
import com.egrmeister.lunchpack.domain.SessionStatus
import com.egrmeister.lunchpack.ui.common.ConfirmDialog
import com.egrmeister.lunchpack.ui.common.EmptyState
import com.egrmeister.lunchpack.ui.common.Formats
import com.egrmeister.lunchpack.ui.common.InfoCard
import com.egrmeister.lunchpack.ui.common.ItemIconImage
import com.egrmeister.lunchpack.ui.theme.LunchColors

@Composable
fun PickerScreen(
    viewModel: PickerViewModel,
    onBack: () -> Unit,
    onDone: (openedSessionId: Long?) -> Unit,
    onCreatePack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.doneSessionId) {
        state.doneSessionId?.let { onDone(if (it >= 0) it else null) }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val date = state.date
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (date == null) "Pack now" else "Choose a pack") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    text = if (date == null) {
                        "Unscheduled packing — not added to your week."
                    } else {
                        "For ${Formats.relativeDay(date, state.today)}"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = LunchColors.Muted,
                )
            }
            val current = state.current
            if (current != null) {
                item {
                    if (date == null && current.status != SessionStatus.SAVED) {
                        Surface(color = LunchColors.PaleBlue, shape = MaterialTheme.shapes.medium) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Unfinished: ${current.packName}", style = MaterialTheme.typography.titleSmall)
                                    Text(PackingRules.progressLabel(current.packedCount, current.totalCount))
                                }
                                Button(onClick = { viewModel.resume(current.id) }, modifier = Modifier.heightIn(min = 48.dp)) {
                                    Text("Resume")
                                }
                            }
                        }
                    } else if (date != null) {
                        InfoCard("Currently planned: ${current.packName} (${current.status.label}). Choosing a pack replaces this plan; saved history is kept.")
                    }
                }
            }
            if (!state.loading && state.templates.isEmpty()) {
                item {
                    EmptyState("No packs yet.") {
                        Button(onClick = onCreatePack, modifier = Modifier.heightIn(min = 48.dp)) { Text("Create a pack") }
                    }
                }
            }
            items(state.templates, key = { it.id }) { template ->
                PickerRow(template, enabled = !state.busy && template.items.isNotEmpty()) {
                    viewModel.choose(template.id)
                }
            }
            if (state.templates.isNotEmpty()) {
                item {
                    TextButton(onClick = onCreatePack, modifier = Modifier.heightIn(min = 48.dp)) { Text("Create a new pack") }
                }
            }
        }
    }

    val pending = state.pendingReplace
    if (pending != null) {
        ConfirmDialog(
            title = "Replace “${pending.currentPackName}”?",
            message = "It already has packing progress. Replacing it starts a new checklist; anything you saved stays in History.",
            confirmLabel = "Replace",
            onConfirm = { viewModel.choose(pending.templateId, confirmed = true) },
            onDismiss = viewModel::dismissReplace,
        )
    }
}

@Composable
private fun PickerRow(template: PackTemplate, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Choose ${template.name}", onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (template.favorite) {
                        Icon(Icons.Filled.Favorite, contentDescription = "Favorite", tint = LunchColors.Error)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(template.name, style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = "${template.items.size} ${if (template.items.size == 1) "item" else "items"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = LunchColors.Muted,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                template.items.take(4).forEach { ItemIconImage(it.icon, 22.dp) }
            }
        }
    }
}
