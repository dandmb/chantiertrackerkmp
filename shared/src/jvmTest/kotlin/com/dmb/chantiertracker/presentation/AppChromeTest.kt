package com.dmb.chantiertracker.presentation

import kotlin.test.assertTrue
import kotlin.test.assertEquals
import javax.imageio.ImageIO
import java.io.File
import com.dmb.chantiertracker.presentation.theme.LightColors
import com.dmb.chantiertracker.presentation.theme.DarkColors
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class AppChromeTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    @Test
    fun the_staging_banner_is_shown_on_a_pre_production_build() = runComposeUiTest {
        setContent {
            customAppLocale = "fr"
            AppEnvironment { AppTheme { AppChrome(showStagingBanner = true) { Box(Modifier.fillMaxSize()) { Text("écran") } } } }
        }
        waitForIdle()
        onNodeWithText("PRÉPROD · DONNÉES RÉELLES").assertIsDisplayed()
        onNodeWithText("écran").assertIsDisplayed()
    }

    @Test
    fun no_banner_on_a_normal_build() = runComposeUiTest {
        setContent {
            customAppLocale = "fr"
            AppEnvironment { AppTheme { AppChrome(showStagingBanner = false) { Box(Modifier.fillMaxSize()) { Text("écran") } } } }
        }
        waitForIdle()
        onNodeWithText("PRÉPROD · DONNÉES RÉELLES").assertDoesNotExist()
        onNodeWithText("écran").assertIsDisplayed()
    }

    @Test
    fun the_banner_is_the_brand_yellow_with_black_text_above_the_screen_in_light_and_in_dark() {
        for (dark in listOf(false, true)) runComposeUiTest {
            setContent {
                customAppLocale = "fr"
                AppEnvironment {
                    AppTheme(darkTheme = dark) {
                        Box(Modifier.size(412.dp, 300.dp)) {
                            AppChrome(showStagingBanner = true) { Box(Modifier.fillMaxSize()) { Text("écran") } }
                        }
                    }
                }
            }
            waitForIdle()

            val scheme = if (dark) DarkColors else LightColors
            val banner = onNodeWithTag(STAGING_BANNER_TAG).captureToImage()
            assertEquals(scheme.primaryContainer, banner.toPixelMap()[2, 2], "yellow fill (dark=$dark)")
            val (hi, lo) = listOf(scheme.onPrimaryContainer.luminance() + 0.05f, scheme.primaryContainer.luminance() + 0.05f).sortedDescending()
            assertTrue(hi / lo >= 4.5f, "text contrast ${hi / lo} (dark=$dark)")
            onNodeWithText("PRÉPROD · DONNÉES RÉELLES").assertIsDisplayed()
            val bannerBottom = onNodeWithTag(STAGING_BANNER_TAG).getBoundsInRoot().bottom
            assertTrue(onNodeWithText("écran").getBoundsInRoot().top >= bannerBottom, "the screen starts under the banner")
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(SnapshotSettings.outDir("build/auth-snapshots"), "21-staging-banner-${if (dark) "dark" else "light"}-fr.png"))
        }
    }
}
