package com.dmb.chantiertracker.presentation.logs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.presentation.auth.components.AuthPrimaryButton
import com.dmb.chantiertracker.presentation.format.formatMoney
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.action_cancel
import com.dmb.chantiertracker.resources.action_create
import com.dmb.chantiertracker.resources.action_save
import com.dmb.chantiertracker.resources.error_not_found
import com.dmb.chantiertracker.resources.material_label
import com.dmb.chantiertracker.resources.material_new_option
import com.dmb.chantiertracker.resources.material_query_hint
import com.dmb.chantiertracker.resources.material_unit_hint
import com.dmb.chantiertracker.resources.material_unit_label
import com.dmb.chantiertracker.resources.per_unit_suffix
import com.dmb.chantiertracker.resources.projects_retry
import com.dmb.chantiertracker.resources.purchase_line_add_title
import com.dmb.chantiertracker.resources.purchase_line_edit_title
import com.dmb.chantiertracker.resources.quantity_label
import com.dmb.chantiertracker.resources.supplier_label
import com.dmb.chantiertracker.resources.total_price_label
import com.dmb.chantiertracker.resources.unit_price_label
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PurchaseLineFormScreen(
    entryLocalId: String,
    projectLocalId: String,
    lineLocalId: String?,
    currency: String?,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    onTitleResolved: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: PurchaseLineFormViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(entryLocalId, lineLocalId) { viewModel.load(entryLocalId, projectLocalId, lineLocalId) }
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    val title = stringResource(if (lineLocalId != null) Res.string.purchase_line_edit_title else Res.string.purchase_line_add_title)
    LaunchedEffect(title) { onTitleResolved(title) }

    Box(modifier.fillMaxSize()) {
        when {
            state.isMissing -> MissingState(onBack)
            !state.ready -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            else -> Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.isEdit) {
                    OutlinedTextField(
                        value = state.selectedMaterial?.let { "${it.name} (${it.unit})" }.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(Res.string.material_label)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    MaterialPicker(state, viewModel)
                }

                OutlinedTextField(
                    value = state.quantity,
                    onValueChange = viewModel::onQuantityChange,
                    label = { Text(stringResource(Res.string.quantity_label)) },
                    isError = state.quantityError != null,
                    supportingText = state.quantityError?.let { { Text(stringResource(it)) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled = !state.isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.unitPrice,
                    onValueChange = viewModel::onUnitPriceChange,
                    label = { Text(stringResource(Res.string.unit_price_label)) },
                    isError = state.unitPriceError != null,
                    supportingText = state.unitPriceError?.let { { Text(stringResource(it)) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    enabled = !state.isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.supplier,
                    onValueChange = viewModel::onSupplierChange,
                    label = { Text(stringResource(Res.string.supplier_label)) },
                    singleLine = true,
                    enabled = !state.isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(Res.string.total_price_label, formatMoney(state.totalPrice, currency)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AuthPrimaryButton(
                    text = stringResource(Res.string.action_save),
                    onClick = viewModel::submit,
                    loading = state.isSubmitting,
                )
            }
        }
    }
}

@Composable
private fun MaterialPicker(state: PurchaseLineFormUiState, viewModel: PurchaseLineFormViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.materialQuery,
            onValueChange = viewModel::onMaterialQueryChange,
            label = { Text(stringResource(Res.string.material_label)) },
            placeholder = { Text(stringResource(Res.string.material_query_hint)) },
            isError = state.materialError != null,
            supportingText = state.materialError?.let { { Text(stringResource(it)) } },
            singleLine = true,
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        )

        state.suggestions.take(6).forEach { material ->
            PickerRow("${material.name} (${material.unit})") { viewModel.selectMaterial(material) }
        }

        if (state.canOfferCreate && state.creatingNewUnit == null) {
            PickerRow(stringResource(Res.string.material_new_option, state.materialQuery.trim())) { viewModel.startCreateMaterial() }
        }

        state.creatingNewUnit?.let { unit ->
            OutlinedTextField(
                value = unit,
                onValueChange = viewModel::onNewUnitChange,
                label = { Text(stringResource(Res.string.material_unit_label)) },
                placeholder = { Text(stringResource(Res.string.material_unit_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = viewModel::cancelCreateMaterial) { Text(stringResource(Res.string.action_cancel)) }
                Button(onClick = viewModel::confirmCreateMaterial, enabled = unit.isNotBlank()) {
                    Text(stringResource(Res.string.action_create))
                }
            }
        }
    }
}

@Composable
private fun PickerRow(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun BoxScope.MissingState(onBack: () -> Unit) {
    Column(
        modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.error_not_found),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = onBack) { Text(stringResource(Res.string.projects_retry)) }
    }
}
