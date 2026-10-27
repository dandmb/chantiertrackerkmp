package com.dmb.chantiertracker.presentation.projects

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dmb.chantiertracker.domain.model.ISO_4217_CURRENCIES
import com.dmb.chantiertracker.presentation.AppOutlinedTextField
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.create_currency_label
import com.dmb.chantiertracker.resources.create_timezone_label
import org.jetbrains.compose.resources.stringResource

@Composable
fun TimezoneField(
    value: String,
    options: List<String>,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) = ChoiceField(value, options, stringResource(Res.string.create_timezone_label), enabled, onSelect, modifier)

@Composable
fun CurrencyField(
    value: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = if (value.isBlank() || value in ISO_4217_CURRENCIES) ISO_4217_CURRENCIES else listOf(value) + ISO_4217_CURRENCIES
    ChoiceField(value, options, stringResource(Res.string.create_currency_label), enabled, onSelect, modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceField(
    value: String,
    options: List<String>,
    label: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier,
    ) {
        AppOutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
