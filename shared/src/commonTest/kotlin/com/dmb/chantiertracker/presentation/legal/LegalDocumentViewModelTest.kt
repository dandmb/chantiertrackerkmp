package com.dmb.chantiertracker.presentation.legal

import com.dmb.chantiertracker.domain.model.LegalLanguage
import com.dmb.chantiertracker.support.FakeConnectivityObserver
import com.dmb.chantiertracker.support.FakeLegalDocumentRepository
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.legalText
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class LegalDocumentViewModelTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }
    @AfterTest fun tearDown() { resetTestMainDispatcher() }

    private val connectivity = FakeConnectivityObserver(initiallyOnline = true)
    private val savedEnglish = legalText(language = LegalLanguage.English, markdown = "## 1. Cookies\n\nSaved English text.", publishedOn = "2026-10-01")

    private fun offline(repository: FakeLegalDocumentRepository) {
        connectivity.setOnline(false)
        repository.serverReachable = false
    }

    @Test
    fun online_the_server_text_is_shown_with_its_title_and_date() = runTest {
        val repository = FakeLegalDocumentRepository()
        val vm = LegalDocumentViewModel(repository, connectivity)

        vm.load(LegalDocument.CookiePolicy, LegalLanguage.French)
        advanceUntilIdle()

        val state = assertIs<LegalDocumentUiState.Shown>(vm.state.value)
        assertEquals(listOf("Politique de cookies", "2026-10-25"), listOf(state.text.title, state.text.publishedOn))
        assertEquals(LegalBlock.Heading(2, LegalInline(LegalSpan("1. Cookies"))), state.blocks.first())
        assertEquals(false, state.savedCopyOnly)
        assertEquals(listOf("cookie-policy" to LegalLanguage.French), repository.refreshes)
    }

    @Test
    fun an_english_reader_gets_the_english_text() = runTest {
        val repository = FakeLegalDocumentRepository()
        val vm = LegalDocumentViewModel(repository, connectivity)

        vm.load(LegalDocument.CookiePolicy, LegalLanguage.English)
        advanceUntilIdle()

        assertEquals("Cookie Policy", assertIs<LegalDocumentUiState.Shown>(vm.state.value).text.title)
        assertEquals(listOf("cookie-policy" to LegalLanguage.English), repository.refreshes)
    }

    @Test
    fun loading_the_same_document_again_does_not_ask_the_server_twice() = runTest {
        val repository = FakeLegalDocumentRepository()
        val vm = LegalDocumentViewModel(repository, connectivity)

        vm.load(LegalDocument.CookiePolicy, LegalLanguage.French)
        vm.load(LegalDocument.CookiePolicy, LegalLanguage.French)
        advanceUntilIdle()

        assertEquals(1, repository.refreshes.size)
    }

    @Test
    fun offline_the_last_version_received_is_shown_and_marked_as_saved() = runTest {
        val repository = FakeLegalDocumentRepository(savedEnglish).also(::offline)
        val vm = LegalDocumentViewModel(repository, connectivity)

        vm.load(LegalDocument.CookiePolicy, LegalLanguage.English)
        advanceUntilIdle()

        val state = assertIs<LegalDocumentUiState.Shown>(vm.state.value)
        assertEquals("2026-10-01", state.text.publishedOn)
        assertEquals(true, state.savedCopyOnly)
    }

    @Test
    fun the_saved_version_of_the_other_language_is_never_shown_instead() = runTest {
        val repository = FakeLegalDocumentRepository(savedEnglish).also(::offline)
        val vm = LegalDocumentViewModel(repository, connectivity)

        vm.load(LegalDocument.CookiePolicy, LegalLanguage.French)
        advanceUntilIdle()

        assertEquals(LegalDocumentUiState.Unavailable(offline = true), vm.state.value)
    }

    @Test
    fun never_received_and_offline_the_screen_says_so_and_retry_shows_the_text_once_back_online() = runTest {
        val repository = FakeLegalDocumentRepository().also(::offline)
        val vm = LegalDocumentViewModel(repository, connectivity)
        vm.load(LegalDocument.CookiePolicy, LegalLanguage.French)
        advanceUntilIdle()
        assertEquals(LegalDocumentUiState.Unavailable(offline = true), vm.state.value)

        connectivity.setOnline(true)
        repository.serverReachable = true
        vm.retry()
        advanceUntilIdle()

        assertEquals(false, assertIs<LegalDocumentUiState.Shown>(vm.state.value).savedCopyOnly)
    }

    @Test
    fun never_received_and_the_server_failing_while_online_is_not_called_offline() = runTest {
        val repository = FakeLegalDocumentRepository().apply { serverReachable = false }
        val vm = LegalDocumentViewModel(repository, connectivity)

        vm.load(LegalDocument.CookiePolicy, LegalLanguage.French)
        advanceUntilIdle()

        assertEquals(LegalDocumentUiState.Unavailable(offline = false), vm.state.value)
    }

    @Test
    fun a_newer_text_received_replaces_the_saved_one_on_screen() = runTest {
        val repository = FakeLegalDocumentRepository(savedEnglish)
        val vm = LegalDocumentViewModel(repository, connectivity)

        vm.load(LegalDocument.CookiePolicy, LegalLanguage.English)
        advanceUntilIdle()

        val state = assertIs<LegalDocumentUiState.Shown>(vm.state.value)
        assertEquals("2026-10-25", state.text.publishedOn)
        assertEquals(false, state.savedCopyOnly)
    }

    @Test
    fun a_web_link_opens_in_the_in_app_tab_and_a_mail_link_in_the_mail_app() = runTest {
        val web = mutableListOf<String>()
        val mail = mutableListOf<String>()
        val vm = LegalDocumentViewModel(FakeLegalDocumentRepository(), connectivity)

        vm.openLink(LegalLink.Web("https://chantiertracker.com/cgu"), { web += it }, { mail += it })
        vm.openLink(LegalLink.Mail("mailto:dpo@chantiertracker.com"), { web += it }, { mail += it })
        advanceUntilIdle()

        assertEquals(listOf("https://chantiertracker.com/cgu"), web)
        assertEquals(listOf("mailto:dpo@chantiertracker.com"), mail)
        assertNull(vm.linkNotice.value)
    }

    @Test
    fun offline_a_web_link_gives_its_address_instead_of_opening_but_a_mail_link_still_opens() = runTest {
        connectivity.setOnline(false)
        val web = mutableListOf<String>()
        val mail = mutableListOf<String>()
        val vm = LegalDocumentViewModel(FakeLegalDocumentRepository(), connectivity)

        vm.openLink(LegalLink.Web("https://chantiertracker.com/cgu"), { web += it }, { mail += it })
        advanceUntilIdle()
        assertEquals(LegalLinkNotice(LegalLink.Web("https://chantiertracker.com/cgu"), LegalLinkProblem.Offline), vm.linkNotice.value)

        vm.dismissLinkNotice()
        vm.openLink(LegalLink.Mail("mailto:dpo@chantiertracker.com"), { web += it }, { mail += it })
        advanceUntilIdle()

        assertEquals(emptyList(), web)
        assertEquals(listOf("mailto:dpo@chantiertracker.com"), mail)
        assertNull(vm.linkNotice.value)
    }

    @Test
    fun a_link_that_no_app_can_open_gives_its_address() = runTest {
        val vm = LegalDocumentViewModel(FakeLegalDocumentRepository(), connectivity)

        vm.openLink(LegalLink.Mail("mailto:dpo@chantiertracker.com"), {}, { error("no mail app") })
        advanceUntilIdle()

        assertEquals(LegalLinkNotice(LegalLink.Mail("mailto:dpo@chantiertracker.com"), LegalLinkProblem.CannotOpen), vm.linkNotice.value)
    }
}
