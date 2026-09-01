package com.dmb.chantiertracker.presentation.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.abs
import kotlin.math.sin

private const val VIEW = 100f

private class Palette(val strong: Color, val soft: Color, val faint: Color, val accent: Color)

@Composable
private fun palette(): Palette {
    val scheme = MaterialTheme.colorScheme
    return Palette(
        strong = scheme.primary,
        soft = scheme.primary.copy(alpha = 0.45f),
        faint = scheme.primary.copy(alpha = 0.14f),
        accent = scheme.tertiary,
    )
}

private fun DrawScope.frame(): Pair<Float, (Float, Float) -> Offset> {
    val scale = size.minDimension / VIEW
    val ox = (size.width - VIEW * scale) / 2f
    val oy = (size.height - VIEW * scale) / 2f
    return scale to { x, y -> Offset(x * scale + ox, y * scale + oy) }
}

private fun triangleWave(t: Float): Float = 1f - abs(2f * (t % 1f) - 1f)

@Composable
fun RemoteSiteIllustration(modifier: Modifier = Modifier, entrance: Float = 1f, loop: Float = 0f) {
    val pal = palette()
    Canvas(modifier.aspectRatio(1f)) {
        val (scale, p) = frame()
        val line = 2.4f * scale
        val rise = (1f - entrance) * 26f

        translate(top = rise * scale) {
            drawRect(pal.faint, topLeft = p(14f, 58f), size = Size(20f * scale, 30f * scale))
            drawRect(pal.faint, topLeft = p(34f, 46f), size = Size(22f * scale, 42f * scale))
            gridWindows(p, 36f, 50f, 54f, 84f, scale, pal.soft)

            drawLine(pal.strong, p(66f, 88f), p(66f, 26f), strokeWidth = line, cap = StrokeCap.Round)
            drawLine(pal.strong, p(72f, 88f), p(72f, 26f), strokeWidth = line, cap = StrokeCap.Round)
            var y = 88f
            var flip = true
            while (y > 30f) {
                val n = y - 10f
                if (flip) drawLine(pal.soft, p(66f, y), p(72f, n), strokeWidth = line * 0.6f)
                else drawLine(pal.soft, p(72f, y), p(66f, n), strokeWidth = line * 0.6f)
                drawLine(pal.soft, p(66f, n), p(72f, n), strokeWidth = line * 0.6f)
                y = n
                flip = !flip
            }
            drawLine(pal.strong, p(40f, 26f), p(90f, 26f), strokeWidth = line, cap = StrokeCap.Round)
            drawLine(pal.strong, p(69f, 16f), p(69f, 26f), strokeWidth = line, cap = StrokeCap.Round)
            drawLine(pal.soft, p(69f, 16f), p(88f, 26f), strokeWidth = line * 0.7f)
            drawLine(pal.soft, p(69f, 16f), p(46f, 26f), strokeWidth = line * 0.7f)

            val hook = 30f + triangleWave(loop) * 8f
            drawLine(pal.soft, p(82f, 26f), p(82f, hook), strokeWidth = line * 0.7f)
            drawRect(pal.strong, topLeft = p(80f, hook), size = Size(4f * scale, 4f * scale))
        }

        val center = p(69f, 20f)
        for (i in 0 until 3) {
            val prog = (loop + i / 3f) % 1f
            val radius = prog * 46f * scale
            val alpha = (1f - prog).coerceIn(0f, 1f) * 0.5f * entrance
            if (radius > 1f) {
                drawCircle(
                    color = pal.accent.copy(alpha = alpha),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 2f * scale),
                )
            }
        }
    }
}

@Composable
fun PhotoProofIllustration(modifier: Modifier = Modifier, entrance: Float = 1f, loop: Float = 0f) {
    val pal = palette()
    Canvas(modifier.aspectRatio(1f)) {
        val (scale, p) = frame()
        val line = 2.4f * scale
        val pop = 0.9f + entrance * 0.1f

        scale(pop, pop, pivot = p(50f, 52f)) {
            drawRoundedStroke(p(16f, 32f), p(84f, 82f), 6f * scale, pal.strong, line)
            drawRect(pal.strong, topLeft = p(34f, 24f), size = Size(20f * scale, 10f * scale))
            drawLine(pal.strong, p(64f, 24f), p(72f, 24f), strokeWidth = line, cap = StrokeCap.Round)

            val lens = p(50f, 57f)
            drawCircle(pal.soft, radius = 15f * scale, center = lens, style = Stroke(width = line))
            val aperture = 8.5f * scale + sin(loop * 6.2832f) * 1.6f * scale
            drawCircle(pal.strong, radius = aperture, center = lens, style = Stroke(width = line * 0.8f))

            val flash = triangleWave(loop * 2f)
            drawCircle(
                color = pal.accent.copy(alpha = 0.25f + 0.6f * flash),
                radius = 3.6f * scale,
                center = p(24f, 40f),
            )
        }

        val b = 5f * scale
        val breathe = (0.5f + 0.5f * triangleWave(loop)) * entrance
        val inset = (6f + breathe * 5f) * scale
        val r = Rect(p(16f, 32f).x + inset, p(16f, 32f).y + inset, p(84f, 82f).x - inset, p(84f, 82f).y - inset)
        val bracket = pal.accent.copy(alpha = 0.35f + 0.45f * breathe)
        drawLine(bracket, Offset(r.left, r.top), Offset(r.left + b, r.top), strokeWidth = line * 0.8f, cap = StrokeCap.Round)
        drawLine(bracket, Offset(r.left, r.top), Offset(r.left, r.top + b), strokeWidth = line * 0.8f, cap = StrokeCap.Round)
        drawLine(bracket, Offset(r.right, r.top), Offset(r.right - b, r.top), strokeWidth = line * 0.8f, cap = StrokeCap.Round)
        drawLine(bracket, Offset(r.right, r.top), Offset(r.right, r.top + b), strokeWidth = line * 0.8f, cap = StrokeCap.Round)
        drawLine(bracket, Offset(r.left, r.bottom), Offset(r.left + b, r.bottom), strokeWidth = line * 0.8f, cap = StrokeCap.Round)
        drawLine(bracket, Offset(r.left, r.bottom), Offset(r.left, r.bottom - b), strokeWidth = line * 0.8f, cap = StrokeCap.Round)
        drawLine(bracket, Offset(r.right, r.bottom), Offset(r.right - b, r.bottom), strokeWidth = line * 0.8f, cap = StrokeCap.Round)
        drawLine(bracket, Offset(r.right, r.bottom), Offset(r.right, r.bottom - b), strokeWidth = line * 0.8f, cap = StrokeCap.Round)
    }
}

@Composable
fun BudgetIllustration(modifier: Modifier = Modifier, entrance: Float = 1f, loop: Float = 0f) {
    val pal = palette()
    Canvas(modifier.aspectRatio(1f)) {
        val (scale, p) = frame()
        val line = 2.4f * scale
        val baseY = 82f

        drawLine(pal.soft, p(14f, baseY), p(88f, baseY), strokeWidth = line, cap = StrokeCap.Round)

        val heights = floatArrayOf(20f, 34f, 28f, 48f)
        heights.forEachIndexed { i, h ->
            val wobble = sin((loop * 6.2832f) + i) * 2.4f
            val grown = (h + wobble) * entrance
            val x = 20f + i * 17f
            val fill = if (i == heights.lastIndex) pal.strong else pal.faint
            drawRect(fill, topLeft = p(x, baseY - grown), size = Size(11f * scale, grown * scale))
            if (i == heights.lastIndex) {
                drawRoundedStroke(p(x, baseY - grown), p(x + 11f, baseY), 1.5f * scale, pal.strong, line * 0.7f)
            }
        }

        val trend = Path()
        val pts = listOf(p(18f, 66f), p(35f, 54f), p(52f, 58f), p(78f, 34f))
        trend.moveTo(pts[0].x, pts[0].y)
        val reveal = entrance
        for (i in 1 until pts.size) {
            val prev = pts[i - 1]
            val cur = pts[i]
            val seg = ((reveal * (pts.size - 1)) - (i - 1)).coerceIn(0f, 1f)
            trend.lineTo(prev.x + (cur.x - prev.x) * seg, prev.y + (cur.y - prev.y) * seg)
        }
        drawPath(trend, color = pal.accent, style = Stroke(width = line, cap = StrokeCap.Round))
        if (reveal > 0.98f) {
            val tip = pts.last()
            drawLine(pal.accent, tip, Offset(tip.x - 7f * scale, tip.y + 1f * scale), strokeWidth = line, cap = StrokeCap.Round)
            drawLine(pal.accent, tip, Offset(tip.x - 1f * scale, tip.y + 7f * scale), strokeWidth = line, cap = StrokeCap.Round)
        }

        val coin = p(24f, 24f)
        val bob = sin(loop * 6.2832f) * 2f * scale
        translate(top = bob) {
            drawCircle(pal.strong, radius = 8f * scale, center = coin, style = Stroke(width = line))
            drawLine(pal.strong, p(24f, 18f), p(24f, 30f), strokeWidth = line * 0.7f, cap = StrokeCap.Round)
            drawLine(pal.strong, p(21f, 21.5f), p(27f, 21.5f), strokeWidth = line * 0.7f, cap = StrokeCap.Round)
            drawLine(pal.strong, p(21f, 26.5f), p(27f, 26.5f), strokeWidth = line * 0.7f, cap = StrokeCap.Round)
        }
    }
}

private fun DrawScope.gridWindows(
    p: (Float, Float) -> Offset,
    x0: Float,
    y0: Float,
    x1: Float,
    y1: Float,
    scale: Float,
    color: Color,
) {
    var wy = y0
    while (wy < y1 - 3f) {
        var wx = x0
        while (wx < x1 - 3f) {
            drawRect(color, topLeft = p(wx, wy), size = Size(3f * scale, 3f * scale))
            wx += 6f
        }
        wy += 6f
    }
}

private fun DrawScope.drawRoundedStroke(
    topLeft: Offset,
    bottomRight: Offset,
    radius: Float,
    color: Color,
    line: Float,
) {
    drawRoundRect(
        color = color,
        topLeft = topLeft,
        size = Size(bottomRight.x - topLeft.x, bottomRight.y - topLeft.y),
        cornerRadius = CornerRadius(radius, radius),
        style = Stroke(width = line),
    )
}
