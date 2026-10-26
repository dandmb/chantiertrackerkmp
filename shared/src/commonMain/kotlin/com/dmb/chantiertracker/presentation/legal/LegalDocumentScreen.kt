package com.dmb.chantiertracker.presentation.legal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dmb.chantiertracker.domain.model.LegalLanguage
import com.dmb.chantiertracker.presentation.AppOutlinedButton
import com.dmb.chantiertracker.presentation.CapitalTitle
import com.dmb.chantiertracker.presentation.ResponsiveContent
import com.dmb.chantiertracker.presentation.formatIsoDate
import com.dmb.chantiertracker.presentation.i18n.LocalAppLocale
import com.dmb.chantiertracker.presentation.main.DetailTopBar
import com.dmb.chantiertracker.presentation.theme.WarningAmberContainerDark
import com.dmb.chantiertracker.presentation.theme.WarningAmberContainerLight
import com.dmb.chantiertracker.presentation.theme.WarningOnAmberDark
import com.dmb.chantiertracker.presentation.theme.WarningOnAmberLight
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.legal_loading
import com.dmb.chantiertracker.resources.legal_retry
import com.dmb.chantiertracker.resources.legal_saved_copy
import com.dmb.chantiertracker.resources.legal_unavailable_error_message
import com.dmb.chantiertracker.resources.legal_unavailable_error_title
import com.dmb.chantiertracker.resources.legal_unavailable_offline_message
import com.dmb.chantiertracker.resources.legal_unavailable_offline_title
import com.dmb.chantiertracker.resources.legal_updated_on
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun StandaloneLegalDocumentScreen(
    document: LegalDocument,
    onBack: () -> Unit,
    viewModel: LegalDocumentViewModel = koinViewModel(),
) {
    Scaffold(topBar = { DetailTopBar(title = stringResource(document.link), onBack = onBack) }) { padding ->
        LegalDocumentScreen(document, Modifier.padding(padding), viewModel)
    }
}

@Composable
fun LegalDocumentScreen(
    document: LegalDocument,
    modifier: Modifier = Modifier,
    viewModel: LegalDocumentViewModel = koinViewModel(),
) {
    val language = LegalLanguage.forLocale(LocalAppLocale.current)
    LaunchedEffect(document, language) { viewModel.load(document, language) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notice by viewModel.linkNotice.collectAsStateWithLifecycle()

    val webOpener = LocalLegalPageOpener.current ?: platformLegalPageOpener()
    val uriHandler = LocalUriHandler.current
    val mailOpener = remember(uriHandler) { LegalPageOpener { uriHandler.openUri(it) } }
    val onLink: (LegalLink) -> Unit = { viewModel.openLink(it, webOpener, mailOpener) }
    notice?.let { LegalLinkNoticeDialog(it, onDismiss = viewModel::dismissLinkNotice) }

    ResponsiveContent(modifier) {
        when (val current = state) {
            LegalDocumentUiState.Loading -> {
                val description = stringResource(Res.string.legal_loading)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.semantics { contentDescription = description })
                }
            }
            is LegalDocumentUiState.Unavailable -> LegalDocumentUnavailable(current.offline, onRetry = viewModel::retry)
            is LegalDocumentUiState.Shown -> LegalDocumentContent(current, onLink)
        }
    }
}

@Composable
private fun LegalDocumentContent(state: LegalDocumentUiState.Shown, onLink: (LegalLink) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CapitalTitle(
                text = state.text.title,
                style = MaterialTheme.typography.headlineSmall,
                isHeading = true,
            )
            Text(
                text = stringResource(Res.string.legal_updated_on, formatIsoDate(state.text.publishedOn)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.savedCopyOnly) SavedCopyNotice()
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LegalBlocks(state.blocks, onLink, Arrangement.spacedBy(12.dp))
    }
}

@Composable
private fun SavedCopyNotice() {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Surface(
        color = if (dark) WarningAmberContainerDark else WarningAmberContainerLight,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(Res.string.legal_saved_copy),
            style = MaterialTheme.typography.bodyMedium,
            color = if (dark) WarningOnAmberDark else WarningOnAmberLight,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun LegalDocumentUnavailable(offline: Boolean, onRetry: () -> Unit) {
    val (title, message) = if (offline) {
        Res.string.legal_unavailable_offline_title to Res.string.legal_unavailable_offline_message
    } else {
        Res.string.legal_unavailable_error_title to Res.string.legal_unavailable_error_message
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        CapitalTitle(
            text = stringResource(title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        AppOutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(Res.string.legal_retry))
        }
    }
}

@Composable
private fun LegalBlocks(blocks: List<LegalBlock>, onLink: (LegalLink) -> Unit, arrangement: Arrangement.Vertical) {
    Column(verticalArrangement = arrangement) {
        blocks.forEach { LegalBlockView(it, onLink) }
    }
}

@Composable
private fun LegalBlockView(block: LegalBlock, onLink: (LegalLink) -> Unit) {
    val body = MaterialTheme.typography.bodyMedium
    when (block) {
        is LegalBlock.Heading -> Text(
            text = block.text.annotated(onLink),
            style = headingStyle(block.level),
            modifier = Modifier.padding(top = if (block.level <= 2) 12.dp else 4.dp).semantics { heading() },
        )
        is LegalBlock.Paragraph -> Text(block.text.annotated(onLink), style = body)
        is LegalBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            block.items.forEachIndexed { index, item ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (block.ordered) "${block.start + index}." else "•",
                        style = body,
                        modifier = Modifier.widthIn(min = if (block.ordered) 20.dp else 8.dp),
                    )
                    Box(Modifier.weight(1f)) { LegalBlocks(item, onLink, Arrangement.spacedBy(8.dp)) }
                }
            }
        }
        is LegalBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline))
            Box(Modifier.padding(start = 12.dp)) { LegalBlocks(block.blocks, onLink, Arrangement.spacedBy(8.dp)) }
        }
        is LegalBlock.Code -> Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(block.text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.padding(12.dp))
        }
        LegalBlock.Rule -> HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun headingStyle(level: Int): TextStyle = when (level) {
    1 -> MaterialTheme.typography.titleLarge
    2 -> MaterialTheme.typography.titleMedium
    else -> MaterialTheme.typography.titleSmall
}.copy(fontWeight = FontWeight.SemiBold)

@Composable
private fun LegalInline.annotated(onLink: (LegalLink) -> Unit): AnnotatedString {
    val linkStyles = TextLinkStyles(
        SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium, textDecoration = TextDecoration.Underline),
    )
    val highlight = SpanStyle(
        background = MaterialTheme.colorScheme.primaryContainer,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        fontWeight = FontWeight.SemiBold,
    )
    return remember(this, linkStyles, highlight, onLink) {
        buildAnnotatedString {
            spans.forEach { span ->
                val style = SpanStyle(
                    fontWeight = if (span.bold) FontWeight.SemiBold else null,
                    fontStyle = if (span.italic) FontStyle.Italic else null,
                    fontFamily = if (span.code) FontFamily.Monospace else null,
                )
                val link = span.link
                if (link == null) {
                    withStyle(style) { append(span.text) }
                } else {
                    withLink(LinkAnnotation.Clickable(tag = link.target, styles = linkStyles) { onLink(link) }) {
                        withStyle(style) { append(span.text) }
                    }
                }
            }
            pendingRanges(plainText).forEach { addStyle(highlight, it.first, it.last + 1) }
        }
    }
}
