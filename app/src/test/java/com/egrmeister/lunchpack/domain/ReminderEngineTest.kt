package com.egrmeister.lunchpack.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ReminderEngineTest {
    private val zone: ZoneId = ZoneId.of("Europe/Minsk")
    private val today: LocalDate = LocalDate.of(2026, 10, 8)
    private val tomorrow: LocalDate = today.plusDays(1)

    private class Setup(
        val clock: FixedClock,
        val store: FakeReminderStore,
        val alarms: FakeAlarms,
        val notifications: FakeNotifications,
        val plans: MutableMap<LocalDate, PlanReminderState>,
        val engine: ReminderEngine,
    )

    private fun setup(enabled: Boolean = true, allowed: Boolean = true): Setup {
        val clock = FixedClock(Instant.EPOCH, zone).apply { setLocal(today, 12) }
        val store = FakeReminderStore(ReminderConfig(enabled, 19, 30))
        val alarms = FakeAlarms()
        val notifications = FakeNotifications(allowed)
        val plans = mutableMapOf<LocalDate, PlanReminderState>()
        val lookup = object : PlanLookup {
            override suspend fun planFor(date: LocalDate): PlanReminderState? = plans[date]
        }
        val engine = ReminderEngine(store, lookup, alarms, notifications, clock)
        return Setup(clock, store, alarms, notifications, plans, engine)
    }

    private fun at(date: LocalDate, hour: Int, minute: Int): Instant = date.atTime(hour, minute).atZone(zone).toInstant()

    @Test
    fun nextTriggerIsLaterTodayOrTomorrow() {
        assertEquals(at(today, 19, 30), ReminderPolicy.nextTrigger(at(today, 12, 0), zone, 19, 30))
        assertEquals(at(tomorrow, 19, 30), ReminderPolicy.nextTrigger(at(today, 19, 30), zone, 19, 30))
        assertEquals(at(tomorrow, 19, 30), ReminderPolicy.nextTrigger(at(today, 21, 0), zone, 19, 30))
    }

    @Test
    fun disabledByDefaultAndCancelsWhenOff() = runTest {
        assertEquals(false, ReminderConfig.Default.enabled)
        val s = setup(enabled = false)
        s.engine.reschedule()
        assertTrue(s.alarms.scheduled.isEmpty())
        assertEquals(1, s.alarms.cancelled)
        assertNull(s.store.scheduled)
    }

    @Test
    fun notifiesOnlyWhenTomorrowHasAPlanThatIsNotSavedComplete() = runTest {
        val s = setup()
        s.engine.reschedule()
        val scheduled = s.store.scheduled!!
        assertEquals(at(today, 19, 30).toEpochMilli(), scheduled)

        // No plan for tomorrow.
        s.clock.setLocal(today, 19, 35)
        assertEquals(ReminderDecision.NO_PLAN, s.engine.onAlarm(scheduled))
        assertTrue(s.notifications.posted.isEmpty())

        // Rescheduled for the next day after delivery.
        val next = s.store.scheduled!!
        assertEquals(at(tomorrow, 19, 30).toEpochMilli(), next)

        // Planned but saved complete → skip; in progress / partial → notify.
        s.clock.setLocal(tomorrow, 19, 31)
        s.plans[tomorrow.plusDays(1)] = PlanReminderState(SessionStatus.SAVED, savedComplete = true)
        assertEquals(ReminderDecision.ALREADY_PACKED, s.engine.onAlarm(next))

        val third = s.store.scheduled!!
        s.clock.setLocal(tomorrow.plusDays(1), 19, 31)
        s.plans[tomorrow.plusDays(2)] = PlanReminderState(SessionStatus.SAVED, savedComplete = false)
        assertEquals(ReminderDecision.NOTIFY, s.engine.onAlarm(third))
        assertEquals(listOf(tomorrow.plusDays(2)), s.notifications.posted)
    }

    @Test
    fun needsReviewPlanStillNotifies() {
        val decision = ReminderPolicy.evaluate(
            enabled = true,
            storedScheduledFor = 1000L,
            callbackScheduledFor = 1000L,
            now = Instant.ofEpochMilli(2000L),
            zone = zone,
            canPost = true,
            tomorrowPlan = PlanReminderState(SessionStatus.NEEDS_REVIEW, savedComplete = true),
        )
        assertEquals(ReminderDecision.NOTIFY, decision)
    }

    @Test
    fun staleCallbacksAreSkippedWithoutBacklog() = runTest {
        val s = setup()
        s.plans[tomorrow] = PlanReminderState(SessionStatus.NOT_STARTED, false)
        s.engine.reschedule()
        val original = s.store.scheduled!!

        // Superseded: the user changed the time, a callback for the old schedule arrives.
        s.store.config = ReminderConfig(true, 20, 0)
        s.engine.reschedule()
        s.clock.setLocal(today, 19, 31)
        assertEquals(ReminderDecision.STALE_SUPERSEDED, s.engine.onAlarm(original))

        // Previous-day delivery: the alarm for today arrives tomorrow (device was off).
        val current = s.store.scheduled!!
        s.clock.setLocal(tomorrow, 8, 0)
        assertEquals(ReminderDecision.STALE_PREVIOUS_DAY, s.engine.onAlarm(current))
        assertTrue(s.notifications.posted.isEmpty())
        // ...and the next one is scheduled for tonight, not replayed.
        assertEquals(at(tomorrow, 20, 0).toEpochMilli(), s.store.scheduled)
    }

    @Test
    fun permissionDenialBlocksPostingButKeepsScheduling() = runTest {
        val s = setup(allowed = false)
        s.plans[tomorrow] = PlanReminderState(SessionStatus.IN_PROGRESS, false)
        s.engine.reschedule()
        val scheduled = s.store.scheduled!!
        s.clock.setLocal(today, 19, 40)
        assertEquals(ReminderDecision.BLOCKED, s.engine.onAlarm(scheduled))
        assertTrue(s.notifications.posted.isEmpty())
        assertEquals(2, s.alarms.scheduled.size) // still rescheduled
        assertEquals(ReminderStatus.BLOCKED, ReminderPolicy.status(enabled = true, canPost = false))
        assertEquals(ReminderStatus.ACTIVE, ReminderPolicy.status(enabled = true, canPost = true))
        assertEquals(ReminderStatus.OFF, ReminderPolicy.status(enabled = false, canPost = false))

        // Granting later makes the next callback post.
        s.notifications.allowed = true
        val next = s.store.scheduled!!
        s.clock.setLocal(tomorrow, 19, 31)
        s.plans[tomorrow.plusDays(1)] = PlanReminderState(SessionStatus.NOT_STARTED, false)
        assertEquals(ReminderDecision.NOTIFY, s.engine.onAlarm(next))
    }

    @Test
    fun removingTomorrowsPlanDismissesTheVisibleReminder() = runTest {
        val s = setup()
        s.plans[tomorrow] = PlanReminderState(SessionStatus.NOT_STARTED, false)
        s.engine.onPlansChanged()
        assertEquals(0, s.notifications.dismissed)
        s.plans.remove(tomorrow)
        s.engine.onPlansChanged()
        assertEquals(1, s.notifications.dismissed)
    }

    @Test
    fun cancelAllClearsAlarmNotificationAndSchedule() = runTest {
        val s = setup()
        s.engine.reschedule()
        s.engine.cancelAll()
        assertNull(s.store.scheduled)
        assertEquals(1, s.alarms.cancelled)
        assertEquals(1, s.notifications.dismissed)
    }

    @Test
    fun timeZoneChangeKeepsLocalReminderTime() = runTest {
        val s = setup()
        s.engine.reschedule()
        s.clock.zoneId = ZoneId.of("Asia/Tokyo")
        s.engine.reschedule()
        val local = Instant.ofEpochMilli(s.store.scheduled!!).atZone(ZoneId.of("Asia/Tokyo")).toLocalTime()
        assertEquals(java.time.LocalTime.of(19, 30), local)
    }
}
