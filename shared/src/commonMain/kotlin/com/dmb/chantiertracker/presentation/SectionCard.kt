package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.dmb.chantiertracker.presentation.main.AddIcon
import com.dmb.chantiertracker.presentation.theme.LocalSectionColors

enum class SectionTone { Yellow, Ink }

private val CardShape = RoundedCornerShape(2.dp)
private val CardBorderWidth = 2.dp
private val HeaderGap = 12.dp

private val LocalBandContent = staticCompositionLocalOf { Color.Unspecified }

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: SectionTone = SectionTone.Ink,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalSectionColors.current
    val band = if (tone == SectionTone.Yellow) colors.yellowBand else colors.inkBand
    val onBand = if (tone == SectionTone.Yellow) colors.onYellowBand else colors.onInkBand
    val iconTint = if (tone == SectionTone.Yellow) colors.onYellowBand else colors.inkBandIcon
    val subtitleColor = if (tone == SectionTone.Yellow) colors.yellowBandSubtitle else colors.inkBandSubtitle
    Column(
        modifier
            .fillMaxWidth()
            .border(CardBorderWidth, colors.cardBorder, CardShape)
            .background(colors.card, CardShape)
            .padding(CardBorderWidth),
    ) {
        Box(Modifier.fillMaxWidth().background(band).padding(horizontal = 16.dp, vertical = 12.dp)) {
            CompositionLocalProvider(LocalBandContent provides onBand) {
                TitleThenAction(
                    title = {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            icon?.let { Icon(it, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp)) }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                CapitalTitle(
                                    text = title,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = onBand,
                                    modifier = Modifier.semantics { heading() },
                                )
                                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = subtitleColor) }
                            }
                        }
                    },
                    action = action,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(CardBorderWidth).background(colors.bandRule))
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun TitleThenAction(title: @Composable () -> Unit, action: (@Composable () -> Unit)?) {
    if (action == null) {
        title()
        return
    }
    Layout(contents = listOf(title, action)) { (titleMeasurables, actionMeasurables), constraints ->
        val gap = HeaderGap.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val actionPlaceable = actionMeasurables.single().measure(loose)
        val titleMeasurable = titleMeasurables.single()
        val besideWidth = constraints.maxWidth - actionPlaceable.width - gap
        val fitsBeside = besideWidth > 0 && titleMeasurable.maxIntrinsicWidth(Constraints.Infinity) <= besideWidth
        if (fitsBeside) {
            val titlePlaceable = titleMeasurable.measure(loose.copy(maxWidth = besideWidth))
            val height = maxOf(titlePlaceable.height, actionPlaceable.height)
            layout(constraints.maxWidth, height) {
                titlePlaceable.place(0, (height - titlePlaceable.height) / 2)
                actionPlaceable.place(constraints.maxWidth - actionPlaceable.width, (height - actionPlaceable.height) / 2)
            }
        } else {
            val titlePlaceable = titleMeasurable.measure(loose)
            val stackedGap = gap / 2
            layout(constraints.maxWidth, titlePlaceable.height + stackedGap + actionPlaceable.height) {
                titlePlaceable.place(0, 0)
                actionPlaceable.place(0, titlePlaceable.height + stackedGap)
            }
        }
    }
}

@Composable
fun SectionHeaderAction(text: String, onClick: () -> Unit, icon: ImageVector? = null, enabled: Boolean = true) {
    val content = LocalBandContent.current.takeOrElse { LocalSectionColors.current.onSecondaryButton }
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        border = BorderStroke(CardBorderWidth, content),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = content),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
    ) {
        icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(16.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = if (icon != null) 6.dp else 0.dp))
    }
}

@Composable
fun SectionPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = AddIcon,
    enabled: Boolean = true,
) {
    val colors = LocalSectionColors.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = colors.primaryButton, contentColor = colors.onPrimaryButton),
    ) {
        icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) }
        Text(text, modifier = Modifier.padding(start = if (icon != null) 6.dp else 0.dp))
    }
}

@Composable
fun SectionSubheading(text: String, modifier: Modifier = Modifier) {
    CapitalTitle(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.1.em),
        color = LocalSectionColors.current.subheading,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
fun DetailInfoRow(label: String, value: String, muted: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun DetailEmptyHint(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodyMedium, color = LocalSectionColors.current.emptyText)
}

@Composable
fun DashedEmptyHint(text: String) {
    val colors = LocalSectionColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val width = 2.dp.toPx()
                drawRoundRect(
                    color = colors.dashedBorder,
                    style = Stroke(width = width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
            .padding(vertical = 24.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = colors.emptyText, textAlign = TextAlign.Center)
    }
}
