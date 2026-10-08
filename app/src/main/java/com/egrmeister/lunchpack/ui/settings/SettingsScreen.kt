@file:OptIn(ExperimentalMaterial3Api::class)

package com.egrmeister.lunchpack.ui.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.egrmeister.lunchpack.domain.ReminderStatus
import com.egrmeister.lunchpack.ui.common.ConfirmDialog
import com.egrmeister.lunchpack.ui.common.Formats
import com.egrmeister.lunchpack.ui.common.InfoCard
import com.egrmeister.lunchpack.ui.common.SectionHeader
import com.egrmeister.lunchpack.ui.theme.LunchColors

private enum class SettingsDialog { RESTORE, CLEAR_HISTORY, CLEAR_ALL, TIME }

private fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Settings app unavailable; the blocked state stays visible.
    }
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onManagePacks: () -> Unit,
    onPrivacy: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    // POST_NOTIFICATIONS is requested only here, right after the user turns reminders on.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refreshPermission()
    }

    val onReminderToggle: (Boolean) -> Unit = { enabled ->
        viewModel.setReminderEnabled(enabled)
        if (enabled && needsNotificationPermission(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SectionHeader("Packs")
            ActionRow("Manage packs", "Create, edit, duplicate, delete and favorite packs.", onClick = onManagePacks)
            ActionRow("Restore starter packs", "Adds the three example packs again with unique names.") {
                dialog = SettingsDialog.RESTORE
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionHeader("Reminder")
            SwitchRow(
                title = "Daily reminder",
                subtitle = "Only when tomorrow has a planned pack that isn't saved as complete.",
                checked = state.reminder.enabled,
                onChange = onReminderToggle,
            )
            ActionRow(
                title = "Reminder time",
                subtitle = Formats.time(state.reminder.hour, state.reminder.minute),
                enabled = state.reminder.enabled,
            ) { dialog = SettingsDialog.TIME }
            when (state.reminderStatus) {
                ReminderStatus.BLOCKED -> {
                    InfoCard(
                        "Notifications are blocked for LunchPack, so reminders can't appear. Planning and packing still work.",
                        color = LunchColors.Peach,
                    )
                    OutlinedButton(
                        onClick = { openNotificationSettings(context) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Open notification settings") }
                }
                ReminderStatus.ACTIVE -> InfoCard("Reminder on for ${Formats.time(state.reminder.hour, state.reminder.minute)}.")
                ReminderStatus.OFF -> Unit
            }
            Text(
                "Delivery is approximate: Android may show the reminder somewhat later than the chosen time to save " +
                    "battery. A reminder is skipped if it would arrive on a later day. If you force-stop LunchPack, " +
                    "Android cancels its reminders until you open the app again.",
                style = MaterialTheme.typography.bodySmall,
                color = LunchColors.Muted,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionHeader("Display")
            SwitchRow(
                title = "Reduce animation",
                subtitle = "Skips decorative motion in the lunchbox.",
                checked = state.reducedMotion,
                onChange = viewModel::setReducedMotion,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionHeader("Data")
            ActionRow("Clear history", "Deletes all saved results. Packs and plans stay.") {
                dialog = SettingsDialog.CLEAR_HISTORY
            }
            ActionRow("Clear all local data", "Deletes packs, plans, history and settings, then restores the examples.") {
                dialog = SettingsDialog.CLEAR_ALL
            }
            ActionRow("Privacy", "What LunchPack stores and why it works offline.", onClick = onPrivacy)
            Text(
                "LunchPack 1.0.0 · works fully offline",
                style = MaterialTheme.typography.bodySmall,
                color = LunchColors.Muted,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }

    when (dialog) {
        SettingsDialog.RESTORE -> ConfirmDialog(
            title = "Restore starter packs?",
            message = "Everyday Box, Snack Break and Outing Pack are added as new packs. Your packs, plans and " +
                "history are kept; names get a number if they are already used.",
            confirmLabel = "Restore",
            onConfirm = {
                dialog = null
                viewModel.restoreStarters()
            },
            onDismiss = { dialog = null },
        )
        SettingsDialog.CLEAR_HISTORY -> ConfirmDialog(
            title = "Clear history?",
            message = "All saved results will be deleted. Packs and plans are not affected.",
            confirmLabel = "Clear history",
            onConfirm = {
                dialog = null
                viewModel.clearHistory()
            },
            onDismiss = { dialog = null },
        )
        SettingsDialog.CLEAR_ALL -> ConfirmDialog(
            title = "Clear all local data?",
            message = "This cancels reminders and deletes every pack, plan, saved result and setting on this device. " +
                "The example packs are then restored. This can't be undone.",
            confirmLabel = "Clear everything",
            onConfirm = {
                dialog = null
                viewModel.clearAllData()
            },
            onDismiss = { dialog = null },
        )
        SettingsDialog.TIME -> TimeDialog(
            hour = state.reminder.hour,
            minute = state.reminder.minute,
            is24h = DateFormat.is24HourFormat(context),
            onConfirm = { h, m ->
                dialog = null
                viewModel.setReminderTime(h, m)
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun TimeDialog(hour: Int, minute: Int, is24h: Boolean, onConfirm: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val pickerState = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = is24h)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reminder time") },
        text = { TimePicker(state = pickerState) },
        confirmButton = { TextButton(onClick = { onConfirm(pickerState.hour, pickerState.minute) }) { Text("Set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ActionRow(title: String, subtitle: String, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else LunchColors.Muted,
        )
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LunchColors.Muted)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LunchColors.Muted)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
