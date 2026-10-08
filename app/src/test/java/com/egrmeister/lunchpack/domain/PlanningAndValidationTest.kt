package com.egrmeister.lunchpack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class PlanningAndValidationTest {
    private val today: LocalDate = LocalDate.of(2026, 10, 8) // Thursday

    @Test
    fun planningWindowIsTodayThroughThirtyDaysAhead() {
        assertFalse(PlanningWindow.contains(today, today.minusDays(1)))
        assertTrue(PlanningWindow.contains(today, today))
        assertTrue(PlanningWindow.contains(today, today.plusDays(30)))
        assertFalse(PlanningWindow.contains(today, today.plusDays(31)))
    }

    @Test
    fun weeksStartOnTheLocaleFirstDayAndHaveSevenDates() {
        val mondayStart = PlanningWindow.weekStart(today, DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 10, 5), mondayStart)
        val sundayStart = PlanningWindow.weekStart(today, DayOfWeek.SUNDAY)
        assertEquals(LocalDate.of(2026, 10, 4), sundayStart)
        val days = PlanningWindow.weekDays(mondayStart)
        assertEquals(7, days.size)
        assertEquals(LocalDate.of(2026, 10, 11), days.last())
    }

    @Test
    fun weekNavigationIsBoundedByThePlanningWindow() {
        val first = PlanningWindow.firstWeekStart(today, DayOfWeek.MONDAY)
        val last = PlanningWindow.lastWeekStart(today, DayOfWeek.MONDAY)
        assertFalse(PlanningWindow.canGoPrevious(first, today, DayOfWeek.MONDAY))
        assertTrue(PlanningWindow.canGoNext(first, today, DayOfWeek.MONDAY))
        assertFalse(PlanningWindow.canGoNext(last, today, DayOfWeek.MONDAY))
        // today + 30 = Nov 7 (Saturday) → its week starts Monday Nov 2.
        assertEquals(LocalDate.of(2026, 11, 2), last)
        assertEquals(first, PlanningWindow.clampWeekStart(first.minusWeeks(3), today, DayOfWeek.MONDAY))
        assertEquals(last, PlanningWindow.clampWeekStart(last.plusWeeks(5), today, DayOfWeek.MONDAY))
    }

    @Test
    fun dayKindsDoNotTreatUnplannedOrPastDaysAsMissed() {
        assertEquals(PlanningWindow.DayKind.PAST, PlanningWindow.dayKind(today.minusDays(1), today))
        assertEquals(PlanningWindow.DayKind.TODAY, PlanningWindow.dayKind(today, today))
        assertEquals(PlanningWindow.DayKind.TOMORROW, PlanningWindow.dayKind(today.plusDays(1), today))
        assertEquals(PlanningWindow.DayKind.UPCOMING, PlanningWindow.dayKind(today.plusDays(30), today))
        assertEquals(PlanningWindow.DayKind.BEYOND_WINDOW, PlanningWindow.dayKind(today.plusDays(31), today))
    }

    @Test
    fun weekBoundaryAcrossMonthAndYearEnd() {
        val dec31 = LocalDate.of(2026, 12, 31)
        val start = PlanningWindow.weekStart(dec31, DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 12, 28), start)
        assertEquals(LocalDate.of(2027, 1, 3), PlanningWindow.weekDays(start).last())
    }

    @Test
    fun statusFollowsRevisions() {
        assertEquals(SessionStatus.NOT_STARTED, PackingRules.status(0, null, 0))
        assertEquals(SessionStatus.IN_PROGRESS, PackingRules.status(1, null, 1))
        assertEquals(SessionStatus.SAVED, PackingRules.status(3, 3, 2))
        assertEquals(SessionStatus.NEEDS_REVIEW, PackingRules.status(4, 3, 2))
        assertEquals(SessionStatus.NEEDS_REVIEW, PackingRules.status(4, 3, 0))
    }

    private fun item(name: String, note: String = "") = ItemDraft(null, name, note, Compartment.MAIN, ItemIcon.GENERIC)

    @Test
    fun validationLimits() {
        val ok = TemplateDraft(null, "Box", false, listOf(item("Fork")))
        assertTrue(TemplateValidator.validate(ok, emptyList(), 0).isEmpty())

        assertTrue(TemplateError.PackNameBlank in TemplateValidator.validate(ok.copy(name = "   "), emptyList(), 0))
        assertTrue(TemplateError.PackNameTooLong in TemplateValidator.validate(ok.copy(name = "x".repeat(31)), emptyList(), 0))
        assertTrue(TemplateValidator.validate(ok.copy(name = " " + "x".repeat(30) + " "), emptyList(), 0).isEmpty())
        assertTrue(TemplateError.NoItems in TemplateValidator.validate(ok.copy(items = emptyList()), emptyList(), 0))
        assertTrue(TemplateError.TooManyItems in TemplateValidator.validate(ok.copy(items = List(31) { item("i$it") }), emptyList(), 0))
        assertTrue(TemplateError.ItemNameBlank(0) in TemplateValidator.validate(ok.copy(items = listOf(item(" "))), emptyList(), 0))
        assertTrue(TemplateError.ItemNameTooLong(0) in TemplateValidator.validate(ok.copy(items = listOf(item("y".repeat(41)))), emptyList(), 0))
        assertTrue(TemplateValidator.validate(ok.copy(items = listOf(item("y".repeat(40)))), emptyList(), 0).isEmpty())
        assertTrue(TemplateError.NoteTooLong(0) in TemplateValidator.validate(ok.copy(items = listOf(item("a", "n".repeat(101)))), emptyList(), 0))
        assertTrue(TemplateError.TooManyTemplates in TemplateValidator.validate(ok, emptyList(), 30))
        // Editing an existing template is allowed at the limit and may keep its own name.
        assertTrue(TemplateValidator.validate(ok.copy(id = 7, name = "BOX"), listOf(7L to "Box"), 30).isEmpty())
        assertTrue(TemplateError.DuplicatePackName in TemplateValidator.validate(ok.copy(name = "box"), listOf(7L to "Box"), 1))
    }

    @Test
    fun uniqueNamesStayWithinTheLimit() {
        assertEquals("Lunch", TemplateValidator.uniqueName("Lunch", listOf("Other")))
        assertEquals("Lunch (2)", TemplateValidator.uniqueName("Lunch", listOf("lunch")))
        assertEquals("Lunch (3)", TemplateValidator.uniqueName("Lunch", listOf("Lunch", "Lunch (2)")))
        val long = "z".repeat(30)
        val unique = TemplateValidator.uniqueName(long, listOf(long))
        assertTrue(unique.length <= Limits.PACK_NAME_MAX)
        assertTrue(unique.endsWith("(2)"))
    }

    @Test
    fun startersAreTheThreeSpecifiedExamples() {
        assertEquals(listOf("Everyday Box", "Snack Break", "Outing Pack"), Starters.packs.map { it.name })
        val outing = Starters.packs[2].items
        assertEquals(listOf("Main container", "Snack pouch", "Napkin", "Cutlery", "Water bottle"), outing.map { it.name })
        assertEquals(Compartment.BOTTLE, outing.last().compartment)
    }
}
