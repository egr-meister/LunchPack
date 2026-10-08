package com.egrmeister.lunchpack.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class FixedClock(var instant: Instant, var zoneId: ZoneId = ZoneId.of("Europe/Minsk")) : AppClock {
    override fun now(): Instant = instant
    override fun zone(): ZoneId = zoneId

    fun setLocal(date: LocalDate, hour: Int, minute: Int = 0) {
        instant = date.atTime(hour, minute).atZone(zoneId).toInstant()
    }
}

class SequentialIds : IdGenerator {
    private var next = 0
    override fun newId(): String = "id-${next++}"
}

/** In-memory PackingDataSource mirroring the Room implementation's semantics. */
class InMemoryDataSource : PackingDataSource {
    val templates = linkedMapOf<Long, PackTemplate>()
    val sessions = linkedMapOf<Long, PackingSession>()
    val history = linkedMapOf<Long, HistoryEntry>()
    private var templateSeq = 0L
    private var sessionSeq = 0L
    private var historySeq = 0L

    override suspend fun <T> transaction(block: suspend () -> T): T = block()

    override suspend fun templateCount(): Int = templates.size
    override suspend fun templateNames(): List<Pair<Long, String>> = templates.values.map { it.id to it.name }
    override suspend fun getTemplate(id: Long): PackTemplate? = templates[id]

    override suspend fun insertTemplate(template: PackTemplate): Long {
        val id = ++templateSeq
        templates[id] = template.copy(id = id)
        return id
    }

    override suspend fun updateTemplate(template: PackTemplate) {
        templates[template.id] = template
    }

    override suspend fun setFavorite(id: Long, favorite: Boolean, updatedAt: Instant) {
        templates[id]?.let { templates[id] = it.copy(favorite = favorite, updatedAt = updatedAt) }
    }

    override suspend fun deleteTemplate(id: Long) {
        templates.remove(id)
    }

    override suspend fun detachSessionsFromTemplate(templateId: Long) {
        sessions.replaceAll { _, s -> if (s.sourceTemplateId == templateId) s.copy(sourceTemplateId = null) else s }
    }

    override suspend fun getSession(id: Long): PackingSession? = sessions[id]
    override suspend fun sessionForDate(date: LocalDate): PackingSession? = sessions.values.firstOrNull { it.plannedDate == date }
    override suspend fun unscheduledSessions(): List<PackingSession> = sessions.values.filter { it.plannedDate == null }

    override suspend fun insertSession(session: PackingSession): Long {
        if (session.plannedDate != null) {
            check(sessions.values.none { it.plannedDate == session.plannedDate }) { "unique plannedLocalDate violated" }
        }
        val id = ++sessionSeq
        sessions[id] = session.copy(id = id, items = session.items.map { it.copy(sessionId = id) })
        return id
    }

    override suspend fun updateSessionHeader(session: PackingSession) {
        val existing = sessions.getValue(session.id)
        sessions[session.id] = session.copy(items = existing.items)
    }

    override suspend fun replaceSessionItems(sessionId: Long, items: List<SessionItem>) {
        val existing = sessions.getValue(sessionId)
        sessions[sessionId] = existing.copy(items = items.map { it.copy(sessionId = sessionId) })
    }

    override suspend fun setItemPacked(itemId: String, packed: Boolean) {
        sessions.replaceAll { _, s -> s.copy(items = s.items.map { if (it.id == itemId) it.copy(packed = packed) else it }) }
    }

    override suspend fun setAllUnpacked(sessionId: Long) {
        val existing = sessions.getValue(sessionId)
        sessions[sessionId] = existing.copy(items = existing.items.map { it.copy(packed = false) })
    }

    override suspend fun deleteSession(id: Long) {
        sessions.remove(id)
    }

    override suspend fun insertHistory(entry: HistoryEntry): Long {
        val id = ++historySeq
        history[id] = entry.copy(id = id)
        return id
    }

    override suspend fun latestHistoryForSession(sessionId: Long): HistoryEntry? =
        history.values.filter { it.sessionId == sessionId }.maxWithOrNull(compareBy({ it.savedAt }, { it.id }))

    override suspend fun trimHistory(keep: Int) {
        val keepIds = history.values.sortedWith(compareByDescending<HistoryEntry> { it.savedAt }.thenByDescending { it.id })
            .take(keep).map { it.id }.toSet()
        history.keys.retainAll(keepIds)
    }

    override suspend fun deleteHistory(id: Long) {
        history.remove(id)
    }

    override suspend fun clearHistory() {
        history.clear()
    }
}

class FakeReminderStore(var config: ReminderConfig = ReminderConfig.Default) : ReminderStore {
    var scheduled: Long? = null
    override suspend fun reminderConfig(): ReminderConfig = config
    override suspend fun scheduledFor(): Long? = scheduled
    override suspend fun setScheduledFor(epochMillis: Long?) {
        scheduled = epochMillis
    }
}

class FakeAlarms : AlarmGateway {
    val scheduled = mutableListOf<Instant>()
    var cancelled = 0
    override fun schedule(at: Instant) {
        scheduled += at
    }

    override fun cancel() {
        cancelled++
    }
}

class FakeNotifications(var allowed: Boolean = true) : NotificationGateway {
    val posted = mutableListOf<LocalDate>()
    var dismissed = 0
    override fun canPost(): Boolean = allowed
    override fun postTomorrowReminder(date: LocalDate) {
        posted += date
    }

    override fun dismiss() {
        dismissed++
    }
}
