package com.egrmeister.lunchpack.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {
    @Transaction
    @Query("SELECT * FROM pack_templates ORDER BY favorite DESC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<TemplateWithItems>>

    @Transaction
    @Query("SELECT * FROM pack_templates WHERE id = :id")
    fun observe(id: Long): Flow<TemplateWithItems?>

    @Transaction
    @Query("SELECT * FROM pack_templates WHERE id = :id")
    suspend fun get(id: Long): TemplateWithItems?

    @Query("SELECT COUNT(*) FROM pack_templates")
    suspend fun count(): Int

    @Query("SELECT * FROM pack_templates")
    suspend fun headers(): List<PackTemplateEntity>

    @Insert
    suspend fun insert(template: PackTemplateEntity): Long

    @Update
    suspend fun update(template: PackTemplateEntity)

    @Query("UPDATE pack_templates SET favorite = :favorite, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean, updatedAt: Long)

    @Query("DELETE FROM pack_templates WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert
    suspend fun insertItems(items: List<TemplateItemEntity>)

    @Query("DELETE FROM template_items WHERE templateId = :templateId")
    suspend fun deleteItems(templateId: Long)
}

@Dao
interface SessionDao {
    @Transaction
    @Query("SELECT * FROM packing_sessions WHERE id = :id")
    fun observe(id: Long): Flow<SessionWithItems?>

    @Transaction
    @Query("SELECT * FROM packing_sessions WHERE id = :id")
    suspend fun get(id: Long): SessionWithItems?

    @Transaction
    @Query("SELECT * FROM packing_sessions WHERE plannedLocalDate = :date")
    fun observeForDate(date: String): Flow<SessionWithItems?>

    @Transaction
    @Query("SELECT * FROM packing_sessions WHERE plannedLocalDate = :date")
    suspend fun getForDate(date: String): SessionWithItems?

    @Transaction
    @Query(
        "SELECT * FROM packing_sessions WHERE plannedLocalDate >= :from AND plannedLocalDate <= :to " +
            "ORDER BY plannedLocalDate ASC",
    )
    fun observeBetween(from: String, to: String): Flow<List<SessionWithItems>>

    @Transaction
    @Query("SELECT * FROM packing_sessions WHERE plannedLocalDate IS NULL ORDER BY id DESC")
    suspend fun unscheduled(): List<SessionWithItems>

    @Transaction
    @Query(
        "SELECT * FROM packing_sessions WHERE plannedLocalDate IS NULL AND status != 'saved' " +
            "ORDER BY id DESC LIMIT 1",
    )
    fun observeUnfinishedUnscheduled(): Flow<SessionWithItems?>

    @Insert
    suspend fun insert(session: PackingSessionEntity): Long

    @Update
    suspend fun update(session: PackingSessionEntity)

    @Query("DELETE FROM packing_sessions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE packing_sessions SET sourceTemplateId = NULL WHERE sourceTemplateId = :templateId")
    suspend fun detachTemplate(templateId: Long)

    @Insert
    suspend fun insertItems(items: List<SessionItemEntity>)

    @Query("DELETE FROM session_items WHERE sessionId = :sessionId")
    suspend fun deleteItems(sessionId: Long)

    @Query("UPDATE session_items SET packed = :packed WHERE id = :itemId")
    suspend fun setPacked(itemId: String, packed: Boolean)

    @Query("UPDATE session_items SET packed = 0 WHERE sessionId = :sessionId")
    suspend fun unpackAll(sessionId: Long)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM packing_history ORDER BY savedAt DESC, id DESC")
    fun observeAll(): Flow<List<PackingHistoryEntity>>

    @Transaction
    @Query("SELECT * FROM packing_history WHERE id = :id")
    fun observe(id: Long): Flow<HistoryWithItems?>

    @Transaction
    @Query("SELECT * FROM packing_history WHERE sessionId = :sessionId ORDER BY savedAt DESC, id DESC LIMIT 1")
    suspend fun latestForSession(sessionId: Long): HistoryWithItems?

    @Insert
    suspend fun insert(entry: PackingHistoryEntity): Long

    @Insert
    suspend fun insertItems(items: List<HistoryItemEntity>)

    @Query(
        "DELETE FROM packing_history WHERE id NOT IN " +
            "(SELECT id FROM packing_history ORDER BY savedAt DESC, id DESC LIMIT :keep)",
    )
    suspend fun trim(keep: Int)

    @Query("DELETE FROM history_items WHERE historyId NOT IN (SELECT id FROM packing_history)")
    suspend fun deleteOrphanItems()

    @Query("DELETE FROM history_items WHERE historyId = :id")
    suspend fun deleteItems(id: Long)

    @Query("DELETE FROM packing_history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM history_items")
    suspend fun clearItems()

    @Query("DELETE FROM packing_history")
    suspend fun clear()
}
