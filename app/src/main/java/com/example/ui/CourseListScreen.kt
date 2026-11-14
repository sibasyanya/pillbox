package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.CourseStatus
import com.example.data.DoseFormatter
import com.example.data.MedicationCourse
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ShortDateRuFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.forLanguageTag("ru"))
private val StatusBadgeShape = RoundedCornerShape(10.dp)
private val DoseBoxShape = RoundedCornerShape(12.dp)

@Composable
fun CourseListScreen(
    courses: List<MedicationCourse>,
    consumedDoseByCourse: Map<Long, Double>,
    selectedFilter: CourseFilter,
    onFilterChange: (CourseFilter) -> Unit,
    onEditCourse: (MedicationCourse) -> Unit,
    onDuplicateCourse: (MedicationCourse) -> Unit,
    onCompleteCourseEarly: (MedicationCourse) -> Unit,
    onReactivateCourse: (MedicationCourse) -> Unit,
    onDeleteCourse: (MedicationCourse) -> Unit,
    onNavigateToAddCourse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeCount = remember(courses) { courses.count { it.status == CourseStatus.ACTIVE } }
    val completedCount = remember(courses) { courses.count { it.status == CourseStatus.COMPLETED } }

    val filteredCourses = remember(courses, selectedFilter) {
        when (selectedFilter) {
            CourseFilter.ALL -> courses
            CourseFilter.ACTIVE -> courses.filter { it.status == CourseStatus.ACTIVE }
            CourseFilter.COMPLETED -> courses.filter { it.status == CourseStatus.COMPLETED }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .testTag("course_history_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "courses_header", contentType = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.courses_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UniformFilterButton(
                            label = stringResource(R.string.courses_filter_all, courses.size),
                            selected = selectedFilter == CourseFilter.ALL,
                            onClick = { onFilterChange(CourseFilter.ALL) },
                            testTag = "filter_all_chip",
                            modifier = Modifier.weight(1f)
                        )
                        UniformFilterButton(
                            label = stringResource(R.string.courses_filter_active, activeCount),
                            selected = selectedFilter == CourseFilter.ACTIVE,
                            onClick = { onFilterChange(CourseFilter.ACTIVE) },
                            testTag = "filter_active_chip",
                            modifier = Modifier.weight(1f)
                        )
                        UniformFilterButton(
                            label = stringResource(R.string.courses_filter_completed, completedCount),
                            selected = selectedFilter == CourseFilter.COMPLETED,
                            onClick = { onFilterChange(CourseFilter.COMPLETED) },
                            testTag = "filter_completed_chip",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (filteredCourses.isEmpty()) {
                item(key = "empty_courses_card", contentType = "empty") {
                    FastCard(
                        modifier = Modifier.testTag("empty_courses_card"),
                        contentPadding = 24.dp
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Text(
                                text = stringResource(R.string.courses_Empty),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            FastActionButton(
                                text = stringResource(R.string.today_create_course_cta),
                                icon = Icons.Default.Add,
                                onClick = onNavigateToAddCourse,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("empty_history_add_course_button")
                            )
                        }
                    }
                }
            } else {
                items(
                    items = filteredCourses,
                    key = { it.id },
                    contentType = { "course_card" }
                ) { course ->
                    val takenDoseSum = consumedDoseByCourse[course.id] ?: 0.0
                    CourseHistoryCard(
                        course = course,
                        consumedDose = takenDoseSum,
                        onEditCourse = { onEditCourse(course) },
                        onDuplicateCourse = { onDuplicateCourse(course) },
                        onCompleteEarly = { onCompleteCourseEarly(course) },
                        onReactivate = { onReactivateCourse(course) },
                        onDelete = { onDeleteCourse(course) }
                    )
                }
            }

            item(key = "bottom_spacer", contentType = "spacer") {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun UniformFilterButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    FastActionButton(
        text = label,
        onClick = onClick,
        height = 44.dp,
        containerColor = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        borderColor = if (selected) {
            null
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        },
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        modifier = modifier.testTag(testTag)
    )
}

@Composable
private fun CourseHistoryCard(
    course: MedicationCourse,
    consumedDose: Double,
    onEditCourse: () -> Unit,
    onDuplicateCourse: () -> Unit,
    onCompleteEarly: () -> Unit,
    onReactivate: () -> Unit,
    onDelete: () -> Unit
) {
    val dateRangeLine = remember(
        course.formName,
        course.startDateEpochDay,
        course.endDateEpochDay,
        course.totalDurationDays
    ) {
        val startDateStr = LocalDate.ofEpochDay(course.startDateEpochDay).format(ShortDateRuFormatter)
        val endDateStr = LocalDate.ofEpochDay(course.endDateEpochDay).format(ShortDateRuFormatter)
        "${course.formName} • $startDateStr — $endDateStr (${course.totalDurationDays} дн.)"
    }
    val totalRequired = remember(course.phases) { course.totalRequiredDose }
    val totalFormatted = remember(totalRequired, course.unitName) {
        "Всего на курс: ${DoseFormatter.format(totalRequired)} ${course.unitName}"
    }
    val remainingDose = (totalRequired - consumedDose).coerceAtLeast(0.0)
    val remainingFormatted = remember(remainingDose, course.unitName) {
        "Остаток: ${DoseFormatter.format(remainingDose)} ${course.unitName}"
    }
    val progress = if (totalRequired > 0.0) {
        (consumedDose / totalRequired).toFloat().coerceIn(0f, 1f)
    } else 0f

    val phaseSummaryLines = remember(course.phases, course.unitName) {
        course.phases.map { phase ->
            val phaseDoseStr = DoseFormatter.format(phase.dosePerIntake)
            val phaseTotalStr = DoseFormatter.format(phase.phaseTotalDose)
            val timesStr = phase.intakeTimes.joinToString(", ")
            "• Фаза ${phase.phaseNumber}: ${phase.durationDays} дн. × ${phase.frequencyPerDay} р/д по $phaseDoseStr ${course.unitName} = $phaseTotalStr ${course.unitName} ($timesStr)"
        }
    }

    val isActive = course.status == CourseStatus.ACTIVE

    FastCard(
        modifier = Modifier.testTag("course_card_${course.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isActive) {
                            Icons.Default.MedicalServices
                        } else {
                            Icons.Default.CheckCircle
                        },
                        contentDescription = null,
                        tint = if (isActive) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                Column {
                    Text(
                        text = course.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = dateRangeLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(34.dp)
                    .clip(StatusBadgeShape)
                    .background(
                        if (isActive) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        StatusBadgeShape
                    )
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = if (isActive) {
                        stringResource(R.string.courses_status_active)
                    } else {
                        stringResource(R.string.courses_status_completed)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }

        if (course.notes.isNotBlank()) {
            Text(
                text = course.notes,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(DoseBoxShape)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    DoseBoxShape
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = totalFormatted,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = remainingFormatted,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            FastProgressBar(
                progress = progress,
                height = 8.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (line in phaseSummaryLines) {
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

        // Первый ряд: Редактировать курс + Дублировать курс (одинаковая ширина и высота 48.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FastActionButton(
                text = stringResource(R.string.courses_edit),
                icon = Icons.Default.Edit,
                onClick = onEditCourse,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .weight(1f)
                    .testTag("edit_course_button_${course.id}")
            )

            FastActionButton(
                text = stringResource(R.string.courses_duplicate),
                icon = Icons.Default.ContentCopy,
                onClick = onDuplicateCourse,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .weight(1f)
                    .testTag("duplicate_course_button_${course.id}")
            )
        }

        // Второй ряд: Завершить/Возобновить + Удалить курс (одинаковая ширина и высота 48.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isActive) {
                FastActionButton(
                    text = stringResource(R.string.courses_complete_early),
                    icon = Icons.Default.DoneAll,
                    onClick = onCompleteEarly,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("complete_course_button_${course.id}")
                )
            } else {
                FastActionButton(
                    text = stringResource(R.string.courses_reactivate),
                    icon = Icons.Default.Replay,
                    onClick = onReactivate,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("reactivate_course_button_${course.id}")
                )
            }

            FastActionButton(
                text = stringResource(R.string.courses_delete),
                icon = Icons.Default.DeleteOutline,
                onClick = onDelete,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.error,
                borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .testTag("delete_course_button_${course.id}")
            )
        }
    }
}
