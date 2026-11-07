package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.presentation.auth.login.LoginScreen
import com.dmb.chantiertracker.presentation.auth.login.LoginViewModel
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.FakeAuthRepository
import com.dmb.chantiertracker.support.FakeBillingRepository
import com.dmb.chantiertracker.support.FakeCheckoutLauncher
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AuthLayoutUiTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    @Test
    fun the_auth_screens_keep_the_band_the_overlapping_card_and_the_centred_title_in_both_modes() {
        for (dark in listOf(false, true)) runComposeUiTest {
            setContent {
                customAppLocale = "fr"
                AppEnvironment {
                    AppTheme(darkTheme = dark) {
                        CompositionLocalProvider(LocalDensity provides Density(1f)) {
                            AppSurface {
                                Box(Modifier.size(412.dp, 892.dp)) {
                                    LoginScreen({}, {}, onOpenLegalDocument = {}, viewModel = LoginViewModel(FakeAuthRepository(), FakeCheckoutLauncher(), FakeBillingRepository()))
                                }
                            }
                        }
                    }
                }
            }
            waitForIdle()

            val pixels = onRoot().captureToImage().toPixelMap()
            assertEquals(Color(0xFFB6D45A), pixels[4, 4], "lemon band at the top (dark=$dark)")
            assertEquals(Color(0xFFB6D45A), pixels[4, 200], "the band is tall, like before the redesign (dark=$dark)")
            val title = onNodeWithText("Connexion").getBoundsInRoot()
            val middle = (title.left + title.right) / 2
            assertTrue(abs(middle.value - 206f) < 2f, "title centred: $title (dark=$dark)")
            assertTrue(title.top > 200.dp, "title in the card, under the band: $title (dark=$dark)")
        }
    }
}
