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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.DoseFormatter
import com.example.data.MedicationFormEntity
import com.example.receiver.NotificationHelper
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FullDateRuFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy г.", Locale.forLanguageTag("ru"))
private val PhaseBadgeShape = RoundedCornerShape(10.dp)
private val FreqBoxShape = RoundedCornerShape(14.dp)

private data class EditingTimeSlotTarget(
    val phaseId: Long,
    val slotIndex: Int,
    val currentTime: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCourseScreen(
    builderState: CourseBuilderUiState,
    medicationForms: List<MedicationFormEntity>,
    onNameChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onSelectForm: (String, String) -> Unit,
    onStartDateChange: (Long) -> Unit,
    onAddPhase: () -> Unit,
    onRemovePhase: (Long) -> Unit,
    onPhaseDurationChange: (Long, String) -> Unit,
    onPhaseFrequencyChange: (Long, Int) -> Unit,
    onPhaseDoseChange: (Long, String) -> Unit,
    onPhaseTimeSlotChange: (Long, Int, String) -> Unit,
    onCancelEditing: () -> Unit,
    onSaveAndStartReminders: () -> Unit,
    onAddCustomForm: (String, String) -> Unit,
    onUpdateCustomForm: (MedicationFormEntity, String, String) -> Unit,
    onDeleteCustomForm: (MedicationFormEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showFormsDictionaryDialog by remember { mutableStateOf(false) }
    var editingTimeTarget by remember { mutableStateOf<EditingTimeSlotTarget?>(null) }

    val availableForms = remember(medicationForms) {
        if (medicationForms.isNotEmpty()) {
            medicationForms
        } else {
            listOf(
                MedicationFormEntity(id = 1, title = "Таблетки", unit = "таб.", isDefault = true),
                MedicationFormEntity(id = 2, title = "Капли", unit = "кап.", isDefault = true),
                MedicationFormEntity(id = 3, title = "Сироп", unit = "мл", isDefault = true),
                MedicationFormEntity(id = 4, title = "Инъекции", unit = "ампул", isDefault = true),
                MedicationFormEntity(id = 5, title = "Процедура", unit = "процед.", isDefault = true),
                MedicationFormEntity(id = 6, title = "Капсулы", unit = "капс.", isDefault = true)
            )
        }
    }
    val chunkedForms = remember(availableForms) { availableForms.chunked(2) }
    val startDateFormatted = remember(builderState.startDateEpochDay) {
        LocalDate.ofEpochDay(builderState.startDateEpochDay).format(FullDateRuFormatter)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .widthIn(max = 680.dp)
                .testTag("add_course_scroll_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "header_title", contentType = "header") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (builderState.isEditing) {
                            stringResource(R.string.builder_edit_title)
                        } else {
                            stringResource(R.string.builder_title)
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )

                    if (builderState.isEditing) {
                        FastActionButton(
                            text = stringResource(R.string.builder_cancel_edit),
                            icon = Icons.Default.Close,
                            onClick = onCancelEditing,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cancel_editing_course_button")
                        )
                    }
                }
            }

            if (builderState.errorMessage != null) {
                item(key = "error_banner", contentType = "error") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(FreqBoxShape)
                            .background(MaterialTheme.colorScheme.errorContainer, FreqBoxShape)
                            .padding(14.dp)
                            .testTag("builder_error_banner")
                    ) {
                        Text(
                            text = builderState.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Карточка основных параметров курса
            item(key = "course_meta_card", contentType = "meta") {
                FastCard(verticalSpacing = 14.dp) {
                    OutlinedTextField(
                        value = builderState.name,
                        onValueChange = onNameChange,
                        label = { Text(stringResource(R.string.builder_medication_name_label)) },
                        placeholder = { Text(stringResource(R.string.builder_medication_name_placeholder)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("course_name_input")
                    )

                    OutlinedTextField(
                        value = builderState.notes,
                        onValueChange = onNotesChange,
                        label = { Text(stringResource(R.string.builder_notes_label)) },
                        placeholder = { Text(stringResource(R.string.builder_notes_placeholder)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("course_notes_input")
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                    Text(
                        text = stringResource(R.string.builder_form_section_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Сетка форм выпуска (2 колонки одинаковой ширины и высоты 44.dp)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        chunkedForms.forEach { rowForms ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowForms.forEach { form ->
                                    val isSelected = builderState.selectedFormTitle == form.title
                                    FastActionButton(
                                        text = "${form.title} (${form.unit})",
                                        icon = if (isSelected) Icons.Default.Check else null,
                                        onClick = { onSelectForm(form.title, form.unit) },
                                        height = 44.dp,
                                        containerColor = if (isSelected) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                        contentColor = if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        borderColor = if (isSelected) {
                                            null
                                        } else {
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                                        },
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("form_chip_${form.title}")
                                    )
                                }
                                if (rowForms.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    FastActionButton(
                        text = stringResource(R.string.builder_manage_forms),
                        icon = Icons.Default.Tune,
                        onClick = { showFormsDictionaryDialog = true },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manage_forms_button")
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                    Text(
                        text = "${stringResource(R.string.builder_start_date_label)}: $startDateFormatted",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    FastActionButton(
                        text = "${stringResource(R.string.builder_change_date)} ($startDateFormatted)",
                        icon = Icons.Default.CalendarMonth,
                        onClick = { showDatePicker = true },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("change_start_date_button")
                    )
                }
            }

            // Заголовок секции этапов
            item(key = "phases_header", contentType = "section_header") {
                Text(
                    text = stringResource(R.string.builder_phases_section_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
            }

            // Карточки фаз курса
            itemsIndexed(
                items = builderState.phases,
                key = { _, phase -> phase.id },
                contentType = { _, _ -> "phase_editor" }
            ) { index, phase ->
                PhaseEditorCard(
                    phaseIndexOneBased = index + 1,
                    phase = phase,
                    unitName = builderState.selectedUnit,
                    canDelete = builderState.phases.size > 1,
                    onDurationChange = { onPhaseDurationChange(phase.id, it) },
                    onFrequencyChange = { onPhaseFrequencyChange(phase.id, it) },
                    onDoseChange = { onPhaseDoseChange(phase.id, it) },
                    onDeletePhase = { onRemovePhase(phase.id) },
                    onEditTimeSlot = { slotIndex, currentTime ->
                        editingTimeTarget = EditingTimeSlotTarget(
                            phaseId = phase.id,
                            slotIndex = slotIndex,
                            currentTime = currentTime
                        )
                    }
                )
            }

            // Кнопка добавления этапа единой высоты 48.dp
            item(key = "add_phase_footer_button", contentType = "footer") {
                FastActionButton(
                    text = stringResource(R.string.builder_add_phase),
                    icon = Icons.Default.Add,
                    onClick = onAddPhase,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_phase_bottom_button")
                )
            }
        }

        // Закрепленная нижняя плашка (Sticky Bottom Card)
        StickyCalculatorBottomBar(
            isEditing = builderState.isEditing,
            totalDurationDays = builderState.totalDurationDays,
            formattedTotalRequiredDose = builderState.formattedTotalRequiredDose,
            unitName = builderState.selectedUnit,
            onSaveAndStart = onSaveAndStartReminders
        )
    }

    // Диалог выбора даты начала курса
    if (showDatePicker) {
        val initialMillis = builderState.startDateEpochDay * 86_400_000L
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val millis = datePickerState.selectedDateMillis
                        if (millis != null) {
                            val selectedEpochDay = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                                .toEpochDay()
                            onStartDateChange(selectedEpochDay)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Выбрать")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Отмена")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Диалог выбора времени для конкретного слота приема
    val currentEditingTarget = editingTimeTarget
    if (currentEditingTarget != null) {
        val parsed = NotificationHelper.parseLocalTimeOrNull(currentEditingTarget.currentTime)
        val timePickerState = rememberTimePickerState(
            initialHour = parsed?.hour ?: 8,
            initialMinute = parsed?.minute ?: 0,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { editingTimeTarget = null },
            title = { Text("Время приема №${currentEditingTarget.slotIndex + 1}") },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val formatted = String.format(
                            Locale.ROOT,
                            "%02d:%02d",
                            timePickerState.hour,
                            timePickerState.minute
                        )
                        onPhaseTimeSlotChange(
                            currentEditingTarget.phaseId,
                            currentEditingTarget.slotIndex,
                            formatted
                        )
                        editingTimeTarget = null
                    }
                ) {
                    Text("Готово")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingTimeTarget = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (showFormsDictionaryDialog) {
        MedicationFormsDictionaryDialog(
            forms = availableForms,
            onAddForm = onAddCustomForm,
            onUpdateForm = onUpdateCustomForm,
            onDeleteForm = onDeleteCustomForm,
            onDismiss = { showFormsDictionaryDialog = false }
        )
    }
}

@Composable
private fun PhaseEditorCard(
    phaseIndexOneBased: Int,
    phase: DraftPhaseUiState,
    unitName: String,
    canDelete: Boolean,
    onDurationChange: (String) -> Unit,
    onFrequencyChange: (Int) -> Unit,
    onDoseChange: (String) -> Unit,
    onDeletePhase: () -> Unit,
    onEditTimeSlot: (Int, String) -> Unit
) {
    val subtotalFormatted = remember(phase.phaseSubtotalDose) {
        DoseFormatter.format(phase.phaseSubtotalDose)
    }
    val chunkedSlots = remember(phase.intakeTimes) {
        phase.intakeTimes.mapIndexed { idx, time -> idx to time }.chunked(3)
    }

    FastCard(
        modifier = Modifier.testTag("phase_card_$phaseIndexOneBased")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(36.dp)
                    .clip(PhaseBadgeShape)
                    .background(MaterialTheme.colorScheme.primaryContainer, PhaseBadgeShape)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.builder_phase_card_title, phaseIndexOneBased),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(36.dp)
                    .clip(PhaseBadgeShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer, PhaseBadgeShape)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = stringResource(
                        R.string.builder_phase_subtotal,
                        subtotalFormatted,
                        unitName,
                        phase.parsedDurationDays
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = phase.durationDaysInput,
                onValueChange = onDurationChange,
                label = { Text(stringResource(R.string.builder_duration_days)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("phase_duration_input_$phaseIndexOneBased")
            )

            OutlinedTextField(
                value = phase.dosePerIntakeInput,
                onValueChange = onDoseChange,
                label = { Text(stringResource(R.string.builder_dose_per_intake, unitName)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("phase_dose_input_$phaseIndexOneBased")
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(FreqBoxShape)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    FreqBoxShape
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.builder_frequency_per_day),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${phase.frequencyPerDay} раз(а) в день",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalIconButton(
                    onClick = { onFrequencyChange(phase.frequencyPerDay - 1) },
                    enabled = phase.frequencyPerDay > 1,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("phase_freq_dec_$phaseIndexOneBased")
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Уменьшить частоту"
                    )
                }

                Text(
                    text = phase.frequencyPerDay.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                FilledTonalIconButton(
                    onClick = { onFrequencyChange(phase.frequencyPerDay + 1) },
                    enabled = phase.frequencyPerDay < 12,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("phase_freq_inc_$phaseIndexOneBased")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Увеличить частоту"
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.builder_times_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            chunkedSlots.forEach { rowSlots ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowSlots.forEach { (slotIdx, timeStr) ->
                        FastActionButton(
                            text = timeStr,
                            icon = Icons.Default.AccessTime,
                            onClick = { onEditTimeSlot(slotIdx, timeStr) },
                            height = 44.dp,
                            shape = PhaseBadgeShape,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("time_slot_${phaseIndexOneBased}_$slotIdx")
                        )
                    }
                    repeat(3 - rowSlots.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        if (canDelete) {
            FastActionButton(
                text = stringResource(R.string.builder_delete_phase),
                icon = Icons.Default.DeleteOutline,
                onClick = onDeletePhase,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.error,
                borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("delete_phase_button_$phaseIndexOneBased")
            )
        }
    }
}

@Composable
private fun StickyCalculatorBottomBar(
    isEditing: Boolean,
    totalDurationDays: Int,
    formattedTotalRequiredDose: String,
    unitName: String,
    onSaveAndStart: () -> Unit
) {
    val summaryText = stringResource(
        R.string.builder_sticky_summary,
        totalDurationDays,
        formattedTotalRequiredDose,
        unitName
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sticky_calculator_card"),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 680.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Calculate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = summaryText,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("sticky_calculator_summary_text")
                )
            }

            FastActionButton(
                text = if (isEditing) {
                    stringResource(R.string.builder_save_edit)
                } else {
                    stringResource(R.string.builder_save_and_start)
                },
                icon = Icons.Default.NotificationsActive,
                onClick = onSaveAndStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_course_button")
            )
        }
    }
}

@Composable
fun MedicationFormsDictionaryDialog(
    forms: List<MedicationFormEntity>,
    onAddForm: (String, String) -> Unit,
    onUpdateForm: (MedicationFormEntity, String, String) -> Unit,
    onDeleteForm: (MedicationFormEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var titleInput by remember { mutableStateOf("") }
    var unitInput by remember { mutableStateOf("") }
    var editingForm by remember { mutableStateOf<MedicationFormEntity?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.forms_dialog_title))
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text(stringResource(R.string.forms_name_field)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_form_title_input")
                )
                OutlinedTextField(
                    value = unitInput,
                    onValueChange = { unitInput = it },
                    label = { Text(stringResource(R.string.forms_unit_field)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_form_unit_input")
                )
                Button(
                    onClick = {
                        val currentEditing = editingForm
                        if (currentEditing != null) {
                            onUpdateForm(currentEditing, titleInput, unitInput)
                            editingForm = null
                        } else {
                            onAddForm(titleInput, unitInput)
                        }
                        titleInput = ""
                        unitInput = ""
                    },
                    enabled = titleInput.isNotBlank() && unitInput.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_custom_form_button")
                ) {
                    Text(
                        text = if (editingForm != null) {
                            stringResource(R.string.forms_update_btn)
                        } else {
                            stringResource(R.string.forms_add_btn)
                        }
                    )
                }

                HorizontalDivider()

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    forms.forEach { form ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(PhaseBadgeShape)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    PhaseBadgeShape
                                )
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${form.title} (${form.unit})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Row {
                                IconButton(
                                    onClick = {
                                        editingForm = form
                                        titleInput = form.title
                                        unitInput = form.unit
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Редактировать форму"
                                    )
                                }
                                if (!form.isDefault || forms.size > 1) {
                                    IconButton(
                                        onClick = { onDeleteForm(form) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Удалить форму",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.forms_close_btn))
            }
        }
    )
}
