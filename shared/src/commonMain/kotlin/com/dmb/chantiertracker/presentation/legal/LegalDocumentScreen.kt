package com.dmb.chantiertracker.presentation.legal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.presentation.ResponsiveContent
import com.dmb.chantiertracker.presentation.main.DetailTopBar
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.legal_language_notice
import com.dmb.chantiertracker.resources.legal_last_updated
import org.jetbrains.compose.resources.stringResource

@Composable
fun StandaloneLegalDocumentScreen(document: LegalDocument, onBack: () -> Unit) {
    Scaffold(topBar = { DetailTopBar(title = stringResource(document.label), onBack = onBack) }) { padding ->
        LegalDocumentScreen(document, Modifier.padding(padding))
    }
}

@Composable
fun LegalDocumentScreen(document: LegalDocument, modifier: Modifier = Modifier) {
    val valuesByToken = LegalPlaceholder.entries.associate { it.token to stringResource(it.value) }

    ResponsiveContent(modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(Res.string.legal_language_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Text(
                text = stringResource(
                    Res.string.legal_last_updated,
                    valuesByToken.getValue(LegalPlaceholder.LastUpdatedDate.token),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            document.sections.forEach { section ->
                LegalSectionBlock(
                    title = stringResource(section.title),
                    body = stringResource(section.body).withLegalPlaceholders(valuesByToken),
                )
            }
        }
    }
}

@Composable
private fun LegalSectionBlock(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        parseLegalBlocks(body).forEach { block ->
            when (block) {
                is LegalBlock.Paragraph -> Text(block.text, style = MaterialTheme.typography.bodyMedium)
                is LegalBlock.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("•", style = MaterialTheme.typography.bodyMedium)
                    Text(block.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
