package com.example.data

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

enum class CourseStatus(val labelRu: String) {
    ACTIVE("Активен"),
    COMPLETED("Завершен")
}

@Immutable
data class CoursePhase(
    val phaseNumber: Int,
    val durationDays: Int,
    val frequencyPerDay: Int,
    val dosePerIntake: Double,
    val intakeTimes: List<String>
) {
    val phaseTotalDose: Double
        get() = durationDays.coerceAtLeast(0) *
            frequencyPerDay.coerceAtLeast(0) *
            dosePerIntake.coerceAtLeast(0.0)

    companion object {
        fun defaultSlotsForFrequency(frequency: Int): List<String> {
            val safeFreq = frequency.coerceIn(1, 12)
            return when (safeFreq) {
                1 -> listOf("08:00")
                2 -> listOf("08:00", "20:00")
                3 -> listOf("08:00", "14:00", "20:00")
                4 -> listOf("08:00", "12:00", "16:00", "20:00")
                5 -> listOf("08:00", "11:00", "14:00", "17:00", "20:00")
                6 -> listOf("07:00", "10:00", "13:00", "16:00", "19:00", "22:00")
                else -> {
                    val startMinutes = 7 * 60
                    val endMinutes = 22 * 60
                    val step = (endMinutes - startMinutes) / (safeFreq - 1).coerceAtLeast(1)
                    (0 until safeFreq).map { idx ->
                        val totalMinutes = startMinutes + idx * step
                        val h = (totalMinutes / 60).coerceIn(0, 23)
                        val m = ((totalMinutes % 60) / 15) * 15
                        String.format(Locale.ROOT, "%02d:%02d", h, m)
                    }
                }
            }
        }

        fun normalizeSlots(currentSlots: List<String>, targetFrequency: Int): List<String> {
            val safeFreq = targetFrequency.coerceIn(1, 12)
            if (currentSlots.size == safeFreq) return currentSlots
            val defaults = defaultSlotsForFrequency(safeFreq)
            return List(safeFreq) { index ->
                currentSlots.getOrNull(index) ?: defaults[index]
            }
        }
    }
}

@Immutable
@Entity(tableName = "medication_courses")
data class MedicationCourse(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val formName: String,
    val unitName: String,
    val startDateEpochDay: Long,
    val status: CourseStatus = CourseStatus.ACTIVE,
    val phases: List<CoursePhase>,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    val totalDurationDays: Int
        get() = phases.sumOf { it.durationDays.coerceAtLeast(0) }

    val totalRequiredDose: Double
        get() = phases.sumOf { it.phaseTotalDose }

    val endDateEpochDay: Long
        get() = startDateEpochDay + (totalDurationDays - 1).coerceAtLeast(0)
}

@Immutable
@Entity(
    tableName = "intake_records",
    foreignKeys = [
        ForeignKey(
            entity = MedicationCourse::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["courseId", "dateEpochDay", "scheduledTime"], unique = true),
        Index(value = ["dateEpochDay"])
    ]
)
data class IntakeRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val courseId: Long,
    val dateEpochDay: Long,
    val scheduledTime: String,
    val phaseNumber: Int,
    val doseAmount: Double,
    val isTaken: Boolean,
    val isSkipped: Boolean = false,
    val takenAtTimestamp: Long? = null
)

@Immutable
data class CourseConsumedDoseTuple(
    val courseId: Long,
    val totalDose: Double
)

@Immutable
@Entity(tableName = "medication_forms")
data class MedicationFormEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val unit: String,
    val isDefault: Boolean = false
)

@Immutable
data class ScheduledIntakeItem(
    val uniqueKey: String,
    val courseId: Long,
    val courseName: String,
    val formName: String,
    val unitName: String,
    val notes: String,
    val dateEpochDay: Long,
    val scheduledTime: String,
    val dosePerIntake: Double,
    val phaseNumber: Int,
    val totalPhases: Int,
    val dayInPhase: Int,
    val phaseDurationDays: Int,
    val dayInCourse: Int,
    val totalCourseDays: Int,
    val isTaken: Boolean,
    val isSkipped: Boolean = false,
    val takenAtTimestamp: Long?
)

object DoseFormatter {
    fun format(value: Double): String {
        val rounded = value.roundToLong()
        return if (abs(value - rounded.toDouble()) < 0.0001) {
            rounded.toString()
        } else {
            val formatted = String.format(Locale.ROOT, "%.2f", value)
                .trimEnd('0')
                .trimEnd('.')
            formatted.replace('.', ',')
        }
    }
}
