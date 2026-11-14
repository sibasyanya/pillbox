package com.example.data

import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

class MedicationRepository(private val dao: MedicationDao) {

    val allCourses: Flow<List<MedicationCourse>> = dao.observeAllCourses()
    val activeCourses: Flow<List<MedicationCourse>> = dao.observeActiveCourses()
    val allIntakeRecords: Flow<List<IntakeRecord>> = dao.observeAllIntakeRecords()
    val intakeRecordsCount: Flow<Int> = dao.observeIntakeRecordsCount()
    val consumedDoseTuples: Flow<List<CourseConsumedDoseTuple>> = dao.observeConsumedDoseByCourse()
    val allForms: Flow<List<MedicationFormEntity>> = dao.observeAllForms()

    fun observeIntakeRecordsForDate(dateEpochDay: Long): Flow<List<IntakeRecord>> {
        return dao.observeIntakeRecordsForDate(dateEpochDay)
    }

    /**
     * Инициализирует только базовый справочник форм выпуска, если он пуст.
     * Демо-курсы НЕ создаются.
     */
    suspend fun ensureSeedData() {
        if (dao.getFormsCount() == 0) {
            val defaultForms = listOf(
                MedicationFormEntity(title = "Таблетки", unit = "таб.", isDefault = true),
                MedicationFormEntity(title = "Капли", unit = "кап.", isDefault = true),
                MedicationFormEntity(title = "Сироп", unit = "мл", isDefault = true),
                MedicationFormEntity(title = "Инъекции", unit = "ампул", isDefault = true),
                MedicationFormEntity(title = "Процедура", unit = "процед.", isDefault = true),
                MedicationFormEntity(title = "Ингаляции", unit = "процед.", isDefault = true),
                MedicationFormEntity(title = "Компрессы", unit = "процед.", isDefault = true),
                MedicationFormEntity(title = "Капсулы", unit = "капс.", isDefault = true)
            )
            dao.insertForms(defaultForms)
        }
    }

    suspend fun saveCourse(course: MedicationCourse): Long {
        return dao.insertCourse(course)
    }

    suspend fun updateExistingCourse(course: MedicationCourse) {
        dao.updateCourse(course)
    }

    suspend fun updateCourseStatus(courseId: Long, status: CourseStatus) {
        dao.updateCourseStatus(courseId, status)
    }

    suspend fun deleteCourse(courseId: Long) {
        dao.deleteCourseById(courseId)
    }

    suspend fun getActiveCoursesSnapshot(): List<MedicationCourse> {
        return dao.getActiveCoursesSnapshot()
    }

    suspend fun getCourseById(courseId: Long): MedicationCourse? {
        return dao.getCourseById(courseId)
    }

    suspend fun setIntakeTakenState(
        courseId: Long,
        dateEpochDay: Long,
        scheduledTime: String,
        phaseNumber: Int,
        doseAmount: Double,
        isTaken: Boolean
    ) {
        val existing = dao.getIntakeRecord(courseId, dateEpochDay, scheduledTime)
        val record = if (existing != null) {
            existing.copy(
                phaseNumber = phaseNumber,
                doseAmount = doseAmount,
                isTaken = isTaken,
                isSkipped = false,
                takenAtTimestamp = if (isTaken) System.currentTimeMillis() else null
            )
        } else {
            IntakeRecord(
                courseId = courseId,
                dateEpochDay = dateEpochDay,
                scheduledTime = scheduledTime,
                phaseNumber = phaseNumber,
                doseAmount = doseAmount,
                isTaken = isTaken,
                isSkipped = false,
                takenAtTimestamp = if (isTaken) System.currentTimeMillis() else null
            )
        }
        dao.upsertIntakeRecord(record)
    }

    suspend fun setIntakeSkippedState(
        courseId: Long,
        dateEpochDay: Long,
        scheduledTime: String,
        phaseNumber: Int,
        doseAmount: Double,
        isSkipped: Boolean
    ) {
        val existing = dao.getIntakeRecord(courseId, dateEpochDay, scheduledTime)
        val record = if (existing != null) {
            existing.copy(
                phaseNumber = phaseNumber,
                doseAmount = doseAmount,
                isTaken = false,
                isSkipped = isSkipped,
                takenAtTimestamp = if (isSkipped) System.currentTimeMillis() else null
            )
        } else {
            IntakeRecord(
                courseId = courseId,
                dateEpochDay = dateEpochDay,
                scheduledTime = scheduledTime,
                phaseNumber = phaseNumber,
                doseAmount = doseAmount,
                isTaken = false,
                isSkipped = isSkipped,
                takenAtTimestamp = if (isSkipped) System.currentTimeMillis() else null
            )
        }
        dao.upsertIntakeRecord(record)
    }

    suspend fun addForm(title: String, unit: String) {
        val cleanTitle = title.trim()
        val cleanUnit = unit.trim()
        if (cleanTitle.isNotEmpty() && cleanUnit.isNotEmpty()) {
            dao.insertForm(MedicationFormEntity(title = cleanTitle, unit = cleanUnit, isDefault = false))
        }
    }

    suspend fun updateForm(form: MedicationFormEntity) {
        dao.updateForm(form)
    }

    suspend fun deleteForm(form: MedicationFormEntity) {
        dao.deleteForm(form)
    }

    suspend fun exportBackupJson(): String {
        val courses = dao.getAllCoursesSnapshot()
        val records = dao.getAllIntakeRecordsSnapshot()
        val forms = dao.getAllFormsSnapshot()
        val converters = Converters()

        val root = JSONObject()
        root.put("schemaVersion", 2)
        root.put("exportedAt", System.currentTimeMillis())

        val formsArr = JSONArray()
        for (form in forms) {
            formsArr.put(
                JSONObject().apply {
                    put("id", form.id)
                    put("title", form.title)
                    put("unit", form.unit)
                    put("isDefault", form.isDefault)
                }
            )
        }
        root.put("forms", formsArr)

        val coursesArr = JSONArray()
        for (course in courses) {
            coursesArr.put(
                JSONObject().apply {
                    put("id", course.id)
                    put("name", course.name)
                    put("formName", course.formName)
                    put("unitName", course.unitName)
                    put("startDateEpochDay", course.startDateEpochDay)
                    put("status", course.status.name)
                    put("phasesSerialized", converters.fromCoursePhaseList(course.phases))
                    put("notes", course.notes)
                    put("createdAt", course.createdAt)
                }
            )
        }
        root.put("courses", coursesArr)

        val recordsArr = JSONArray()
        for (rec in records) {
            recordsArr.put(
                JSONObject().apply {
                    put("id", rec.id)
                    put("courseId", rec.courseId)
                    put("dateEpochDay", rec.dateEpochDay)
                    put("scheduledTime", rec.scheduledTime)
                    put("phaseNumber", rec.phaseNumber)
                    put("doseAmount", rec.doseAmount)
                    put("isTaken", rec.isTaken)
                    put("isSkipped", rec.isSkipped)
                    if (rec.takenAtTimestamp != null) {
                        put("takenAtTimestamp", rec.takenAtTimestamp)
                    }
                }
            )
        }
        root.put("intakeRecords", recordsArr)

        return root.toString(2)
    }

    suspend fun importBackupJson(jsonContent: String): Int {
        val root = JSONObject(jsonContent)
        val converters = Converters()

        val formsArr = root.optJSONArray("forms")
        if (formsArr != null) {
            val existingTitles = dao.getAllFormsSnapshot().map { it.title }.toSet()
            for (i in 0 until formsArr.length()) {
                val obj = formsArr.getJSONObject(i)
                val title = obj.optString("title").trim()
                val unit = obj.optString("unit").trim()
                val isDef = obj.optBoolean("isDefault", false)
                if (title.isNotEmpty() && unit.isNotEmpty() && title !in existingTitles) {
                    dao.insertForm(MedicationFormEntity(title = title, unit = unit, isDefault = isDef))
                }
            }
        }

        var importedCoursesCount = 0
        val coursesArr = root.optJSONArray("courses")
        if (coursesArr != null) {
            for (i in 0 until coursesArr.length()) {
                val obj = coursesArr.getJSONObject(i)
                val phases = converters.toCoursePhaseList(obj.optString("phasesSerialized", ""))
                if (phases.isEmpty()) continue
                val course = MedicationCourse(
                    id = obj.optLong("id", 0L),
                    name = obj.optString("name", "Курс"),
                    formName = obj.optString("formName", "Таблетки"),
                    unitName = obj.optString("unitName", "таб."),
                    startDateEpochDay = obj.optLong("startDateEpochDay"),
                    status = converters.toCourseStatus(obj.optString("status", "ACTIVE")),
                    phases = phases,
                    notes = obj.optString("notes", ""),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                )
                dao.insertCourse(course)
                importedCoursesCount++
            }
        }

        val recordsArr = root.optJSONArray("intakeRecords")
        if (recordsArr != null) {
            for (i in 0 until recordsArr.length()) {
                val obj = recordsArr.getJSONObject(i)
                val courseId = obj.optLong("courseId", 0L)
                if (courseId <= 0L) continue
                val isTaken = obj.optBoolean("isTaken", false)
                val isSkipped = if (isTaken) false else obj.optBoolean("isSkipped", false)
                val record = IntakeRecord(
                    id = obj.optLong("id", 0L),
                    courseId = courseId,
                    dateEpochDay = obj.optLong("dateEpochDay"),
                    scheduledTime = obj.optString("scheduledTime", "08:00"),
                    phaseNumber = obj.optInt("phaseNumber", 1),
                    doseAmount = obj.optDouble("doseAmount", 1.0),
                    isTaken = isTaken,
                    isSkipped = isSkipped,
                    takenAtTimestamp = if (obj.has("takenAtTimestamp")) obj.optLong("takenAtTimestamp") else null
                )
                dao.upsertIntakeRecord(record)
            }
        }

        return importedCoursesCount
    }

    companion object {
        /**
         * Вычисляет активную фазу и день внутри фазы для заданного курса и даты.
         */
        fun resolvePhaseForDay(
            course: MedicationCourse,
            targetEpochDay: Long
        ): PhaseResolution? {
            val phases = course.phases
            if (phases.isEmpty()) return null
            val dayOffset = (targetEpochDay - course.startDateEpochDay).toInt()
            if (dayOffset < 0) return null
            val totalDays = course.totalDurationDays
            if (dayOffset >= totalDays) return null

            var accumulatedDays = 0
            val size = phases.size
            for (idx in 0 until size) {
                val phase = phases[idx]
                val duration = phase.durationDays.coerceAtLeast(1)
                if (dayOffset < accumulatedDays + duration) {
                    val dayInPhase = (dayOffset - accumulatedDays) + 1
                    return PhaseResolution(
                        phase = phase,
                        phaseIndexOneBased = idx + 1,
                        totalPhases = size,
                        dayInPhase = dayInPhase,
                        phaseDurationDays = duration,
                        dayInCourse = dayOffset + 1,
                        totalCourseDays = totalDays
                    )
                }
                accumulatedDays += duration
            }
            return null
        }

        /**
         * Строит отсортированный по времени список приемов на выбранную дату.
         */
        fun buildDailySchedule(
            courses: List<MedicationCourse>,
            recordsForDate: List<IntakeRecord>,
            targetEpochDay: Long
        ): List<ScheduledIntakeItem> {
            val recordMap = if (recordsForDate.isEmpty()) {
                emptyMap()
            } else {
                recordsForDate.associateBy { "${it.courseId}_${it.scheduledTime}" }
            }
            val result = ArrayList<ScheduledIntakeItem>(courses.size * 2)

            for (course in courses) {
                if (course.status != CourseStatus.ACTIVE) continue
                val resolution = resolvePhaseForDay(course, targetEpochDay) ?: continue
                val phase = resolution.phase
                for (timeSlot in phase.intakeTimes) {
                    val key = "${course.id}_$timeSlot"
                    val record = recordMap[key]
                    result.add(
                        ScheduledIntakeItem(
                            uniqueKey = "${course.id}_${targetEpochDay}_$timeSlot",
                            courseId = course.id,
                            courseName = course.name,
                            formName = course.formName,
                            unitName = course.unitName,
                            notes = course.notes,
                            dateEpochDay = targetEpochDay,
                            scheduledTime = timeSlot,
                            dosePerIntake = phase.dosePerIntake,
                            phaseNumber = resolution.phaseIndexOneBased,
                            totalPhases = resolution.totalPhases,
                            dayInPhase = resolution.dayInPhase,
                            phaseDurationDays = resolution.phaseDurationDays,
                            dayInCourse = resolution.dayInCourse,
                            totalCourseDays = resolution.totalCourseDays,
                            isTaken = record?.isTaken == true,
                            isSkipped = record?.isSkipped == true,
                            takenAtTimestamp = record?.takenAtTimestamp
                        )
                    )
                }
            }

            result.sortWith(
                compareBy<ScheduledIntakeItem> { it.scheduledTime }
                    .thenBy { it.courseName }
            )
            return result
        }
    }
}

data class PhaseResolution(
    val phase: CoursePhase,
    val phaseIndexOneBased: Int,
    val totalPhases: Int,
    val dayInPhase: Int,
    val phaseDurationDays: Int,
    val dayInCourse: Int,
    val totalCourseDays: Int
)
