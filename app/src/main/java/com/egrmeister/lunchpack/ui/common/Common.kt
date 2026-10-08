package com.egrmeister.lunchpack.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.egrmeister.lunchpack.R
import com.egrmeister.lunchpack.domain.ItemIcon
import com.egrmeister.lunchpack.domain.SessionStatus
import com.egrmeister.lunchpack.ui.theme.LunchColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@DrawableRes
fun ItemIcon.drawable(): Int = when (this) {
    ItemIcon.SANDWICH -> R.drawable.ic_item_sandwich
    ItemIcon.CONTAINER -> R.drawable.ic_item_container
    ItemIcon.FRUIT -> R.drawable.ic_item_fruit
    ItemIcon.SNACK_POUCH -> R.drawable.ic_item_snack_pouch
    ItemIcon.BOTTLE -> R.drawable.ic_item_bottle
    ItemIcon.NAPKIN -> R.drawable.ic_item_napkin
    ItemIcon.SPOON -> R.drawable.ic_item_spoon
    ItemIcon.FORK -> R.drawable.ic_item_fork
    ItemIcon.CUTLERY -> R.drawable.ic_item_cutlery
    ItemIcon.GENERIC -> R.drawable.ic_item_generic
}

/** Decorative item illustration; the surrounding row or chip carries the text label. */
@Composable
fun ItemIconImage(icon: ItemIcon, size: Dp = 28.dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(icon.drawable()),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier
            .padding(top = 8.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
fun StatusBadge(status: SessionStatus, savedComplete: Boolean, modifier: Modifier = Modifier) {
    val (label, color) = when (status) {
        SessionStatus.NOT_STARTED -> "Not started" to LunchColors.CreamDeep
        SessionStatus.IN_PROGRESS -> "In progress" to LunchColors.PaleBlue
        SessionStatus.SAVED -> (if (savedComplete) "Saved" else "Saved · Partial") to LunchColors.TealSoft
        SessionStatus.NEEDS_REVIEW -> "Needs review" to LunchColors.Peach
    }
    Badge(label, color, modifier)
}

@Composable
fun Badge(label: String, color: Color, modifier: Modifier = Modifier) {
    Surface(color = color, shape = MaterialTheme.shapes.small, modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = LunchColors.Navy,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) action()
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancel",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } },
    )
}

@Composable
fun InfoCard(text: String, modifier: Modifier = Modifier, color: Color = LunchColors.PaleBlue) {
    Surface(color = color, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = LunchColors.Navy,
            modifier = Modifier.padding(14.dp),
        )
    }
}

/** A labelled row with minimum 48 dp height for settings-like lists. */
@Composable
fun MinHeightRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(modifier = modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { content() }
    }
}

object Formats {
    private val dayFormatter: DateTimeFormatter
        get() = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    private val longDayFormatter: DateTimeFormatter
        get() = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())
    private val shortMonthDay: DateTimeFormatter
        get() = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

    fun day(date: LocalDate): String = date.format(dayFormatter)

    fun longDay(date: LocalDate): String = date.format(longDayFormatter)

    fun monthDay(date: LocalDate): String = date.format(shortMonthDay)

    fun relativeDay(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today · ${day(date)}"
        today.plusDays(1) -> "Tomorrow · ${day(date)}"
        else -> longDay(date)
    }

    fun savedAt(instant: Instant, zone: ZoneId): String {
        val local = instant.atZone(zone)
        val date = local.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        val time = local.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        return "$date, $time"
    }

    fun time(hour: Int, minute: Int): String =
        java.time.LocalTime.of(hour, minute).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
}
