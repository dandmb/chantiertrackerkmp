package com.dmb.chantiertracker.presentation.settings

import com.dmb.chantiertracker.core.AppConfig
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.support.FakeAppPreferences
import com.dmb.chantiertracker.support.FakeBuildInfo
import com.dmb.chantiertracker.support.FakeUrlOpener
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }
    @AfterTest fun tearDown() { resetTestMainDispatcher() }

    private fun vm(opener: FakeUrlOpener = FakeUrlOpener()) = SettingsViewModel(
        AppConfig(FakeBuildInfo(isDebug = false)),
        AppSettings(FakeAppPreferences(), CoroutineScope(Dispatchers.Unconfined)),
        opener,
    )

    @Test
    fun the_five_legal_pages_point_at_the_real_public_web_routes() {
        assertEquals(
            listOf(
                "https://chantiertracker.com/mentions-legales",
                "https://chantiertracker.com/cgu",
                "https://chantiertracker.com/cgv",
                "https://chantiertracker.com/confidentialite",
                "https://chantiertracker.com/cookies",
            ),
            LegalPage.entries.map { it.url },
        )
    }

    @Test
    fun clicking_a_legal_page_opens_its_url_in_the_browser() = runTest {
        val opener = FakeUrlOpener()
        val viewModel = vm(opener)

        viewModel.onLegalPageClick(LegalPage.PrivacyPolicy)
        advanceUntilIdle()

        assertEquals(listOf("https://chantiertracker.com/confidentialite"), opener.opened)
        assertNull(viewModel.linkError.value)
    }

    @Test
    fun a_browser_failure_surfaces_as_a_link_error() = runTest {
        val opener = FakeUrlOpener().apply { error = IllegalStateException("no browser") }
        val viewModel = vm(opener)

        viewModel.onLegalPageClick(LegalPage.TermsOfUse)
        advanceUntilIdle()

        assertEquals(DomainException.Unexpected, viewModel.linkError.value)
    }

    @Test
    fun the_next_click_clears_a_previous_link_error() = runTest {
        val opener = FakeUrlOpener().apply { error = IllegalStateException("no browser") }
        val viewModel = vm(opener)
        viewModel.onLegalPageClick(LegalPage.TermsOfUse)
        advanceUntilIdle()
        assertEquals(DomainException.Unexpected, viewModel.linkError.value)

        opener.error = null
        viewModel.onLegalPageClick(LegalPage.CookiePolicy)
        advanceUntilIdle()

        assertNull(viewModel.linkError.value)
        assertEquals(2, opener.opened.size)
    }
}
