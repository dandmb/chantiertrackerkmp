package com.dmb.chantiertracker.presentation.logs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.domain.model.Attachment
import com.dmb.chantiertracker.domain.model.ConsumptionLine
import com.dmb.chantiertracker.domain.model.DailyEntry
import com.dmb.chantiertracker.domain.model.EntryType
import com.dmb.chantiertracker.domain.model.Material
import com.dmb.chantiertracker.domain.model.MaterialStock
import com.dmb.chantiertracker.domain.model.PurchaseLine
import com.dmb.chantiertracker.presentation.DetailEmptyHint
import com.dmb.chantiertracker.presentation.DetailSection
import com.dmb.chantiertracker.presentation.DetailSectionDivider
import com.dmb.chantiertracker.presentation.format.formatAmount
import com.dmb.chantiertracker.presentation.format.formatMoney
import com.dmb.chantiertracker.presentation.formatIsoDate
import com.dmb.chantiertracker.presentation.i18n.localizedText
import com.dmb.chantiertracker.presentation.main.AddIcon
import com.dmb.chantiertracker.presentation.main.FlagIcon
import com.dmb.chantiertracker.presentation.main.PlayIcon
import com.dmb.chantiertracker.presentation.main.VideocamIcon
import com.dmb.chantiertracker.presentation.main.CloseIcon
import com.dmb.chantiertracker.presentation.main.ConstructionIcon
import com.dmb.chantiertracker.presentation.main.DeleteIcon
import com.dmb.chantiertracker.presentation.main.EditIcon
import com.dmb.chantiertracker.presentation.main.ShoppingCartIcon
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.action_add
import com.dmb.chantiertracker.resources.action_delete
import com.dmb.chantiertracker.resources.attachment_add
import com.dmb.chantiertracker.resources.attachment_add_video
import com.dmb.chantiertracker.resources.attachment_close
import com.dmb.chantiertracker.resources.attachment_delete
import com.dmb.chantiertracker.resources.attachment_finalizing
import com.dmb.chantiertracker.resources.attachment_upload_error
import com.dmb.chantiertracker.resources.attachment_video_delete
import com.dmb.chantiertracker.resources.attachments_empty
import com.dmb.chantiertracker.resources.attachments_loading
import com.dmb.chantiertracker.resources.attachments_title
import com.dmb.chantiertracker.resources.video_play
import com.dmb.chantiertracker.resources.video_too_long_no_upgrade
import com.dmb.chantiertracker.resources.video_too_long_upgrade
import com.dmb.chantiertracker.resources.video_upload_in_progress
import com.dmb.chantiertracker.resources.consumption_lines_empty
import com.dmb.chantiertracker.resources.consumption_lines_title
import com.dmb.chantiertracker.resources.entry_add
import com.dmb.chantiertracker.resources.entry_edit
import com.dmb.chantiertracker.resources.entry_none_yet
import com.dmb.chantiertracker.resources.entry_no_summary
import com.dmb.chantiertracker.resources.entry_no_title
import com.dmb.chantiertracker.resources.entry_report
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
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    onReportEntry: (entryLocalId: String) -> Unit = {},
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
                onReportEntry = onReportEntry,
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
    onReportEntry: (String) -> Unit,
    onAddPurchaseLine: (String, String) -> Unit,
    onEditPurchaseLine: (String, String, String) -> Unit,
    onAddConsumptionLine: (String, String) -> Unit,
    onEditConsumptionLine: (String, String, String) -> Unit,
) {
    val detail = state.detail!!
    val purchaseEntry = detail.entries.firstOrNull { it.type == EntryType.PURCHASE }
    val workEntry = detail.entries.firstOrNull { it.type == EntryType.WORK }
    val projectId = state.projectLocalId

    // The date is already the screen title (DetailTopBar, via onDateResolved) —
    // not repeated here.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        EntrySection(
            title = stringResource(Res.string.entry_type_purchase),
            icon = ShoppingCartIcon,
            entry = purchaseEntry,
            canEdit = state.canEdit,
            canReport = state.canReport,
            emptyHint = stringResource(Res.string.entry_none_yet),
            onAdd = { viewModel.addEntry(EntryType.PURCHASE) },
            onEditSummary = { purchaseEntry?.let { onEditEntry(it.localId) } },
            onReport = { purchaseEntry?.let { onReportEntry(it.localId) } },
        ) {
            if (purchaseEntry != null && projectId != null) {
                PurchaseLinesSubSection(
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
                    state = state,
                    viewModel = viewModel,
                )
            }
        }

        DetailSectionDivider()

        EntrySection(
            title = stringResource(Res.string.entry_type_work),
            icon = ConstructionIcon,
            entry = workEntry,
            canEdit = state.canEdit,
            canReport = state.canReport,
            emptyHint = stringResource(Res.string.entry_none_yet),
            onAdd = { viewModel.addEntry(EntryType.WORK) },
            onEditSummary = { workEntry?.let { onEditEntry(it.localId) } },
            onReport = { workEntry?.let { onReportEntry(it.localId) } },
        ) {
            if (workEntry != null && projectId != null) {
                ConsumptionLinesSubSection(
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

// One of the two daily-entry blocks (Achats / Travaux). A DetailSection with a
// leading type icon: when the entry doesn't exist yet, a full-width "add" CTA +
// the empty hint; once it exists, its summary + an "edit summary" header action
// + the entry's own sub-sections (lines, attachments).
@Composable
private fun EntrySection(
    title: String,
    icon: ImageVector,
    entry: DailyEntry?,
    canEdit: Boolean,
    canReport: Boolean,
    emptyHint: String,
    onAdd: () -> Unit,
    onEditSummary: () -> Unit,
    onReport: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    DetailSection(
        title = title,
        leadingIcon = icon,
        // "Modifier" and "Signaler" are mutually exclusive (canReport already
        // requires !canEdit) — whichever the member is allowed to do sits in the
        // header's action slot.
        action = when {
            entry != null && canEdit -> {
                {
                    TextButton(onClick = onEditSummary) {
                        Icon(EditIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(stringResource(Res.string.entry_edit), Modifier.padding(start = 4.dp))
                    }
                }
            }
            entry != null && canReport -> {
                {
                    TextButton(onClick = onReport) {
                        Icon(FlagIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(stringResource(Res.string.entry_report), Modifier.padding(start = 4.dp))
                    }
                }
            }
            else -> null
        },
    ) {
        if (entry == null) {
            if (canEdit) {
                Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                    Icon(AddIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(text = stringResource(Res.string.entry_add), modifier = Modifier.padding(start = 6.dp))
                }
            }
            DetailEmptyHint(emptyHint)
        } else {
            val summary = entry.summary?.takeIf { it.isNotBlank() }
            Text(
                text = summary ?: stringResource(
                    if (entry.type == EntryType.WORK) Res.string.entry_no_title else Res.string.entry_no_summary,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = if (summary != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

// A level-2 heading inside an EntrySection (Articles achetés / Matériaux
// consommés / Justificatifs) — smaller than the section title, still clearly a
// heading, with an optional right-aligned action.
@Composable
private fun SubSection(
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            action?.invoke()
        }
        content()
    }
}

@Composable
private fun SubSectionAddAction(onAdd: () -> Unit) {
    TextButton(onClick = onAdd) {
        Icon(AddIcon, contentDescription = null, modifier = Modifier.size(16.dp))
        Text(text = stringResource(Res.string.action_add), modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun PurchaseLinesSubSection(
    materials: List<Material>,
    lines: List<PurchaseLine>,
    currency: String?,
    canEdit: Boolean,
    isAdmin: Boolean,
    onAdd: () -> Unit,
    onEdit: (PurchaseLine) -> Unit,
    onDelete: (PurchaseLine) -> Unit,
) {
    SubSection(
        title = stringResource(Res.string.purchase_lines_title),
        action = if (canEdit) {
            { SubSectionAddAction(onAdd) }
        } else {
            null
        },
    ) {
        if (lines.isEmpty()) {
            DetailEmptyHint(stringResource(Res.string.purchase_lines_empty))
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
private fun ConsumptionLinesSubSection(
    stock: List<MaterialStock>,
    lines: List<ConsumptionLine>,
    canEdit: Boolean,
    isAdmin: Boolean,
    onAdd: () -> Unit,
    onEdit: (ConsumptionLine) -> Unit,
    onDelete: (ConsumptionLine) -> Unit,
) {
    SubSection(
        title = stringResource(Res.string.consumption_lines_title),
        action = if (canEdit) {
            { SubSectionAddAction(onAdd) }
        } else {
            null
        },
    ) {
        if (lines.isEmpty()) {
            DetailEmptyHint(stringResource(Res.string.consumption_lines_empty))
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
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
    state: DailyLogUiState,
    viewModel: DailyLogViewModel,
) {
    val attachments = state.attachments
    val canEdit = state.canEdit
    var zoomedAttachment by remember { mutableStateOf<Attachment?>(null) }
    var photoReadError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val upload = state.attachmentUpload
    val busy = upload != null

    // Decode every photo thumbnail up front and reveal the whole section at once
    // (ADR-43). The data is already local (offline-first) — this only waits on
    // the file read + image decode, never the network. A decode that fails
    // (missing file) still counts as "done" so the indicator can't hang. Videos
    // draw an icon face, no decode, so they don't gate anything.
    val photoPaths = attachments.filterNot { it.isVideo }.map { it.localPath }
    val thumbnails = remember(entryLocalId) { mutableStateMapOf<String, ImageBitmap?>() }
    LaunchedEffect(photoPaths) {
        coroutineScope {
            photoPaths.filterNot { it in thumbnails }.forEach { path ->
                launch {
                    thumbnails[path] = runCatching {
                        val bytes = PlatformFile(path).readBytes()
                        withContext(Dispatchers.Default) { bytes.decodeToImageBitmap() }
                    }.getOrNull()
                }
            }
        }
    }
    val allPhotosDecoded = photoPaths.all { it in thumbnails }
    // Only gate the *first* render for this entry — a photo added afterwards is a
    // deliberate user action and just appears (its upload had its own indicator).
    var firstLoadDone by remember(entryLocalId) { mutableStateOf(false) }
    LaunchedEffect(allPhotosDecoded) { if (allPhotosDecoded) firstLoadDone = true }

    val photoPicker = rememberFilePickerLauncher(type = FileKitType.Image) { picked ->
        if (picked == null) return@rememberFilePickerLauncher
        photoReadError = false
        scope.launch {
            val mime = runCatching { picked.mimeType() }.getOrNull()
            val mimeString = mime?.let { "${it.primaryType}/${it.subtype}" } ?: "image/jpeg"
            val bytes = runCatching { picked.readBytes() }.getOrNull()
            if (bytes != null) {
                viewModel.onPhotoSelected(entryLocalId, bytes, picked.name, mimeString)
            } else {
                photoReadError = true
            }
        }
    }
    val videoPicker = rememberFilePickerLauncher(type = FileKitType.Video) { picked ->
        if (picked == null) return@rememberFilePickerLauncher
        val mime = runCatching { picked.mimeType() }.getOrNull()
        val mimeString = mime?.let { "${it.primaryType}/${it.subtype}" } ?: "video/mp4"
        // Streamed from disk inside the ViewModel — never read into a ByteArray
        // here (ADR-38).
        viewModel.onVideoSelected(entryLocalId, picked.asUploadFile(mimeString))
    }

    SubSection(title = stringResource(Res.string.attachments_title)) {
        if (canEdit) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { photoPicker.launch() }, enabled = !busy) {
                    Icon(AddIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(text = stringResource(Res.string.attachment_add), modifier = Modifier.padding(start = 4.dp))
                }
                if (state.canAddVideo) {
                    TextButton(onClick = { videoPicker.launch() }, enabled = !busy) {
                        Icon(VideocamIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(
                            text = stringResource(Res.string.attachment_add_video),
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        }

        upload?.let { up ->
            val fraction = up.fraction?.takeIf { up.stage == AttachmentUploadUi.Stage.Uploading }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (fraction != null) {
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text(
                    text = if (fraction != null) {
                        "${stringResource(Res.string.video_upload_in_progress)} ${(fraction * 100).roundToInt()}%"
                    } else {
                        stringResource(Res.string.attachment_finalizing)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (photoReadError) {
            Text(stringResource(Res.string.attachment_upload_error), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        state.videoTooLong?.let { tooLong ->
            val res = if (tooLong.hasUpgrade) Res.string.video_too_long_upgrade else Res.string.video_too_long_no_upgrade
            Text(
                stringResource(res, tooLong.actual, tooLong.limit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        state.attachmentError?.let {
            Text(it.localizedText(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        when {
            attachments.isEmpty() -> DetailEmptyHint(stringResource(Res.string.attachments_empty))
            !firstLoadDone && !allPhotosDecoded -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    stringResource(Res.string.attachments_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                attachments.forEach { attachment ->
                    AttachmentThumbnail(
                        attachment = attachment,
                        bitmap = if (attachment.isVideo) null else thumbnails[attachment.localPath],
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
private fun AttachmentThumbnail(
    attachment: Attachment,
    bitmap: ImageBitmap?,
    canEdit: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Box(Modifier.size(72.dp)) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxSize().clickable(onClick = onClick),
        ) {
            if (attachment.isVideo) {
                VideoThumbnailFace(attachment)
            } else {
                bitmap?.let { loaded ->
                    Image(
                        bitmap = loaded,
                        contentDescription = attachment.originalName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
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
                Icon(
                    DeleteIcon,
                    contentDescription = stringResource(
                        if (attachment.isVideo) Res.string.attachment_video_delete else Res.string.attachment_delete,
                    ),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun VideoThumbnailFace(attachment: Attachment) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            PlayIcon,
            contentDescription = stringResource(Res.string.video_play),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
        attachment.durationSeconds?.let { seconds ->
            Text(
                VideoLimit.formatDuration(seconds.toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
            )
        }
    }
}

// Full-screen viewer for both photos and videos (ADR-39) — the platform-default
// Dialog only wraps its content into a small centred card, uncomfortable for
// either. `usePlatformDefaultWidth = false` lets the black backdrop and the
// media fill the screen, WhatsApp-style; a tap on the backdrop dismisses.
@Composable
private fun AttachmentZoomDialog(attachment: Attachment, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("attachment-zoom")
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (attachment.isVideo) {
                // Fill the whole dialog; each platform player fits the video to
                // this space keeping its own aspect ratio (ADR-40).
                VideoPlayer(
                    localPath = attachment.localPath,
                    modifier = Modifier.fillMaxSize().testTag("zoom-media"),
                )
            } else {
                val bitmap by loadAttachmentBitmap(attachment)
                bitmap?.let { loaded ->
                    Image(
                        bitmap = loaded,
                        contentDescription = attachment.originalName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } ?: CircularProgressIndicator(color = Color.White)
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .safeDrawingPadding()
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape),
            ) {
                Icon(CloseIcon, contentDescription = stringResource(Res.string.attachment_close), tint = Color.White)
            }
        }
    }
}

// Decodes the locally-stored photo off the main thread — the file read + JPEG
// decode happen once per distinct localPath (remember keyed on it), not on every
// recomposition. Null while loading or if decoding fails. Used by the full-screen
// viewer; the thumbnail grid decodes up front in AttachmentsSection (ADR-43).
@Composable
private fun loadAttachmentBitmap(attachment: Attachment) = remember(attachment.localPath) { mutableStateOf<ImageBitmap?>(null) }.also { state ->
    LaunchedEffect(attachment.localPath) {
        state.value = runCatching {
            val bytes = PlatformFile(attachment.localPath).readBytes()
            withContext(Dispatchers.Default) { bytes.decodeToImageBitmap() }
        }.getOrNull()
    }
}
