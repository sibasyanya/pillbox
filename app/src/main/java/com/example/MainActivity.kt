package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.receiver.NotificationHelper
import com.example.ui.AddCourseScreen
import com.example.ui.AppScreenTab
import com.example.ui.CourseListScreen
import com.example.ui.FastProgressBar
import com.example.ui.MedicationViewModel
import com.example.ui.SettingsScreen
import com.example.ui.TodayScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.update.UpdateUiState

class MainActivity : ComponentActivity() {

    private val viewModel: MedicationViewModel by viewModels {
        MedicationViewModel.Factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.ensureNotificationChannel(this)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MedicationTrackerApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MedicationTrackerApp(
    viewModel: MedicationViewModel
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isExpandedScreen = configuration.screenWidthDp >= 600

    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val statusBannerMessage by viewModel.statusBannerMessage.collectAsStateWithLifecycle()
    val updateUiState by viewModel.updateUiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { /* Состояние обрабатывается автоматически */ }
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPerm) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(statusBannerMessage) {
        val message = statusBannerMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearBannerMessage()
        }
    }

    if (selectedTab != AppScreenTab.TODAY) {
        BackHandler {
            viewModel.selectTab(AppScreenTab.TODAY)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (!isExpandedScreen) {
                NavigationBar(modifier = Modifier.testTag("bottom_navigation_bar")) {
                    NavigationBarItem(
                        selected = selectedTab == AppScreenTab.TODAY,
                        onClick = { viewModel.selectTab(AppScreenTab.TODAY) },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == AppScreenTab.TODAY) {
                                    Icons.Filled.CalendarToday
                                } else {
                                    Icons.Outlined.CalendarToday
                                },
                                contentDescription = stringResource(R.string.nav_today)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_today)) },
                        modifier = Modifier.testTag("nav_tab_today")
                    )

                    NavigationBarItem(
                        selected = selectedTab == AppScreenTab.ADD_COURSE,
                        onClick = { viewModel.selectTab(AppScreenTab.ADD_COURSE) },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == AppScreenTab.ADD_COURSE) {
                                    Icons.Filled.AddCircle
                                } else {
                                    Icons.Outlined.AddCircleOutline
                                },
                                contentDescription = stringResource(R.string.nav_builder)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_builder)) },
                        modifier = Modifier.testTag("nav_tab_builder")
                    )

                    NavigationBarItem(
                        selected = selectedTab == AppScreenTab.COURSE_LIST,
                        onClick = { viewModel.selectTab(AppScreenTab.COURSE_LIST) },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == AppScreenTab.COURSE_LIST) {
                                    Icons.AutoMirrored.Filled.ViewList
                                } else {
                                    Icons.AutoMirrored.Outlined.ViewList
                                },
                                contentDescription = stringResource(R.string.nav_courses)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_courses)) },
                        modifier = Modifier.testTag("nav_tab_courses")
                    )

                    NavigationBarItem(
                        selected = selectedTab == AppScreenTab.SETTINGS,
                        onClick = { viewModel.selectTab(AppScreenTab.SETTINGS) },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == AppScreenTab.SETTINGS) {
                                    Icons.Filled.Settings
                                } else {
                                    Icons.Outlined.Settings
                                },
                                contentDescription = stringResource(R.string.nav_settings)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_settings)) },
                        modifier = Modifier.testTag("nav_tab_settings")
                    )
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isExpandedScreen) {
                NavigationRail(modifier = Modifier.testTag("side_navigation_rail")) {
                    NavigationRailItem(
                        selected = selectedTab == AppScreenTab.TODAY,
                        onClick = { viewModel.selectTab(AppScreenTab.TODAY) },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == AppScreenTab.TODAY) {
                                    Icons.Filled.CalendarToday
                                } else {
                                    Icons.Outlined.CalendarToday
                                },
                                contentDescription = stringResource(R.string.nav_today)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_today)) },
                        modifier = Modifier.testTag("nav_rail_today")
                    )

                    NavigationRailItem(
                        selected = selectedTab == AppScreenTab.ADD_COURSE,
                        onClick = { viewModel.selectTab(AppScreenTab.ADD_COURSE) },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == AppScreenTab.ADD_COURSE) {
                                    Icons.Filled.AddCircle
                                } else {
                                    Icons.Outlined.AddCircleOutline
                                },
                                contentDescription = stringResource(R.string.nav_builder)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_builder)) },
                        modifier = Modifier.testTag("nav_rail_builder")
                    )

                    NavigationRailItem(
                        selected = selectedTab == AppScreenTab.COURSE_LIST,
                        onClick = { viewModel.selectTab(AppScreenTab.COURSE_LIST) },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == AppScreenTab.COURSE_LIST) {
                                    Icons.AutoMirrored.Filled.ViewList
                                } else {
                                    Icons.AutoMirrored.Outlined.ViewList
                                },
                                contentDescription = stringResource(R.string.nav_courses)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_courses)) },
                        modifier = Modifier.testTag("nav_rail_courses")
                    )

                    NavigationRailItem(
                        selected = selectedTab == AppScreenTab.SETTINGS,
                        onClick = { viewModel.selectTab(AppScreenTab.SETTINGS) },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == AppScreenTab.SETTINGS) {
                                    Icons.Filled.Settings
                                } else {
                                    Icons.Outlined.Settings
                                },
                                contentDescription = stringResource(R.string.nav_settings)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_settings)) },
                        modifier = Modifier.testTag("nav_rail_settings")
                    )
                }
            }

            when (selectedTab) {
                AppScreenTab.TODAY -> {
                    TodayTabContent(
                        viewModel = viewModel,
                        modifier = Modifier.weight(1f)
                    )
                }

                AppScreenTab.ADD_COURSE -> {
                    AddCourseTabContent(
                        viewModel = viewModel,
                        modifier = Modifier.weight(1f)
                    )
                }

                AppScreenTab.COURSE_LIST -> {
                    CourseListTabContent(
                        viewModel = viewModel,
                        modifier = Modifier.weight(1f)
                    )
                }

                AppScreenTab.SETTINGS -> {
                    SettingsTabContent(
                        viewModel = viewModel,
                        updateUiState = updateUiState,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    if (updateUiState.showUpdateDialog && updateUiState.availableUpdate != null) {
        UpdateAvailableDialog(
            updateUiState = updateUiState,
            onConfirmDownloadAndInstall = {
                viewModel.downloadAndInstallUpdate(context)
            },
            onDismiss = {
                viewModel.dismissUpdateDialog()
            }
        )
    }
}

@Composable
private fun TodayTabContent(
    viewModel: MedicationViewModel,
    modifier: Modifier = Modifier
) {
    val selectedDateEpochDay by viewModel.selectedDateEpochDay.collectAsStateWithLifecycle()
    val dailySchedule by viewModel.dailySchedule.collectAsStateWithLifecycle()

    TodayScreen(
        selectedDateEpochDay = selectedDateEpochDay,
        scheduleItems = dailySchedule,
        onPreviousDay = viewModel::selectPreviousDay,
        onNextDay = viewModel::selectNextDay,
        onSelectToday = viewModel::selectToday,
        onToggleIntakeTaken = viewModel::toggleIntakeTaken,
        onToggleIntakeSkipped = viewModel::toggleIntakeSkipped,
        onNavigateToAddCourse = { viewModel.selectTab(AppScreenTab.ADD_COURSE) },
        modifier = modifier
    )
}

@Composable
private fun AddCourseTabContent(
    viewModel: MedicationViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val builderState by viewModel.builderState.collectAsStateWithLifecycle()
    val medicationForms by viewModel.medicationForms.collectAsStateWithLifecycle()

    AddCourseScreen(
        builderState = builderState,
        medicationForms = medicationForms,
        onNameChange = viewModel::updateDraftName,
        onNotesChange = viewModel::updateDraftNotes,
        onSelectForm = viewModel::selectDraftForm,
        onStartDateChange = viewModel::updateDraftStartDate,
        onAddPhase = viewModel::addDraftPhase,
        onRemovePhase = viewModel::removeDraftPhase,
        onPhaseDurationChange = viewModel::updatePhaseDuration,
        onPhaseFrequencyChange = viewModel::updatePhaseFrequency,
        onPhaseDoseChange = viewModel::updatePhaseDose,
        onPhaseTimeSlotChange = viewModel::updatePhaseTimeSlot,
        onCancelEditing = viewModel::cancelEditingCourse,
        onSaveAndStartReminders = {
            viewModel.saveDraftCourseAndStartReminders(context) {}
        },
        onAddCustomForm = viewModel::addCustomForm,
        onUpdateCustomForm = viewModel::updateCustomForm,
        onDeleteCustomForm = viewModel::deleteCustomForm,
        modifier = modifier
    )
}

@Composable
private fun CourseListTabContent(
    viewModel: MedicationViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allCourses by viewModel.allCourses.collectAsStateWithLifecycle()
    val consumedDoseByCourse by viewModel.consumedDoseByCourse.collectAsStateWithLifecycle()
    val courseFilter by viewModel.courseFilter.collectAsStateWithLifecycle()

    CourseListScreen(
        courses = allCourses,
        consumedDoseByCourse = consumedDoseByCourse,
        selectedFilter = courseFilter,
        onFilterChange = viewModel::setCourseFilter,
        onEditCourse = viewModel::startEditingCourse,
        onDuplicateCourse = viewModel::duplicateCourse,
        onCompleteCourseEarly = { viewModel.completeCourseEarly(context, it) },
        onReactivateCourse = { viewModel.reactivateCourse(context, it) },
        onDeleteCourse = { viewModel.deleteCourse(context, it) },
        onNavigateToAddCourse = { viewModel.selectTab(AppScreenTab.ADD_COURSE) },
        modifier = modifier
    )
}

@Composable
private fun SettingsTabContent(
    viewModel: MedicationViewModel,
    updateUiState: UpdateUiState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allCourses by viewModel.allCourses.collectAsStateWithLifecycle()
    val intakeRecordsCount by viewModel.intakeRecordsCount.collectAsStateWithLifecycle()
    val medicationForms by viewModel.medicationForms.collectAsStateWithLifecycle()

    SettingsScreen(
        coursesCount = allCourses.size,
        intakeRecordsCount = intakeRecordsCount,
        medicationForms = medicationForms,
        updateUiState = updateUiState,
        onCheckForUpdate = { viewModel.checkForAppUpdate(manual = true) },
        onDownloadAndInstallUpdate = { viewModel.downloadAndInstallUpdate(context) },
        onTriggerDelayedTestNotification = {
            viewModel.triggerDelayedTestNotification(context)
        },
        onRescheduleAlarms = { viewModel.rescheduleAllAlarms(context) },
        onExportJsonBackup = viewModel::exportBackupToJson,
        onImportJsonBackup = { json -> viewModel.importBackupFromJson(context, json) },
        onAddCustomForm = viewModel::addCustomForm,
        onUpdateCustomForm = viewModel::updateCustomForm,
        onDeleteCustomForm = viewModel::deleteCustomForm,
        modifier = modifier
    )
}

@Composable
private fun UpdateAvailableDialog(
    updateUiState: UpdateUiState,
    onConfirmDownloadAndInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    val info = updateUiState.availableUpdate ?: return
    val pct = (updateUiState.downloadProgress * 100).toInt()

    AlertDialog(
        onDismissRequest = {
            if (!updateUiState.isDownloading) {
                onDismiss()
            }
        },
        icon = {
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Доступна версия v${info.versionName}",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Вышло новое обновление приложения «Таблетница». Все ваши курсы и отметки сохранятся.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = info.releaseNotes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (updateUiState.isDownloading) {
                    Text(
                        text = "Скачивание пакета обновления: $pct%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FastProgressBar(
                        progress = updateUiState.downloadProgress,
                        height = 10.dp,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }

                if (updateUiState.needsInstallPermission) {
                    Text(
                        text = "Для установки подтвердите разрешение «Установка неизвестных приложений» для «Таблетницы» и нажмите кнопку ниже.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmDownloadAndInstall,
                enabled = !updateUiState.isDownloading,
                modifier = Modifier.testTag("modal_install_update_button")
            ) {
                Text(
                    text = when {
                        updateUiState.isDownloading -> "Загрузка ($pct%)…"
                        updateUiState.downloadedApkPath != null -> "Установить сейчас"
                        else -> "Скачать и установить"
                    }
                )
            }
        },
        dismissButton = {
            if (!updateUiState.isDownloading) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("modal_dismiss_update_button")
                ) {
                    Text("Позже")
                }
            }
        }
    )
}
