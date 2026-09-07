package com.dmb.chantiertracker.presentation.logs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.presentation.auth.components.AuthPrimaryButton
import com.dmb.chantiertracker.presentation.format.formatAmount
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.action_save
import com.dmb.chantiertracker.resources.consumption_line_add_title
import com.dmb.chantiertracker.resources.consumption_line_edit_title
import com.dmb.chantiertracker.resources.material_label
import com.dmb.chantiertracker.resources.quantity_label
import com.dmb.chantiertracker.resources.stock_available
import com.dmb.chantiertracker.resources.stock_none_available
import com.dmb.chantiertracker.resources.validation_stock_exceeded
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ConsumptionLineFormScreen(
    entryLocalId: String,
    projectLocalId: String,
    lineLocalId: String?,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    onTitleResolved: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ConsumptionLineFormViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(entryLocalId, lineLocalId) { viewModel.load(entryLocalId, projectLocalId, lineLocalId) }
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    val title = stringResource(if (lineLocalId != null) Res.string.consumption_line_edit_title else Res.string.consumption_line_add_title)
    LaunchedEffect(title) { onTitleResolved(title) }

    Box(modifier.fillMaxSize()) {
        when {
            state.isMissing -> MissingState(onBack)
            !state.ready -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            else -> Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp)),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(Res.string.material_label), style = MaterialTheme.typography.titleSmall)

                if (state.pickable.isEmpty()) {
                    Text(
                        stringResource(Res.string.stock_none_available),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.pickable.forEach { s ->
                    val selected = s.materialLocalId == state.selectedMaterialId
                    Surface(
                        onClick = { viewModel.selectMaterial(s.materialLocalId) },
                        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${s.materialName} (${s.unit})", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(Res.string.stock_available, formatAmount(s.available), s.unit),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                state.selectedStock?.let {
                    OutlinedTextField(
                        value = state.quantity,
                        onValueChange = viewModel::onQuantityChange,
                        label = { Text(stringResource(Res.string.quantity_label) + " (${it.unit})") },
                        isError = state.quantityError != null || state.exceedsStock,
                        supportingText = {
                            val message = when {
                                state.exceedsStock -> stringResource(Res.string.validation_stock_exceeded, formatAmount(state.ceiling ?: 0.0))
                                state.quantityError != null -> stringResource(state.quantityError!!)
                                else -> stringResource(Res.string.stock_available, formatAmount(state.ceiling ?: 0.0), it.unit)
                            }
                            Text(message)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        enabled = !state.isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                AuthPrimaryButton(
                    text = stringResource(Res.string.action_save),
                    onClick = viewModel::submit,
                    loading = state.isSubmitting,
                    enabled = state.canSave,
                )
            }
        }
    }
}
