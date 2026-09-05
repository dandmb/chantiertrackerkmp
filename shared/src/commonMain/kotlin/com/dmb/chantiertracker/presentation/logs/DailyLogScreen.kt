package com.dmb.chantiertracker.presentation.logs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.domain.model.Attachment
import com.dmb.chantiertracker.domain.model.ConsumptionLine
import com.dmb.chantiertracker.domain.model.DailyEntry
import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.domain.model.Material
import com.dmb.chantiertracker.domain.model.MaterialStock
import com.dmb.chantiertracker.domain.model.PurchaseLine
import com.dmb.chantiertracker.presentation.format.formatAmount
import com.dmb.chantiertracker.presentation.format.formatMoney
import com.dmb.chantiertracker.presentation.formatIsoDate
import com.dmb.chantiertracker.presentation.main.AddIcon
import com.dmb.chantiertracker.presentation.main.CloseIcon
import com.dmb.chantiertracker.presentation.main.ConstructionIcon
import com.dmb.chantiertracker.presentation.main.DeleteIcon
import com.dmb.chantiertracker.presentation.main.EditIcon
import com.dmb.chantiertracker.presentation.main.ShoppingCartIcon
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.action_add
import com.dmb.chantiertracker.resources.action_delete
import com.dmb.chantiertracker.resources.attachment_add
import com.dmb.chantiertracker.resources.attachment_close
import com.dmb.chantiertracker.resources.attachment_delete
import com.dmb.chantiertracker.resources.attachment_upload_error
import com.dmb.chantiertracker.resources.attachments_empty
import com.dmb.chantiertracker.resources.attachments_title
import com.dmb.chantiertracker.resources.consumption_lines_empty
import com.dmb.chantiertracker.resources.consumption_lines_title
import com.dmb.chantiertracker.resources.entry_add
import com.dmb.chantiertracker.resources.entry_edit
import com.dmb.chantiertracker.resources.entry_none_yet
import com.dmb.chantiertracker.resources.entry_no_summary
import com.dmb.chantiertracker.resources.entry_no_title
import com.dmb.chantiertracker.resources.entry_type_purchase
import com.dmb.chantiertracker.resources.entry_type_work
import com.dmb.chantiertracker.resources.error_not_found
import com.dmb.chantiertracker.resources.per_unit_suffix
import com.dmb.chantiertracker.resources.projects_retry
import com.dmb.chantiertracker.resources.purchase_lines_empty
import com.dmb.chantiertracker.resources.purchase_lines_title
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.mimeType
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DailyLogScreen(
    dailyLogLocalId: String,
    modifier: Modifier = Modifier,
    onDateResolved: (String) -> Unit = {},
    onEditEntry: (entryLocalId: String) -> Unit = {},
    onAddPurchaseLine: (entryLocalId: String, projectLocalId: String, currency: String?) -> Unit = { _, _, _ -> },
    onEditPurchaseLine: (entryLocalId: String, projectLocalId: String, lineLocalId: String, currency: String?) -> Unit = { _, _, _, _ -> },
    onAddConsumptionLine: (entryLocalId: String, projectLocalId: String) -> Unit = { _, _ -> },
    onEditConsumptionLine: (entryLocalId: String, projectLocalId: String, lineLocalId: String) -> Unit = { _, _, _ -> },
    viewModel: DailyLogViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(dailyLogLocalId) { viewModel.load(dailyLogLocalId) }
    LaunchedEffect(state.detail?.date) {
        state.detail?.date?.let { onDateResolved(formatIsoDate(it)) }
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
                OutlinedButton(onClick = viewModel::retry) { Text(stringResource(Res.string.projects_retry)) }
            }
            state.detail != null -> DailyLogContent(
                state = state,
                viewModel = viewModel,
                onEditEntry = onEditEntry,
                onAddPurchaseLine = { entryId, projectId -> onAddPurchaseLine(entryId, projectId, state.currency) },
                onEditPurchaseLine = { entryId, projectId, lineId -> onEditPurchaseLine(entryId, projectId, lineId, state.currency) },
                onAddConsumptionLine = onAddConsumptionLine,
                onEditConsumptionLine = onEditConsumptionLine,
            )
        }
    }
}

@Composable
private fun DailyLogContent(
    state: DailyLogUiState,
    viewModel: DailyLogViewModel,
    onEditEntry: (String) -> Unit,
    onAddPurchaseLine: (String, String) -> Unit,
    onEditPurchaseLine: (String, String, String) -> Unit,
    onAddConsumptionLine: (String, String) -> Unit,
    onEditConsumptionLine: (String, String, String) -> Unit,
) {
    val detail = state.detail!!
    val purchaseEntry = detail.entries.firstOrNull { it.type == EntryType.PURCHASE }
    val workEntry = detail.entries.firstOrNull { it.type == EntryType.WORK }
    val projectId = state.projectLocalId

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = formatIsoDate(detail.date),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )

        EntryCard(
            type = EntryType.PURCHASE,
            icon = ShoppingCartIcon,
            entry = purchaseEntry,
            canEdit = state.canEdit,
            onAdd = { viewModel.addEntry(EntryType.PURCHASE) },
            onEdit = { purchaseEntry?.let { onEditEntry(it.localId) } },
        ) {
            if (purchaseEntry != null && projectId != null) {
                PurchaseLinesSection(
                    materials = state.materials,
                    lines = state.purchaseLines,
                    currency = state.currency,
                    canEdit = state.canEdit,
                    isAdmin = state.isAdmin,
                    onAdd = { onAddPurchaseLine(purchaseEntry.localId, projectId) },
                    onEdit = { line -> onEditPurchaseLine(purchaseEntry.localId, projectId, line.localId) },
                    onDelete = { line -> viewModel.deletePurchaseLine(line.localId) },
                )
                AttachmentsSection(
                    entryLocalId = purchaseEntry.localId,
                    attachments = state.attachments,
                    canEdit = state.canEdit,
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
            onEdit = { workEntry?.let { onEditEntry(it.localId) } },
        ) {
            if (workEntry != null && projectId != null) {
                ConsumptionLinesSection(
                    stock = state.stock,
                    lines = state.consumptionLines,
                    canEdit = state.canEdit,
                    isAdmin = state.isAdmin,
                    onAdd = { onAddConsumptionLine(workEntry.localId, projectId) },
                    onEdit = { line -> onEditConsumptionLine(workEntry.localId, projectId, line.localId) },
                    onDelete = { line -> viewModel.deleteConsumptionLine(line.localId) },
                )
            }
        }
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
                    Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                        Icon(AddIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(text = stringResource(Res.string.entry_add), modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, canEdit: Boolean, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (canEdit) {
            TextButton(onClick = onAdd) {
                Icon(AddIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(text = stringResource(Res.string.action_add), modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

@Composable
private fun PurchaseLinesSection(
    materials: List<Material>,
    lines: List<PurchaseLine>,
    currency: String?,
    canEdit: Boolean,
    isAdmin: Boolean,
    onAdd: () -> Unit,
    onEdit: (PurchaseLine) -> Unit,
    onDelete: (PurchaseLine) -> Unit,
) {
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(Res.string.purchase_lines_title), canEdit, onAdd)

        if (lines.isEmpty()) {
            Text(stringResource(Res.string.purchase_lines_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            lines.forEach { line ->
                val material = materials.firstOrNull { it.localId == line.materialLocalId }
                LineRow(
                    title = material?.name ?: line.materialLocalId,
                    subtitle = buildString {
                        append("${formatAmount(line.quantity)} ${material?.unit.orEmpty()} · ")
                        append("${formatMoney(line.unitPrice, currency)} ${stringResource(Res.string.per_unit_suffix)} · ")
                        append(formatMoney(line.totalPrice, currency))
                        line.supplier?.let { append(" · $it") }
                    },
                    canEdit = canEdit,
                    isAdmin = isAdmin,
                    onEdit = { onEdit(line) },
                    onDelete = { onDelete(line) },
                )
            }
        }
    }
}

@Composable
private fun ConsumptionLinesSection(
    stock: List<MaterialStock>,
    lines: List<ConsumptionLine>,
    canEdit: Boolean,
    isAdmin: Boolean,
    onAdd: () -> Unit,
    onEdit: (ConsumptionLine) -> Unit,
    onDelete: (ConsumptionLine) -> Unit,
) {
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(Res.string.consumption_lines_title), canEdit, onAdd)

        if (lines.isEmpty()) {
            Text(stringResource(Res.string.consumption_lines_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            lines.forEach { line ->
                val materialStock = stock.firstOrNull { it.materialLocalId == line.materialLocalId }
                LineRow(
                    title = materialStock?.materialName ?: line.materialLocalId,
                    subtitle = "${formatAmount(line.quantity)} ${materialStock?.unit.orEmpty()}",
                    canEdit = canEdit,
                    isAdmin = isAdmin,
                    onEdit = { onEdit(line) },
                    onDelete = { onDelete(line) },
                )
            }
        }
    }
}

@Composable
private fun LineRow(
    title: String,
    subtitle: String,
    canEdit: Boolean,
    isAdmin: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (canEdit) {
            IconButton(onClick = onEdit) { Icon(EditIcon, contentDescription = stringResource(Res.string.entry_edit), modifier = Modifier.size(18.dp)) }
        }
        if (isAdmin) {
            IconButton(onClick = onDelete) { Icon(DeleteIcon, contentDescription = stringResource(Res.string.action_delete), modifier = Modifier.size(18.dp)) }
        }
    }
}

// ─── attachments (justificatifs — PURCHASE entry only) ────────────────────────

@Composable
private fun AttachmentsSection(
    entryLocalId: String,
    attachments: List<Attachment>,
    canEdit: Boolean,
    viewModel: DailyLogViewModel,
) {
    var zoomedAttachment by remember { mutableStateOf<Attachment?>(null) }
    var uploadError by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val pickerLauncher = rememberFilePickerLauncher(type = FileKitType.Image) { picked ->
        if (picked == null) return@rememberFilePickerLauncher
        scope.launch {
            isUploading = true
            uploadError = false
            runCatching {
                val bytes = picked.readBytes()
                val mime = runCatching { picked.mimeType() }.getOrNull()
                val mimeString = mime?.let { "${it.primaryType}/${it.subtype}" } ?: "image/jpeg"
                viewModel.addAttachment(entryLocalId, bytes, picked.name, mimeString)
            }.onFailure { uploadError = true }
            isUploading = false
        }
    }

    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.attachments_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (canEdit) {
                TextButton(onClick = { pickerLauncher.launch() }, enabled = !isUploading) {
                    if (isUploading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Icon(AddIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Text(text = stringResource(Res.string.attachment_add), modifier = Modifier.padding(start = 4.dp))
                }
            }
        }

        if (uploadError) {
            Text(stringResource(Res.string.attachment_upload_error), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        if (attachments.isEmpty()) {
            Text(stringResource(Res.string.attachments_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                attachments.forEach { attachment ->
                    AttachmentThumbnail(
                        attachment = attachment,
                        canEdit = canEdit,
                        onClick = { zoomedAttachment = attachment },
                        onDelete = { viewModel.deleteAttachment(attachment.localId) },
                    )
                }
            }
        }
    }

    zoomedAttachment?.let { attachment ->
        AttachmentZoomDialog(attachment = attachment, onDismiss = { zoomedAttachment = null })
    }
}

@Composable
private fun AttachmentThumbnail(attachment: Attachment, canEdit: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    val bitmap by loadAttachmentBitmap(attachment)

    Box(Modifier.size(72.dp)) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxSize().clickable(onClick = onClick),
        ) {
            val loaded = bitmap
            if (loaded != null) {
                Image(
                    bitmap = loaded,
                    contentDescription = attachment.originalName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (canEdit) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(22.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), CircleShape),
            ) {
                Icon(DeleteIcon, contentDescription = stringResource(Res.string.attachment_delete), modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun AttachmentZoomDialog(attachment: Attachment, onDismiss: () -> Unit) {
    val bitmap by loadAttachmentBitmap(attachment)

    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth()) {
            val loaded = bitmap
            if (loaded != null) {
                Image(
                    bitmap = loaded,
                    contentDescription = attachment.originalName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                CircularProgressIndicator(Modifier.align(Alignment.Center).padding(48.dp))
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(MaterialTheme.colorScheme.surface, CircleShape),
            ) {
                Icon(CloseIcon, contentDescription = stringResource(Res.string.attachment_close))
            }
        }
    }
}

// Decodes the locally-stored photo off the main flow of composition — the file
// I/O + JPEG decode happen once per distinct localPath (remember keyed on it),
// not on every recomposition. Null while loading or if decoding fails.
@Composable
private fun loadAttachmentBitmap(attachment: Attachment) = remember(attachment.localPath) { mutableStateOf<ImageBitmap?>(null) }.also { state ->
    LaunchedEffect(attachment.localPath) {
        state.value = runCatching { PlatformFile(attachment.localPath).readBytes().decodeToImageBitmap() }.getOrNull()
    }
}
