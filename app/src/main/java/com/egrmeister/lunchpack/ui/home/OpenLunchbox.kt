package com.egrmeister.lunchpack.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.egrmeister.lunchpack.domain.Compartment
import com.egrmeister.lunchpack.domain.PackingRules
import com.egrmeister.lunchpack.domain.SessionItem
import com.egrmeister.lunchpack.ui.common.ItemIconImage
import com.egrmeister.lunchpack.ui.theme.LocalReducedMotion
import com.egrmeister.lunchpack.ui.theme.LunchColors

private val IconSlot = 40.dp
private val IconSize = 32.dp

private fun Compartment.fill(): Color = when (this) {
    Compartment.MAIN, Compartment.EXTRAS -> LunchColors.Peach
    Compartment.SIDES, Compartment.SNACK, Compartment.BOTTLE -> LunchColors.PaleBlue
}

/**
 * The open lunchbox: four labelled compartments inside a teal frame plus a bottle pocket
 * beside it. Packed items show their icon in their compartment; icons never overlap — extra
 * items collapse into a "+N more" control that filters the checklist to that compartment.
 */
@Composable
fun OpenLunchbox(
    items: List<SessionItem>,
    selected: Compartment?,
    onSelect: (Compartment) -> Unit,
    modifier: Modifier = Modifier,
) {
    val packed = PackingRules.packedByCompartment(items)
    val totals = Compartment.entries.associateWith { c -> items.count { it.compartment == c } }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val boxWidth = maxWidth
        val stacked = boxWidth < 340.dp
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BoxFrame {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Compartment.inBox.forEach { c ->
                            CompartmentCell(c, packed.getValue(c), totals.getValue(c), c == selected, onSelect, 2)
                        }
                    }
                }
                CompartmentCell(
                    Compartment.BOTTLE,
                    packed.getValue(Compartment.BOTTLE),
                    totals.getValue(Compartment.BOTTLE),
                    selected == Compartment.BOTTLE,
                    onSelect,
                    1,
                    label = "Bottle pocket",
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BoxFrame(Modifier.weight(1f)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CompartmentCell(
                                Compartment.MAIN, packed.getValue(Compartment.MAIN), totals.getValue(Compartment.MAIN),
                                selected == Compartment.MAIN, onSelect, 2, Modifier.weight(1.25f),
                            )
                            CompartmentCell(
                                Compartment.SIDES, packed.getValue(Compartment.SIDES), totals.getValue(Compartment.SIDES),
                                selected == Compartment.SIDES, onSelect, 2, Modifier.weight(1f),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CompartmentCell(
                                Compartment.SNACK, packed.getValue(Compartment.SNACK), totals.getValue(Compartment.SNACK),
                                selected == Compartment.SNACK, onSelect, 2, Modifier.weight(1f),
                            )
                            CompartmentCell(
                                Compartment.EXTRAS, packed.getValue(Compartment.EXTRAS), totals.getValue(Compartment.EXTRAS),
                                selected == Compartment.EXTRAS, onSelect, 2, Modifier.weight(1.25f),
                            )
                        }
                    }
                }
                CompartmentCell(
                    Compartment.BOTTLE,
                    packed.getValue(Compartment.BOTTLE),
                    totals.getValue(Compartment.BOTTLE),
                    selected == Compartment.BOTTLE,
                    onSelect,
                    3,
                    Modifier.width(if (boxWidth < 420.dp) 80.dp else 100.dp),
                    label = "Bottle",
                )
            }
        }
    }
}

@Composable
private fun BoxFrame(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        color = LunchColors.Teal,
        shape = MaterialTheme.shapes.large,
        modifier = modifier,
    ) {
        Box(Modifier.padding(10.dp)) { content() }
    }
}

@Composable
private fun CompartmentCell(
    compartment: Compartment,
    packedItems: List<SessionItem>,
    total: Int,
    isSelected: Boolean,
    onSelect: (Compartment) -> Unit,
    maxRows: Int,
    modifier: Modifier = Modifier,
    label: String = compartment.label,
) {
    val reduced = LocalReducedMotion.current
    val names = packedItems.joinToString { it.name }
    val description = buildString {
        append("$label compartment, ${packedItems.size} of $total packed")
        if (names.isNotEmpty()) append(": $names")
        append(if (isSelected) ". Showing only this compartment." else ".")
    }
    Surface(
        color = compartment.fill(),
        shape = MaterialTheme.shapes.medium,
        border = if (isSelected) BorderStroke(3.dp, LunchColors.Navy) else null,
        modifier = modifier
            .heightIn(min = 96.dp)
            .clickable(role = Role.Button, onClickLabel = "Show $label items") { onSelect(compartment) }
            .semantics(mergeDescendants = true) {
                contentDescription = description
                selected = isSelected
            },
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .then(if (reduced) Modifier else Modifier.animateContentSize()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(color = Color.White.copy(alpha = 0.85f), shape = MaterialTheme.shapes.small) {
                Text(
                    text = if (total > 0) "$label · ${packedItems.size}/$total" else label,
                    style = MaterialTheme.typography.labelMedium,
                    color = LunchColors.Navy,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            IconGrid(packedItems, maxRows)
        }
    }
}

@Composable
private fun IconGrid(packedItems: List<SessionItem>, maxRows: Int) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val perRow = (maxWidth / IconSlot).toInt().coerceAtLeast(1)
        val (visible, hidden) = PackingRules.iconOverflow(packedItems.size, perRow * maxRows)
        if (packedItems.isEmpty()) {
            Text(
                text = "Empty",
                style = MaterialTheme.typography.bodySmall,
                color = LunchColors.Muted,
            )
        } else {
            val slots: List<SessionItem?> = packedItems.take(visible) + if (hidden > 0) listOf(null) else emptyList()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                slots.chunked(perRow).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        row.forEach { item ->
                            Box(Modifier.size(IconSlot), contentAlignment = Alignment.Center) {
                                if (item != null) {
                                    ItemIconImage(item.icon, IconSize)
                                } else {
                                    MoreBadge(hidden, IconSlot)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreBadge(hidden: Int, size: Dp) {
    Surface(color = LunchColors.Navy, shape = MaterialTheme.shapes.small, modifier = Modifier.size(size - 4.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "+$hidden",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
    }
}
