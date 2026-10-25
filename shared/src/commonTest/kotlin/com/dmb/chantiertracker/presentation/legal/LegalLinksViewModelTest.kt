package com.dmb.chantiertracker.presentation.legal

import com.dmb.chantiertracker.support.FakeConnectivityObserver
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class LegalLinksViewModelTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }
    @AfterTest fun tearDown() { resetTestMainDispatcher() }

    private val opened = mutableListOf<String>()
    private val opener = LegalPageOpener { opened += it }

    @Test
    fun online_the_page_of_the_web_site_is_opened() = runTest {
        val vm = LegalLinksViewModel(FakeConnectivityObserver(initiallyOnline = true))

        vm.open(LegalDocument.TermsOfUse, opener)
        vm.open(LegalDocument.AccountDeletion, opener)
        advanceUntilIdle()

        assertEquals(listOf("https://chantiertracker.com/cgu", "https://chantiertracker.com/suppression-compte"), opened)
        assertNull(vm.notice.value)
    }

    @Test
    fun offline_nothing_is_opened_and_the_notice_names_the_page() = runTest {
        val vm = LegalLinksViewModel(FakeConnectivityObserver(initiallyOnline = false))

        vm.open(LegalDocument.PrivacyPolicy, opener)
        advanceUntilIdle()

        assertEquals(emptyList(), opened)
        assertEquals(LegalPageNotice(LegalDocument.PrivacyPolicy, LegalPageProblem.Offline), vm.notice.value)
    }

    @Test
    fun a_page_that_cannot_be_opened_gives_its_address_instead_of_failing_silently() = runTest {
        val vm = LegalLinksViewModel(FakeConnectivityObserver(initiallyOnline = true))

        vm.open(LegalDocument.CookiePolicy, LegalPageOpener { error("no browser") })
        advanceUntilIdle()

        assertEquals(LegalPageNotice(LegalDocument.CookiePolicy, LegalPageProblem.CannotOpen), vm.notice.value)
    }

    @Test
    fun the_notice_is_dismissed_and_the_page_opens_once_back_online() = runTest {
        val connectivity = FakeConnectivityObserver(initiallyOnline = false)
        val vm = LegalLinksViewModel(connectivity)
        vm.open(LegalDocument.LegalNotice, opener)
        advanceUntilIdle()

        vm.dismissNotice()
        connectivity.setOnline(true)
        vm.open(LegalDocument.LegalNotice, opener)
        advanceUntilIdle()

        assertNull(vm.notice.value)
        assertEquals(listOf("https://chantiertracker.com/mentions-legales"), opened)
    }
}
