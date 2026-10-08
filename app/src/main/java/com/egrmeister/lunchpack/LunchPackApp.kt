package com.egrmeister.lunchpack

import android.app.Application
import android.content.Context
import android.util.Log
import com.egrmeister.lunchpack.data.AppDatabase
import com.egrmeister.lunchpack.data.LunchRepository
import com.egrmeister.lunchpack.data.SettingsStore
import com.egrmeister.lunchpack.domain.AppClock
import com.egrmeister.lunchpack.domain.PackingService
import com.egrmeister.lunchpack.domain.ReminderEngine
import com.egrmeister.lunchpack.domain.SystemAppClock
import com.egrmeister.lunchpack.domain.today
import com.egrmeister.lunchpack.reminders.AndroidAlarmScheduler
import com.egrmeister.lunchpack.reminders.AndroidNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate

class LunchPackApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifications.ensureChannel()
        container.appScope.launch { container.startup() }
    }

    companion object {
        /** Production log tag. Logs never contain item names or notes. */
        const val TAG = "LunchPack"
    }
}

/** "Today" in the device time zone; refreshed at midnight, on resume and on clock changes. */
class DateTicker(private val clock: AppClock, scope: CoroutineScope) {
    private val _today = MutableStateFlow(clock.today())
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    init {
        scope.launch {
            while (isActive) {
                val now = clock.now().atZone(clock.zone())
                val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(clock.zone())
                val untilMidnight = Duration.between(now, nextMidnight).toMillis() + 1_000L
                // Re-check at least every 15 minutes so a time-zone change is picked up promptly.
                delay(untilMidnight.coerceIn(1_000L, 15 * 60_000L))
                refresh()
            }
        }
    }

    fun refresh() {
        _today.value = clock.today()
    }
}

/** Manual dependency injection. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val clock: AppClock = SystemAppClock
    val database: AppDatabase = AppDatabase.build(appContext)
    val settings = SettingsStore(appContext)
    val repository = LunchRepository(database)
    val notifications = AndroidNotifications(appContext)
    val alarms = AndroidAlarmScheduler(appContext)
    val engine = ReminderEngine(
        store = settings,
        plans = repository,
        alarms = alarms,
        notifications = notifications,
        clock = clock,
        log = { Log.i(LunchPackApp.TAG, it) },
    )
    val service = PackingService(
        ds = repository,
        clock = clock,
        onPlansChanged = { engine.onPlansChanged() },
    )
    val dateTicker = DateTicker(clock, appScope)

    private val _notificationsAllowed = MutableStateFlow(notifications.canPost())
    val notificationsAllowed: StateFlow<Boolean> = _notificationsAllowed.asStateFlow()

    /** First launch seeds the example packs exactly once; every launch reschedules reminders. */
    suspend fun startup() {
        if (!settings.isSeeded()) {
            service.insertStarters()
            settings.markSeeded()
        }
        engine.reschedule()
    }

    /** Called on every resume: permission/channel changes and the current date. */
    fun onForeground() {
        dateTicker.refresh()
        val allowed = notifications.canPost()
        if (allowed != _notificationsAllowed.value) {
            _notificationsAllowed.value = allowed
        }
    }

    /**
     * Clear all local data: cancel alarms and notifications first, wipe Room and DataStore,
     * then restore defaults (example packs, reminders off).
     */
    suspend fun clearAllData() {
        engine.cancelAll()
        withContext(Dispatchers.IO) { repository.clearAllTables() }
        settings.clearAll()
        service.insertStarters()
        settings.markSeeded()
        engine.reschedule()
        dateTicker.refresh()
    }
}
