package com.dmb.chantiertracker.presentation

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.dmb.chantiertracker.presentation.main.AddIcon
import com.dmb.chantiertracker.presentation.theme.BandColors
import com.dmb.chantiertracker.presentation.theme.DisabledFill
import com.dmb.chantiertracker.presentation.theme.DisabledText
import com.dmb.chantiertracker.presentation.theme.LocalSectionColors

enum class SectionTone { Neutral, Orange, Lemon, Salmon, Lilac }

private val CardShape = RoundedCornerShape(20.dp)
private val CardBorderWidth = 1.dp
private val HeaderGap = 12.dp
private val ActionShape = RoundedCornerShape(10.dp)

private val LocalBand = staticCompositionLocalOf<BandColors?> { null }

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: SectionTone = SectionTone.Neutral,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
    flush: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalSectionColors.current
    val band = colors.band(tone)
    val subtitleColor = if (tone == SectionTone.Neutral) colors.bandSubtitle else band.content
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(colors.card)
            .border(CardBorderWidth, colors.cardBorder, CardShape),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(band.fill)
                .heightIn(min = 58.dp)
                .padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            CompositionLocalProvider(LocalBand provides band) {
                TitleThenAction(
                    title = {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            icon?.let { Icon(it, contentDescription = null, tint = band.content, modifier = Modifier.size(22.dp)) }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                CapitalTitle(
                                    text = title,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = band.content,
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
        if (tone == SectionTone.Neutral) {
            Box(Modifier.fillMaxWidth().height(CardBorderWidth).background(colors.cardBorder))
        }
        if (flush) {
            Column(Modifier.fillMaxWidth(), content = content)
        } else {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable
fun SectionRow(modifier: Modifier = Modifier, divider: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        if (divider) Box(Modifier.fillMaxWidth().height(CardBorderWidth).background(LocalSectionColors.current.cardBorder))
    }
}

@Composable
fun SectionProgressBar(progress: Float, modifier: Modifier = Modifier, atLimit: Boolean = false) {
    val colors = LocalSectionColors.current
    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(percent = 50)),
        color = if (atLimit) MaterialTheme.colorScheme.error else colors.progressFill,
        trackColor = colors.progressTrack,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

@Composable
fun SectionChip(text: String, modifier: Modifier = Modifier, container: Color = Color.Unspecified, content: Color = Color.Unspecified) {
    val colors = LocalSectionColors.current
    Surface(
        modifier = modifier,
        color = container.takeOrElse { colors.chip },
        contentColor = content.takeOrElse { colors.onChip },
        shape = RoundedCornerShape(percent = 50),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
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
    val band = LocalBand.current ?: LocalSectionColors.current.neutralBand
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ActionShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = band.actionFill,
            contentColor = band.actionIcon,
            disabledContainerColor = band.actionFill.copy(alpha = DISABLED_ALPHA),
            disabledContentColor = band.actionIcon.copy(alpha = DISABLED_ALPHA),
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = Modifier.heightIn(min = 34.dp),
    ) {
        icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = if (icon != null) 6.dp else 0.dp))
    }
}

private const val DISABLED_ALPHA = 0.5f

@Composable
fun CreateFab(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalSectionColors.current
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = text },
        shape = RoundedCornerShape(percent = 50),
        containerColor = colors.createButton,
        contentColor = colors.onCreateButton,
        icon = { Icon(AddIcon, contentDescription = null) },
        text = { Text(text, style = MaterialTheme.typography.titleMedium) },
    )
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
        modifier = modifier.heightIn(min = 52.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.createButton,
            contentColor = colors.onCreateButton,
            disabledContainerColor = DisabledFill,
            disabledContentColor = DisabledText,
        ),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
    ) {
        icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(20.dp)) }
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = if (icon != null) 8.dp else 0.dp))
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
                    cornerRadius = CornerRadius(14.dp.toPx()),
                )
            }
            .padding(vertical = 24.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = colors.emptyText, textAlign = TextAlign.Center)
    }
}
