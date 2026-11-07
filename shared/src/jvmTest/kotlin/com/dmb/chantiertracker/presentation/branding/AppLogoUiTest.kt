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

    private fun isOrange(argb: Int): Boolean =
        argb ushr 24 > 200 && (argb shr 16 and 0xFF) in 222..242 && (argb shr 8 and 0xFF) in 106..126 && (argb and 0xFF) in 49..69

    private fun isCream(argb: Int): Boolean =
        argb ushr 24 > 200 && (argb shr 16 and 0xFF) in 246..255 && (argb shr 8 and 0xFF) in 242..252 && (argb and 0xFF) in 229..239

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
        assertTrue(pixels.count { isOrange(it.third) } > 1000, "orange hook and load")
        assertTrue(pixels.count { isCream(it.third) } > 5000, "cream house, bricks and jib")
        val outside = pixels.count { (x, y, argb) -> argb ushr 24 > 0 && hypot(x + 0.5 - 216, y + 0.5 - 216) > 132 + 1 }
        assertEquals(0, outside, "pixels of the drawing outside the safe zone (circle of 66/108)")
    }

    @Test
    fun the_splash_screen_shows_the_new_logo() = runComposeUiTest {
        setContent { AppTheme { SplashScreen() } }
        val pixels = onRoot().captureToImage().toAwtImage().argbPixels()
        assertTrue(pixels.count { isOrange(it.third) } > 100, "the splash screen shows the orange hook and load of the crane")
        assertEquals(0xFF22300D.toInt(), pixels.first { it.first == 1 && it.second == 1 }.third, "the splash screen is ink")
    }

    @Test
    fun the_square_logo_is_the_launcher_icon_on_ink() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) { AppLogo(size = 216.dp) }
        }
        val pixels = onRoot().captureToImage().toAwtImage().argbPixels()
        assertEquals(0xFF22300D.toInt(), pixels.first { it.first == 0 && it.second == 0 }.third, "ink corner, not rounded")
        assertTrue(pixels.count { isOrange(it.third) } > 250, "orange hook and load")
    }
}
