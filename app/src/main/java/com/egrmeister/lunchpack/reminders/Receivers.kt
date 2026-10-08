package com.egrmeister.lunchpack.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.egrmeister.lunchpack.LunchPackApp
import kotlinx.coroutines.launch

/** Receives only our own explicit, immutable alarm PendingIntent (android:exported="false"). */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMINDER) return
        val scheduledFor = intent.getLongExtra(EXTRA_SCHEDULED_FOR, -1L)
        val container = (context.applicationContext as LunchPackApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.engine.onAlarm(scheduledFor)
            } catch (e: Exception) {
                Log.w(LunchPackApp.TAG, "Reminder callback failed: ${e.javaClass.simpleName}")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMINDER = "com.egrmeister.lunchpack.action.REMINDER"
        const val EXTRA_SCHEDULED_FOR = "scheduled_for"
    }
}

/** Reboot, app update and manual clock / time-zone changes: reschedule and refresh "today". */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED) return
        val container = (context.applicationContext as LunchPackApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.dateTicker.refresh()
                container.engine.reschedule()
                Log.i(LunchPackApp.TAG, "Rescheduled after system event")
            } catch (e: Exception) {
                Log.w(LunchPackApp.TAG, "Reschedule failed: ${e.javaClass.simpleName}")
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
