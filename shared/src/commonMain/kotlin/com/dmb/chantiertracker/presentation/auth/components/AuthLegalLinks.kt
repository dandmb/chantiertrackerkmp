package com.dmb.chantiertracker.presentation.auth.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.presentation.legal.LegalDocument
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.register_legal_consent
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthLegalFooter(onOpenDocument: (LegalDocument) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.Center,
    ) {
        LegalDocument.entries.forEach { document ->
            Text(
                text = stringResource(document.link),
                modifier = Modifier
                    .clickable(role = Role.Button) { onOpenDocument(document) }
                    .padding(horizontal = 4.dp, vertical = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = TextDecoration.Underline,
            )
        }
    }
}

@Composable
fun RegisterLegalConsent(onOpenDocument: (LegalDocument) -> Unit, modifier: Modifier = Modifier) {
    val termsLabel = stringResource(LegalDocument.TermsOfUse.label)
    val privacyLabel = stringResource(LegalDocument.PrivacyPolicy.label)
    val sentence = stringResource(Res.string.register_legal_consent, termsLabel, privacyLabel)
    val linkStyles = TextLinkStyles(
        SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            textDecoration = TextDecoration.Underline,
        ),
    )

    val text = buildAnnotatedString {
        append(sentence)
        listOf(termsLabel to LegalDocument.TermsOfUse, privacyLabel to LegalDocument.PrivacyPolicy).forEach { (label, document) ->
            val start = sentence.indexOf(label)
            addLink(
                LinkAnnotation.Clickable(tag = document.name, styles = linkStyles) { onOpenDocument(document) },
                start,
                start + label.length,
            )
        }
    }
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
