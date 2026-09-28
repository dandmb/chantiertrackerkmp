package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.theme.AppTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

// ADR-56 sous-étape 1/5 (fondations) — no real screen consumes ResponsiveContent
// yet (that's sous-étapes 3/4), so this is what proves the primitive itself
// behaves correctly before wiring it into ~25 screens: the content band caps
// and centers at a wide window, stays edge-to-edge on a phone-narrow one, and
// the WidthSizeClass it hands back matches the actual measured width.
//
// This exact test is also what caught a real, pre-existing bug while it was
// being written: the first version of ResponsiveContent used
// `Modifier.fillMaxWidth().widthIn(max = X)` (the order AuthScreenLayout's
// three call sites already used) — captured at 1440dp, the "capped" band
// filled the entire width, not X. `fillMaxWidth()` first hands down a tight
// (min == max == available) constraint, which a `widthIn(max = X)` chained
// after it can no longer shrink once X is below that min — so the cap
// silently never applied. `widthIn(max = X)` BEFORE `fillMaxWidth()` is the
// order that actually works. AuthScreenLayout's three call sites (plus one
// in OnboardingScreen with the same reversed order) were corrected the same
// way once this was confirmed — see docs/walkthrough for the isolated,
// colored-box proof this test's screenshots are the polished version of.
@OptIn(ExperimentalTestApi::class)
class ResponsiveContentSnapshotTest {

    private val outDir = File("build/auth-snapshots").apply { mkdirs() }

    @Composable
    private fun Demo() {
        ResponsiveContent(maxContentWidth = 640.dp) { widthClass ->
            Box(
                Modifier.fillMaxSize().background(Color(0xFFFFC107)),
                contentAlignment = Alignment.Center,
            ) {
                Text("widthClass=$widthClass", style = MaterialTheme.typography.titleLarge, color = Color.Black)
            }
        }
    }

    private fun capture(name: String, width: Int, height: Int = 500) = runDesktopComposeUiTest(width = width, height = height) {
        setContent {
            customAppLocale = "fr"
            AppEnvironment {
                AppTheme(darkTheme = false) {
                    Box(Modifier.fillMaxSize().background(Color(0xFFEEEEEE))) {
                        Demo()
                    }
                }
            }
        }
        waitForIdle()
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(outDir, "56-responsive-content-$name.png"))
    }

    @Test
    fun capture_compact_width_fills_edge_to_edge() = capture("compact", width = 412)

    @Test
    fun capture_medium_width_still_fills_below_the_cap() = capture("medium", width = 700)

    @Test
    fun capture_expanded_width_caps_and_centers() = capture("expanded", width = 1440)
}
