package com.dmb.chantiertracker.presentation.branding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.presentation.theme.Ink
import com.dmb.chantiertracker.presentation.theme.Yellow

internal data class LogoPath(val pathData: String, val color: Color, val strokeWidth: Float = 0f, val roundCap: Boolean = false)

internal const val LogoScale = 0.575f
internal const val LogoTranslateX = 20.075f
internal const val LogoTranslateY = 29.275f

internal val LogoPaths: List<LogoPath> = listOf(
    LogoPath(
        "M100,9 L100,4 L85.509,4 L92.424,8.322 L92,9 Z M90.491,9 L84,4.943 L77.509,9 Z M74.491,9 L68,4.943 L61.509,9 Z " +
            "M58.491,9 L52,4.943 L45.509,9 Z M40,4 L40,9 L44,9 L43.576,8.322 L50.491,4 Z M53.509,4 L60,8.057 L66.491,4 Z " +
            "M69.509,4 L76,8.057 L82.491,4 Z",
        Color.White,
    ),
    LogoPath("M81.5,9 H84.5 V15 H81.5 Z M79,14 H87 V19 H79 Z", Yellow),
    LogoPath("M83,19 V22 a5,5 0 1,1 -8,3", Yellow, strokeWidth = 3.5f, roundCap = true),
    LogoPath("M83,28 L76,35 M83,28 L92,35", Yellow, strokeWidth = 2f),
    LogoPath("M74,35 H94 V43 H74 Z", Yellow),
    LogoPath("M44,18 L76,46 L68,46 L44,25 L20,46 L12,46 Z", Color.White),
    LogoPath(
        "M18,74 H42 V82 H18 Z M46,74 H70 V82 H46 Z M18,63 H30 V71 H18 Z M33,63 H57 V71 H33 Z M60,63 H70 V71 H60 Z " +
            "M18,52 H42 V60 H18 Z",
        Color.White,
    ),
)

val AppGlyph: ImageVector = ImageVector.Builder(
    name = "AppGlyph",
    defaultWidth = 108.dp,
    defaultHeight = 108.dp,
    viewportWidth = 108f,
    viewportHeight = 108f,
).apply {
    group(scaleX = LogoScale, scaleY = LogoScale, translationX = LogoTranslateX, translationY = LogoTranslateY) {
        LogoPaths.forEach { logoPath ->
            val nodes = PathParser().parsePathString(logoPath.pathData).toNodes()
            if (logoPath.strokeWidth > 0f) {
                addPath(
                    pathData = nodes,
                    stroke = SolidColor(logoPath.color),
                    strokeLineWidth = logoPath.strokeWidth,
                    strokeLineCap = if (logoPath.roundCap) StrokeCap.Round else StrokeCap.Butt,
                )
            } else {
                addPath(pathData = nodes, fill = SolidColor(logoPath.color))
            }
        }
    }
}.build()

@Composable
fun AppLogo(size: Dp, modifier: Modifier = Modifier) {
    Image(
        imageVector = AppGlyph,
        contentDescription = null,
        modifier = modifier.size(size).background(Ink),
    )
}
