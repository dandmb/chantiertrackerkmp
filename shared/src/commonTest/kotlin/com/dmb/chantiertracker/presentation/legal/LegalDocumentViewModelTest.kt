package com.dmb.chantiertracker.presentation.legal

import com.dmb.chantiertracker.domain.model.EditorIdentity
import com.dmb.chantiertracker.support.FakeEditorIdentityRepository
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
class LegalDocumentViewModelTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }
    @AfterTest fun tearDown() { resetTestMainDispatcher() }

    @Test
    fun opening_a_document_shows_the_last_known_identity_and_asks_the_server_again() = runTest {
        val repo = FakeEditorIdentityRepository(EditorIdentity(siret = "123"))
        val vm = LegalDocumentViewModel(repo)
        advanceUntilIdle()

        assertEquals("123", vm.editorIdentity.value?.siret)
        assertEquals(1, repo.refreshCount)
    }

    @Test
    fun an_update_from_the_server_reaches_the_screen() = runTest {
        val repo = FakeEditorIdentityRepository(null)
        val vm = LegalDocumentViewModel(repo)
        advanceUntilIdle()
        assertNull(vm.editorIdentity.value)

        repo.identityFlow.value = EditorIdentity(companyName = "Chantier Martin SAS")
        advanceUntilIdle()

        assertEquals("Chantier Martin SAS", vm.editorIdentity.value?.companyName)
    }
}
