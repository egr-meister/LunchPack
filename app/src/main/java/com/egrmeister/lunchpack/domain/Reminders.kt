package com.egrmeister.lunchpack.domain

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ReminderConfig(val enabled: Boolean, val hour: Int, val minute: Int) {
    companion object {
        val Default = ReminderConfig(enabled = false, hour = 19, minute = 0)
    }
}

/** Persistence of the reminder setting and of the one currently scheduled trigger. */
interface ReminderStore {
    suspend fun reminderConfig(): ReminderConfig
    suspend fun scheduledFor(): Long?
    suspend fun setScheduledFor(epochMillis: Long?)
}

/** Inexact one-shot alarm. */
interface AlarmGateway {
    fun schedule(at: Instant)
    fun cancel()
}

interface NotificationGateway {
    fun canPost(): Boolean
    fun postTomorrowReminder(date: LocalDate)
    fun dismiss()
}

/** State of a planned pack as far as reminders care. */
data class PlanReminderState(val status: SessionStatus, val savedComplete: Boolean) {
    val isSavedComplete: Boolean get() = status == SessionStatus.SAVED && savedComplete
}

interface PlanLookup {
    suspend fun planFor(date: LocalDate): PlanReminderState?
}

enum class ReminderDecision {
    NOTIFY,
    DISABLED,
    /** A newer schedule replaced this callback (time changed, re-enabled, …). */
    STALE_SUPERSEDED,
    /** Delivered on a later day than it was scheduled for — skipped, no backlog. */
    STALE_PREVIOUS_DAY,
    BLOCKED,
    NO_PLAN,
    ALREADY_PACKED,
}

enum class ReminderStatus { OFF, ACTIVE, BLOCKED }

object ReminderPolicy {
    /** Next occurrence of hour:minute strictly after [now], in [zone]. */
    fun nextTrigger(now: Instant, zone: ZoneId, hour: Int, minute: Int): Instant {
        val localNow = now.atZone(zone)
        val todayAt = localNow.toLocalDate().atTime(hour, minute).atZone(zone).toInstant()
        return if (todayAt.isAfter(now)) {
            todayAt
        } else {
            localNow.toLocalDate().plusDays(1).atTime(hour, minute).atZone(zone).toInstant()
        }
    }

    fun status(enabled: Boolean, canPost: Boolean): ReminderStatus = when {
        !enabled -> ReminderStatus.OFF
        !canPost -> ReminderStatus.BLOCKED
        else -> ReminderStatus.ACTIVE
    }

    /**
     * Decides whether an alarm callback may post. Checked in this order: reminders enabled,
     * callback is the current schedule, delivered on the scheduled day, notifications allowed,
     * tomorrow has a plan, and that plan is not already saved complete.
     */
    fun evaluate(
        enabled: Boolean,
        storedScheduledFor: Long?,
        callbackScheduledFor: Long,
        now: Instant,
        zone: ZoneId,
        canPost: Boolean,
        tomorrowPlan: PlanReminderState?,
    ): ReminderDecision {
        if (!enabled) return ReminderDecision.DISABLED
        if (storedScheduledFor == null || storedScheduledFor != callbackScheduledFor) {
            return ReminderDecision.STALE_SUPERSEDED
        }
        val scheduledDay = Instant.ofEpochMilli(callbackScheduledFor).atZone(zone).toLocalDate()
        if (scheduledDay != now.atZone(zone).toLocalDate()) return ReminderDecision.STALE_PREVIOUS_DAY
        if (!canPost) return ReminderDecision.BLOCKED
        if (tomorrowPlan == null) return ReminderDecision.NO_PLAN
        if (tomorrowPlan.isSavedComplete) return ReminderDecision.ALREADY_PACKED
        return ReminderDecision.NOTIFY
    }
}

/**
 * Schedules the single daily reminder and handles its callback. Reschedules after every
 * delivery and whenever settings, plans, the clock, the time zone or the app version change.
 */
class ReminderEngine(
    private val store: ReminderStore,
    private val plans: PlanLookup,
    private val alarms: AlarmGateway,
    private val notifications: NotificationGateway,
    private val clock: AppClock,
    private val log: (String) -> Unit = {},
) {
    private val mutex = Mutex()

    suspend fun reschedule() {
        mutex.withLock { rescheduleLocked() }
    }

    private suspend fun rescheduleLocked() {
        val config = store.reminderConfig()
        if (!config.enabled) {
            alarms.cancel()
            store.setScheduledFor(null)
            return
        }
        val next = ReminderPolicy.nextTrigger(clock.now(), clock.zone(), config.hour, config.minute)
        store.setScheduledFor(next.toEpochMilli())
        alarms.schedule(next)
    }

    suspend fun onAlarm(callbackScheduledFor: Long): ReminderDecision = mutex.withLock {
        val config = store.reminderConfig()
        val tomorrow = clock.today().plusDays(1)
        val decision = ReminderPolicy.evaluate(
            enabled = config.enabled,
            storedScheduledFor = store.scheduledFor(),
            callbackScheduledFor = callbackScheduledFor,
            now = clock.now(),
            zone = clock.zone(),
            canPost = notifications.canPost(),
            tomorrowPlan = plans.planFor(tomorrow),
        )
        if (decision == ReminderDecision.NOTIFY) notifications.postTomorrowReminder(tomorrow)
        log("Reminder callback: $decision")
        rescheduleLocked()
        decision
    }

    /** Clears the visible reminder when tomorrow's plan was removed or saved complete. */
    suspend fun onPlansChanged() {
        mutex.withLock {
            val tomorrow = clock.today().plusDays(1)
            val plan = plans.planFor(tomorrow)
            if (plan == null || plan.isSavedComplete) notifications.dismiss()
            rescheduleLocked()
        }
    }

    suspend fun cancelAll() {
        mutex.withLock {
            alarms.cancel()
            notifications.dismiss()
            store.setScheduledFor(null)
        }
    }
}
