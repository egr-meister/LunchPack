package com.egrmeister.lunchpack.domain

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class PackingServiceTest {
    private val today: LocalDate = LocalDate.of(2026, 10, 8)
    private lateinit var clock: FixedClock
    private lateinit var ds: InMemoryDataSource
    private lateinit var service: PackingService
    private var plansChanged = 0

    @Before
    fun setUp() {
        clock = FixedClock(java.time.Instant.EPOCH).apply { setLocal(today, 9) }
        ds = InMemoryDataSource()
        plansChanged = 0
        service = PackingService(ds, clock, SequentialIds(), onPlansChanged = { plansChanged++ })
    }

    private suspend fun everydayBoxId(): Long {
        service.insertStarters()
        return ds.templates.values.first { it.name == "Everyday Box" }.id
    }

    private fun draft(name: String, vararg items: Pair<String, Compartment>, id: Long? = null) = TemplateDraft(
        id = id,
        name = name,
        favorite = false,
        items = items.map { (n, c) -> ItemDraft(null, n, "", c, ItemIcon.GENERIC) },
    )

    // ---------------------------------------------------------------- packed state & progress

    @Test
    fun togglingPersistsPackedStateRevisionAndProgress() = runTest {
        val sessionId = (service.assignPlan(today, everydayBoxId(), false) as AssignResult.Assigned).sessionId
        val session = ds.sessions.getValue(sessionId)
        assertEquals(6, session.totalCount)
        assertEquals(0, session.packedCount)
        assertEquals(SessionStatus.NOT_STARTED, session.status)

        val first = session.items[0].id
        assertEquals(true, service.toggleItem(sessionId, first))
        var now = ds.sessions.getValue(sessionId)
        assertEquals(1, now.packedCount)
        assertEquals(1, now.revision)
        assertEquals(SessionStatus.IN_PROGRESS, now.status)
        assertEquals("1 of 6 packed.", PackingRules.progressLabel(now.packedCount, now.totalCount))

        assertEquals(false, service.toggleItem(sessionId, first))
        now = ds.sessions.getValue(sessionId)
        assertEquals(0, now.packedCount)
        assertEquals(2, now.revision)
        assertEquals(SessionStatus.NOT_STARTED, now.status)
    }

    @Test
    fun startFreshClearsPackedStatesButKeepsTemplateAndHistory() = runTest {
        val templateId = everydayBoxId()
        val sessionId = (service.assignPlan(today, templateId, false) as AssignResult.Assigned).sessionId
        ds.sessions.getValue(sessionId).items.forEach { service.toggleItem(sessionId, it.id) }
        assertTrue(service.save(sessionId, allowPartial = false) is SaveResult.Saved)

        assertTrue(service.startFresh(sessionId))
        val session = ds.sessions.getValue(sessionId)
        assertEquals(0, session.packedCount)
        assertEquals(SessionStatus.NEEDS_REVIEW, session.status)
        assertEquals(1, ds.history.size)
        assertEquals(6, ds.templates.getValue(templateId).items.size)
    }

    // ---------------------------------------------------------------- compartments

    @Test
    fun itemsKeepTheirCompartmentAndPackedIconsGroupByCompartment() = runTest {
        val sessionId = (service.assignPlan(today, everydayBoxId(), false) as AssignResult.Assigned).sessionId
        val items = ds.sessions.getValue(sessionId).items
        val byName = items.associateBy { it.name }
        assertEquals(Compartment.MAIN, byName.getValue("Main container").compartment)
        assertEquals(Compartment.SIDES, byName.getValue("Fruit container").compartment)
        assertEquals(Compartment.SNACK, byName.getValue("Snack").compartment)
        assertEquals(Compartment.EXTRAS, byName.getValue("Napkin").compartment)
        assertEquals(Compartment.BOTTLE, byName.getValue("Water bottle").compartment)

        service.toggleItem(sessionId, byName.getValue("Napkin").id)
        service.toggleItem(sessionId, byName.getValue("Spoon").id)
        val grouped = PackingRules.packedByCompartment(ds.sessions.getValue(sessionId).items)
        assertEquals(listOf("Napkin", "Spoon"), grouped.getValue(Compartment.EXTRAS).map { it.name })
        assertTrue(grouped.getValue(Compartment.MAIN).isEmpty())

        // Unchecking removes the icon from its compartment.
        service.toggleItem(sessionId, byName.getValue("Napkin").id)
        val after = PackingRules.packedByCompartment(ds.sessions.getValue(sessionId).items)
        assertEquals(listOf("Spoon"), after.getValue(Compartment.EXTRAS).map { it.name })
    }

    @Test
    fun iconOverflowNeverHidesItemsSilently() {
        assertEquals(3 to 0, PackingRules.iconOverflow(3, 4))
        assertEquals(4 to 0, PackingRules.iconOverflow(4, 4))
        assertEquals(3 to 2, PackingRules.iconOverflow(5, 4))
        assertEquals(0 to 9, PackingRules.iconOverflow(9, 1))
    }

    // ---------------------------------------------------------------- snapshots

    @Test
    fun planIsASnapshotUntilExplicitUpdateFromTemplate() = runTest {
        val id = (service.saveTemplate(draft("Work Box", "Main" to Compartment.MAIN, "Fork" to Compartment.EXTRAS)) as TemplateSaveResult.Saved).id
        val planId = (service.assignPlan(today.plusDays(1), id, false) as AssignResult.Assigned).sessionId
        service.toggleItem(planId, ds.sessions.getValue(planId).items[0].id)

        val template = ds.templates.getValue(id)
        service.saveTemplate(
            TemplateDraft(
                id = id,
                name = "Work Box v2",
                favorite = false,
                items = template.items.map { ItemDraft(it.id, it.name, it.note, it.compartment, it.icon) } +
                    ItemDraft(null, "Napkin", "", Compartment.EXTRAS, ItemIcon.NAPKIN),
            ),
        )
        var plan = ds.sessions.getValue(planId)
        assertEquals("Work Box", plan.packName)
        assertEquals(2, plan.totalCount)
        assertEquals(1, plan.packedCount)

        assertEquals(UpdateResult.Updated, service.updateFromTemplate(planId))
        plan = ds.sessions.getValue(planId)
        assertEquals("Work Box v2", plan.packName)
        assertEquals(3, plan.totalCount)
        assertEquals(0, plan.packedCount) // progress restarted
    }

    @Test
    fun historyIsNotRewrittenByTemplateChanges() = runTest {
        val id = everydayBoxId()
        val planId = (service.assignPlan(today, id, false) as AssignResult.Assigned).sessionId
        ds.sessions.getValue(planId).items.forEach { service.toggleItem(planId, it.id) }
        service.save(planId, allowPartial = false)
        val template = ds.templates.getValue(id)
        service.saveTemplate(
            TemplateDraft(id, "Renamed", false, template.items.take(1).map { ItemDraft(it.id, "Changed", "", it.compartment, it.icon) }),
        )
        val entry = ds.history.values.single()
        assertEquals("Everyday Box", entry.packName)
        assertEquals(6, entry.items.size)
        assertEquals("Main container", entry.items.first().name)
    }

    // ---------------------------------------------------------------- favorites

    @Test
    fun favoritesToggleTheSameRecordAndSortFirst() = runTest {
        service.insertStarters()
        val before = ds.templates.size
        val outing = ds.templates.values.first { it.name == "Outing Pack" }
        assertEquals(true, service.toggleFavorite(outing.id))
        assertEquals(before, ds.templates.size)
        assertEquals(1, ds.templates.values.count { it.name == "Outing Pack" })
        val sorted = PackingRules.sortForPicker(ds.templates.values.toList())
        assertEquals("Outing Pack", sorted.first().name)
        assertEquals(before, sorted.size)
        assertEquals(false, service.toggleFavorite(outing.id))
        assertFalse(ds.templates.getValue(outing.id).favorite)
    }

    // ---------------------------------------------------------------- templates & validation

    @Test
    fun duplicateNamesAreRejectedIgnoringCase() = runTest {
        service.saveTemplate(draft("Daily", "A" to Compartment.MAIN))
        val result = service.saveTemplate(draft("  dAILY ", "B" to Compartment.MAIN))
        assertTrue(result is TemplateSaveResult.Invalid)
        assertTrue(TemplateError.DuplicatePackName in (result as TemplateSaveResult.Invalid).errors)
    }

    @Test
    fun duplicateCreatesAUniqueCopyWithNewItemIds() = runTest {
        val id = everydayBoxId()
        val result = service.duplicateTemplate(id) as DuplicateResult.Duplicated
        assertEquals("Everyday Box (2)", result.name)
        val copy = ds.templates.getValue(result.id)
        val original = ds.templates.getValue(id)
        assertEquals(original.items.map { it.name }, copy.items.map { it.name })
        assertTrue(copy.items.map { it.id }.intersect(original.items.map { it.id }.toSet()).isEmpty())
    }

    @Test
    fun restoreStartersKeepsExistingDataAndUsesUniqueNames() = runTest {
        service.insertStarters()
        val added = service.insertStarters()
        assertEquals(3, added)
        assertEquals(6, ds.templates.size)
        assertTrue(ds.templates.values.any { it.name == "Snack Break (2)" })
    }

    // ---------------------------------------------------------------- replacement & deletion

    @Test
    fun replacingAPlanWithProgressNeedsConfirmationAndKeepsHistory() = runTest {
        service.insertStarters()
        val everyday = ds.templates.values.first { it.name == "Everyday Box" }.id
        val snack = ds.templates.values.first { it.name == "Snack Break" }.id
        val date = today.plusDays(2)
        val planId = (service.assignPlan(date, everyday, false) as AssignResult.Assigned).sessionId

        // No progress yet: replaced without asking.
        val replaced = service.assignPlan(date, snack, false)
        assertTrue(replaced is AssignResult.Assigned)
        assertNull(ds.sessions[planId])
        val snackPlan = (replaced as AssignResult.Assigned).sessionId

        service.toggleItem(snackPlan, ds.sessions.getValue(snackPlan).items[0].id)
        service.save(snackPlan, allowPartial = true)
        val needs = service.assignPlan(date, everyday, false)
        assertEquals(AssignResult.NeedsConfirmation("Snack Break"), needs)
        assertNotNull(ds.sessions[snackPlan])

        assertTrue(service.assignPlan(date, everyday, true) is AssignResult.Assigned)
        assertEquals(1, ds.sessions.values.count { it.plannedDate == date })
        assertEquals(1, ds.history.size) // saved result survives replacement
    }

    @Test
    fun deletingATemplatePreservesPlansAndBlocksNewPlans() = runTest {
        val id = everydayBoxId()
        val planId = (service.assignPlan(today.plusDays(1), id, false) as AssignResult.Assigned).sessionId
        service.deleteTemplate(id)

        val plan = ds.sessions.getValue(planId)
        assertNull(plan.sourceTemplateId)
        assertEquals("Everyday Box", plan.packName)
        assertEquals(6, plan.totalCount)
        assertEquals(AssignResult.TemplateMissing, service.assignPlan(today.plusDays(3), id, false))
        assertEquals(UpdateResult.TemplateMissing, service.updateFromTemplate(planId))
    }

    @Test
    fun removingAPlanKeepsHistoryAndNotifiesReminders() = runTest {
        val id = everydayBoxId()
        val planId = (service.assignPlan(today, id, false) as AssignResult.Assigned).sessionId
        service.toggleItem(planId, ds.sessions.getValue(planId).items[0].id)
        service.save(planId, allowPartial = true)
        val changesBefore = plansChanged
        assertTrue(service.removePlan(today))
        assertTrue(plansChanged > changesBefore)
        assertNull(ds.sessions[planId])
        assertEquals(1, ds.history.size)
        assertFalse(service.removePlan(today))
    }

    @Test
    fun onlyOneUnfinishedUnscheduledSession() = runTest {
        service.insertStarters()
        val everyday = ds.templates.values.first { it.name == "Everyday Box" }.id
        val snack = ds.templates.values.first { it.name == "Snack Break" }.id
        val first = (service.startUnscheduled(everyday, false) as StartResult.Started).sessionId
        assertNull(ds.sessions.getValue(first).plannedDate)
        service.toggleItem(first, ds.sessions.getValue(first).items[0].id)

        assertEquals(StartResult.NeedsConfirmation("Everyday Box"), service.startUnscheduled(snack, false))
        val second = (service.startUnscheduled(snack, true) as StartResult.Started).sessionId
        assertEquals(1, ds.sessions.values.count { it.plannedDate == null })
        assertEquals(second, ds.sessions.values.single { it.plannedDate == null }.id)
        // Unscheduled sessions never create weekly assignments.
        assertTrue(ds.sessions.values.none { it.plannedDate != null })
    }

    // ---------------------------------------------------------------- saving & revisions

    @Test
    fun partialSaveNeedsConfirmationAndChangesNeedANewRevision() = runTest {
        val planId = (service.assignPlan(today, everydayBoxId(), false) as AssignResult.Assigned).sessionId
        val items = ds.sessions.getValue(planId).items
        service.toggleItem(planId, items[0].id)

        assertEquals(SaveResult.NeedsPartialConfirmation(1, 6), service.save(planId, allowPartial = false))
        assertTrue(ds.history.isEmpty())

        val saved = service.save(planId, allowPartial = true) as SaveResult.Saved
        assertFalse(saved.complete)
        var plan = ds.sessions.getValue(planId)
        assertEquals(SessionStatus.SAVED, plan.status)
        assertFalse(plan.isSavedComplete)

        // Reopen and change → needs review.
        service.toggleItem(planId, items[1].id)
        plan = ds.sessions.getValue(planId)
        assertEquals(SessionStatus.NEEDS_REVIEW, plan.status)

        items.drop(2).forEach { service.toggleItem(planId, it.id) }
        val complete = service.save(planId, allowPartial = false) as SaveResult.Saved
        assertTrue(complete.complete)
        plan = ds.sessions.getValue(planId)
        assertTrue(plan.isSavedComplete)
        assertEquals(2, ds.history.size)
        assertEquals(listOf(false, true), ds.history.values.map { it.complete })
    }

    @Test
    fun savingTwiceOrAnUnchangedResultDoesNotDuplicateHistory() = runTest {
        val planId = (service.assignPlan(today, everydayBoxId(), false) as AssignResult.Assigned).sessionId
        val items = ds.sessions.getValue(planId).items
        items.forEach { service.toggleItem(planId, it.id) }
        assertTrue(service.save(planId, false) is SaveResult.Saved)
        assertEquals(SaveResult.Unchanged(true), service.save(planId, false))
        assertEquals(1, ds.history.size)

        // Toggle off and on again: same content as the saved snapshot → no duplicate entry.
        service.toggleItem(planId, items[0].id)
        service.toggleItem(planId, items[0].id)
        assertEquals(SessionStatus.NEEDS_REVIEW, ds.sessions.getValue(planId).status)
        assertEquals(SaveResult.Unchanged(true), service.save(planId, false))
        assertEquals(1, ds.history.size)
        assertEquals(SessionStatus.SAVED, ds.sessions.getValue(planId).status)
    }

    @Test
    fun concurrentSaveTapsWriteOneHistoryEntry() = runTest {
        val planId = (service.assignPlan(today, everydayBoxId(), false) as AssignResult.Assigned).sessionId
        ds.sessions.getValue(planId).items.forEach { service.toggleItem(planId, it.id) }
        val results = (1..5).map { async { service.save(planId, false) } }.awaitAll()
        assertEquals(1, results.count { it is SaveResult.Saved })
        assertEquals(4, results.count { it is SaveResult.Unchanged })
        assertEquals(1, ds.history.size)
    }

    @Test
    fun historyKeepsOnlyTheLatest100() = runTest {
        val id = everydayBoxId()
        val planId = (service.assignPlan(today, id, false) as AssignResult.Assigned).sessionId
        val first = ds.sessions.getValue(planId).items[0].id
        repeat(105) { i ->
            clock.instant = clock.instant.plusSeconds(60)
            service.toggleItem(planId, first)
            // Alternate packed/unpacked so each save is a real change.
            val r = service.save(planId, allowPartial = true)
            assertTrue("save $i", r is SaveResult.Saved || r is SaveResult.Unchanged)
        }
        assertTrue(ds.history.size <= Limits.HISTORY_KEEP)
    }

    @Test
    fun plansOutsideTheWindowAreRejected() = runTest {
        val id = everydayBoxId()
        assertEquals(AssignResult.OutsideWindow, service.assignPlan(today.minusDays(1), id, false))
        assertEquals(AssignResult.OutsideWindow, service.assignPlan(today.plusDays(31), id, false))
        assertTrue(service.assignPlan(today.plusDays(30), id, false) is AssignResult.Assigned)
    }
}
