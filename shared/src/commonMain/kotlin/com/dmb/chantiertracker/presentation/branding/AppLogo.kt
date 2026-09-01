package com.dmb.chantiertracker.presentation.branding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.presentation.theme.Terracotta

val AppGlyph: ImageVector = ImageVector.Builder(
    name = "AppGlyph",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.White)) {
        moveToRelative(13.7829f, 15.1718f)
        lineToRelative(2.1213f, -2.1213f)
        lineToRelative(5.9963f, 5.9963f)
        lineToRelative(-2.1213f, 2.1213f)
        close()
        moveTo(17.5f, 10f)
        curveToRelative(1.93f, 0f, 3.5f, -1.57f, 3.5f, -3.5f)
        curveToRelative(0f, -0.58f, -0.16f, -1.12f, -0.41f, -1.6f)
        lineToRelative(-2.7f, 2.7f)
        lineToRelative(-1.49f, -1.49f)
        lineToRelative(2.7f, -2.7f)
        curveToRelative(-0.48f, -0.25f, -1.02f, -0.41f, -1.6f, -0.41f)
        curveTo(15.57f, 3f, 14f, 4.57f, 14f, 6.5f)
        curveToRelative(0f, 0.41f, 0.08f, 0.8f, 0.21f, 1.16f)
        lineToRelative(-1.85f, 1.85f)
        lineToRelative(-1.78f, -1.78f)
        lineToRelative(0.71f, -0.71f)
        lineToRelative(-1.41f, -1.41f)
        lineTo(12f, 3.49f)
        curveToRelative(-1.17f, -1.17f, -3.07f, -1.17f, -4.24f, 0f)
        lineTo(4.22f, 7.03f)
        lineToRelative(1.41f, 1.41f)
        lineTo(2.81f, 8.44f)
        lineToRelative(-0.71f, 0.71f)
        lineToRelative(3.54f, 3.54f)
        lineToRelative(0.71f, -0.71f)
        lineTo(6.35f, 9.15f)
        lineToRelative(1.41f, 1.41f)
        lineToRelative(0.71f, -0.71f)
        lineToRelative(1.78f, 1.78f)
        lineToRelative(-7.41f, 7.41f)
        lineToRelative(2.12f, 2.12f)
        lineTo(16.34f, 9.79f)
        curveToRelative(0.36f, 0.13f, 0.75f, 0.21f, 1.16f, 0.21f)
        close()
    }
}.build()

@Composable
fun AppLogo(size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .background(Terracotta, RoundedCornerShape(size * 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = AppGlyph,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.58f).padding(0.dp),
        )
    }
}
