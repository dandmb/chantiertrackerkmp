package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.dmb.chantiertracker.core.AppConfig
import com.dmb.chantiertracker.domain.model.BillingAvailability
import com.dmb.chantiertracker.domain.model.Plan
import com.dmb.chantiertracker.domain.model.PlanUsage
import com.dmb.chantiertracker.presentation.auth.welcome.WelcomeScreen
import com.dmb.chantiertracker.presentation.billing.LocalServerBillingStatus
import com.dmb.chantiertracker.presentation.help.DiscoverScreen
import com.dmb.chantiertracker.presentation.help.HelpScreen
import com.dmb.chantiertracker.presentation.help.HelpViewModel
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.offer.MyOfferScreen
import com.dmb.chantiertracker.presentation.offer.MyOfferViewModel
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.FakeAccountRepository
import com.dmb.chantiertracker.support.FakeBuildInfo
import com.dmb.chantiertracker.support.FakeConnectivityObserver
import com.dmb.chantiertracker.support.FakeLaunchStatusRepository
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class InformationScreensSnapshotTest {

    private val outDir = SnapshotSettings.outDir("build/information-snapshots")

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    private val usage = PlanUsage(
        Plan.FREE, projectsLimit = 3, projectsUsed = 3, photosUsed = 12, photosLimit = 150, videosUsed = 2, videosLimit = 5,
        videoDurationLimitSeconds = 120, supervisorsUsed = 1, supervisorsLimit = 3, isFounder = true,
    )

    private fun snapshot(name: String, locale: String, dark: Boolean, height: Int = 892, screen: @Composable () -> Unit) = runDesktopComposeUiTest(width = 412, height = height) {
        setContent {
            customAppLocale = locale
            AppEnvironment {
                AppTheme(darkTheme = dark) {
                    CompositionLocalProvider(LocalServerBillingStatus provides BillingAvailability.CLOSED) {
                        Surface { Box(Modifier.size(412.dp, height.dp)) { AtTheSnapshotFontScale { screen() } } }
                    }
                }
            }
        }
        waitForIdle()
        val suffix = if (dark) "dark" else "light"
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(outDir, "$name-$suffix-$locale.png"))
    }

    @Test
    fun capture_the_information_screens_in_light_and_dark() {
        for (locale in listOf("fr", "en")) {
            for (dark in listOf(false, true)) {
                snapshot("welcome", locale, dark) {
                    WelcomeScreen(onCreateAccount = {}, onSignIn = {}, onDiscoverPlans = null, onOpenLegalDocument = {})
                }
                snapshot("my-offer", locale, dark) {
                    MyOfferScreen(
                        viewModel = MyOfferViewModel(
                            FakeAccountRepository(usage),
                            FakeLaunchStatusRepository(BillingAvailability.CLOSED),
                            FakeConnectivityObserver(initiallyOnline = true),
                        ),
                    )
                }
                snapshot("help", locale, dark, height = 2_000) {
                    HelpScreen(
                        onDiscover = {},
                        viewModel = HelpViewModel({ _, _, _ -> }, AppConfig(FakeBuildInfo(isDebug = false, appVersion = "1.0")), com.dmb.chantiertracker.support.FakeSupportContactRepository("aide@exemple.test")),
                    )
                }
                snapshot("discover", locale, dark, height = 1_500) { DiscoverScreen() }
            }
        }
    }
}
