package com.hrcoach.service.workout

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import com.hrcoach.MainActivity
import com.hrcoach.R
import com.hrcoach.domain.model.ZoneStatus
import com.hrcoach.service.WorkoutForegroundService
import com.hrcoach.service.workout.notification.BadgeBitmapCache
import com.hrcoach.service.workout.notification.BadgeBitmapRenderer
import com.hrcoach.service.workout.notification.NotifPayload

/**
 * Chip-first workout notification.
 *
 * On Android 15 foreground-service ongoing notifications render as the compact "Live Update"
 * chip on the lockscreen and in the status bar. We lean into that surface rather than fight it:
 * the chip background is tinted with the current zone colour (green / amber / red / grey)
 * via [NotificationCompat.Builder.setColor] + `setColorized(true)`, so zone state is legible
 * at a glance without the user unlocking or expanding anything.
 *
 * The notification-shade expanded view still gets the 144px gradient badge as its large icon.
 * No MediaStyle / MediaSession — those were impersonating a media player to coax Android into
 * showing a full-width lockscreen card, which no major running app attempts and which Android 15
 * reserves for apps that actually hold audio focus.
 */
class WorkoutNotificationHelper(
    private val context: Context,
    private val channelId: String,
    private val notificationId: Int,
) {
    @Volatile
    private var stopped = false

    @Volatile
    private var lastPayload: NotifPayload? = null

    private val renderer = BadgeBitmapRenderer()

    /** 144px badge for the notification large-icon slot (shown in the shade, not on the chip). */
    private val bitmapCache = BadgeBitmapCache<Bitmap>(maxEntries = 16) { hr, zone, paused ->
        renderer.render(currentHr = hr, zoneStatus = zone, paused = paused)
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

    /** Steady-state tick — zone-tinted chip + rich shade view. */
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

    /** Cached 144px badge bitmap used as the notification large-icon. */
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
        return baseBuilder(accent = CHIP_NEUTRAL)
            .setContentTitle("Cardea")
            .setContentText(text)
            .build()
    }

    private fun buildRichNotification(payload: NotifPayload): Notification {
        val accent = chipColorFor(payload)
        val badge = badgeFor(payload)

        val builder = baseBuilder(accent = accent)
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

        return builder.build()
    }

    private fun baseBuilder(accent: Int): NotificationCompat.Builder {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notif_cardea)
            .setColor(accent)
            .setColorized(true) // Tints the chip background + shade header with the zone colour
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
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Active workout tracking"
            setShowBadge(false)
            // IMPORTANCE_DEFAULT is required so the chip renders as a first-class Live Update
            // rather than a silent status-bar-only notification. Sound is muted because we
            // never want the channel to ding on workout start. setOnlyAlertOnce(true) on the
            // builder already silences repeat updates.
            setSound(null, null)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    // ------------------------------------------------------------------
    // Zone → chip colour
    // ------------------------------------------------------------------

    /**
     * Maps zone state to the chip tint. Runners read these conventionally:
     * green = on target, red = too hot (slow down), amber = too easy (push), grey = no signal.
     * Paused overrides zone state so the chip reads as inactive.
     */
    private fun chipColorFor(payload: NotifPayload): Int {
        if (payload.isPaused) return CHIP_NEUTRAL
        return when (payload.zoneStatus) {
            ZoneStatus.IN_ZONE -> CHIP_GREEN
            ZoneStatus.ABOVE_ZONE -> CHIP_RED
            ZoneStatus.BELOW_ZONE -> CHIP_AMBER
            ZoneStatus.NO_DATA -> CHIP_NEUTRAL
        }
    }

    companion object {
        private const val REQUEST_OPEN = 1001
        private const val REQUEST_PAUSE = 1002
        private const val REQUEST_RESUME = 1003

        // Canonical source: ui/theme/Color.kt (ZoneGreen, ZoneAmber, ZoneRed).
        // Duplicated here as raw ints because this class runs service-side
        // and cannot import androidx.compose.ui.graphics.Color.
        // Keep in sync with Color.kt — if the palette changes, update both places.
        private const val CHIP_GREEN = 0xFF22C55E.toInt()    // ZoneGreen
        private const val CHIP_AMBER = 0xFFFACC15.toInt()    // ZoneAmber
        private const val CHIP_RED = 0xFFEF4444.toInt()      // ZoneRed
        private const val CHIP_NEUTRAL = 0xFF52525B.toInt()  // slate-600 — paused / no data
    }
}
