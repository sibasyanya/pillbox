package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.widget.Toast
import com.example.R
import com.example.data.AppDatabase
import com.example.data.MedicationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return

        when (action) {
            NotificationHelper.ACTION_MEDICATION_REMINDER -> {
                handleMedicationReminder(context, intent)
            }

            NotificationHelper.ACTION_MARK_TAKEN -> {
                handleMarkTakenAction(context, intent)
            }
        }
    }

    private fun handleMedicationReminder(context: Context, intent: Intent) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MedicationTracker:AlarmWakeLock"
        )
        wakeLock?.acquire(5_000L)

        val courseId = intent.getLongExtra(NotificationHelper.EXTRA_COURSE_ID, -1L)
        if (courseId <= 0L) {
            if (wakeLock?.isHeld == true) wakeLock.release()
            return
        }

        val courseName = intent.getStringExtra(NotificationHelper.EXTRA_COURSE_NAME) ?: "Препарат"
        val unitName = intent.getStringExtra(NotificationHelper.EXTRA_UNIT_NAME) ?: "таб."
        val dateEpochDay = intent.getLongExtra(
            NotificationHelper.EXTRA_DATE_EPOCH_DAY,
            LocalDate.now().toEpochDay()
        )
        val scheduledTime = intent.getStringExtra(NotificationHelper.EXTRA_SCHEDULED_TIME) ?: "08:00"
        val phaseNumber = intent.getIntExtra(NotificationHelper.EXTRA_PHASE_NUMBER, 1)
        val totalPhases = intent.getIntExtra(NotificationHelper.EXTRA_TOTAL_PHASES, 1)
        val dayInPhase = intent.getIntExtra(NotificationHelper.EXTRA_DAY_IN_PHASE, 1)
        val phaseDuration = intent.getIntExtra(NotificationHelper.EXTRA_PHASE_DURATION, 1)
        val doseAmount = intent.getDoubleExtra(NotificationHelper.EXTRA_DOSE_AMOUNT, 1.0)

        NotificationHelper.showMedicationNotification(
            context = context,
            courseId = courseId,
            courseName = courseName,
            unitName = unitName,
            dateEpochDay = dateEpochDay,
            scheduledTime = scheduledTime,
            phaseNumber = phaseNumber,
            totalPhases = totalPhases,
            dayInPhase = dayInPhase,
            phaseDuration = phaseDuration,
            doseAmount = doseAmount
        )

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val repository = MedicationRepository(db.medicationDao())
                val course = repository.getCourseById(courseId)
                if (course != null) {
                    NotificationHelper.scheduleCourseAlarms(context, course)
                }
            } finally {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
                pendingResult.finish()
            }
        }
    }

    private fun handleMarkTakenAction(context: Context, intent: Intent) {
        val courseId = intent.getLongExtra(NotificationHelper.EXTRA_COURSE_ID, -1L)
        if (courseId <= 0L) return

        val courseName = intent.getStringExtra(NotificationHelper.EXTRA_COURSE_NAME) ?: "Препарат"
        val dateEpochDay = intent.getLongExtra(
            NotificationHelper.EXTRA_DATE_EPOCH_DAY,
            LocalDate.now().toEpochDay()
        )
        val scheduledTime = intent.getStringExtra(NotificationHelper.EXTRA_SCHEDULED_TIME) ?: "08:00"
        val phaseNumber = intent.getIntExtra(NotificationHelper.EXTRA_PHASE_NUMBER, 1)
        val doseAmount = intent.getDoubleExtra(NotificationHelper.EXTRA_DOSE_AMOUNT, 1.0)
        val notificationId = intent.getIntExtra(
            NotificationHelper.EXTRA_NOTIFICATION_ID,
            NotificationHelper.computeNotificationId(courseId, dateEpochDay, scheduledTime)
        )

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val repository = MedicationRepository(db.medicationDao())
                repository.setIntakeTakenState(
                    courseId = courseId,
                    dateEpochDay = dateEpochDay,
                    scheduledTime = scheduledTime,
                    phaseNumber = phaseNumber,
                    doseAmount = doseAmount,
                    isTaken = true
                )
                NotificationHelper.cancelNotification(context, notificationId)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.notif_confirmed_toast, courseName),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
