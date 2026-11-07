package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.presentation.main.ChevronLeftIcon
import com.dmb.chantiertracker.presentation.main.ChevronRightIcon

private val ActionHeight = 48.dp
private val ActionGap = 8.dp

@Composable
fun OutlinedActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    iconAfterText: Boolean = false,
) {
    AppOutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = ActionHeight),
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
    ) {
        if (!iconAfterText) Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(start = if (iconAfterText) 0.dp else 8.dp, end = if (iconAfterText) 8.dp else 0.dp),
        )
        if (iconAfterText) Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun TextAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = ActionHeight),
        colors = ButtonDefaults.textButtonColors(contentColor = if (destructive) colors.error else colors.onSurface),
    ) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) {
            content()
        }
    }
}

@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, destructive: Boolean = false) {
    TextAction(onClick = onClick, modifier = modifier, enabled = enabled, destructive = destructive) { Text(text) }
}

@Composable
fun AdaptiveActions(modifier: Modifier = Modifier, gap: Dp = ActionGap, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val fitsOnOneLine = constraints.hasBoundedWidth &&
            measurables.sumOf { it.maxIntrinsicWidth(Constraints.Infinity) } + gapPx * (measurables.size - 1).coerceAtLeast(0) <= constraints.maxWidth
        if (fitsOnOneLine || !constraints.hasBoundedWidth) {
            val placeables = measurables.map { it.measure(loose) }
            val height = placeables.maxOfOrNull { it.height } ?: 0
            val width = placeables.sumOf { it.width } + gapPx * (placeables.size - 1).coerceAtLeast(0)
            layout(width.coerceAtMost(constraints.maxWidth), height) {
                var x = 0
                placeables.forEach { placeable ->
                    placeable.place(x, (height - placeable.height) / 2)
                    x += placeable.width + gapPx
                }
            }
        } else {
            val full = Constraints.fixedWidth(constraints.maxWidth)
            val placeables = measurables.map { it.measure(full) }
            val height = placeables.sumOf { it.height } + gapPx * (placeables.size - 1).coerceAtLeast(0)
            layout(constraints.maxWidth, height) {
                var y = 0
                placeables.forEach { placeable ->
                    placeable.place(0, y)
                    y += placeable.height + gapPx
                }
            }
        }
    }
}

@Composable
fun PaginationBar(
    pageLabel: String,
    previousLabel: String,
    onPrevious: () -> Unit,
    previousEnabled: Boolean,
    nextLabel: String,
    onNext: () -> Unit,
    nextEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Layout(
        contents = listOf(
            { OutlinedActionButton(previousLabel, ChevronLeftIcon, onPrevious, enabled = previousEnabled) },
            {
                Text(
                    text = pageLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            },
            { OutlinedActionButton(nextLabel, ChevronRightIcon, onNext, enabled = nextEnabled, iconAfterText = true) },
        ),
        modifier = modifier,
    ) { (previous, label, next), constraints ->
        val gapPx = ActionGap.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val parts = listOf(previous.single(), label.single(), next.single())
        val needed = parts.sumOf { it.maxIntrinsicWidth(Constraints.Infinity) } + gapPx * 2
        if (!constraints.hasBoundedWidth || needed <= constraints.maxWidth) {
            val (p, l, n) = parts.map { it.measure(loose) }
            val width = if (constraints.hasBoundedWidth) constraints.maxWidth else p.width + l.width + n.width + gapPx * 2
            val height = maxOf(p.height, l.height, n.height)
            layout(width, height) {
                p.place(0, (height - p.height) / 2)
                l.place((width - l.width) / 2, (height - l.height) / 2)
                n.place(width - n.width, (height - n.height) / 2)
            }
        } else {
            stacked(parts, constraints.maxWidth, gapPx)
        }
    }
}

private fun androidx.compose.ui.layout.MeasureScope.stacked(parts: List<Measurable>, width: Int, gapPx: Int) =
    parts.let { (previous, label, next) ->
        val full = Constraints.fixedWidth(width)
        val l = label.measure(Constraints(maxWidth = width))
        val p = previous.measure(full)
        val n = next.measure(full)
        val height = l.height + p.height + n.height + gapPx * 2
        layout(width, height) {
            l.place((width - l.width) / 2, 0)
            p.place(0, l.height + gapPx)
            n.place(0, l.height + gapPx + p.height + gapPx)
        }
    }
