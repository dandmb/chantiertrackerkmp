package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.onboarding.ONBOARDING_PAGES
import com.dmb.chantiertracker.presentation.onboarding.OnboardingScreenContent
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class OnboardingScreenUiTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    @Test
    fun first_page_shows_next_skip_and_localized_title() = runComposeUiTest {
        setContent {
            customAppLocale = "fr"
            AppEnvironment {
                AppTheme {
                    val state = rememberPagerState(initialPage = 0, pageCount = { ONBOARDING_PAGES })
                    OnboardingScreenContent(pagerState = state, loop = 0f, onFinish = {})
                }
            }
        }
        onNodeWithText("Suivant").assertIsDisplayed()
        onNodeWithText("Passer").assertIsDisplayed()
        onNodeWithText("Suis ton chantier à distance").assertIsDisplayed()
    }

    @Test
    fun last_page_swaps_next_for_start_and_hides_skip() {
        var finished = false
        runComposeUiTest {
            setContent {
                customAppLocale = "en"
                AppEnvironment {
                    AppTheme {
                        val state = rememberPagerState(
                            initialPage = ONBOARDING_PAGES - 1,
                            pageCount = { ONBOARDING_PAGES },
                        )
                        OnboardingScreenContent(pagerState = state, loop = 0f, onFinish = { finished = true })
                    }
                }
            }
            onNodeWithText("Get started").assertIsDisplayed()
            onNodeWithText("Skip").assertDoesNotExist()
            onNodeWithText("Get started").performClick()
        }
        assertTrue(finished)
    }

    @Test
    fun skip_finishes_onboarding() {
        var finished = false
        runComposeUiTest {
            setContent {
                customAppLocale = "en"
                AppEnvironment {
                    AppTheme {
                        val state = rememberPagerState(initialPage = 0, pageCount = { ONBOARDING_PAGES })
                        OnboardingScreenContent(pagerState = state, loop = 0f, onFinish = { finished = true })
                    }
                }
            }
            onNodeWithText("Skip").performClick()
        }
        assertTrue(finished)
    }
}
