package com.egrmeister.lunchpack.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.egrmeister.lunchpack.domain.ReminderConfig
import com.egrmeister.lunchpack.domain.ReminderStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.lunchpackStore: DataStore<Preferences> by preferencesDataStore(name = "lunchpack_settings")

/**
 * DataStore for reminder preferences and lightweight UI state (selected session, reduced
 * animation, first-launch seeding flag). App-private and excluded from backup / transfer.
 */
class SettingsStore(context: Context) : ReminderStore {
    private val store = context.applicationContext.lunchpackStore

    private object Keys {
        val SEEDED = booleanPreferencesKey("starters_seeded")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val SCHEDULED_FOR = longPreferencesKey("reminder_scheduled_for")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val SELECTED_SESSION = longPreferencesKey("selected_session_id")
    }

    val reminderConfigFlow: Flow<ReminderConfig> = store.data.map { it.toConfig() }

    val reducedMotionFlow: Flow<Boolean> = store.data.map { it[Keys.REDUCED_MOTION] ?: false }

    val selectedSessionFlow: Flow<Long?> = store.data.map { it[Keys.SELECTED_SESSION] }

    override suspend fun reminderConfig(): ReminderConfig = store.data.first().toConfig()

    override suspend fun scheduledFor(): Long? = store.data.first()[Keys.SCHEDULED_FOR]

    override suspend fun setScheduledFor(epochMillis: Long?) {
        store.edit { prefs ->
            if (epochMillis == null) {
                prefs.remove(Keys.SCHEDULED_FOR)
            } else {
                prefs[Keys.SCHEDULED_FOR] = epochMillis
            }
        }
    }

    suspend fun setReminderEnabled(enabled: Boolean) {
        store.edit { it[Keys.REMINDER_ENABLED] = enabled }
    }

    suspend fun setReminderTime(hour: Int, minute: Int) {
        store.edit {
            it[Keys.REMINDER_HOUR] = hour.coerceIn(0, 23)
            it[Keys.REMINDER_MINUTE] = minute.coerceIn(0, 59)
        }
    }

    suspend fun setReducedMotion(enabled: Boolean) {
        store.edit { it[Keys.REDUCED_MOTION] = enabled }
    }

    suspend fun setSelectedSession(id: Long?) {
        store.edit { prefs ->
            if (id == null) {
                prefs.remove(Keys.SELECTED_SESSION)
            } else {
                prefs[Keys.SELECTED_SESSION] = id
            }
        }
    }

    suspend fun isSeeded(): Boolean = store.data.first()[Keys.SEEDED] ?: false

    suspend fun markSeeded() {
        store.edit { it[Keys.SEEDED] = true }
    }

    suspend fun clearAll() {
        store.edit { it.clear() }
    }

    private fun Preferences.toConfig() = ReminderConfig(
        enabled = this[Keys.REMINDER_ENABLED] ?: ReminderConfig.Default.enabled,
        hour = this[Keys.REMINDER_HOUR] ?: ReminderConfig.Default.hour,
        minute = this[Keys.REMINDER_MINUTE] ?: ReminderConfig.Default.minute,
    )
}
