package com.dmb.chantiertracker.presentation.logs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.domain.model.ConsumptionLine
import com.dmb.chantiertracker.domain.model.DailyEntry
import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.domain.model.Material
import com.dmb.chantiertracker.domain.model.MaterialStock
import com.dmb.chantiertracker.domain.model.PurchaseLine
import com.dmb.chantiertracker.presentation.format.formatAmount
import com.dmb.chantiertracker.presentation.format.formatMoney
import com.dmb.chantiertracker.presentation.main.AddIcon
import com.dmb.chantiertracker.presentation.main.ConstructionIcon
import com.dmb.chantiertracker.presentation.main.DeleteIcon
import com.dmb.chantiertracker.presentation.main.EditIcon
import com.dmb.chantiertracker.presentation.main.ShoppingCartIcon
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.action_cancel
import com.dmb.chantiertracker.resources.action_create
import com.dmb.chantiertracker.resources.action_delete
import com.dmb.chantiertracker.resources.action_save
import com.dmb.chantiertracker.resources.consumption_line_add
import com.dmb.chantiertracker.resources.consumption_line_add_title
import com.dmb.chantiertracker.resources.consumption_line_edit_title
import com.dmb.chantiertracker.resources.consumption_lines_title
import com.dmb.chantiertracker.resources.consumption_lines_empty
import com.dmb.chantiertracker.resources.entry_add
import com.dmb.chantiertracker.resources.entry_edit
import com.dmb.chantiertracker.resources.entry_edit_title
import com.dmb.chantiertracker.resources.entry_none_yet
import com.dmb.chantiertracker.resources.entry_no_summary
import com.dmb.chantiertracker.resources.entry_no_title
import com.dmb.chantiertracker.resources.entry_summary_label
import com.dmb.chantiertracker.resources.entry_title_label
import com.dmb.chantiertracker.resources.entry_type_purchase
import com.dmb.chantiertracker.resources.entry_type_work
import com.dmb.chantiertracker.resources.error_not_found
import com.dmb.chantiertracker.resources.material_label
import com.dmb.chantiertracker.resources.material_name_label
import com.dmb.chantiertracker.resources.material_new_option
import com.dmb.chantiertracker.resources.material_new_title
import com.dmb.chantiertracker.resources.material_unit_hint
import com.dmb.chantiertracker.resources.material_unit_label
import com.dmb.chantiertracker.resources.per_unit_suffix
import com.dmb.chantiertracker.resources.projects_retry
import com.dmb.chantiertracker.resources.purchase_line_add
import com.dmb.chantiertracker.resources.purchase_line_add_title
import com.dmb.chantiertracker.resources.purchase_line_edit_title
import com.dmb.chantiertracker.resources.purchase_lines_empty
import com.dmb.chantiertracker.resources.purchase_lines_title
import com.dmb.chantiertracker.resources.quantity_label
import com.dmb.chantiertracker.resources.stock_already_used
import com.dmb.chantiertracker.resources.stock_available
import com.dmb.chantiertracker.resources.stock_none_available
import com.dmb.chantiertracker.resources.supplier_label
import com.dmb.chantiertracker.resources.total_price_label
import com.dmb.chantiertracker.resources.unit_price_label
import com.dmb.chantiertracker.resources.validation_stock_exceeded
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DailyLogScreen(
    dailyLogLocalId: String,
    modifier: Modifier = Modifier,
    onDateResolved: (String) -> Unit = {},
    viewModel: DailyLogViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(dailyLogLocalId) { viewModel.load(dailyLogLocalId) }
    LaunchedEffect(state.detail?.date) {
        state.detail?.date?.let(onDateResolved)
    }

    Box(modifier.fillMaxSize()) {
        when {
            state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.isMissing -> Column(
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
                OutlinedButton(onClick = viewModel::retry) {
                    Text(stringResource(Res.string.projects_retry))
                }
            }
            state.detail != null -> DailyLogContent(state = state, viewModel = viewModel)
        }
    }
}

@Composable
private fun DailyLogContent(state: DailyLogUiState, viewModel: DailyLogViewModel) {
    val detail = state.detail!!
    val purchaseEntry = detail.entries.firstOrNull { it.type == EntryType.PURCHASE }
    val workEntry = detail.entries.firstOrNull { it.type == EntryType.WORK }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = detail.date,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )

        EntryCard(
            type = EntryType.PURCHASE,
            icon = ShoppingCartIcon,
            entry = purchaseEntry,
            canEdit = state.canEdit,
            onAdd = { viewModel.addEntry(EntryType.PURCHASE) },
            onEdit = { viewModel.startEditingSummary(purchaseEntry!!.localId) },
        ) {
            if (purchaseEntry != null) {
                PurchaseLinesSection(
                    entryLocalId = purchaseEntry.localId,
                    materials = state.materials,
                    lines = state.purchaseLines,
                    currency = state.currency,
                    canEdit = state.canEdit,
                    isAdmin = state.isAdmin,
                    viewModel = viewModel,
                )
            }
        }
        EntryCard(
            type = EntryType.WORK,
            icon = ConstructionIcon,
            entry = workEntry,
            canEdit = state.canEdit,
            onAdd = { viewModel.addEntry(EntryType.WORK) },
            onEdit = { viewModel.startEditingSummary(workEntry!!.localId) },
        ) {
            if (workEntry != null) {
                ConsumptionLinesSection(
                    entryLocalId = workEntry.localId,
                    stock = state.stock,
                    lines = state.consumptionLines,
                    canEdit = state.canEdit,
                    isAdmin = state.isAdmin,
                    viewModel = viewModel,
                )
            }
        }
    }

    val editingEntry = detail.entries.firstOrNull { it.localId == state.editingEntryLocalId }
    if (editingEntry != null) {
        EntrySummaryDialog(
            type = editingEntry.type,
            initialSummary = editingEntry.summary.orEmpty(),
            error = state.summaryError,
            isSubmitting = state.isSubmitting,
            onDismiss = viewModel::cancelEditingSummary,
            onSubmit = viewModel::saveSummary,
        )
    }
}

@Composable
private fun EntryCard(
    type: EntryType,
    icon: ImageVector,
    entry: DailyEntry?,
    canEdit: Boolean,
    onAdd: () -> Unit,
    onEdit: () -> Unit,
    content: @Composable () -> Unit = {},
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    text = stringResource(if (type == EntryType.WORK) Res.string.entry_type_work else Res.string.entry_type_purchase),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (entry != null) {
                val summary = entry.summary?.takeIf { it.isNotBlank() }
                Text(
                    text = summary ?: stringResource(
                        if (type == EntryType.WORK) Res.string.entry_no_title else Res.string.entry_no_summary,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (summary != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (canEdit) {
                    OutlinedButton(onClick = onEdit) { Text(stringResource(Res.string.entry_edit)) }
                }
                content()
            } else {
                Text(
                    text = stringResource(Res.string.entry_none_yet),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (canEdit) {
                    Button(onClick = onAdd) {
                        Icon(AddIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(text = stringResource(Res.string.entry_add), modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EntrySummaryDialog(
    type: EntryType,
    initialSummary: String,
    error: StringResource?,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var value by remember(initialSummary) { mutableStateOf(initialSummary) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.entry_edit_title)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(stringResource(if (type == EntryType.WORK) Res.string.entry_title_label else Res.string.entry_summary_label)) },
                isError = error != null,
                supportingText = error?.let { { Text(stringResource(it)) } },
                minLines = 3,
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(value) }, enabled = !isSubmitting) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                } else {
                    Text(stringResource(Res.string.entry_edit))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}

// ─── purchase lines ──────────────────────────────────────────────────────────

private sealed interface PurchaseLineDialogTarget {
    data object New : PurchaseLineDialogTarget
    data class Edit(val line: PurchaseLine) : PurchaseLineDialogTarget
}

@Composable
private fun PurchaseLinesSection(
    entryLocalId: String,
    materials: List<Material>,
    lines: List<PurchaseLine>,
    currency: String?,
    canEdit: Boolean,
    isAdmin: Boolean,
    viewModel: DailyLogViewModel,
) {
    var dialogTarget by remember { mutableStateOf<PurchaseLineDialogTarget?>(null) }

    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.purchase_lines_title), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (canEdit) {
                TextButton(onClick = { dialogTarget = PurchaseLineDialogTarget.New }) {
                    Icon(AddIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(text = stringResource(Res.string.purchase_line_add), modifier = Modifier.padding(start = 4.dp))
                }
            }
        }

        if (lines.isEmpty()) {
            Text(stringResource(Res.string.purchase_lines_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            lines.forEach { line ->
                val material = materials.firstOrNull { it.localId == line.materialLocalId }
                PurchaseLineRow(
                    line = line,
                    material = material,
                    currency = currency,
                    canEdit = canEdit,
                    isAdmin = isAdmin,
                    onEdit = { dialogTarget = PurchaseLineDialogTarget.Edit(line) },
                    onDelete = { viewModel.deletePurchaseLine(line.localId) },
                )
            }
        }
    }

    dialogTarget?.let { target ->
        PurchaseLineFormDialog(
            entryLocalId = entryLocalId,
            materials = materials,
            line = (target as? PurchaseLineDialogTarget.Edit)?.line,
            currency = currency,
            viewModel = viewModel,
            onDismiss = { dialogTarget = null },
        )
    }
}

@Composable
private fun PurchaseLineRow(
    line: PurchaseLine,
    material: Material?,
    currency: String?,
    canEdit: Boolean,
    isAdmin: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(material?.name ?: line.materialLocalId, style = MaterialTheme.typography.bodyMedium)
            val supplierSuffix = line.supplier?.let { " · $it" }.orEmpty()
            Text(
                text = "${formatAmount(line.quantity)} ${material?.unit.orEmpty()} · ${formatMoney(line.unitPrice, currency)} " +
                    "${stringResource(Res.string.per_unit_suffix)} · ${formatMoney(line.totalPrice, currency)}$supplierSuffix",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (canEdit) {
            IconButton(onClick = onEdit) { Icon(EditIcon, contentDescription = stringResource(Res.string.entry_edit), modifier = Modifier.size(18.dp)) }
        }
        if (isAdmin) {
            IconButton(onClick = onDelete) { Icon(DeleteIcon, contentDescription = stringResource(Res.string.action_delete), modifier = Modifier.size(18.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PurchaseLineFormDialog(
    entryLocalId: String,
    materials: List<Material>,
    line: PurchaseLine?,
    currency: String?,
    viewModel: DailyLogViewModel,
    onDismiss: () -> Unit,
) {
    val isEdit = line != null
    val lineMaterial = remember(line, materials) { materials.firstOrNull { it.localId == line?.materialLocalId } }
    var selectedMaterial by remember(line) { mutableStateOf(lineMaterial) }
    var quantity by remember(line) { mutableStateOf(line?.quantity?.let(::toInputString).orEmpty()) }
    var unitPrice by remember(line) { mutableStateOf(line?.unitPrice?.let(::toInputString).orEmpty()) }
    var supplier by remember(line) { mutableStateOf(line?.supplier.orEmpty()) }
    var materialError by remember { mutableStateOf<StringResource?>(null) }
    var quantityError by remember { mutableStateOf<StringResource?>(null) }
    var unitPriceError by remember { mutableStateOf<StringResource?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val totalPrice = (parseAmountOrNull(quantity) ?: 0.0) * (parseAmountOrNull(unitPrice) ?: 0.0)

    fun submit() {
        val matError = if (!isEdit) validateMaterialSelected(selectedMaterial?.localId) else null
        val qError = validateRequiredQuantity(quantity)
        val pError = validateRequiredUnitPrice(unitPrice)
        materialError = matError
        quantityError = qError
        unitPriceError = pError
        if (matError != null || qError != null || pError != null) return

        scope.launch {
            isSubmitting = true
            val q = parseAmountOrNull(quantity)!!
            val p = parseAmountOrNull(unitPrice)!!
            val s = supplier.trim().ifBlank { null }
            if (isEdit && line != null) {
                viewModel.updatePurchaseLine(line.localId, q, p, s)
            } else if (selectedMaterial != null) {
                viewModel.createPurchaseLine(entryLocalId, selectedMaterial!!.localId, q, p, s)
            }
            isSubmitting = false
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isEdit) Res.string.purchase_line_edit_title else Res.string.purchase_line_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isEdit) {
                    OutlinedTextField(
                        value = "${lineMaterial?.name.orEmpty()} (${lineMaterial?.unit.orEmpty()})",
                        onValueChange = {},
                        enabled = false,
                        label = { Text(stringResource(Res.string.material_label)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    MaterialPickerField(
                        materials = materials,
                        selected = selectedMaterial,
                        onSelect = { selectedMaterial = it; materialError = null },
                        onCreateNew = viewModel::createMaterial,
                        error = materialError,
                        enabled = !isSubmitting,
                    )
                }
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it; quantityError = null },
                    label = { Text(stringResource(Res.string.quantity_label)) },
                    isError = quantityError != null,
                    supportingText = quantityError?.let { { Text(stringResource(it)) } },
                    enabled = !isSubmitting,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = unitPrice,
                    onValueChange = { unitPrice = it; unitPriceError = null },
                    label = { Text(stringResource(Res.string.unit_price_label)) },
                    isError = unitPriceError != null,
                    supportingText = unitPriceError?.let { { Text(stringResource(it)) } },
                    enabled = !isSubmitting,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = supplier,
                    onValueChange = { supplier = it },
                    label = { Text(stringResource(Res.string.supplier_label)) },
                    enabled = !isSubmitting,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(Res.string.total_price_label, formatMoney(totalPrice, currency)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = ::submit, enabled = !isSubmitting) {
                if (isSubmitting) CircularProgressIndicator(modifier = Modifier.size(18.dp)) else Text(stringResource(Res.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaterialPickerField(
    materials: List<Material>,
    selected: Material?,
    onSelect: (Material) -> Unit,
    onCreateNew: suspend (name: String, unit: String) -> Material?,
    error: StringResource?,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember(selected) { mutableStateOf(selected?.name.orEmpty()) }
    var pendingName by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; expanded = true },
            label = { Text(stringResource(Res.string.material_label)) },
            isError = error != null,
            supportingText = error?.let { { Text(stringResource(it)) } },
            enabled = enabled,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        val filtered = materials.filter { it.name.contains(query, ignoreCase = true) }
        val exactMatch = materials.any { it.name.equals(query.trim(), ignoreCase = true) }
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            filtered.forEach { material ->
                DropdownMenuItem(
                    text = { Text("${material.name} (${material.unit})") },
                    onClick = { query = material.name; onSelect(material); expanded = false },
                )
            }
            if (query.isNotBlank() && !exactMatch) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.material_new_option, query.trim())) },
                    onClick = { pendingName = query.trim(); expanded = false },
                )
            }
        }
    }

    pendingName?.let { name ->
        NewMaterialDialog(
            name = name,
            onDismiss = { pendingName = null },
            onConfirm = { unit ->
                scope.launch {
                    val created = onCreateNew(name, unit)
                    if (created != null) {
                        query = created.name
                        onSelect(created)
                    }
                    pendingName = null
                }
            },
        )
    }
}

@Composable
private fun NewMaterialDialog(name: String, onDismiss: () -> Unit, onConfirm: (unit: String) -> Unit) {
    var unit by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.material_new_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = {}, enabled = false, label = { Text(stringResource(Res.string.material_name_label)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text(stringResource(Res.string.material_unit_label)) },
                    placeholder = { Text(stringResource(Res.string.material_unit_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(unit.trim()) }, enabled = unit.isNotBlank()) { Text(stringResource(Res.string.action_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}

// ─── consumption lines (stock-limited) ───────────────────────────────────────

private sealed interface ConsumptionLineDialogTarget {
    data object New : ConsumptionLineDialogTarget
    data class Edit(val line: ConsumptionLine) : ConsumptionLineDialogTarget
}

@Composable
private fun ConsumptionLinesSection(
    entryLocalId: String,
    stock: List<MaterialStock>,
    lines: List<ConsumptionLine>,
    canEdit: Boolean,
    isAdmin: Boolean,
    viewModel: DailyLogViewModel,
) {
    var dialogTarget by remember { mutableStateOf<ConsumptionLineDialogTarget?>(null) }
    val excludedMaterialIds = lines.map { it.materialLocalId }

    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.consumption_lines_title), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (canEdit) {
                TextButton(onClick = { dialogTarget = ConsumptionLineDialogTarget.New }) {
                    Icon(AddIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(text = stringResource(Res.string.consumption_line_add), modifier = Modifier.padding(start = 4.dp))
                }
            }
        }

        if (lines.isEmpty()) {
            Text(stringResource(Res.string.consumption_lines_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            lines.forEach { line ->
                val materialStock = stock.firstOrNull { it.materialLocalId == line.materialLocalId }
                ConsumptionLineRow(
                    line = line,
                    materialName = materialStock?.materialName,
                    unit = materialStock?.unit,
                    canEdit = canEdit,
                    isAdmin = isAdmin,
                    onEdit = { dialogTarget = ConsumptionLineDialogTarget.Edit(line) },
                    onDelete = { viewModel.deleteConsumptionLine(line.localId) },
                )
            }
        }
    }

    dialogTarget?.let { target ->
        val editing = (target as? ConsumptionLineDialogTarget.Edit)?.line
        ConsumptionLineFormDialog(
            entryLocalId = entryLocalId,
            stock = stock,
            excludedMaterialIds = if (editing != null) emptyList() else excludedMaterialIds,
            line = editing,
            viewModel = viewModel,
            onDismiss = { dialogTarget = null },
        )
    }
}

@Composable
private fun ConsumptionLineRow(
    line: ConsumptionLine,
    materialName: String?,
    unit: String?,
    canEdit: Boolean,
    isAdmin: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(materialName ?: line.materialLocalId, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "${formatAmount(line.quantity)} ${unit.orEmpty()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (canEdit) {
            IconButton(onClick = onEdit) { Icon(EditIcon, contentDescription = stringResource(Res.string.entry_edit), modifier = Modifier.size(18.dp)) }
        }
        if (isAdmin) {
            IconButton(onClick = onDelete) { Icon(DeleteIcon, contentDescription = stringResource(Res.string.action_delete), modifier = Modifier.size(18.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConsumptionLineFormDialog(
    entryLocalId: String,
    stock: List<MaterialStock>,
    excludedMaterialIds: List<String>,
    line: ConsumptionLine?,
    viewModel: DailyLogViewModel,
    onDismiss: () -> Unit,
) {
    val isEdit = line != null
    var selectedMaterialId by remember(line) { mutableStateOf(line?.materialLocalId) }
    var quantity by remember(line) { mutableStateOf(line?.quantity?.let(::toInputString).orEmpty()) }
    var quantityError by remember { mutableStateOf<StringResource?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val selectedStock = stock.firstOrNull { it.materialLocalId == selectedMaterialId }
    val ceiling = selectedMaterialId?.let { availableCeiling(stock, it, line?.quantity) }
    val requestedQuantity = parseAmountOrNull(quantity) ?: 0.0
    val exceedsStock = ceiling != null && requestedQuantity > ceiling
    val available = stock.filter { it.available > 0.0 }

    fun submit() {
        val materialId = selectedMaterialId
        val qError = validateRequiredQuantity(quantity)
        quantityError = qError
        if (materialId == null || qError != null || exceedsStock) return

        scope.launch {
            isSubmitting = true
            val ok = if (isEdit && line != null) {
                viewModel.updateConsumptionLine(line.localId, materialId, requestedQuantity)
            } else {
                viewModel.createConsumptionLine(entryLocalId, materialId, requestedQuantity)
            }
            isSubmitting = false
            if (ok) onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isEdit) Res.string.consumption_line_edit_title else Res.string.consumption_line_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isEdit) {
                    OutlinedTextField(
                        value = "${selectedStock?.materialName.orEmpty()} (${selectedStock?.unit.orEmpty()})",
                        onValueChange = {},
                        enabled = false,
                        label = { Text(stringResource(Res.string.material_label)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    ExposedDropdownMenuBox(expanded = expanded && !isSubmitting, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value = selectedStock?.let { "${it.materialName} (${it.unit})" }.orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(Res.string.material_label)) },
                            enabled = !isSubmitting,
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                        )
                        ExposedDropdownMenu(expanded = expanded && !isSubmitting, onDismissRequest = { expanded = false }) {
                            if (available.isEmpty()) {
                                DropdownMenuItem(text = { Text(stringResource(Res.string.stock_none_available)) }, onClick = {}, enabled = false)
                            }
                            available.forEach { s ->
                                val alreadyUsed = s.materialLocalId in excludedMaterialIds
                                val subtitle = if (alreadyUsed) {
                                    stringResource(Res.string.stock_already_used)
                                } else {
                                    stringResource(Res.string.stock_available, formatAmount(s.available), s.unit)
                                }
                                DropdownMenuItem(
                                    text = { Text("${s.materialName} (${s.unit}) — $subtitle") },
                                    enabled = !alreadyUsed,
                                    onClick = { selectedMaterialId = s.materialLocalId; expanded = false },
                                )
                            }
                        }
                    }
                }

                selectedStock?.let {
                    Text(
                        text = stringResource(Res.string.stock_available, formatAmount(ceiling ?: 0.0), it.unit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it; quantityError = null },
                    label = { Text(stringResource(Res.string.quantity_label)) },
                    isError = quantityError != null || exceedsStock,
                    supportingText = {
                        val message = when {
                            exceedsStock -> stringResource(Res.string.validation_stock_exceeded, formatAmount(ceiling ?: 0.0))
                            quantityError != null -> stringResource(quantityError!!)
                            else -> null
                        }
                        message?.let { Text(it) }
                    },
                    enabled = !isSubmitting,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = ::submit, enabled = !isSubmitting && selectedMaterialId != null && !exceedsStock) {
                if (isSubmitting) CircularProgressIndicator(modifier = Modifier.size(18.dp)) else Text(stringResource(Res.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}

private fun toInputString(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
