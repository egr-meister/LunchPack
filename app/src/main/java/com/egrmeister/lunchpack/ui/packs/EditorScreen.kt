@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.egrmeister.lunchpack.ui.packs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.egrmeister.lunchpack.domain.Compartment
import com.egrmeister.lunchpack.domain.ItemIcon
import com.egrmeister.lunchpack.domain.Limits
import com.egrmeister.lunchpack.domain.TemplateError
import com.egrmeister.lunchpack.ui.common.ConfirmDialog
import com.egrmeister.lunchpack.ui.common.EmptyState
import com.egrmeister.lunchpack.ui.common.ItemIconImage
import com.egrmeister.lunchpack.ui.theme.LunchColors

@Composable
fun EditorScreen(viewModel: EditorViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.saved) { if (state.saved) onClose() }

    val requestClose: () -> Unit = {
        if (state.dirty) {
            confirmDiscard = true
        } else {
            onClose()
        }
    }
    // Predictive-back compatible: only intercepts Back while there are unsaved edits.
    BackHandler(enabled = state.dirty && !state.saved) { confirmDiscard = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New pack" else "Edit pack") },
                navigationIcon = {
                    IconButton(onClick = requestClose) { Icon(Icons.Filled.Close, contentDescription = "Cancel") }
                },
                actions = {
                    TextButton(
                        onClick = viewModel::save,
                        enabled = !state.saving && !state.loading && !state.missing,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Save") }
                },
            )
        },
    ) { padding ->
        if (state.missing) {
            EmptyState("This pack no longer exists.", Modifier.padding(padding)) {
                OutlinedButton(onClick = onClose) { Text("Back to packs") }
            }
        } else if (!state.loading) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.errors.isNotEmpty()) {
                    item {
                        Surface(color = LunchColors.Peach, shape = MaterialTheme.shapes.medium) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text("Please fix:", style = MaterialTheme.typography.titleSmall)
                                state.errors.forEach { Text("• ${it.message}", style = MaterialTheme.typography.bodyMedium) }
                            }
                        }
                    }
                }
                item {
                    val nameError = state.errors.any {
                        it is TemplateError.PackNameBlank || it is TemplateError.PackNameTooLong ||
                            it is TemplateError.DuplicatePackName
                    }
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = viewModel::setName,
                        label = { Text("Pack name") },
                        singleLine = true,
                        isError = nameError,
                        supportingText = { Text("${state.name.trim().length}/${Limits.PACK_NAME_MAX}") },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Text("Favorite", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(checked = state.favorite, onCheckedChange = viewModel::setFavorite)
                    }
                }
                item {
                    Text(
                        "Items (${state.items.size}/${Limits.MAX_ITEMS})",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                itemsIndexed(state.items, key = { _, item -> item.key }) { index, item ->
                    ItemEditor(
                        index = index,
                        item = item,
                        isFirst = index == 0,
                        isLast = index == state.items.lastIndex,
                        errors = state.errors,
                        onChange = { transform -> viewModel.updateItem(item.key, transform) },
                        onMove = { delta -> viewModel.move(item.key, delta) },
                        onRemove = { viewModel.removeItem(item.key) },
                    )
                }
                item {
                    OutlinedButton(
                        onClick = viewModel::addItem,
                        enabled = state.canAddItem,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(if (state.canAddItem) "Add item" else "Item limit reached")
                    }
                }
            }
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard changes?",
            message = "Your unsaved edits to this pack will be lost.",
            confirmLabel = "Discard",
            dismissLabel = "Keep editing",
            onConfirm = {
                confirmDiscard = false
                onClose()
            },
            onDismiss = { confirmDiscard = false },
        )
    }
}

@Composable
private fun ItemEditor(
    index: Int,
    item: EditableItem,
    isFirst: Boolean,
    isLast: Boolean,
    errors: List<TemplateError>,
    onChange: ((EditableItem) -> EditableItem) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    val nameError = errors.any {
        (it is TemplateError.ItemNameBlank && it.index == index) ||
            (it is TemplateError.ItemNameTooLong && it.index == index)
    }
    val noteError = errors.any { it is TemplateError.NoteTooLong && it.index == index }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ItemIconImage(item.icon, 28.dp)
                Spacer(Modifier.size(8.dp))
                Text("Item ${index + 1}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { onMove(-1) }, enabled = !isFirst) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move item ${index + 1} up")
                }
                IconButton(onClick = { onMove(1) }, enabled = !isLast) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move item ${index + 1} down")
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove item ${index + 1}")
                }
            }
            OutlinedTextField(
                value = item.name,
                onValueChange = { v -> onChange { it.copy(name = v.take(Limits.ITEM_NAME_MAX + 10)) } },
                label = { Text("Item name") },
                singleLine = true,
                isError = nameError,
                supportingText = { Text("${item.name.trim().length}/${Limits.ITEM_NAME_MAX}") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Compartment", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Compartment.entries.forEach { c ->
                    FilterChip(
                        selected = item.compartment == c,
                        onClick = { onChange { it.copy(compartment = c) } },
                        label = { Text(c.label) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            Text("Icon", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ItemIcon.entries.forEach { icon -> IconChoice(icon, item.icon == icon) { onChange { it.copy(icon = icon) } } }
            }
            OutlinedTextField(
                value = item.note,
                onValueChange = { v -> onChange { it.copy(note = v.take(Limits.NOTE_MAX + 10)) } },
                label = { Text("Note (optional)") },
                isError = noteError,
                supportingText = { Text("${item.note.trim().length}/${Limits.NOTE_MAX}") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun IconChoice(icon: ItemIcon, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) LunchColors.TealSoft else MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.small,
        border = if (selected) BorderStroke(2.dp, LunchColors.TealDark) else null,
        modifier = Modifier
            .size(48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = icon.label },
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            ItemIconImage(icon, 30.dp)
        }
    }
}
