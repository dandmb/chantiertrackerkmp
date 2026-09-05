package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.dmb.chantiertracker.presentation.main.CalendarIcon
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.action_cancel
import com.dmb.chantiertracker.resources.action_ok
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/**
 * A read-only text field that opens the Material 3 calendar on tap. The picker
 * only offers dates on or after [minDate] (via [SelectableDates]); the field
 * holds an ISO `yyyy-MM-dd` string (or "" when unset).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    minDate: LocalDate,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
) {
    var showPicker by remember { mutableStateOf(false) }
    val minMillis = remember(minDate) { minDate.toUtcMillis() }

    Box(modifier) {
        OutlinedTextField(
            // Stored ISO, shown as JJ-MM-AAAA like every other date in the app.
            value = if (value.isBlank()) "" else formatIsoDate(value),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            singleLine = true,
            isError = isError,
            supportingText = supportingText,
            trailingIcon = { Icon(CalendarIcon, contentDescription = null) },
        )
        // Transparent tap target over the field — a read-only OutlinedTextField
        // doesn't forward clicks on its own.
        if (enabled) {
            Box(
                Modifier
                    .matchParentSize()
                    .testTag("dateFieldTap:$label")
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { showPicker = true },
            )
        }
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = parseIsoDateOrNull(value)?.toUtcMillis(),
            initialDisplayedMonthMillis = (parseIsoDateOrNull(value) ?: minDate).toUtcMillis(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= minMillis
                override fun isSelectableYear(year: Int): Boolean = year >= minDate.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { onValueChange(utcMillisToLocalDate(it).toString()) }
                        showPicker = false
                    },
                    enabled = pickerState.selectedDateMillis != null,
                ) { Text(stringResource(Res.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(Res.string.action_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
