package com.egrmeister.lunchpack.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID

/** Injected clock so date, "today/tomorrow" and reminder logic are testable. */
interface AppClock {
    fun now(): Instant
    fun zone(): ZoneId
}

/** Current local date in the device time zone. */
fun AppClock.today(): LocalDate = now().atZone(zone()).toLocalDate()

object SystemAppClock : AppClock {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

fun interface IdGenerator {
    fun newId(): String

    companion object {
        val Random = IdGenerator { UUID.randomUUID().toString() }
    }
}

object Limits {
    const val MAX_TEMPLATES = 30
    const val MAX_ITEMS = 30
    const val PACK_NAME_MAX = 30
    const val ITEM_NAME_MAX = 40
    const val NOTE_MAX = 100
    const val HISTORY_KEEP = 100
    const val PLAN_DAYS_AHEAD = 30L
}

object PackingRules {
    /**
     * Status derived from revisions:
     * - saved and unchanged since → SAVED
     * - saved earlier, changed since → NEEDS_REVIEW (a new revision must be saved)
     * - never saved → IN_PROGRESS when something is packed, otherwise NOT_STARTED
     */
    fun status(revision: Int, savedRevision: Int?, packedCount: Int): SessionStatus = when {
        savedRevision != null && savedRevision == revision -> SessionStatus.SAVED
        savedRevision != null -> SessionStatus.NEEDS_REVIEW
        packedCount > 0 -> SessionStatus.IN_PROGRESS
        else -> SessionStatus.NOT_STARTED
    }

    fun progressLabel(packed: Int, total: Int): String = "$packed of $total packed."

    /** Favorites first, then by name. The list still holds each template exactly once. */
    fun sortForPicker(templates: List<PackTemplate>): List<PackTemplate> =
        templates.sortedWith(compareByDescending<PackTemplate> { it.favorite }.thenBy { it.name.lowercase() })

    /** Packed items per compartment, in display order. */
    fun packedByCompartment(items: List<SessionItem>): Map<Compartment, List<SessionItem>> =
        Compartment.entries.associateWith { c ->
            items.filter { it.packed && it.compartment == c }.sortedBy { it.displayOrder }
        }

    /**
     * How many icons fit in a compartment without overlapping. When there are more items than
     * slots, the last slot becomes a "+N more" control: returns (visibleIcons, hiddenCount).
     */
    fun iconOverflow(count: Int, capacity: Int): Pair<Int, Int> {
        val cap = capacity.coerceAtLeast(1)
        return if (count <= cap) count to 0 else (cap - 1) to (count - (cap - 1))
    }
}

/** Planning window and calendar weeks for the Week screen. */
object PlanningWindow {
    fun lastPlannableDate(today: LocalDate): LocalDate = today.plusDays(Limits.PLAN_DAYS_AHEAD)

    fun contains(today: LocalDate, date: LocalDate): Boolean =
        !date.isBefore(today) && !date.isAfter(lastPlannableDate(today))

    fun weekStart(date: LocalDate, firstDayOfWeek: DayOfWeek): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))

    fun weekDays(weekStart: LocalDate): List<LocalDate> = (0L..6L).map { weekStart.plusDays(it) }

    fun firstWeekStart(today: LocalDate, firstDayOfWeek: DayOfWeek): LocalDate = weekStart(today, firstDayOfWeek)

    fun lastWeekStart(today: LocalDate, firstDayOfWeek: DayOfWeek): LocalDate =
        weekStart(lastPlannableDate(today), firstDayOfWeek)

    /** Clamps a requested week start into the weeks that intersect the planning window. */
    fun clampWeekStart(requested: LocalDate, today: LocalDate, firstDayOfWeek: DayOfWeek): LocalDate {
        val start = weekStart(requested, firstDayOfWeek)
        val min = firstWeekStart(today, firstDayOfWeek)
        val max = lastWeekStart(today, firstDayOfWeek)
        return when {
            start.isBefore(min) -> min
            start.isAfter(max) -> max
            else -> start
        }
    }

    fun canGoPrevious(weekStart: LocalDate, today: LocalDate, firstDayOfWeek: DayOfWeek): Boolean =
        weekStart.isAfter(firstWeekStart(today, firstDayOfWeek))

    fun canGoNext(weekStart: LocalDate, today: LocalDate, firstDayOfWeek: DayOfWeek): Boolean =
        weekStart.isBefore(lastWeekStart(today, firstDayOfWeek))

    enum class DayKind { PAST, TODAY, TOMORROW, UPCOMING, BEYOND_WINDOW }

    fun dayKind(date: LocalDate, today: LocalDate): DayKind = when {
        date.isBefore(today) -> DayKind.PAST
        date == today -> DayKind.TODAY
        date == today.plusDays(1) -> DayKind.TOMORROW
        date.isAfter(lastPlannableDate(today)) -> DayKind.BEYOND_WINDOW
        else -> DayKind.UPCOMING
    }
}
