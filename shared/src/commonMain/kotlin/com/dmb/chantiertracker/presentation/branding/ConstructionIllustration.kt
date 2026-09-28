package com.dmb.chantiertracker.presentation.branding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

private const val DESIGN_W = 100f
private const val DESIGN_H = 66f

@Composable
fun ConstructionIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier.aspectRatio(DESIGN_W / DESIGN_H)) {
        val scale = size.width / DESIGN_W
        val originY = size.height - DESIGN_H * scale
        fun p(x: Float, y: Float) = Offset(x * scale + 0f, y * scale + originY)
        val line = 1.6f * scale

        val faint = Color.White.copy(alpha = 0.16f)
        val soft = Color.White.copy(alpha = 0.28f)
        val strong = Color.White.copy(alpha = 0.92f)

        drawBuilding(p(6f, 38f), p(24f, 65f), faint, soft, scale, windows = false)
        drawBuilding(p(26f, 26f), p(48f, 65f), faint, soft, scale, windows = true)

        drawFramedBuilding(p(74f, 42f), p(95f, 65f), strong, line, scale)

        drawCrane(::p, strong, line, scale)

        drawLine(strong.copy(alpha = 0.35f), p(0f, 65f), p(100f, 65f), strokeWidth = line, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawBuilding(
    topLeft: Offset,
    bottomRight: Offset,
    fill: Color,
    window: Color,
    scale: Float,
    windows: Boolean,
) {
    val size = Size(bottomRight.x - topLeft.x, bottomRight.y - topLeft.y)
    drawRect(fill, topLeft = topLeft, size = size)
    if (windows) {
        val cell = 5f * scale
        val pad = 2.2f * scale
        var wy = topLeft.y + pad
        while (wy + cell * 0.55f < bottomRight.y - pad) {
            var wx = topLeft.x + pad
            while (wx + cell * 0.55f < bottomRight.x - pad) {
                drawRect(window, topLeft = Offset(wx, wy), size = Size(cell * 0.55f, cell * 0.55f))
                wx += cell
            }
            wy += cell
        }
    }
}

private fun DrawScope.drawFramedBuilding(
    topLeft: Offset,
    bottomRight: Offset,
    color: Color,
    line: Float,
    scale: Float,
) {
    val stroke = Stroke(width = line)
    drawRect(color, topLeft = topLeft, size = Size(bottomRight.x - topLeft.x, bottomRight.y - topLeft.y), style = stroke)
    val floors = 3
    for (i in 1 until floors) {
        val y = topLeft.y + (bottomRight.y - topLeft.y) * i / floors
        drawLine(color, Offset(topLeft.x, y), Offset(bottomRight.x, y), strokeWidth = line)
    }
    val midX = (topLeft.x + bottomRight.x) / 2
    drawLine(color, Offset(midX, topLeft.y), Offset(midX, bottomRight.y), strokeWidth = line)
}

private fun DrawScope.drawCrane(p: (Float, Float) -> Offset, color: Color, line: Float, scale: Float) {
    val mastLeft = 60f
    val mastRight = 64f
    val mastTop = 12f
    val mastBottom = 65f

    drawLine(color, p(mastLeft, mastBottom), p(mastLeft, mastTop), strokeWidth = line)
    drawLine(color, p(mastRight, mastBottom), p(mastRight, mastTop), strokeWidth = line)
    var y = mastBottom
    var flip = true
    while (y > mastTop + 6f) {
        val next = y - 9f
        if (flip) drawLine(color, p(mastLeft, y), p(mastRight, next), strokeWidth = line * 0.7f)
        else drawLine(color, p(mastRight, y), p(mastLeft, next), strokeWidth = line * 0.7f)
        drawLine(color, p(mastLeft, next), p(mastRight, next), strokeWidth = line * 0.7f)
        y = next
        flip = !flip
    }

    val apex = p(62f, 4f)
    val jibNear = p(64f, 13f)
    val jibFar = p(97f, 13f)
    val jibNearBottom = p(64f, 16f)
    val jibFarBottom = p(90f, 16f)
    val counterFar = p(45f, 13f)

    drawLine(color, jibNear, jibFar, strokeWidth = line)
    drawLine(color, jibNearBottom, jibFarBottom, strokeWidth = line)
    drawLine(color, counterFar, jibNear, strokeWidth = line)

    var wx = 66f
    while (wx < 88f) {
        drawLine(color, p(wx, 13f), p(wx + 4f, 16f), strokeWidth = line * 0.6f)
        wx += 8f
    }

    drawLine(color, apex, jibFar, strokeWidth = line * 0.8f)
    drawLine(color, apex, counterFar, strokeWidth = line * 0.8f)
    drawLine(color, p(62f, 12f), apex, strokeWidth = line)

    val counterweight = Path().apply {
        moveTo(p(41f, 12f).x, p(41f, 12f).y)
        lineTo(p(47f, 12f).x, p(47f, 12f).y)
        lineTo(p(47f, 18f).x, p(47f, 18f).y)
        lineTo(p(41f, 18f).x, p(41f, 18f).y)
        close()
    }
    drawPath(counterweight, color)

    drawLine(color, p(84f, 16f), p(84f, 34f), strokeWidth = line * 0.7f)
    drawRect(color, topLeft = p(82f, 34f), size = Size(4f * scale, 3.4f * scale))
}
