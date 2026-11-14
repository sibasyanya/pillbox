package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.DoseFormatter
import com.example.data.ScheduledIntakeItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val RussianDayFormatter = DateTimeFormatter.ofPattern("d MMMM, EEE", Locale.forLanguageTag("ru"))
private val TimeStampFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.forLanguageTag("ru"))

private val HeaderGradientBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFF0F766E),
        Color(0xFF0D9488),
        Color(0xFF115E59)
    )
)

private val HeroShape = RoundedCornerShape(20.dp)
private val DateSwitcherShape = RoundedCornerShape(14.dp)
private val BadgeShape = RoundedCornerShape(10.dp)

@Composable
fun TodayScreen(
    selectedDateEpochDay: Long,
    scheduleItems: List<ScheduledIntakeItem>,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onSelectToday: () -> Unit,
    onToggleIntakeTaken: (ScheduledIntakeItem) -> Unit,
    onToggleIntakeSkipped: (ScheduledIntakeItem) -> Unit,
    onNavigateToAddCourse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val todayEpochDay = remember { LocalDate.now().toEpochDay() }
    val selectedDate = remember(selectedDateEpochDay) { LocalDate.ofEpochDay(selectedDateEpochDay) }
    val isToday = selectedDateEpochDay == todayEpochDay

    val totalCount = scheduleItems.size
    val takenCount = remember(scheduleItems) { scheduleItems.count { it.isTaken } }
    val skippedCount = remember(scheduleItems) { scheduleItems.count { it.isSkipped } }
    val progressFraction = if (totalCount > 0) takenCount.toFloat() / totalCount.toFloat() else 0f
    val progressPercent = (progressFraction * 100).toInt()

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
                .testTag("today_schedule_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "hero_date_header", contentType = "header") {
                TodayHeroHeaderCard(
                    selectedDate = selectedDate,
                    isToday = isToday,
                    onPreviousDay = onPreviousDay,
                    onNextDay = onNextDay,
                    onSelectToday = onSelectToday
                )
            }

            item(key = "compliance_indicator", contentType = "compliance") {
                ComplianceProgressCard(
                    takenCount = takenCount,
                    skippedCount = skippedCount,
                    totalCount = totalCount,
                    progressFraction = progressFraction,
                    progressPercent = progressPercent
                )
            }

            if (scheduleItems.isEmpty()) {
                item(key = "empty_schedule", contentType = "empty") {
                    EmptyTodayScheduleCard(
                        onNavigateToAddCourse = onNavigateToAddCourse
                    )
                }
            } else {
                items(
                    items = scheduleItems,
                    key = { it.uniqueKey },
                    contentType = { "intake_item" }
                ) { item ->
                    IntakeScheduleCard(
                        item = item,
                        onToggleTaken = { onToggleIntakeTaken(item) },
                        onToggleSkipped = { onToggleIntakeSkipped(item) }
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
private fun TodayHeroHeaderCard(
    selectedDate: LocalDate,
    isToday: Boolean,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onSelectToday: () -> Unit
) {
    val formattedDate = remember(selectedDate) {
        selectedDate.format(RussianDayFormatter)
            .replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.forLanguageTag("ru")) else it.toString()
            }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HeroShape)
            .background(HeaderGradientBrush, HeroShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.app_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(DateSwitcherShape)
                .background(Color.Black.copy(alpha = 0.24f), DateSwitcherShape)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onPreviousDay,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("prev_day_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.today_prev_day),
                    tint = Color.White
                )
            }

            Text(
                text = if (isToday) "Сегодня • $formattedDate" else formattedDate,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = onNextDay,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("next_day_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.today_next_day),
                    tint = Color.White
                )
            }
        }

        if (!isToday) {
            FastActionButton(
                text = stringResource(R.string.today_return_to_today),
                icon = Icons.Outlined.CalendarToday,
                onClick = onSelectToday,
                containerColor = Color.White.copy(alpha = 0.14f),
                contentColor = Color.White,
                borderColor = Color.White.copy(alpha = 0.65f),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("return_today_button")
            )
        }
    }
}

@Composable
private fun ComplianceProgressCard(
    takenCount: Int,
    skippedCount: Int,
    totalCount: Int,
    progressFraction: Float,
    progressPercent: Int
) {
    val isAllDone = totalCount > 0 && takenCount == totalCount
    val baseSummary = if (totalCount > 0) {
        stringResource(
            R.string.today_progress_summary,
            takenCount,
            totalCount,
            progressPercent
        )
    } else {
        "0 из 0 приемов (0%)"
    }
    val summaryText = if (skippedCount > 0) {
        "$baseSummary • Не принято: $skippedCount"
    } else {
        baseSummary
    }

    FastCard(
        modifier = Modifier.testTag("compliance_progress_card"),
        verticalSpacing = 10.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.today_progress_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = summaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .clip(BadgeShape)
                    .background(
                        if (isAllDone) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        BadgeShape
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$progressPercent%",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isAllDone) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
            }
        }

        FastProgressBar(
            progress = progressFraction,
            height = 10.dp,
            color = if (isAllDone) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.primary
            },
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        if (isAllDone) {
            Text(
                text = stringResource(R.string.today_all_completed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun IntakeScheduleCard(
    item: ScheduledIntakeItem,
    onToggleTaken: () -> Unit,
    onToggleSkipped: () -> Unit
) {
    val formattedDose = remember(item.dosePerIntake) { DoseFormatter.format(item.dosePerIntake) }
    val subtitleLine = remember(
        item.formName,
        formattedDose,
        item.unitName,
        item.dayInCourse,
        item.totalCourseDays
    ) {
        "${item.formName} • Доза: $formattedDose ${item.unitName} • День курса ${item.dayInCourse} из ${item.totalCourseDays}"
    }
    val timeFormatted = remember(item.takenAtTimestamp) {
        item.takenAtTimestamp?.let {
            Instant.ofEpochMilli(it)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
                .format(TimeStampFormatter)
        }
    }
    val phaseBadgeText = stringResource(
        R.string.today_phase_badge,
        item.phaseNumber,
        item.totalPhases,
        item.dayInPhase,
        item.phaseDurationDays
    )

    val cardContainerColor = when {
        item.isTaken -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        item.isSkipped -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
        else -> MaterialTheme.colorScheme.surface
    }
    val cardBorderColor = when {
        item.isSkipped -> MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    }

    val badgeContainerColor = when {
        item.isTaken -> MaterialTheme.colorScheme.tertiaryContainer
        item.isSkipped -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val badgeContentColor = when {
        item.isTaken -> MaterialTheme.colorScheme.onTertiaryContainer
        item.isSkipped -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    FastCard(
        modifier = Modifier.testTag("intake_card_${item.uniqueKey}"),
        containerColor = cardContainerColor,
        borderColor = cardBorderColor
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .height(36.dp)
                    .clip(BadgeShape)
                    .background(badgeContainerColor, BadgeShape)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = badgeContentColor
                )
                Text(
                    text = item.scheduledTime,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = badgeContentColor
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(36.dp)
                    .clip(BadgeShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer, BadgeShape)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = phaseBadgeText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.testTag("phase_badge_${item.uniqueKey}")
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(badgeContainerColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        item.isTaken -> Icons.Default.CheckCircle
                        item.isSkipped -> Icons.Default.Cancel
                        else -> Icons.Default.MedicalServices
                    },
                    contentDescription = null,
                    tint = badgeContentColor
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.courseName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (item.isTaken || item.isSkipped) {
                        TextDecoration.LineThrough
                    } else {
                        TextDecoration.None
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitleLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                if (item.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = item.notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Нижний ряд из двух кнопок единой высоты 48.dp и ширины (Принято / Не принято)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val takenBtnText = if (item.isTaken) {
                if (timeFormatted != null) {
                    "${stringResource(R.string.today_btn_taken_done)} ($timeFormatted)"
                } else {
                    stringResource(R.string.today_btn_taken_done)
                }
            } else {
                stringResource(R.string.today_btn_take)
            }

            FastActionButton(
                text = takenBtnText,
                icon = if (item.isTaken) Icons.Default.CheckCircle else Icons.Outlined.CheckCircleOutline,
                onClick = onToggleTaken,
                containerColor = if (item.isTaken) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                },
                contentColor = if (item.isTaken) {
                    MaterialTheme.colorScheme.onTertiaryContainer
                } else {
                    MaterialTheme.colorScheme.onPrimary
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("take_button_${item.uniqueKey}")
            )

            val skippedBtnText = if (item.isSkipped) {
                if (timeFormatted != null) {
                    "${stringResource(R.string.today_btn_skipped_done)} ($timeFormatted)"
                } else {
                    stringResource(R.string.today_btn_skipped_done)
                }
            } else {
                stringResource(R.string.today_btn_skip)
            }

            FastActionButton(
                text = skippedBtnText,
                icon = if (item.isSkipped) Icons.Default.Cancel else Icons.Default.Close,
                onClick = onToggleSkipped,
                containerColor = if (item.isSkipped) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                contentColor = if (item.isSkipped) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                borderColor = if (item.isSkipped) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                },
                fontWeight = if (item.isSkipped) FontWeight.Bold else FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .testTag("skip_button_${item.uniqueKey}")
            )
        }
    }
}

@Composable
private fun EmptyTodayScheduleCard(
    onNavigateToAddCourse: () -> Unit
) {
    FastCard(
        modifier = Modifier.testTag("empty_today_card"),
        contentPadding = 24.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MedicalServices,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text(
                text = stringResource(R.string.today_empty_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.today_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            FastActionButton(
                text = stringResource(R.string.today_create_course_cta),
                icon = Icons.Default.Add,
                onClick = onNavigateToAddCourse,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("empty_create_course_button")
            )
        }
    }
}
