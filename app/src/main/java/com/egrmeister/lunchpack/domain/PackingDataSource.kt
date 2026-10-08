package com.egrmeister.lunchpack.domain

import java.time.Instant
import java.time.LocalDate

/**
 * Storage primitives used by [PackingService]. The Room implementation lives in the data
 * package; unit tests use an in-memory implementation, so all business rules are tested
 * against the same service code that runs in the app.
 */
interface PackingDataSource {
    /** Runs [block] atomically (a Room transaction in production). */
    suspend fun <T> transaction(block: suspend () -> T): T

    // Templates
    suspend fun templateCount(): Int
    suspend fun templateNames(): List<Pair<Long, String>>
    suspend fun getTemplate(id: Long): PackTemplate?
    suspend fun insertTemplate(template: PackTemplate): Long
    suspend fun updateTemplate(template: PackTemplate)
    suspend fun setFavorite(id: Long, favorite: Boolean, updatedAt: Instant)
    suspend fun deleteTemplate(id: Long)
    suspend fun detachSessionsFromTemplate(templateId: Long)

    // Sessions (plans and unscheduled packing)
    suspend fun getSession(id: Long): PackingSession?
    suspend fun sessionForDate(date: LocalDate): PackingSession?
    suspend fun unscheduledSessions(): List<PackingSession>
    suspend fun insertSession(session: PackingSession): Long
    suspend fun updateSessionHeader(session: PackingSession)
    suspend fun replaceSessionItems(sessionId: Long, items: List<SessionItem>)
    suspend fun setItemPacked(itemId: String, packed: Boolean)
    suspend fun setAllUnpacked(sessionId: Long)
    suspend fun deleteSession(id: Long)

    // History
    suspend fun insertHistory(entry: HistoryEntry): Long
    suspend fun latestHistoryForSession(sessionId: Long): HistoryEntry?
    suspend fun trimHistory(keep: Int)
    suspend fun deleteHistory(id: Long)
    suspend fun clearHistory()
}
