package com.example.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.AppDatabase
import com.example.data.CoursePhase
import com.example.data.CourseStatus
import com.example.data.DoseFormatter
import com.example.data.IntakeRecord
import com.example.data.MedicationCourse
import com.example.data.MedicationFormEntity
import com.example.data.MedicationRepository
import com.example.data.ScheduledIntakeItem
import com.example.receiver.NotificationHelper
import com.example.update.AppUpdateManager
import com.example.update.UpdateUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

enum class AppScreenTab {
    TODAY,
    ADD_COURSE,
    COURSE_LIST,
    SETTINGS
}

enum class CourseFilter {
    ALL,
    ACTIVE,
    COMPLETED
}

@Immutable
data class DraftPhaseUiState(
    val id: Long,
    val durationDaysInput: String,
    val frequencyPerDay: Int,
    val dosePerIntakeInput: String,
    val intakeTimes: List<String>
) {
    val parsedDurationDays: Int
        get() = durationDaysInput.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0

    val parsedDosePerIntake: Double
        get() = dosePerIntakeInput.trim().replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0

    val phaseSubtotalDose: Double
        get() = parsedDurationDays * frequencyPerDay.coerceAtLeast(0) * parsedDosePerIntake
}

@Immutable
data class CourseBuilderUiState(
    val editingCourseId: Long? = null,
    val editingCourseStatus: CourseStatus = CourseStatus.ACTIVE,
    val editingCourseCreatedAt: Long? = null,
    val name: String = "",
    val notes: String = "",
    val selectedFormTitle: String = "Таблетки",
    val selectedUnit: String = "таб.",
    val startDateEpochDay: Long = LocalDate.now().toEpochDay(),
    val phases: List<DraftPhaseUiState> = listOf(
        DraftPhaseUiState(
            id = 1L,
            durationDaysInput = "5",
            frequencyPerDay = 2,
            dosePerIntakeInput = "1",
            intakeTimes = listOf("08:00", "20:00")
        )
    ),
    val errorMessage: String? = null
) {
    val isEditing: Boolean
        get() = editingCourseId != null

    val totalDurationDays: Int
        get() = phases.sumOf { it.parsedDurationDays }

    val totalRequiredDose: Double
        get() = phases.sumOf { it.phaseSubtotalDose }

    val formattedTotalRequiredDose: String
        get() = DoseFormatter.format(totalRequiredDose)
}

@OptIn(ExperimentalCoroutinesApi::class)
class MedicationViewModel(
    application: Application,
    private val repository: MedicationRepository
) : AndroidViewModel(application) {

    private val _selectedTab = MutableStateFlow(AppScreenTab.TODAY)
    val selectedTab: StateFlow<AppScreenTab> = _selectedTab.asStateFlow()

    private val _selectedDateEpochDay = MutableStateFlow(LocalDate.now().toEpochDay())
    val selectedDateEpochDay: StateFlow<Long> = _selectedDateEpochDay.asStateFlow()

    private val _courseFilter = MutableStateFlow(CourseFilter.ALL)
    val courseFilter: StateFlow<CourseFilter> = _courseFilter.asStateFlow()

    private val _builderState = MutableStateFlow(CourseBuilderUiState())
    val builderState: StateFlow<CourseBuilderUiState> = _builderState.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    private val _updateUiState = MutableStateFlow(UpdateUiState())
    val updateUiState: StateFlow<UpdateUiState> = _updateUiState.asStateFlow()

    private var nextDraftPhaseId = 10L

    val allCourses: StateFlow<List<MedicationCourse>> = repository.allCourses
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val intakeRecordsCount: StateFlow<Int> = repository.intakeRecordsCount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )

    /**
     * Прямая SQL-агрегация суммарно принятых доз по каждому курсу (`GROUP BY courseId`),
     * исключающая чтение всей таблицы `intake_records` в память при рендеринге списка курсов.
     */
    val consumedDoseByCourse: StateFlow<Map<Long, Double>> = repository.consumedDoseTuples
        .map { tuples ->
            if (tuples.isEmpty()) {
                emptyMap()
            } else {
                val map = HashMap<Long, Double>(tuples.size)
                for (tuple in tuples) {
                    map[tuple.courseId] = tuple.totalDose
                }
                map
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap()
        )

    val medicationForms: StateFlow<List<MedicationFormEntity>> = repository.allForms
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private val recordsForSelectedDate: StateFlow<List<IntakeRecord>> = _selectedDateEpochDay
        .flatMapLatest { epochDay ->
            repository.observeIntakeRecordsForDate(epochDay)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val dailySchedule: StateFlow<List<ScheduledIntakeItem>> = combine(
        allCourses,
        recordsForSelectedDate,
        _selectedDateEpochDay
    ) { courses, records, epochDay ->
        MedicationRepository.buildDailySchedule(
            courses = courses,
            recordsForDate = records,
            targetEpochDay = epochDay
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            AppUpdateManager.cleanupOldApks(getApplication())
            repository.ensureSeedData()
            val activeSnapshot = repository.getActiveCoursesSnapshot()
            for (course in activeSnapshot) {
                if (course.name == "Амоксициллин (Схема 3 фазы)" || course.name == "Контрольный курс (3 фазы)") {
                    NotificationHelper.cancelCourseAlarms(getApplication(), course)
                    repository.deleteCourse(course.id)
                }
            }
            val remainingActive = repository.getActiveCoursesSnapshot()
            NotificationHelper.rescheduleAllActiveCourses(getApplication(), remainingActive)

            if (AppUpdateManager.shouldAutoCheck(getApplication())) {
                AppUpdateManager.markAutoChecked(getApplication())
                checkForAppUpdate(manual = false)
            }
        }
    }

    fun selectTab(tab: AppScreenTab) {
        _selectedTab.value = tab
    }

    fun clearBannerMessage() {
        _statusBannerMessage.value = null
    }

    fun selectPreviousDay() {
        _selectedDateEpochDay.update { it - 1L }
    }

    fun selectNextDay() {
        _selectedDateEpochDay.update { it + 1L }
    }

    fun selectToday() {
        _selectedDateEpochDay.value = LocalDate.now().toEpochDay()
    }

    fun setCourseFilter(filter: CourseFilter) {
        _courseFilter.value = filter
    }

    fun toggleIntakeTaken(item: ScheduledIntakeItem) {
        val newTaken = !item.isTaken
        viewModelScope.launch(Dispatchers.IO) {
            repository.setIntakeTakenState(
                courseId = item.courseId,
                dateEpochDay = item.dateEpochDay,
                scheduledTime = item.scheduledTime,
                phaseNumber = item.phaseNumber,
                doseAmount = item.dosePerIntake,
                isTaken = newTaken
            )
            NotificationHelper.dismissIntakeNotificationAndCancelAlarm(
                context = getApplication(),
                courseId = item.courseId,
                dateEpochDay = item.dateEpochDay,
                scheduledTime = item.scheduledTime
            )
            if (!newTaken) {
                repository.getCourseById(item.courseId)?.let { course ->
                    NotificationHelper.scheduleCourseAlarms(getApplication(), course)
                }
            }
        }
    }

    fun toggleIntakeSkipped(item: ScheduledIntakeItem) {
        val newSkipped = !item.isSkipped
        viewModelScope.launch(Dispatchers.IO) {
            repository.setIntakeSkippedState(
                courseId = item.courseId,
                dateEpochDay = item.dateEpochDay,
                scheduledTime = item.scheduledTime,
                phaseNumber = item.phaseNumber,
                doseAmount = item.dosePerIntake,
                isSkipped = newSkipped
            )
            NotificationHelper.dismissIntakeNotificationAndCancelAlarm(
                context = getApplication(),
                courseId = item.courseId,
                dateEpochDay = item.dateEpochDay,
                scheduledTime = item.scheduledTime
            )
            if (!newSkipped) {
                repository.getCourseById(item.courseId)?.let { course ->
                    NotificationHelper.scheduleCourseAlarms(getApplication(), course)
                }
            }
        }
    }

    // --- Автообновление приложения через GitHub Releases (Способ 2) ---

    fun checkForAppUpdate(manual: Boolean = true) {
        if (_updateUiState.value.isChecking || _updateUiState.value.isDownloading) return
        viewModelScope.launch(Dispatchers.IO) {
            _updateUiState.update {
                it.copy(
                    isChecking = true,
                    statusText = if (manual) "Проверка обновлений на GitHub…" else it.statusText
                )
            }
            val result = AppUpdateManager.checkForUpdate(BuildConfig.VERSION_NAME)
            result.fold(
                onSuccess = { releaseInfo ->
                    if (releaseInfo != null) {
                        _updateUiState.update {
                            it.copy(
                                isChecking = false,
                                availableUpdate = releaseInfo,
                                showUpdateDialog = true,
                                statusText = "Доступна новая версия v${releaseInfo.versionName}"
                            )
                        }
                    } else {
                        val msg = "У вас установлена актуальная версия (${BuildConfig.VERSION_NAME})"
                        _updateUiState.update {
                            it.copy(
                                isChecking = false,
                                availableUpdate = null,
                                statusText = msg
                            )
                        }
                        if (manual) {
                            _statusBannerMessage.value = msg
                        }
                    }
                },
                onFailure = { error ->
                    val errText = "Не удалось проверить обновления: ${error.localizedMessage ?: "ошибка сети"}"
                    _updateUiState.update {
                        it.copy(
                            isChecking = false,
                            statusText = errText
                        )
                    }
                    if (manual) {
                        _statusBannerMessage.value = errText
                    }
                }
            )
        }
    }

    fun dismissUpdateDialog() {
        _updateUiState.update { it.copy(showUpdateDialog = false) }
    }

    fun showUpdateModal() {
        if (_updateUiState.value.availableUpdate != null) {
            _updateUiState.update { it.copy(showUpdateDialog = true) }
        }
    }

    fun downloadAndInstallUpdate(context: Context) {
        val updateInfo = _updateUiState.value.availableUpdate ?: return
        if (_updateUiState.value.isDownloading) return

        // Если файл уже скачан в текущей сессии — сразу запускаем установку
        val existingPath = _updateUiState.value.downloadedApkPath
        if (existingPath != null) {
            val existingFile = File(existingPath)
            if (existingFile.exists() && existingFile.length() > 0L) {
                triggerPackageInstall(context, existingFile)
                return
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            _updateUiState.update {
                it.copy(
                    isDownloading = true,
                    downloadProgress = 0f,
                    needsInstallPermission = false,
                    statusText = "Скачивание v${updateInfo.versionName}…"
                )
            }

            val result = AppUpdateManager.downloadUpdateApk(
                context = context.applicationContext,
                info = updateInfo,
                onProgress = { progress ->
                    _updateUiState.update { state ->
                        state.copy(downloadProgress = progress)
                    }
                }
            )

            result.fold(
                onSuccess = { apkFile ->
                    _updateUiState.update {
                        it.copy(
                            isDownloading = false,
                            downloadProgress = 1f,
                            downloadedApkPath = apkFile.absolutePath,
                            statusText = "Файл обновления v${updateInfo.versionName} загружен"
                        )
                    }
                    withContext(Dispatchers.Main) {
                        triggerPackageInstall(context, apkFile)
                    }
                },
                onFailure = { error ->
                    val errText = "Ошибка загрузки APK: ${error.localizedMessage ?: "проверьте соединение"}"
                    _updateUiState.update {
                        it.copy(
                            isDownloading = false,
                            statusText = errText
                        )
                    }
                    _statusBannerMessage.value = errText
                }
            )
        }
    }

    fun triggerPackageInstall(context: Context, apkFile: File) {
        if (!AppUpdateManager.canRequestPackageInstalls(context)) {
            _updateUiState.update {
                it.copy(
                    needsInstallPermission = true,
                    showUpdateDialog = true,
                    statusText = "Разрешите установку обновлений из этого источника в настройках Android"
                )
            }
            AppUpdateManager.openInstallUnknownAppsSettings(context)
            return
        }

        _updateUiState.update { it.copy(needsInstallPermission = false) }
        val launched = AppUpdateManager.launchApkInstaller(context, apkFile)
        if (!launched) {
            _statusBannerMessage.value = "Не удалось открыть системный установщик пакетов"
        }
    }

    fun triggerDelayedTestNotification(context: Context) {
        val sampleItem = dailySchedule.value.firstOrNull { !it.isTaken && !it.isSkipped }
            ?: dailySchedule.value.firstOrNull()
        viewModelScope.launch(Dispatchers.IO) {
            NotificationHelper.scheduleDelayedTestAlarm(
                context = context,
                delaySeconds = 10,
                courseId = sampleItem?.courseId ?: 999_001L,
                courseName = sampleItem?.courseName ?: "Проверка фонового приема",
                unitName = sampleItem?.unitName ?: "таб.",
                doseAmount = sampleItem?.dosePerIntake ?: 1.0,
                scheduledTimeOverride = sampleItem?.scheduledTime,
                dateEpochDayOverride = sampleItem?.dateEpochDay
            )
            _statusBannerMessage.value = "Будильник сработает через 10 сек. Сверните приложение для проверки!"
        }
    }

    fun rescheduleAllAlarms(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val active = repository.getActiveCoursesSnapshot()
            NotificationHelper.rescheduleAllActiveCourses(context, active)
            _statusBannerMessage.value = "Будильники перепланированы для активных курсов (${active.size})"
        }
    }

    fun exportBackupToJson(onJsonReady: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    repository.exportBackupJson()
                }
                onJsonReady(json)
                _statusBannerMessage.value = "Резервная копия данных сформирована"
            } catch (e: Exception) {
                _statusBannerMessage.value = "Ошибка при экспорте данных: ${e.localizedMessage}"
            }
        }
    }

    fun importBackupFromJson(context: Context, jsonContent: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val count = repository.importBackupJson(jsonContent)
                val active = repository.getActiveCoursesSnapshot()
                NotificationHelper.rescheduleAllActiveCourses(context, active)
                _statusBannerMessage.value = "Данные успешно импортированы (курсов: $count)"
            } catch (e: Exception) {
                _statusBannerMessage.value = "Ошибка импорта JSON: проверьте формат файла"
            }
        }
    }

    // --- Управление конструктором, редактированием и дублированием курса ---

    private fun buildDraftPhasesFromCourse(course: MedicationCourse): List<DraftPhaseUiState> {
        return if (course.phases.isNotEmpty()) {
            course.phases.sortedBy { it.phaseNumber }.map { phase ->
                DraftPhaseUiState(
                    id = nextDraftPhaseId++,
                    durationDaysInput = phase.durationDays.toString(),
                    frequencyPerDay = phase.frequencyPerDay,
                    dosePerIntakeInput = DoseFormatter.format(phase.dosePerIntake),
                    intakeTimes = CoursePhase.normalizeSlots(phase.intakeTimes, phase.frequencyPerDay)
                )
            }
        } else {
            listOf(
                DraftPhaseUiState(
                    id = nextDraftPhaseId++,
                    durationDaysInput = "5",
                    frequencyPerDay = 2,
                    dosePerIntakeInput = "1",
                    intakeTimes = listOf("08:00", "20:00")
                )
            )
        }
    }

    fun startEditingCourse(course: MedicationCourse) {
        val draftPhases = buildDraftPhasesFromCourse(course)

        _builderState.value = CourseBuilderUiState(
            editingCourseId = course.id,
            editingCourseStatus = course.status,
            editingCourseCreatedAt = course.createdAt,
            name = course.name,
            notes = course.notes,
            selectedFormTitle = course.formName,
            selectedUnit = course.unitName,
            startDateEpochDay = course.startDateEpochDay,
            phases = draftPhases,
            errorMessage = null
        )
        _selectedTab.value = AppScreenTab.ADD_COURSE
    }

    fun duplicateCourse(course: MedicationCourse) {
        val draftPhases = buildDraftPhasesFromCourse(course)
        val todayEpoch = LocalDate.now().toEpochDay()

        _builderState.value = CourseBuilderUiState(
            editingCourseId = null,
            editingCourseStatus = CourseStatus.ACTIVE,
            editingCourseCreatedAt = null,
            name = course.name,
            notes = course.notes,
            selectedFormTitle = course.formName,
            selectedUnit = course.unitName,
            startDateEpochDay = todayEpoch,
            phases = draftPhases,
            errorMessage = null
        )
        _selectedTab.value = AppScreenTab.ADD_COURSE
        _statusBannerMessage.value = "Курс «${course.name}» скопирован — проверьте дату начала и сохраните"
    }

    fun cancelEditingCourse() {
        _builderState.value = CourseBuilderUiState(
            startDateEpochDay = LocalDate.now().toEpochDay()
        )
    }

    fun updateDraftName(name: String) {
        _builderState.update { it.copy(name = name, errorMessage = null) }
    }

    fun updateDraftNotes(notes: String) {
        _builderState.update { it.copy(notes = notes) }
    }

    fun selectDraftForm(formTitle: String, unit: String) {
        _builderState.update {
            it.copy(
                selectedFormTitle = formTitle,
                selectedUnit = unit,
                errorMessage = null
            )
        }
    }

    fun updateDraftStartDate(epochDay: Long) {
        _builderState.update { it.copy(startDateEpochDay = epochDay) }
    }

    fun addDraftPhase() {
        val newId = nextDraftPhaseId++
        _builderState.update { state ->
            val updated = state.phases + DraftPhaseUiState(
                id = newId,
                durationDaysInput = "3",
                frequencyPerDay = 2,
                dosePerIntakeInput = "1",
                intakeTimes = CoursePhase.defaultSlotsForFrequency(2)
            )
            state.copy(phases = updated, errorMessage = null)
        }
    }

    fun removeDraftPhase(phaseId: Long) {
        _builderState.update { state ->
            if (state.phases.size <= 1) return@update state
            state.copy(
                phases = state.phases.filterNot { it.id == phaseId },
                errorMessage = null
            )
        }
    }

    fun updatePhaseDuration(phaseId: Long, durationInput: String) {
        val filtered = durationInput.filter { it.isDigit() }.take(3)
        _builderState.update { state ->
            state.copy(
                phases = state.phases.map { phase ->
                    if (phase.id == phaseId) phase.copy(durationDaysInput = filtered) else phase
                },
                errorMessage = null
            )
        }
    }

    fun updatePhaseFrequency(phaseId: Long, newFrequency: Int) {
        val safeFreq = newFrequency.coerceIn(1, 12)
        _builderState.update { state ->
            state.copy(
                phases = state.phases.map { phase ->
                    if (phase.id == phaseId) {
                        phase.copy(
                            frequencyPerDay = safeFreq,
                            intakeTimes = CoursePhase.defaultSlotsForFrequency(safeFreq)
                        )
                    } else {
                        phase
                    }
                },
                errorMessage = null
            )
        }
    }

    fun updatePhaseDose(phaseId: Long, doseInput: String) {
        val filtered = doseInput.filter { it.isDigit() || it == '.' || it == ',' }.take(6)
        _builderState.update { state ->
            state.copy(
                phases = state.phases.map { phase ->
                    if (phase.id == phaseId) phase.copy(dosePerIntakeInput = filtered) else phase
                },
                errorMessage = null
            )
        }
    }

    fun updatePhaseTimeSlot(phaseId: Long, slotIndex: Int, newTime: String) {
        _builderState.update { state ->
            state.copy(
                phases = state.phases.map { phase ->
                    if (phase.id == phaseId) {
                        val mutableTimes = phase.intakeTimes.toMutableList()
                        if (slotIndex in mutableTimes.indices) {
                            mutableTimes[slotIndex] = newTime
                        }
                        phase.copy(intakeTimes = mutableTimes)
                    } else {
                        phase
                    }
                }
            )
        }
    }

    fun saveDraftCourseAndStartReminders(context: Context, onSuccess: () -> Unit) {
        val state = _builderState.value
        val trimmedName = state.name.trim()
        if (trimmedName.isEmpty()) {
            _builderState.update {
                it.copy(errorMessage = "Введите название препарата или процедуры")
            }
            return
        }

        val validPhases = state.phases.mapIndexed { index, draft ->
            val duration = draft.parsedDurationDays
            val dose = draft.parsedDosePerIntake
            if (duration <= 0 || dose <= 0.0 || draft.frequencyPerDay <= 0) {
                _builderState.update {
                    it.copy(errorMessage = "Проверьте корректность длительности и дозировки в фазе ${index + 1}")
                }
                return
            }
            CoursePhase(
                phaseNumber = index + 1,
                durationDays = duration,
                frequencyPerDay = draft.frequencyPerDay,
                dosePerIntake = dose,
                intakeTimes = CoursePhase.normalizeSlots(draft.intakeTimes, draft.frequencyPerDay)
            )
        }

        viewModelScope.launch {
            val editingId = state.editingCourseId
            if (editingId != null) {
                withContext(Dispatchers.IO) {
                    val previousCourse = repository.getCourseById(editingId)
                    if (previousCourse != null) {
                        NotificationHelper.cancelCourseAlarms(context, previousCourse)
                    }

                    val updatedCourse = MedicationCourse(
                        id = editingId,
                        name = trimmedName,
                        formName = state.selectedFormTitle,
                        unitName = state.selectedUnit,
                        startDateEpochDay = state.startDateEpochDay,
                        status = state.editingCourseStatus,
                        phases = validPhases,
                        notes = state.notes.trim(),
                        createdAt = state.editingCourseCreatedAt ?: System.currentTimeMillis()
                    )
                    repository.updateExistingCourse(updatedCourse)
                    NotificationHelper.scheduleCourseAlarms(
                        context = context,
                        course = updatedCourse,
                        allowCurrentMinuteImmediate = true
                    )
                }

                _builderState.value = CourseBuilderUiState(
                    startDateEpochDay = LocalDate.now().toEpochDay()
                )
                _statusBannerMessage.value = "Изменения курса «$trimmedName» сохранены"
                _selectedTab.value = AppScreenTab.TODAY
                onSuccess()
            } else {
                withContext(Dispatchers.IO) {
                    val newCourse = MedicationCourse(
                        name = trimmedName,
                        formName = state.selectedFormTitle,
                        unitName = state.selectedUnit,
                        startDateEpochDay = state.startDateEpochDay,
                        status = CourseStatus.ACTIVE,
                        phases = validPhases,
                        notes = state.notes.trim()
                    )
                    val savedId = repository.saveCourse(newCourse)
                    val persistedCourse = newCourse.copy(id = savedId)
                    NotificationHelper.scheduleCourseAlarms(
                        context = context,
                        course = persistedCourse,
                        allowCurrentMinuteImmediate = true
                    )
                }

                _selectedDateEpochDay.value = state.startDateEpochDay
                _builderState.value = CourseBuilderUiState(
                    startDateEpochDay = LocalDate.now().toEpochDay()
                )
                _statusBannerMessage.value = "Курс «$trimmedName» сохранен, будильники запланированы"
                _selectedTab.value = AppScreenTab.TODAY
                onSuccess()
            }
        }
    }

    // --- Управление списком курсов ---

    fun completeCourseEarly(context: Context, course: MedicationCourse) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateCourseStatus(course.id, CourseStatus.COMPLETED)
            NotificationHelper.cancelCourseAlarms(context, course)
            _statusBannerMessage.value = "Курс «${course.name}» завершен"
        }
    }

    fun reactivateCourse(context: Context, course: MedicationCourse) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateCourseStatus(course.id, CourseStatus.ACTIVE)
            val updated = course.copy(status = CourseStatus.ACTIVE)
            NotificationHelper.scheduleCourseAlarms(context, updated)
            _statusBannerMessage.value = "Курс «${course.name}» вновь активен"
        }
    }

    fun deleteCourse(context: Context, course: MedicationCourse) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                NotificationHelper.cancelCourseAlarms(context, course)
                repository.deleteCourse(course.id)
            }
            if (_builderState.value.editingCourseId == course.id) {
                cancelEditingCourse()
            }
            _statusBannerMessage.value = "Курс «${course.name}» удален"
        }
    }

    // --- Динамический справочник форм выпуска ---

    fun addCustomForm(title: String, unit: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addForm(title, unit)
            selectDraftForm(title.trim(), unit.trim())
        }
    }

    fun updateCustomForm(form: MedicationFormEntity, newTitle: String, newUnit: String) {
        val cleanTitle = newTitle.trim()
        val cleanUnit = newUnit.trim()
        if (cleanTitle.isEmpty() || cleanUnit.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateForm(form.copy(title = cleanTitle, unit = cleanUnit))
            if (_builderState.value.selectedFormTitle == form.title) {
                selectDraftForm(cleanTitle, cleanUnit)
            }
        }
    }

    fun deleteCustomForm(form: MedicationFormEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteForm(form)
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val db = AppDatabase.getInstance(application)
            val repository = MedicationRepository(db.medicationDao())
            return MedicationViewModel(application, repository) as T
        }
    }
}
