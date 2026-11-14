package com.example

import com.example.data.Converters
import com.example.data.CoursePhase
import com.example.data.CourseStatus
import com.example.data.MedicationCourse
import com.example.data.MedicationRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun phaseMath_controlExample_calculates10DaysAnd33Tablets() {
        val phases = listOf(
            CoursePhase(
                phaseNumber = 1,
                durationDays = 4,
                frequencyPerDay = 3,
                dosePerIntake = 2.0,
                intakeTimes = listOf("08:00", "14:00", "20:00")
            ),
            CoursePhase(
                phaseNumber = 2,
                durationDays = 3,
                frequencyPerDay = 2,
                dosePerIntake = 1.0,
                intakeTimes = listOf("08:00", "20:00")
            ),
            CoursePhase(
                phaseNumber = 3,
                durationDays = 3,
                frequencyPerDay = 1,
                dosePerIntake = 1.0,
                intakeTimes = listOf("08:00")
            )
        )

        val course = MedicationCourse(
            id = 1L,
            name = "Контрольный препарат",
            formName = "Таблетки",
            unitName = "таб.",
            startDateEpochDay = 20_000L,
            status = CourseStatus.ACTIVE,
            phases = phases
        )

        assertEquals(24.0, phases[0].phaseTotalDose, 0.001)
        assertEquals(6.0, phases[1].phaseTotalDose, 0.001)
        assertEquals(3.0, phases[2].phaseTotalDose, 0.001)
        assertEquals(10, course.totalDurationDays)
        assertEquals(33.0, course.totalRequiredDose, 0.001)

        // Проверка 2-го дня 1-й фазы (offset = 1 -> день 2 из 4, Фаза 1 из 3)
        val day2Res = MedicationRepository.resolvePhaseForDay(course, 20_001L)
        assertNotNull(day2Res)
        assertEquals(1, day2Res!!.phaseIndexOneBased)
        assertEquals(3, day2Res.totalPhases)
        assertEquals(2, day2Res.dayInPhase)
        assertEquals(4, day2Res.phaseDurationDays)

        // Проверка 1-го дня 2-й фазы (offset = 4 -> день 1 из 3, Фаза 2 из 3)
        val day5Res = MedicationRepository.resolvePhaseForDay(course, 20_004L)
        assertNotNull(day5Res)
        assertEquals(2, day5Res!!.phaseIndexOneBased)
        assertEquals(3, day5Res.totalPhases)
        assertEquals(1, day5Res.dayInPhase)
        assertEquals(3, day5Res.phaseDurationDays)
    }

    @Test
    fun typeConverters_roundTripPreservesPhases() {
        val converters = Converters()
        val original = listOf(
            CoursePhase(1, 4, 3, 2.0, listOf("08:00", "14:00", "20:00")),
            CoursePhase(2, 3, 2, 1.5, listOf("09:00", "21:00"))
        )
        val serialized = converters.fromCoursePhaseList(original)
        val restored = converters.toCoursePhaseList(serialized)
        assertEquals(original, restored)
    }
}
