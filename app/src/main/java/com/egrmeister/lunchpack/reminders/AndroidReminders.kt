package com.egrmeister.lunchpack.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.egrmeister.lunchpack.MainActivity
import com.egrmeister.lunchpack.R
import com.egrmeister.lunchpack.domain.AlarmGateway
import com.egrmeister.lunchpack.domain.NotificationGateway
import java.time.Instant
import java.time.LocalDate

/**
 * Inexact one-shot AlarmManager scheduling. LunchPack does not request SCHEDULE_EXACT_ALARM or
 * USE_EXACT_ALARM, so Android may deliver the reminder later than the chosen time.
 */
class AndroidAlarmScheduler(private val context: Context) : AlarmGateway {
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(at: Instant) {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pendingIntent(at.toEpochMilli()))
    }

    override fun cancel() {
        alarmManager.cancel(pendingIntent(0L))
    }

    private fun pendingIntent(scheduledFor: Long): PendingIntent {
        // Explicit intent to our own non-exported receiver. One request code → at most one alarm.
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_REMINDER)
            .putExtra(ReminderReceiver.EXTRA_SCHEDULED_FOR, scheduledFor)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private companion object {
        const val REQUEST_CODE = 5101
    }
}

class AndroidNotifications(private val context: Context) : NotificationGateway {

    fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.reminder_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    override fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL_ID)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    override fun postTomorrowReminder(date: LocalDate) {
        if (!canPost()) return
        ensureChannel()
        val open = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_PLAN)
            .putExtra(MainActivity.EXTRA_PLAN_DATE, date.toString())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = context.getString(R.string.reminder_title)
        val body = context.getString(R.string.reminder_body)
        // Generic text only: item names and notes never reach the lock screen.
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            // One notification ID: a new reminder replaces the previous one, never a backlog.
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post — planning keeps working.
        }
    }

    override fun dismiss() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    companion object {
        const val CHANNEL_ID = "pack_reminders"
        const val NOTIFICATION_ID = 2001
    }
}
