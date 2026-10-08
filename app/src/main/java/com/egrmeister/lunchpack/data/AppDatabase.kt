package com.egrmeister.lunchpack.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        PackTemplateEntity::class,
        TemplateItemEntity::class,
        PackingSessionEntity::class,
        SessionItemEntity::class,
        PackingHistoryEntity::class,
        HistoryItemEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun templateDao(): TemplateDao
    abstract fun sessionDao(): SessionDao
    abstract fun historyDao(): HistoryDao

    companion object {
        const val VERSION = 1
        const val NAME = "lunchpack.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(*Migrations.ALL)
                // No destructive fallback: an unknown upgrade path must fail loudly in testing
                // instead of silently wiping the user's packs and history.
                .build()
    }
}

/**
 * Schema migrations. Every schema version is exported to app/schemas (Room Gradle plugin)
 * and committed. Version 1 is the initial schema; each future version adds an explicit
 * Migration here, for example:
 *
 *   val MIGRATION_1_2 = object : Migration(1, 2) {
 *       override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE ...") }
 *   }
 */
object Migrations {
    val ALL: Array<Migration> = emptyArray()
}
