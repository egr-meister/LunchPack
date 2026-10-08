@file:OptIn(ExperimentalMaterial3Api::class)

package com.egrmeister.lunchpack.ui.packs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.egrmeister.lunchpack.domain.Compartment
import com.egrmeister.lunchpack.domain.Limits
import com.egrmeister.lunchpack.domain.PackTemplate
import com.egrmeister.lunchpack.ui.common.ConfirmDialog
import com.egrmeister.lunchpack.ui.common.EmptyState
import com.egrmeister.lunchpack.ui.common.InfoCard
import com.egrmeister.lunchpack.ui.common.ItemIconImage
import com.egrmeister.lunchpack.ui.theme.LunchColors

@Composable
fun PacksScreen(
    viewModel: PacksViewModel,
    onEdit: (Long?) -> Unit,
    onPackNow: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pendingDelete by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Packs") },
                actions = {
                    TextButton(
                        onClick = { onEdit(null) },
                        enabled = state.templates.size < Limits.MAX_TEMPLATES,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text("New pack")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                InfoCard(
                    "Packs are reusable checklists of what goes where. Everyday Box, Snack Break and Outing Pack " +
                        "are editable examples — not meal recommendations.",
                )
            }
            item {
                Text(
                    text = "${state.templates.size} of ${Limits.MAX_TEMPLATES} packs · favorites first",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LunchColors.Muted,
                )
            }
            if (!state.loading && state.templates.isEmpty()) {
                item {
                    EmptyState("No packs yet. Create one, or restore the examples in Settings.") {
                        Button(onClick = { onEdit(null) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Create a pack")
                        }
                    }
                }
            }
            items(state.templates, key = { it.id }) { template ->
                TemplateCard(
                    template = template,
                    onToggleFavorite = { viewModel.toggleFavorite(template.id) },
                    onEdit = { onEdit(template.id) },
                    onDuplicate = { viewModel.duplicate(template.id) },
                    onDelete = { pendingDelete = template.id },
                )
            }
            if (state.templates.isNotEmpty()) {
                item {
                    TextButton(onClick = onPackNow, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Pack one now without planning a date")
                    }
                }
            }
        }
    }

    val deleting = state.templates.firstOrNull { it.id == pendingDelete }
    if (deleting != null) {
        ConfirmDialog(
            title = "Delete “${deleting.name}”?",
            message = "Plans already scheduled from this pack and your saved history keep their own copies. " +
                "The pack can no longer be chosen for new plans.",
            confirmLabel = "Delete",
            onConfirm = {
                pendingDelete = null
                viewModel.delete(deleting.id)
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun TemplateCard(
    template: PackTemplate,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Edit pack", onClick = onEdit),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 12.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(template.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (template.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (template.favorite) "Remove from favorites" else "Add to favorites",
                        tint = if (template.favorite) LunchColors.Error else LunchColors.Muted,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More actions for ${template.name}")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate() })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            Text(
                text = summary(template),
                style = MaterialTheme.typography.bodySmall,
                color = LunchColors.Muted,
            )
            Spacer(Modifier.heightIn(min = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.widthIn(max = 600.dp)) {
                template.items.take(10).forEach { ItemIconImage(it.icon, 24.dp) }
            }
        }
    }
}

private fun summary(template: PackTemplate): String {
    val count = template.items.size
    val places = Compartment.entries.filter { c -> template.items.any { it.compartment == c } }.joinToString { it.label }
    return "$count ${if (count == 1) "item" else "items"} · $places"
}
