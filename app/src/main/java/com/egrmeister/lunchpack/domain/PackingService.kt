package com.egrmeister.lunchpack.domain

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

sealed interface TemplateSaveResult {
    data class Saved(val id: Long) : TemplateSaveResult
    data class Invalid(val errors: List<TemplateError>) : TemplateSaveResult
    data object Missing : TemplateSaveResult
}

sealed interface DuplicateResult {
    data class Duplicated(val id: Long, val name: String) : DuplicateResult
    data object LimitReached : DuplicateResult
    data object Missing : DuplicateResult
}

sealed interface AssignResult {
    data class Assigned(val sessionId: Long) : AssignResult
    /** The day already has a plan with packing progress; ask before replacing it. */
    data class NeedsConfirmation(val currentPackName: String) : AssignResult
    data object TemplateMissing : AssignResult
    data object TemplateEmpty : AssignResult
    data object OutsideWindow : AssignResult
}

sealed interface StartResult {
    data class Started(val sessionId: Long) : StartResult
    /** An unfinished unscheduled session with progress exists; ask before replacing it. */
    data class NeedsConfirmation(val currentPackName: String) : StartResult
    data object TemplateMissing : StartResult
    data object TemplateEmpty : StartResult
}

sealed interface UpdateResult {
    data object Updated : UpdateResult
    data object TemplateMissing : UpdateResult
    data object TemplateEmpty : UpdateResult
    data object SessionMissing : UpdateResult
}

sealed interface SaveResult {
    data class Saved(val historyId: Long, val complete: Boolean) : SaveResult
    /** Nothing changed since the last saved result; no duplicate history entry is written. */
    data class Unchanged(val complete: Boolean) : SaveResult
    data class NeedsPartialConfirmation(val packed: Int, val total: Int) : SaveResult
    data object Empty : SaveResult
    data object NotFound : SaveResult
}

/**
 * All packing business rules: template edits, dated plan snapshots, unscheduled sessions,
 * packed-state toggling, Start Fresh, revisions and saving to history.
 *
 * Every write runs inside one transaction and behind [writeMutex], so a double tap on Save
 * can never write two history entries.
 */
class PackingService(
    private val ds: PackingDataSource,
    private val clock: AppClock,
    private val ids: IdGenerator = IdGenerator.Random,
    private val onPlansChanged: suspend () -> Unit = {},
) {
    private val writeMutex = Mutex()

    // ------------------------------------------------------------------ templates

    suspend fun saveTemplate(draft: TemplateDraft): TemplateSaveResult = writeMutex.withLock {
        ds.transaction { saveTemplateLocked(draft) }
    }

    private suspend fun saveTemplateLocked(draft: TemplateDraft): TemplateSaveResult {
        val errors = TemplateValidator.validate(draft, ds.templateNames(), ds.templateCount())
        if (errors.isNotEmpty()) return TemplateSaveResult.Invalid(errors)
        val now = clock.now()
        val items = draft.items.mapIndexed { index, d ->
            TemplateItem(
                id = d.id ?: ids.newId(),
                name = d.name.trim(),
                note = d.note.trim(),
                compartment = d.compartment,
                icon = d.icon,
                displayOrder = index,
            )
        }
        val id = draft.id
        return if (id == null) {
            val newId = ds.insertTemplate(PackTemplate(0L, draft.name.trim(), draft.favorite, now, now, items))
            TemplateSaveResult.Saved(newId)
        } else {
            val existing = ds.getTemplate(id) ?: return TemplateSaveResult.Missing
            ds.updateTemplate(
                existing.copy(name = draft.name.trim(), favorite = draft.favorite, updatedAt = now, items = items),
            )
            TemplateSaveResult.Saved(id)
        }
    }

    suspend fun duplicateTemplate(id: Long): DuplicateResult = writeMutex.withLock {
        ds.transaction<DuplicateResult> {
            val source = ds.getTemplate(id) ?: return@transaction DuplicateResult.Missing
            if (ds.templateCount() >= Limits.MAX_TEMPLATES) return@transaction DuplicateResult.LimitReached
            val name = TemplateValidator.uniqueName(source.name, ds.templateNames().map { it.second })
            val now = clock.now()
            val items = source.items.sortedBy { it.displayOrder }.mapIndexed { i, item ->
                item.copy(id = ids.newId(), displayOrder = i)
            }
            val newId = ds.insertTemplate(PackTemplate(0L, name, false, now, now, items))
            DuplicateResult.Duplicated(newId, name)
        }
    }

    /**
     * Deletes a template. Dated plan snapshots and history keep their own copies of the items;
     * plans simply lose the link, so the deleted template can no longer be used.
     */
    suspend fun deleteTemplate(id: Long) {
        writeMutex.withLock {
            ds.transaction {
                ds.detachSessionsFromTemplate(id)
                ds.deleteTemplate(id)
            }
        }
    }

    /** Flips the favorite flag on the same record — favorites never create copies. */
    suspend fun toggleFavorite(id: Long): Boolean? = writeMutex.withLock {
        ds.transaction<Boolean?> {
            val t = ds.getTemplate(id) ?: return@transaction null
            val newValue = !t.favorite
            ds.setFavorite(id, newValue, clock.now())
            newValue
        }
    }

    /** Inserts the example packs with unique names, up to the template limit. Returns how many. */
    suspend fun insertStarters(): Int = writeMutex.withLock {
        ds.transaction<Int> {
            var inserted = 0
            for (starter in Starters.packs) {
                if (ds.templateCount() >= Limits.MAX_TEMPLATES) break
                val name = TemplateValidator.uniqueName(starter.name, ds.templateNames().map { it.second })
                val result = saveTemplateLocked(starter.copy(name = name))
                if (result is TemplateSaveResult.Saved) inserted++
            }
            inserted
        }
    }

    // ------------------------------------------------------------------ plans & sessions

    private fun snapshot(template: PackTemplate): List<SessionItem> =
        template.items.sortedBy { it.displayOrder }.mapIndexed { index, item ->
            SessionItem(
                id = ids.newId(),
                sessionId = 0L,
                name = item.name,
                note = item.note,
                compartment = item.compartment,
                icon = item.icon,
                displayOrder = index,
                packed = false,
            )
        }

    private fun newSession(date: LocalDate?, template: PackTemplate): PackingSession {
        val now = clock.now()
        return PackingSession(
            id = 0L,
            plannedDate = date,
            sourceTemplateId = template.id,
            packName = template.name,
            revision = 0,
            savedRevision = null,
            status = SessionStatus.NOT_STARTED,
            savedComplete = false,
            createdAt = now,
            updatedAt = now,
            items = snapshot(template),
        )
    }

    /**
     * Assigns a template to a date as a snapshot. Replacing a plan that has packing progress
     * needs [confirmReplace]. Saved history of the replaced plan is never deleted.
     */
    suspend fun assignPlan(date: LocalDate, templateId: Long, confirmReplace: Boolean): AssignResult {
        val result = writeMutex.withLock<AssignResult> {
            if (!PlanningWindow.contains(clock.today(), date)) return@withLock AssignResult.OutsideWindow
            ds.transaction<AssignResult> {
                val template = ds.getTemplate(templateId) ?: return@transaction AssignResult.TemplateMissing
                if (template.items.isEmpty()) return@transaction AssignResult.TemplateEmpty
                val existing = ds.sessionForDate(date)
                if (existing != null && existing.hasProgress && !confirmReplace) {
                    return@transaction AssignResult.NeedsConfirmation(existing.packName)
                }
                if (existing != null) ds.deleteSession(existing.id)
                AssignResult.Assigned(ds.insertSession(newSession(date, template)))
            }
        }
        if (result is AssignResult.Assigned) onPlansChanged()
        return result
    }

    /** Removes the plan for [date]. History is kept. Returns false when there was no plan. */
    suspend fun removePlan(date: LocalDate): Boolean {
        val removed = writeMutex.withLock {
            ds.transaction<Boolean> {
                val existing = ds.sessionForDate(date) ?: return@transaction false
                ds.deleteSession(existing.id)
                true
            }
        }
        if (removed) onPlansChanged()
        return removed
    }

    /**
     * Starts unscheduled packing (not tied to a weekly date). Only one unfinished unscheduled
     * session exists at a time; replacing one with progress needs [confirmReplace].
     */
    suspend fun startUnscheduled(templateId: Long, confirmReplace: Boolean): StartResult = writeMutex.withLock {
        ds.transaction<StartResult> {
            val template = ds.getTemplate(templateId) ?: return@transaction StartResult.TemplateMissing
            if (template.items.isEmpty()) return@transaction StartResult.TemplateEmpty
            val existing = ds.unscheduledSessions()
            val unfinished = existing.firstOrNull { it.status != SessionStatus.SAVED }
            if (unfinished != null && unfinished.hasProgress && !confirmReplace) {
                return@transaction StartResult.NeedsConfirmation(unfinished.packName)
            }
            // Finished unscheduled sessions are already in history; the unfinished one is replaced.
            existing.forEach { ds.deleteSession(it.id) }
            StartResult.Started(ds.insertSession(newSession(null, template)))
        }
    }

    /** Toggles one item; persists immediately and bumps the revision. */
    suspend fun toggleItem(sessionId: Long, itemId: String): Boolean? = writeMutex.withLock {
        ds.transaction<Boolean?> {
            val session = ds.getSession(sessionId) ?: return@transaction null
            val item = session.items.firstOrNull { it.id == itemId } ?: return@transaction null
            val packed = !item.packed
            ds.setItemPacked(itemId, packed)
            val packedCount = session.packedCount + if (packed) 1 else -1
            bumpRevision(session, packedCount)
            packed
        }
    }

    private suspend fun bumpRevision(session: PackingSession, packedCount: Int) {
        val revision = session.revision + 1
        ds.updateSessionHeader(
            session.copy(
                revision = revision,
                status = PackingRules.status(revision, session.savedRevision, packedCount),
                updatedAt = clock.now(),
            ),
        )
    }

    /** Clears packed states of one session. History and template items are untouched. */
    suspend fun startFresh(sessionId: Long): Boolean = writeMutex.withLock {
        ds.transaction<Boolean> {
            val session = ds.getSession(sessionId) ?: return@transaction false
            ds.setAllUnpacked(sessionId)
            bumpRevision(session, 0)
            true
        }
    }

    /** Explicitly re-snapshots a plan from its (still existing) template. Restarts progress. */
    suspend fun updateFromTemplate(sessionId: Long): UpdateResult {
        val result = writeMutex.withLock {
            ds.transaction<UpdateResult> {
                val session = ds.getSession(sessionId) ?: return@transaction UpdateResult.SessionMissing
                val templateId = session.sourceTemplateId ?: return@transaction UpdateResult.TemplateMissing
                val template = ds.getTemplate(templateId) ?: return@transaction UpdateResult.TemplateMissing
                if (template.items.isEmpty()) return@transaction UpdateResult.TemplateEmpty
                ds.replaceSessionItems(sessionId, snapshot(template).map { it.copy(sessionId = sessionId) })
                val revision = session.revision + 1
                ds.updateSessionHeader(
                    session.copy(
                        packName = template.name,
                        revision = revision,
                        status = PackingRules.status(revision, session.savedRevision, 0),
                        updatedAt = clock.now(),
                    ),
                )
                UpdateResult.Updated
            }
        }
        if (result == UpdateResult.Updated) onPlansChanged()
        return result
    }

    // ------------------------------------------------------------------ saving

    /**
     * Saves the current state as an immutable history snapshot. Atomic and idempotent:
     * - unchanged since the last save → [SaveResult.Unchanged], nothing written;
     * - incomplete without [allowPartial] → [SaveResult.NeedsPartialConfirmation];
     * - identical to the last saved snapshot of this session → marks saved without a duplicate.
     */
    suspend fun save(sessionId: Long, allowPartial: Boolean): SaveResult {
        val result = writeMutex.withLock {
            ds.transaction<SaveResult> {
                val session = ds.getSession(sessionId) ?: return@transaction SaveResult.NotFound
                if (session.items.isEmpty()) return@transaction SaveResult.Empty
                if (session.savedRevision == session.revision) {
                    return@transaction SaveResult.Unchanged(session.savedComplete)
                }
                val complete = session.allPacked
                if (!complete && !allowPartial) {
                    return@transaction SaveResult.NeedsPartialConfirmation(session.packedCount, session.totalCount)
                }
                val now = clock.now()
                val snapshotItems = session.sortedItems.map {
                    HistoryItem(it.name, it.note, it.compartment, it.icon, it.displayOrder, it.packed)
                }
                val savedHeader = session.copy(
                    savedRevision = session.revision,
                    status = SessionStatus.SAVED,
                    savedComplete = complete,
                    updatedAt = now,
                )
                val last = ds.latestHistoryForSession(session.id)
                if (last != null && last.packName == session.packName && last.plannedDate == session.plannedDate &&
                    last.items.sortedBy { it.displayOrder } == snapshotItems
                ) {
                    ds.updateSessionHeader(savedHeader)
                    return@transaction SaveResult.Unchanged(complete)
                }
                val historyId = ds.insertHistory(
                    HistoryEntry(
                        id = 0L,
                        sessionId = session.id,
                        revision = session.revision,
                        packName = session.packName,
                        plannedDate = session.plannedDate,
                        savedAt = now,
                        totalItems = session.totalCount,
                        packedItems = session.packedCount,
                        items = snapshotItems,
                    ),
                )
                ds.trimHistory(Limits.HISTORY_KEEP)
                ds.updateSessionHeader(savedHeader)
                SaveResult.Saved(historyId, complete)
            }
        }
        if (result is SaveResult.Saved || result is SaveResult.Unchanged) onPlansChanged()
        return result
    }

    // ------------------------------------------------------------------ history

    suspend fun deleteHistory(id: Long) = writeMutex.withLock { ds.transaction { ds.deleteHistory(id) } }

    suspend fun clearHistory() = writeMutex.withLock { ds.transaction { ds.clearHistory() } }
}
