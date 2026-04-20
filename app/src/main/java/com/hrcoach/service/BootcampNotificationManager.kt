package com.hrcoach.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.hrcoach.data.db.BootcampSessionEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BootcampNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "bootcamp_reminders"
        const val CHANNEL_NAME = "Bootcamp Reminders"
        private const val WORK_NAME_PREFIX = "bootcamp_reminder"
        private const val TAG_PREFIX_ENROLLMENT = "bootcamp_enrollment"
        private const val RESUME_REMINDER_SUFFIX = "resume"
    }

    fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Gentle reminders for your Bootcamp training schedule"
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun scheduleWeekReminders(
        enrollmentId: Long,
        weekNumber: Int,
        sessions: List<BootcampSessionEntity>,
        startDateMs: Long
    ) {
        val workManager = WorkManager.getInstance(context)
        val now = Instant.now()

        sessions.forEach { session ->
            if (session.status == BootcampSessionEntity.STATUS_DEFERRED) return@forEach
            val reminderAt = reminderTimeForSession(startDateMs = startDateMs, session = session)
            if (!reminderAt.isAfter(now)) return@forEach

            val delayMs = ChronoUnit.MILLIS.between(now, reminderAt)
            val request = OneTimeWorkRequestBuilder<BootcampReminderWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(
                    Data.Builder()
                        .putLong(BootcampReminderWorker.KEY_ENROLLMENT_ID, enrollmentId)
                        .putInt(BootcampReminderWorker.KEY_WEEK_NUMBER, weekNumber)
                        .putLong(BootcampReminderWorker.KEY_SESSION_ID, session.id)
                        .putInt(BootcampReminderWorker.KEY_DAY_OF_WEEK, session.dayOfWeek)
                        .putString(BootcampReminderWorker.KEY_SESSION_TYPE, session.sessionType)
                        .putInt(BootcampReminderWorker.KEY_TARGET_MINUTES, session.targetMinutes)
                        .build()
                )
                .addTag(tagForEnrollment(enrollmentId))
                .build()

            workManager.enqueueUniqueWork(
                uniqueWorkName(
                    enrollmentId = enrollmentId,
                    weekNumber = weekNumber,
                    sessionId = session.id
                ),
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    fun cancelAll(enrollmentId: Long) {
        WorkManager.getInstance(context).cancelAllWorkByTag(tagForEnrollment(enrollmentId))
    }

    fun scheduleResumeReminder(enrollmentId: Long) {
        val workManager = WorkManager.getInstance(context)
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val remindAt = now
            .atZone(zone)
            .toLocalDate()
            .plusDays(1)
            .atTime(18, 0)
            .atZone(zone)
            .toInstant()
        val delayMs = ChronoUnit.MILLIS.between(now, remindAt).coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<BootcampReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putLong(BootcampReminderWorker.KEY_ENROLLMENT_ID, enrollmentId)
                    .putString(BootcampReminderWorker.KEY_REMINDER_KIND, BootcampReminderWorker.REMINDER_KIND_RESUME)
                    .build()
            )
            .addTag(tagForEnrollment(enrollmentId))
            .build()
        workManager.enqueueUniqueWork(
            uniqueResumeWorkName(enrollmentId),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun uniqueWorkName(enrollmentId: Long, weekNumber: Int, sessionId: Long): String =
        "${WORK_NAME_PREFIX}_e${enrollmentId}_w${weekNumber}_s${sessionId}"

    private fun uniqueResumeWorkName(enrollmentId: Long): String =
        "${WORK_NAME_PREFIX}_e${enrollmentId}_$RESUME_REMINDER_SUFFIX"

    private fun tagForEnrollment(enrollmentId: Long): String =
        "${TAG_PREFIX_ENROLLMENT}_$enrollmentId"

    private fun reminderTimeForSession(
        startDateMs: Long,
        session: BootcampSessionEntity
    ): Instant {
        val zone = ZoneId.systemDefault()
        val startDate = Instant.ofEpochMilli(startDateMs).atZone(zone).toLocalDate()
        val sessionDate = startDate
            .with(DayOfWeek.MONDAY)
            .plusWeeks(session.weekNumber - 1L)
            .plusDays(session.dayOfWeek - 1L)
        return sessionDate
            .minusDays(1)
            .atTime(18, 0)
            .atZone(zone)
            .toInstant()
    }
}
