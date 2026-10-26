package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.getTextLayoutResult
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

// Titles in capitals like the web's text-transform: shown upper-cased, yet screen readers (and
// the UI tests) still get the original wording, so nothing is read letter by letter.
@Composable
fun CapitalTitle(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    isHeading: Boolean = false,
) {
    val laidOut = remember { arrayOfNulls<TextLayoutResult>(1) }
    Box(
        propagateMinConstraints = true,
        modifier = modifier.clearAndSetSemantics {
            this.text = AnnotatedString(text)
            if (isHeading) heading()
            getTextLayoutResult { results -> laidOut[0]?.let { results.add(it) } != null }
        },
    ) {
        Text(
            text = text.uppercase(),
            style = style,
            color = color,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = overflow,
            onTextLayout = { laidOut[0] = it },
        )
    }
}
