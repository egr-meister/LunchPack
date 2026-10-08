package com.egrmeister.lunchpack.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "pack_templates")
data class PackTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val favorite: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "template_items",
    foreignKeys = [
        ForeignKey(
            entity = PackTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("templateId")],
)
data class TemplateItemEntity(
    @PrimaryKey val id: String,
    val templateId: Long,
    val name: String,
    val note: String?,
    val compartment: String,
    val iconKey: String,
    val displayOrder: Int,
)

/**
 * A dated plan (plannedLocalDate = ISO local date, unique) or an unscheduled session
 * (plannedLocalDate = null). sourceTemplateId is a plain reference, not a foreign key:
 * deleting a template keeps the snapshot and just clears the link.
 */
@Entity(
    tableName = "packing_sessions",
    indices = [Index(value = ["plannedLocalDate"], unique = true)],
)
data class PackingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plannedLocalDate: String?,
    val sourceTemplateId: Long?,
    val packNameSnapshot: String,
    val revision: Int,
    val savedRevision: Int?,
    val status: String,
    val savedComplete: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "session_items",
    foreignKeys = [
        ForeignKey(
            entity = PackingSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class SessionItemEntity(
    @PrimaryKey val id: String,
    val sessionId: Long,
    val nameSnapshot: String,
    val noteSnapshot: String?,
    val compartment: String,
    val iconKey: String,
    val displayOrder: Int,
    val packed: Boolean,
)

/** Immutable saved result. sessionId is informational only (no foreign key). */
@Entity(tableName = "packing_history", indices = [Index("savedAt"), Index("sessionId")])
data class PackingHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long?,
    val revision: Int,
    val packNameSnapshot: String,
    val plannedLocalDate: String?,
    val savedAt: Long,
    val totalItems: Int,
    val packedItems: Int,
)

@Entity(
    tableName = "history_items",
    foreignKeys = [
        ForeignKey(
            entity = PackingHistoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["historyId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("historyId")],
)
data class HistoryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val historyId: Long,
    val name: String,
    val note: String?,
    val compartment: String,
    val iconKey: String,
    val displayOrder: Int,
    val packed: Boolean,
)

data class TemplateWithItems(
    @Embedded val template: PackTemplateEntity,
    @Relation(parentColumn = "id", entityColumn = "templateId")
    val items: List<TemplateItemEntity>,
)

data class SessionWithItems(
    @Embedded val session: PackingSessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val items: List<SessionItemEntity>,
)

data class HistoryWithItems(
    @Embedded val entry: PackingHistoryEntity,
    @Relation(parentColumn = "id", entityColumn = "historyId")
    val items: List<HistoryItemEntity>,
)
