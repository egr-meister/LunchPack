package com.egrmeister.lunchpack.domain

import java.time.Instant
import java.time.LocalDate

/**
 * Organizational locations inside (and beside) the lunchbox. These are places to put things,
 * not nutritional categories.
 */
enum class Compartment(val key: String, val label: String) {
    MAIN("main", "Main"),
    SIDES("sides", "Sides"),
    SNACK("snack", "Snack"),
    EXTRAS("extras", "Extras"),
    BOTTLE("bottle", "Bottle");

    companion object {
        /** The four compartments drawn inside the box; [BOTTLE] is the pocket beside it. */
        val inBox: List<Compartment> = listOf(MAIN, SIDES, SNACK, EXTRAS)

        fun fromKey(key: String?): Compartment = entries.firstOrNull { it.key == key } ?: EXTRAS
    }
}

/** Bundled vector illustrations for generic food and packing objects. */
enum class ItemIcon(val key: String, val label: String) {
    SANDWICH("sandwich", "Sandwich"),
    CONTAINER("container", "Container"),
    FRUIT("fruit", "Fruit"),
    SNACK_POUCH("snack_pouch", "Snack pouch"),
    BOTTLE("bottle", "Bottle"),
    NAPKIN("napkin", "Napkin"),
    SPOON("spoon", "Spoon"),
    FORK("fork", "Fork"),
    CUTLERY("cutlery", "Cutlery"),
    GENERIC("generic", "Item");

    companion object {
        fun fromKey(key: String?): ItemIcon = entries.firstOrNull { it.key == key } ?: GENERIC
    }
}

enum class SessionStatus(val key: String, val label: String) {
    NOT_STARTED("not_started", "Not started"),
    IN_PROGRESS("in_progress", "In progress"),
    SAVED("saved", "Saved"),
    NEEDS_REVIEW("needs_review", "Needs review");

    companion object {
        fun fromKey(key: String?): SessionStatus = entries.firstOrNull { it.key == key } ?: NOT_STARTED
    }
}

data class TemplateItem(
    val id: String,
    val name: String,
    val note: String,
    val compartment: Compartment,
    val icon: ItemIcon,
    val displayOrder: Int,
)

data class PackTemplate(
    val id: Long,
    val name: String,
    val favorite: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val items: List<TemplateItem>,
)

data class SessionItem(
    val id: String,
    val sessionId: Long,
    val name: String,
    val note: String,
    val compartment: Compartment,
    val icon: ItemIcon,
    val displayOrder: Int,
    val packed: Boolean,
)

/**
 * A packing run: a dated plan (plannedDate != null) or an unscheduled session (plannedDate == null).
 * Its items are a snapshot of the template taken when the plan was assigned.
 */
data class PackingSession(
    val id: Long,
    val plannedDate: LocalDate?,
    val sourceTemplateId: Long?,
    val packName: String,
    val revision: Int,
    val savedRevision: Int?,
    val status: SessionStatus,
    val savedComplete: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val items: List<SessionItem>,
) {
    val totalCount: Int get() = items.size
    val packedCount: Int get() = items.count { it.packed }
    val allPacked: Boolean get() = items.isNotEmpty() && items.all { it.packed }

    /** Packing progress worth protecting before replacement: something packed or ever saved. */
    val hasProgress: Boolean get() = packedCount > 0 || savedRevision != null

    /** Saved, unchanged since, and the saved result was complete. */
    val isSavedComplete: Boolean get() = status == SessionStatus.SAVED && savedComplete

    val sortedItems: List<SessionItem> get() = items.sortedBy { it.displayOrder }
}

data class HistoryItem(
    val name: String,
    val note: String,
    val compartment: Compartment,
    val icon: ItemIcon,
    val displayOrder: Int,
    val packed: Boolean,
)

/** Immutable snapshot of a saved packing result. */
data class HistoryEntry(
    val id: Long,
    val sessionId: Long?,
    val revision: Int,
    val packName: String,
    val plannedDate: LocalDate?,
    val savedAt: Instant,
    val totalItems: Int,
    val packedItems: Int,
    val items: List<HistoryItem>,
) {
    val complete: Boolean get() = totalItems > 0 && packedItems == totalItems
}
