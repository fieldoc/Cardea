package com.hrcoach.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.hrcoach.MainActivity
import com.hrcoach.R
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.absoluteValue

class BootcampReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {

    override fun doWork(): Result {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        createChannelIfNeeded()
        postReminderNotification()
        return Result.success()
    }

    private fun createChannelIfNeeded() {
        val channel = NotificationChannel(
            BootcampNotificationManager.CHANNEL_ID,
            BootcampNotificationManager.CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Gentle reminders for your Bootcamp training schedule"
        }
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun postReminderNotification() {
        val enrollmentId = inputData.getLong(KEY_ENROLLMENT_ID, 0L)
        val weekNumber = inputData.getInt(KEY_WEEK_NUMBER, 0)
        val sessionId = inputData.getLong(KEY_SESSION_ID, 0L)
        val dayOfWeek = inputData.getInt(KEY_DAY_OF_WEEK, 1).coerceIn(1, 7)
        val sessionType = inputData.getString(KEY_SESSION_TYPE).orEmpty()
        val targetMinutes = inputData.getInt(KEY_TARGET_MINUTES, 0).coerceAtLeast(0)
        val reminderKind = inputData.getString(KEY_REMINDER_KIND) ?: REMINDER_KIND_SESSION

        val openAppIntent = Intent(applicationContext, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val (title, text) = if (reminderKind == REMINDER_KIND_RESUME) {
            "Resume your Bootcamp" to "Your program is paused. Pick up where you left off."
        } else {
            val dayLabel = DayOfWeek.of(dayOfWeek).getDisplayName(TextStyle.SHORT, Locale.getDefault())
            val sessionTypeLabel = sessionType
                .lowercase(Locale.getDefault())
                .replace('_', ' ')
                .split(' ')
                .filter { it.isNotBlank() }
                .joinToString(" ") { token -> token.replaceFirstChar { it.titlecase(Locale.getDefault()) } }
                .ifBlank { "Session" }
            "Bootcamp reminder" to "Tomorrow ($dayLabel): $sessionTypeLabel - $targetMinutes min"
        }

        val notification = NotificationCompat.Builder(applicationContext, BootcampNotificationManager.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_cardea)
            .setColor(ContextCompat.getColor(applicationContext, R.color.cardea_notif_accent))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .build()

        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.notify(notificationId(enrollmentId, weekNumber, sessionId, dayOfWeek), notification)
    }

    private fun notificationId(
        enrollmentId: Long,
        weekNumber: Int,
        sessionId: Long,
        dayOfWeek: Int
    ): Int {
        if (sessionId > 0) return (sessionId % Int.MAX_VALUE).toInt()
        return "${enrollmentId}_${weekNumber}_$dayOfWeek".hashCode().absoluteValue
    }

    companion object {
        const val KEY_ENROLLMENT_ID = "enrollment_id"
        const val KEY_WEEK_NUMBER = "week_number"
        const val KEY_SESSION_ID = "session_id"
        const val KEY_DAY_OF_WEEK = "day_of_week"
        const val KEY_SESSION_TYPE = "session_type"
        const val KEY_TARGET_MINUTES = "target_minutes"
        const val KEY_REMINDER_KIND = "reminder_kind"
        const val REMINDER_KIND_SESSION = "SESSION"
        const val REMINDER_KIND_RESUME = "RESUME"
    }
}
