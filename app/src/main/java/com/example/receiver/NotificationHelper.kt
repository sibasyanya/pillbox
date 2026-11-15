package com.example.receiver

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.CourseStatus
import com.example.data.DoseFormatter
import com.example.data.MedicationCourse
import com.example.data.MedicationRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

object NotificationHelper {

    const val CHANNEL_ID = "medication_reminders_alarm_channel_v2"

    const val ACTION_MEDICATION_REMINDER = "com.example.action.MEDICATION_REMINDER"
    const val ACTION_MARK_TAKEN = "com.example.action.MARK_TAKEN"

    const val EXTRA_COURSE_ID = "extra_course_id"
    const val EXTRA_COURSE_NAME = "extra_course_name"
    const val EXTRA_FORM_NAME = "extra_form_name"
    const val EXTRA_UNIT_NAME = "extra_unit_name"
    const val EXTRA_DATE_EPOCH_DAY = "extra_date_epoch_day"
    const val EXTRA_SCHEDULED_TIME = "extra_scheduled_time"
    const val EXTRA_PHASE_NUMBER = "extra_phase_number"
    const val EXTRA_TOTAL_PHASES = "extra_total_phases"
    const val EXTRA_DAY_IN_PHASE = "extra_day_in_phase"
    const val EXTRA_PHASE_DURATION = "extra_phase_duration"
    const val EXTRA_DOSE_AMOUNT = "extra_dose_amount"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

    @Volatile
    private var isChannelInitialized = false

    fun ensureNotificationChannel(context: Context) {
        if (isChannelInitialized) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) {
            isChannelInitialized = true
            return
        }

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_desc)
            enableVibration(true)
            enableLights(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
            vibrationPattern = longArrayOf(0L, 350L, 150L, 350L)
            setSound(soundUri, audioAttrs)
        }
        manager.createNotificationChannel(channel)
        isChannelInitialized = true
    }

    fun showMedicationNotification(
        context: Context,
        courseId: Long,
        courseName: String,
        unitName: String,
        dateEpochDay: Long,
        scheduledTime: String,
        phaseNumber: Int,
        totalPhases: Int,
        dayInPhase: Int,
        phaseDuration: Int,
        doseAmount: Double
    ) {
        ensureNotificationChannel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val notificationId = computeNotificationId(courseId, dateEpochDay, scheduledTime)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val markTakenIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_MARK_TAKEN
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            putExtra(EXTRA_COURSE_ID, courseId)
            putExtra(EXTRA_COURSE_NAME, courseName)
            putExtra(EXTRA_UNIT_NAME, unitName)
            putExtra(EXTRA_DATE_EPOCH_DAY, dateEpochDay)
            putExtra(EXTRA_SCHEDULED_TIME, scheduledTime)
            putExtra(EXTRA_PHASE_NUMBER, phaseNumber)
            putExtra(EXTRA_DOSE_AMOUNT, doseAmount)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val markTakenPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 100_000,
            markTakenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val formattedDose = DoseFormatter.format(doseAmount)
        val title = context.getString(R.string.notif_title, courseName)
        val body = "Доза: $formattedDose $unitName • Фаза $phaseNumber из $totalPhases, день $dayInPhase из $phaseDuration ($scheduledTime)"

        val extrasBundle = android.os.Bundle().apply {
            putLong(EXTRA_COURSE_ID, courseId)
            putLong(EXTRA_DATE_EPOCH_DAY, dateEpochDay)
            putString(EXTRA_SCHEDULED_TIME, scheduledTime)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setExtras(extrasBundle)
            .setContentIntent(contentPendingIntent)
            .addAction(
                android.R.drawable.checkbox_on_background,
                context.getString(R.string.notif_action_taken),
                markTakenPendingIntent
            )
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    fun cancelNotification(context: Context, notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    /**
     * Скрывает активное пуш-уведомление в шторке и отменяет ожидающий будильник для конкретного приема,
     * когда пользователь отмечает прием («Принято» или «Не принято») внутри приложения.
     */
    fun dismissIntakeNotificationAndCancelAlarm(
        context: Context,
        courseId: Long,
        dateEpochDay: Long,
        scheduledTime: String
    ) {
        val notificationManagerCompat = NotificationManagerCompat.from(context)
        val notificationId = computeNotificationId(courseId, dateEpochDay, scheduledTime)
        notificationManagerCompat.cancel(notificationId)

        // Дополнительно проверяем активные уведомления в статус-баре (включая тестовые или за другие минуты того же курса)
        val sysNotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val activeNotifications = sysNotificationManager?.activeNotifications
        if (activeNotifications != null) {
            for (sbn in activeNotifications) {
                val extras = sbn.notification.extras
                val notifCourseId = extras?.getLong(EXTRA_COURSE_ID, -1L) ?: -1L
                val notifTime = extras?.getString(EXTRA_SCHEDULED_TIME)
                if (sbn.id == notificationId ||
                    (notifCourseId == courseId && (notifTime == null || notifTime == scheduledTime)) ||
                    notifCourseId == 999_001L
                ) {
                    notificationManagerCompat.cancel(sbn.id)
                }
            }
        }

        // Отменяем запланированный будильник на это время, если пользователь отметил прием заранее
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val requestCode = computeAlarmRequestCode(courseId, dateEpochDay, scheduledTime)
        val alarmIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_MEDICATION_REMINDER
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            alarmIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    /**
     * Планирует тестовое напоминание через AlarmManager с задержкой (например, 10 секунд),
     * чтобы пользователь мог свернуть приложение и убедиться в приходе пуша в фоне.
     */
    fun scheduleDelayedTestAlarm(
        context: Context,
        delaySeconds: Int = 10,
        courseId: Long = 999_001L,
        courseName: String = "Проверка фонового приема",
        unitName: String = "таб.",
        doseAmount: Double = 1.0,
        scheduledTimeOverride: String? = null,
        dateEpochDayOverride: Long? = null
    ) {
        ensureNotificationChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val now = LocalDateTime.now()
        val todayEpoch = dateEpochDayOverride ?: now.toLocalDate().toEpochDay()
        val scheduledTime = scheduledTimeOverride ?: String.format(
            java.util.Locale.ROOT,
            "%02d:%02d",
            now.hour,
            now.minute
        )

        val alarmIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_MEDICATION_REMINDER
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            putExtra(EXTRA_COURSE_ID, courseId)
            putExtra(EXTRA_COURSE_NAME, courseName)
            putExtra(EXTRA_FORM_NAME, "Таблетки")
            putExtra(EXTRA_UNIT_NAME, unitName)
            putExtra(EXTRA_DATE_EPOCH_DAY, todayEpoch)
            putExtra(EXTRA_SCHEDULED_TIME, scheduledTime)
            putExtra(EXTRA_PHASE_NUMBER, 1)
            putExtra(EXTRA_TOTAL_PHASES, 1)
            putExtra(EXTRA_DAY_IN_PHASE, 1)
            putExtra(EXTRA_PHASE_DURATION, 5)
            putExtra(EXTRA_DOSE_AMOUNT, doseAmount)
        }

        val requestCode = 999_001
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = System.currentTimeMillis() + (delaySeconds.coerceAtLeast(1) * 1000L)
        scheduleExactSafe(context, alarmManager, triggerAtMillis, pendingIntent)
    }

    /**
     * Планирует предстоящие приемы для заданного курса на ближайшие [horizonDays] дней.
     * Вызывается на фоновом диспетчере (Dispatchers.IO), чтобы исключить блокировку UI-потока.
     */
    fun scheduleCourseAlarms(
        context: Context,
        course: MedicationCourse,
        horizonDays: Int = 2,
        allowCurrentMinuteImmediate: Boolean = false
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        if (course.status != CourseStatus.ACTIVE) {
            cancelCourseAlarms(context, course)
            return
        }

        val now = LocalDateTime.now()
        val nowTruncatedToMinute = now.withSecond(0).withNano(0)
        val todayEpoch = now.toLocalDate().toEpochDay()
        val startSearchDay = maxOf(todayEpoch, course.startDateEpochDay)
        val endSearchDay = minOf(course.endDateEpochDay, todayEpoch + horizonDays)

        if (startSearchDay > endSearchDay) return

        val zoneId = ZoneId.systemDefault()

        for (epochDay in startSearchDay..endSearchDay) {
            val resolution = MedicationRepository.resolvePhaseForDay(course, epochDay) ?: continue
            val localDate = LocalDate.ofEpochDay(epochDay)

            for (timeString in resolution.phase.intakeTimes) {
                val localTime = parseLocalTimeOrNull(timeString) ?: continue
                val triggerDateTime = LocalDateTime.of(localDate, localTime)

                val isSameMinuteJustSaved = allowCurrentMinuteImmediate &&
                    triggerDateTime == nowTruncatedToMinute

                if (!triggerDateTime.isAfter(now) && !isSameMinuteJustSaved) continue

                val triggerMillis = if (isSameMinuteJustSaved) {
                    System.currentTimeMillis() + 2_000L
                } else {
                    triggerDateTime
                        .atZone(zoneId)
                        .toInstant()
                        .toEpochMilli()
                }

                val requestCode = computeAlarmRequestCode(course.id, epochDay, timeString)
                val alarmIntent = Intent(context, AlarmReceiver::class.java).apply {
                    action = ACTION_MEDICATION_REMINDER
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                    putExtra(EXTRA_COURSE_ID, course.id)
                    putExtra(EXTRA_COURSE_NAME, course.name)
                    putExtra(EXTRA_FORM_NAME, course.formName)
                    putExtra(EXTRA_UNIT_NAME, course.unitName)
                    putExtra(EXTRA_DATE_EPOCH_DAY, epochDay)
                    putExtra(EXTRA_SCHEDULED_TIME, timeString)
                    putExtra(EXTRA_PHASE_NUMBER, resolution.phaseIndexOneBased)
                    putExtra(EXTRA_TOTAL_PHASES, resolution.totalPhases)
                    putExtra(EXTRA_DAY_IN_PHASE, resolution.dayInPhase)
                    putExtra(EXTRA_PHASE_DURATION, resolution.phaseDurationDays)
                    putExtra(EXTRA_DOSE_AMOUNT, resolution.phase.dosePerIntake)
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    alarmIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                scheduleExactSafe(context, alarmManager, triggerMillis, pendingIntent)
            }
        }
    }

    fun cancelCourseAlarms(context: Context, course: MedicationCourse) {
        val notificationManagerCompat = NotificationManagerCompat.from(context)
        val sysNotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        sysNotificationManager?.activeNotifications?.forEach { sbn ->
            val notifCourseId = sbn.notification.extras?.getLong(EXTRA_COURSE_ID, -1L) ?: -1L
            if (notifCourseId == course.id) {
                notificationManagerCompat.cancel(sbn.id)
            }
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val totalDays = course.totalDurationDays
        if (totalDays <= 0) return

        val todayEpoch = LocalDate.now().toEpochDay()
        val startCancelDay = maxOf(course.startDateEpochDay, todayEpoch - 1L)
        val endCancelDay = minOf(course.endDateEpochDay, todayEpoch + 14L)
        if (startCancelDay > endCancelDay) return

        for (epochDay in startCancelDay..endCancelDay) {
            val resolution = MedicationRepository.resolvePhaseForDay(course, epochDay) ?: continue
            for (timeString in resolution.phase.intakeTimes) {
                val notifId = computeNotificationId(course.id, epochDay, timeString)
                notificationManagerCompat.cancel(notifId)

                val requestCode = computeAlarmRequestCode(course.id, epochDay, timeString)
                val alarmIntent = Intent(context, AlarmReceiver::class.java).apply {
                    action = ACTION_MEDICATION_REMINDER
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    alarmIntent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (pendingIntent != null) {
                    alarmManager.cancel(pendingIntent)
                    pendingIntent.cancel()
                }
            }
        }
    }

    fun rescheduleAllActiveCourses(context: Context, activeCourses: List<MedicationCourse>) {
        ensureNotificationChannel(context)
        for (course in activeCourses) {
            if (course.status == CourseStatus.ACTIVE) {
                scheduleCourseAlarms(context, course)
            }
        }
    }

    private fun scheduleExactSafe(
        context: Context,
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        operation: PendingIntent
    ) {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val showPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }

            if (canExact) {
                val clockInfo = AlarmManager.AlarmClockInfo(triggerAtMillis, showPendingIntent)
                alarmManager.setAlarmClock(clockInfo, operation)
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    operation
                )
            }
        } catch (_: SecurityException) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    operation
                )
            } catch (_: SecurityException) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    operation
                )
            }
        }
    }

    fun parseLocalTimeOrNull(timeStr: String): LocalTime? {
        return try {
            val parts = timeStr.trim().split(":")
            if (parts.size != 2) return null
            val h = parts[0].toInt().coerceIn(0, 23)
            val m = parts[1].toInt().coerceIn(0, 59)
            LocalTime.of(h, m)
        } catch (_: Exception) {
            null
        }
    }

    fun computeNotificationId(courseId: Long, dateEpochDay: Long, scheduledTime: String): Int {
        val raw = "${courseId}_${dateEpochDay}_$scheduledTime".hashCode()
        return abs(raw % 900_000) + 10_000
    }

    fun computeAlarmRequestCode(courseId: Long, dateEpochDay: Long, scheduledTime: String): Int {
        val raw = "alarm_${courseId}_${dateEpochDay}_$scheduledTime".hashCode()
        return abs(raw % 1_900_000_000) + 1
    }
}
