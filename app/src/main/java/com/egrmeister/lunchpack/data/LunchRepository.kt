package com.egrmeister.lunchpack.data

import androidx.room.withTransaction
import com.egrmeister.lunchpack.domain.Compartment
import com.egrmeister.lunchpack.domain.HistoryEntry
import com.egrmeister.lunchpack.domain.HistoryItem
import com.egrmeister.lunchpack.domain.ItemIcon
import com.egrmeister.lunchpack.domain.PackTemplate
import com.egrmeister.lunchpack.domain.PackingDataSource
import com.egrmeister.lunchpack.domain.PackingSession
import com.egrmeister.lunchpack.domain.PlanLookup
import com.egrmeister.lunchpack.domain.PlanReminderState
import com.egrmeister.lunchpack.domain.SessionItem
import com.egrmeister.lunchpack.domain.SessionStatus
import com.egrmeister.lunchpack.domain.TemplateItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

/**
 * Room-backed storage. Implements the primitives used by PackingService and exposes
 * observable flows for the UI. Room runs queries and transactions off the main thread.
 */
class LunchRepository(private val db: AppDatabase) : PackingDataSource, PlanLookup {
    private val templates = db.templateDao()
    private val sessions = db.sessionDao()
    private val history = db.historyDao()

    // ------------------------------------------------------------------ observation

    val templatesFlow: Flow<List<PackTemplate>> = templates.observeAll().map { list -> list.map { it.toDomain() } }

    fun templateFlow(id: Long): Flow<PackTemplate?> = templates.observe(id).map { it?.toDomain() }

    fun sessionFlow(id: Long): Flow<PackingSession?> = sessions.observe(id).map { it?.toDomain() }

    fun sessionForDateFlow(date: LocalDate): Flow<PackingSession?> =
        sessions.observeForDate(date.toString()).map { it?.toDomain() }

    fun plansBetweenFlow(from: LocalDate, to: LocalDate): Flow<List<PackingSession>> =
        sessions.observeBetween(from.toString(), to.toString()).map { list -> list.map { it.toDomain() } }

    val unfinishedUnscheduledFlow: Flow<PackingSession?> =
        sessions.observeUnfinishedUnscheduled().map { it?.toDomain() }

    val historyFlow: Flow<List<HistoryEntry>> = history.observeAll().map { list -> list.map { it.toDomain(emptyList()) } }

    fun historyEntryFlow(id: Long): Flow<HistoryEntry?> = history.observe(id).map { it?.toDomain() }

    // ------------------------------------------------------------------ PackingDataSource

    override suspend fun <T> transaction(block: suspend () -> T): T = db.withTransaction { block() }

    override suspend fun templateCount(): Int = templates.count()

    override suspend fun templateNames(): List<Pair<Long, String>> = templates.headers().map { it.id to it.name }

    override suspend fun getTemplate(id: Long): PackTemplate? = templates.get(id)?.toDomain()

    override suspend fun insertTemplate(template: PackTemplate): Long {
        val id = templates.insert(template.toEntity().copy(id = 0))
        templates.insertItems(template.items.map { it.toEntity(id) })
        return id
    }

    override suspend fun updateTemplate(template: PackTemplate) {
        templates.update(template.toEntity())
        templates.deleteItems(template.id)
        templates.insertItems(template.items.map { it.toEntity(template.id) })
    }

    override suspend fun setFavorite(id: Long, favorite: Boolean, updatedAt: Instant) =
        templates.setFavorite(id, favorite, updatedAt.toEpochMilli())

    override suspend fun deleteTemplate(id: Long) {
        templates.deleteItems(id)
        templates.delete(id)
    }

    override suspend fun detachSessionsFromTemplate(templateId: Long) = sessions.detachTemplate(templateId)

    override suspend fun getSession(id: Long): PackingSession? = sessions.get(id)?.toDomain()

    override suspend fun sessionForDate(date: LocalDate): PackingSession? =
        sessions.getForDate(date.toString())?.toDomain()

    override suspend fun unscheduledSessions(): List<PackingSession> = sessions.unscheduled().map { it.toDomain() }

    override suspend fun insertSession(session: PackingSession): Long {
        val id = sessions.insert(session.toEntity().copy(id = 0))
        sessions.insertItems(session.items.map { it.toEntity(id) })
        return id
    }

    override suspend fun updateSessionHeader(session: PackingSession) = sessions.update(session.toEntity())

    override suspend fun replaceSessionItems(sessionId: Long, items: List<SessionItem>) {
        sessions.deleteItems(sessionId)
        sessions.insertItems(items.map { it.toEntity(sessionId) })
    }

    override suspend fun setItemPacked(itemId: String, packed: Boolean) = sessions.setPacked(itemId, packed)

    override suspend fun setAllUnpacked(sessionId: Long) = sessions.unpackAll(sessionId)

    override suspend fun deleteSession(id: Long) {
        sessions.deleteItems(id)
        sessions.delete(id)
    }

    override suspend fun insertHistory(entry: HistoryEntry): Long {
        val id = history.insert(
            PackingHistoryEntity(
                id = 0,
                sessionId = entry.sessionId,
                revision = entry.revision,
                packNameSnapshot = entry.packName,
                plannedLocalDate = entry.plannedDate?.toString(),
                savedAt = entry.savedAt.toEpochMilli(),
                totalItems = entry.totalItems,
                packedItems = entry.packedItems,
            ),
        )
        history.insertItems(
            entry.items.map {
                HistoryItemEntity(
                    id = 0,
                    historyId = id,
                    name = it.name,
                    note = it.note.ifEmpty { null },
                    compartment = it.compartment.key,
                    iconKey = it.icon.key,
                    displayOrder = it.displayOrder,
                    packed = it.packed,
                )
            },
        )
        return id
    }

    override suspend fun latestHistoryForSession(sessionId: Long): HistoryEntry? =
        history.latestForSession(sessionId)?.toDomain()

    override suspend fun trimHistory(keep: Int) {
        history.trim(keep)
        history.deleteOrphanItems()
    }

    override suspend fun deleteHistory(id: Long) {
        history.deleteItems(id)
        history.delete(id)
    }

    override suspend fun clearHistory() {
        history.clearItems()
        history.clear()
    }

    // ------------------------------------------------------------------ PlanLookup

    override suspend fun planFor(date: LocalDate): PlanReminderState? =
        sessions.getForDate(date.toString())?.session?.let {
            PlanReminderState(SessionStatus.fromKey(it.status), it.savedComplete)
        }

    /** Wipes every table (Clear all local data). Must not run inside a transaction. */
    fun clearAllTables() = db.clearAllTables()
}

// ---------------------------------------------------------------------- mappers

private fun TemplateWithItems.toDomain() = PackTemplate(
    id = template.id,
    name = template.name,
    favorite = template.favorite,
    createdAt = Instant.ofEpochMilli(template.createdAt),
    updatedAt = Instant.ofEpochMilli(template.updatedAt),
    items = items.sortedBy { it.displayOrder }.map {
        TemplateItem(
            id = it.id,
            name = it.name,
            note = it.note.orEmpty(),
            compartment = Compartment.fromKey(it.compartment),
            icon = ItemIcon.fromKey(it.iconKey),
            displayOrder = it.displayOrder,
        )
    },
)

private fun PackTemplate.toEntity() = PackTemplateEntity(
    id = id,
    name = name,
    favorite = favorite,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)

private fun TemplateItem.toEntity(templateId: Long) = TemplateItemEntity(
    id = id,
    templateId = templateId,
    name = name,
    note = note.ifEmpty { null },
    compartment = compartment.key,
    iconKey = icon.key,
    displayOrder = displayOrder,
)

private fun parseDate(value: String?): LocalDate? = value?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

private fun SessionWithItems.toDomain() = PackingSession(
    id = session.id,
    plannedDate = parseDate(session.plannedLocalDate),
    sourceTemplateId = session.sourceTemplateId,
    packName = session.packNameSnapshot,
    revision = session.revision,
    savedRevision = session.savedRevision,
    status = SessionStatus.fromKey(session.status),
    savedComplete = session.savedComplete,
    createdAt = Instant.ofEpochMilli(session.createdAt),
    updatedAt = Instant.ofEpochMilli(session.updatedAt),
    items = items.sortedBy { it.displayOrder }.map {
        SessionItem(
            id = it.id,
            sessionId = it.sessionId,
            name = it.nameSnapshot,
            note = it.noteSnapshot.orEmpty(),
            compartment = Compartment.fromKey(it.compartment),
            icon = ItemIcon.fromKey(it.iconKey),
            displayOrder = it.displayOrder,
            packed = it.packed,
        )
    },
)

private fun PackingSession.toEntity() = PackingSessionEntity(
    id = id,
    plannedLocalDate = plannedDate?.toString(),
    sourceTemplateId = sourceTemplateId,
    packNameSnapshot = packName,
    revision = revision,
    savedRevision = savedRevision,
    status = status.key,
    savedComplete = savedComplete,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
)

private fun SessionItem.toEntity(sessionId: Long) = SessionItemEntity(
    id = id,
    sessionId = sessionId,
    nameSnapshot = name,
    noteSnapshot = note.ifEmpty { null },
    compartment = compartment.key,
    iconKey = icon.key,
    displayOrder = displayOrder,
    packed = packed,
)

private fun PackingHistoryEntity.toDomain(items: List<HistoryItem>) = HistoryEntry(
    id = id,
    sessionId = sessionId,
    revision = revision,
    packName = packNameSnapshot,
    plannedDate = parseDate(plannedLocalDate),
    savedAt = Instant.ofEpochMilli(savedAt),
    totalItems = totalItems,
    packedItems = packedItems,
    items = items,
)

private fun HistoryWithItems.toDomain() = entry.toDomain(
    items.sortedBy { it.displayOrder }.map {
        HistoryItem(
            name = it.name,
            note = it.note.orEmpty(),
            compartment = Compartment.fromKey(it.compartment),
            icon = ItemIcon.fromKey(it.iconKey),
            displayOrder = it.displayOrder,
            packed = it.packed,
        )
    },
)
