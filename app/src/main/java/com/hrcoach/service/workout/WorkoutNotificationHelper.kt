package com.hrcoach.service.workout

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.hrcoach.MainActivity
import com.hrcoach.R
import com.hrcoach.service.WorkoutForegroundService
import com.hrcoach.service.workout.notification.BadgeBitmapCache
import com.hrcoach.service.workout.notification.BadgeBitmapRenderer
import com.hrcoach.service.workout.notification.NotifPayload

class WorkoutNotificationHelper(
    private val context: Context,
    private val channelId: String,
    private val notificationId: Int,
) {
    @Volatile
    private var stopped = false

    @Volatile
    private var lastPayload: NotifPayload? = null

    /** Supplied by the service in onCreate. Required for MediaStyle. */
    private var mediaSessionToken: MediaSessionCompat.Token? = null

    private val renderer = BadgeBitmapRenderer()
    private val bitmapCache = BadgeBitmapCache<Bitmap>(maxEntries = 16) { hr, zone, paused ->
        renderer.render(currentHr = hr, zoneStatus = zone, paused = paused)
    }

    fun attachMediaSession(token: MediaSessionCompat.Token) {
        this.mediaSessionToken = token
    }

    /** Startup call — plain text notification, before the first processTick runs. */
    fun startForeground(service: Service, text: String) {
        stopped = false
        createChannelIfNeeded()
        service.startForeground(notificationId, buildPlainNotification(text))
    }

    /** Pre-first-tick transitional updates (e.g. "Starting workout..."). */
    fun update(text: String) {
        if (stopped) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, buildPlainNotification(text))
    }

    /** Steady-state tick — rich MediaStyle notification. */
    fun update(payload: NotifPayload) {
        if (stopped) return
        if (payload == lastPayload) return  // dedup — NotifPayload is a data class
        lastPayload = payload
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, buildRichNotification(payload))
    }

    fun stop() {
        stopped = true
        bitmapCache.clear()
        lastPayload = null
    }

    /**
     * Returns the (cached) badge bitmap that would be shown for the given payload.
     * Used by the service to populate MediaSession METADATA_KEY_ALBUM_ART so the
     * lockscreen media controller picks up the same artwork as the notification.
     */
    fun badgeFor(payload: NotifPayload): Bitmap {
        return bitmapCache.get(
            currentHr = payload.currentHr,
            zoneStatus = payload.zoneStatus,
            paused = payload.isPaused,
        )
    }

    // ------------------------------------------------------------------
    // Notification builders
    // ------------------------------------------------------------------

    private fun buildPlainNotification(text: String): Notification {
        return baseBuilder()
            .setContentTitle("Cardea")
            .setContentText(text)
            .build()
    }

    private fun buildRichNotification(payload: NotifPayload): Notification {
        val badge = bitmapCache.get(
            currentHr = payload.currentHr,
            zoneStatus = payload.zoneStatus,
            paused = payload.isPaused,
        )

        val builder = baseBuilder()
            .setContentTitle(payload.titleText)
            .setContentText(payload.subtitleText)
            .setLargeIcon(badge)
            .addAction(buildPauseResumeAction(payload.isPaused))

        // Progress bar — indeterminate for free run / unknown total
        val maxProgress = if (payload.isIndeterminate) 0 else payload.totalSeconds.toInt().coerceAtLeast(0)
        val currentProgress = payload.elapsedSeconds.toInt().coerceIn(0, maxProgress.coerceAtLeast(1))
        builder.setProgress(
            maxProgress,
            currentProgress,
            payload.isIndeterminate,
        )

        // Attach MediaStyle if we have a session token
        val token = mediaSessionToken
        if (token != null) {
            val style = MediaStyle()
                .setMediaSession(token)
                .setShowActionsInCompactView(0) // only the pause/resume action
            builder.setStyle(style)
        }

        return builder.build()
    }

    private fun baseBuilder(): NotificationCompat.Builder {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notif_cardea)
            .setColor(ContextCompat.getColor(context, R.color.cardea_notif_accent))
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setShowWhen(true)
            .setUsesChronometer(false)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // show on lockscreen in full
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
    }

    private fun buildPauseResumeAction(isPaused: Boolean): NotificationCompat.Action {
        val action: String
        val title: String
        val iconRes: Int
        val requestCode: Int
        if (isPaused) {
            action = WorkoutForegroundService.ACTION_RESUME
            title = context.getString(R.string.button_resume)
            iconRes = android.R.drawable.ic_media_play
            requestCode = REQUEST_RESUME
        } else {
            action = WorkoutForegroundService.ACTION_PAUSE
            title = context.getString(R.string.button_pause)
            iconRes = android.R.drawable.ic_media_pause
            requestCode = REQUEST_PAUSE
        }
        val intent = Intent(context, WorkoutForegroundService::class.java).apply {
            this.action = action
        }
        val pi = PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(iconRes, title, pi).build()
    }

    private fun createChannelIfNeeded() {
        val channel = NotificationChannel(
            channelId,
            "Cardea Workout",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Active workout tracking"
            setShowBadge(false)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val REQUEST_OPEN = 1001
        private const val REQUEST_PAUSE = 1002
        private const val REQUEST_RESUME = 1003
    }
}
