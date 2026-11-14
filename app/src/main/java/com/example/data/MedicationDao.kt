package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {

    @Query("SELECT * FROM medication_courses ORDER BY status ASC, createdAt DESC")
    fun observeAllCourses(): Flow<List<MedicationCourse>>

    @Query("SELECT * FROM medication_courses ORDER BY createdAt ASC")
    suspend fun getAllCoursesSnapshot(): List<MedicationCourse>

    @Query("SELECT * FROM medication_courses WHERE status = 'ACTIVE' ORDER BY createdAt DESC")
    fun observeActiveCourses(): Flow<List<MedicationCourse>>

    @Query("SELECT * FROM medication_courses WHERE status = 'ACTIVE' ORDER BY createdAt DESC")
    suspend fun getActiveCoursesSnapshot(): List<MedicationCourse>

    @Query("SELECT * FROM medication_courses WHERE id = :courseId LIMIT 1")
    suspend fun getCourseById(courseId: Long): MedicationCourse?

    @Query("SELECT COUNT(*) FROM medication_courses")
    suspend fun getCoursesCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: MedicationCourse): Long

    @Update
    suspend fun updateCourse(course: MedicationCourse)

    @Delete
    suspend fun deleteCourse(course: MedicationCourse)

    @Query("DELETE FROM medication_courses WHERE id = :courseId")
    suspend fun deleteCourseById(courseId: Long)

    @Query("UPDATE medication_courses SET status = :status WHERE id = :courseId")
    suspend fun updateCourseStatus(courseId: Long, status: CourseStatus)

    // --- Записи о фактическом приеме ---

    @Query("SELECT * FROM intake_records WHERE dateEpochDay = :dateEpochDay")
    fun observeIntakeRecordsForDate(dateEpochDay: Long): Flow<List<IntakeRecord>>

    @Query("SELECT * FROM intake_records")
    fun observeAllIntakeRecords(): Flow<List<IntakeRecord>>

    @Query("SELECT COUNT(*) FROM intake_records")
    fun observeIntakeRecordsCount(): Flow<Int>

    @Query("SELECT courseId, COALESCE(SUM(doseAmount), 0.0) AS totalDose FROM intake_records WHERE isTaken = 1 GROUP BY courseId")
    fun observeConsumedDoseByCourse(): Flow<List<CourseConsumedDoseTuple>>

    @Query("SELECT * FROM intake_records ORDER BY dateEpochDay ASC, scheduledTime ASC")
    suspend fun getAllIntakeRecordsSnapshot(): List<IntakeRecord>

    @Query(
        """
        SELECT * FROM intake_records 
        WHERE courseId = :courseId 
          AND dateEpochDay = :dateEpochDay 
          AND scheduledTime = :scheduledTime 
        LIMIT 1
        """
    )
    suspend fun getIntakeRecord(
        courseId: Long,
        dateEpochDay: Long,
        scheduledTime: String
    ): IntakeRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIntakeRecord(record: IntakeRecord): Long

    // --- Динамический справочник форм выпуска и единиц измерения ---

    @Query("SELECT * FROM medication_forms ORDER BY isDefault DESC, id ASC")
    fun observeAllForms(): Flow<List<MedicationFormEntity>>

    @Query("SELECT * FROM medication_forms ORDER BY id ASC")
    suspend fun getAllFormsSnapshot(): List<MedicationFormEntity>

    @Query("SELECT COUNT(*) FROM medication_forms")
    suspend fun getFormsCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertForm(form: MedicationFormEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertForms(forms: List<MedicationFormEntity>)

    @Update
    suspend fun updateForm(form: MedicationFormEntity)

    @Delete
    suspend fun deleteForm(form: MedicationFormEntity)
}
