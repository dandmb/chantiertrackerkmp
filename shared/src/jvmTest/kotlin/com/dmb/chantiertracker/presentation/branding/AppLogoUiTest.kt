package com.dmb.chantiertracker.presentation.branding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.presentation.splash.SplashScreen
import com.dmb.chantiertracker.presentation.theme.AppTheme
import java.awt.image.BufferedImage
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AppLogoUiTest {

    private fun BufferedImage.argbPixels(): List<Triple<Int, Int, Int>> =
        (0 until height).flatMap { y -> (0 until width).map { x -> Triple(x, y, getRGB(x, y)) } }

    private fun isYellow(argb: Int): Boolean =
        argb ushr 24 > 200 && (argb shr 16 and 0xFF) > 230 && (argb shr 8 and 0xFF) in 190..230 && (argb and 0xFF) < 40

    private fun isWhite(argb: Int): Boolean =
        argb ushr 24 > 200 && (argb shr 16 and 0xFF) > 240 && (argb shr 8 and 0xFF) > 240 && (argb and 0xFF) > 240

    @Test
    fun the_in_app_logo_draws_the_house_and_crane_inside_the_round_mask() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                Image(imageVector = AppGlyph, contentDescription = null, modifier = Modifier.size(432.dp))
            }
        }
        val image = onRoot().captureToImage().toAwtImage()
        assertEquals(432, image.width)
        val pixels = image.argbPixels()
        assertTrue(pixels.count { isYellow(it.third) } > 1000, "yellow hook and load")
        assertTrue(pixels.count { isWhite(it.third) } > 5000, "white house, bricks and jib")
        val outside = pixels.count { (x, y, argb) -> argb ushr 24 > 0 && hypot(x + 0.5 - 216, y + 0.5 - 216) > 132 + 1 }
        assertEquals(0, outside, "pixels of the drawing outside the safe zone (circle of 66/108)")
    }

    @Test
    fun the_splash_screen_shows_the_new_logo() = runComposeUiTest {
        setContent { AppTheme { SplashScreen() } }
        val pixels = onRoot().captureToImage().toAwtImage().argbPixels()
        assertTrue(pixels.count { isYellow(it.third) } > 100, "the splash screen shows the yellow hook and load of the crane")
    }

    @Test
    fun the_square_logo_is_the_launcher_icon_on_black() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) { AppLogo(size = 216.dp) }
        }
        val pixels = onRoot().captureToImage().toAwtImage().argbPixels()
        assertEquals(0xFF0A0A0A.toInt(), pixels.first { it.first == 0 && it.second == 0 }.third, "black corner, not rounded")
        assertTrue(pixels.count { isYellow(it.third) } > 250, "yellow hook and load")
    }
}
