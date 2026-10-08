@file:OptIn(ExperimentalMaterial3Api::class)

package com.egrmeister.lunchpack.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.egrmeister.lunchpack.ui.common.SectionHeader

private val sections = listOf(
    "Everything stays on this device" to
        "LunchPack has no account, no server and no internet permission. Your packs, plans, checklists and " +
        "history are stored only in the app's private storage on this device.",
    "No tracking" to
        "There is no advertising, analytics, crash reporting, payments or cloud sync. Nothing you enter is sent anywhere.",
    "Backups" to
        "Android cloud backup and device-to-device transfer are turned off for LunchPack, so your data is not copied " +
        "to other services or devices. Uninstalling the app or using Clear all local data deletes it.",
    "Notifications" to
        "Reminders are optional and off by default. If you turn them on, LunchPack asks for notification permission. " +
        "Reminder text is generic and never shows item names or notes on the lock screen.",
    "Permissions" to
        "POST_NOTIFICATIONS — show the optional reminder (Android 13 and later).\n" +
        "RECEIVE_BOOT_COMPLETED — restore the reminder schedule after a restart.\n" +
        "No camera, microphone, location, contacts or calendar access.",
    "What LunchPack is not" to
        "LunchPack organizes the items you choose to pack. It does not evaluate meals, calories, diets, " +
        "nutrition, allergies or food safety, and it cannot check what is physically in your bag.",
)

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            sections.forEach { (title, body) ->
                SectionHeader(title)
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
