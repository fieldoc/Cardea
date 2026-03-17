package com.hrcoach.service.workout

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.hrcoach.MainActivity

class WorkoutNotificationHelper(
    private val context: Context,
    private val channelId: String,
    private val notificationId: Int
) {
    fun startForeground(service: Service, text: String) {
        createChannelIfNeeded()
        service.startForeground(notificationId, buildNotification(text))
    }

    fun update(text: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, buildNotification(text))
    }

    private fun createChannelIfNeeded() {
        val channel = NotificationChannel(
            channelId,
            "Workout",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active workout tracking"
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, channelId)
            .setContentTitle("HR Coach")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
