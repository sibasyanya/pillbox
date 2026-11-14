package com.example.data

import androidx.room.TypeConverter

class Converters {

    @TypeConverter
    fun fromCourseStatus(status: CourseStatus): String = status.name

    @TypeConverter
    fun toCourseStatus(value: String): CourseStatus {
        return try {
            CourseStatus.valueOf(value)
        } catch (_: IllegalArgumentException) {
            CourseStatus.ACTIVE
        }
    }

    /**
     * Формат сериализации фаз без зависимости от Android JSONObject:
     * Фазы разделяются символом ';', поля внутри фазы — '|', времена приема — ','.
     * Пример: 1|4|3|2.0|08:00,14:00,20:00;2|3|2|1.0|08:00,20:00
     */
    @TypeConverter
    fun fromCoursePhaseList(phases: List<CoursePhase>): String {
        if (phases.isEmpty()) return ""
        return phases.joinToString(separator = ";") { phase ->
            val timesJoined = phase.intakeTimes.joinToString(separator = ",") { it.trim() }
            "${phase.phaseNumber}|${phase.durationDays}|${phase.frequencyPerDay}|${phase.dosePerIntake}|$timesJoined"
        }
    }

    @TypeConverter
    fun toCoursePhaseList(serialized: String): List<CoursePhase> {
        if (serialized.isBlank()) return emptyList()
        return serialized.split(";")
            .filter { it.isNotBlank() }
            .mapIndexedNotNull { index, rawPhase ->
                val parts = rawPhase.split("|")
                if (parts.size < 4) return@mapIndexedNotNull null
                val phaseNum = parts[0].toIntOrNull() ?: (index + 1)
                val duration = parts[1].toIntOrNull()?.coerceAtLeast(1) ?: 1
                val frequency = parts[2].toIntOrNull()?.coerceAtLeast(1) ?: 1
                val dose = parts[3].toDoubleOrNull()?.coerceAtLeast(0.1) ?: 1.0
                val times = if (parts.size >= 5 && parts[4].isNotBlank()) {
                    parts[4].split(",").map { it.trim() }.filter { it.isNotBlank() }
                } else {
                    CoursePhase.defaultSlotsForFrequency(frequency)
                }
                CoursePhase(
                    phaseNumber = phaseNum,
                    durationDays = duration,
                    frequencyPerDay = frequency,
                    dosePerIntake = dose,
                    intakeTimes = CoursePhase.normalizeSlots(times, frequency)
                )
            }
    }
}
