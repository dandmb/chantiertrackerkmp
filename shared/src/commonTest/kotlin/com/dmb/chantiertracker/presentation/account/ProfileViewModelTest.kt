package com.dmb.chantiertracker.presentation.account

import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.model.ProfileField
import com.dmb.chantiertracker.domain.model.ProfileLanguage
import com.dmb.chantiertracker.domain.model.ProfileUpdate
import com.dmb.chantiertracker.domain.model.User
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.profile_language_invalid
import com.dmb.chantiertracker.resources.profile_name_invalid
import com.dmb.chantiertracker.resources.profile_phone_invalid
import com.dmb.chantiertracker.support.FakeAuthRepository
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }
    @AfterTest fun tearDown() { resetTestMainDispatcher() }

    private fun signedIn(phone: String? = "+237 699 00 11 22", language: ProfileLanguage? = ProfileLanguage.ENGLISH) = FakeAuthRepository().apply {
        emitState(AuthState.Authenticated(User(7, "jean@chantier.dev", "Jean", true, GlobalRole.USER, phone, language)))
    }

    @Test
    fun the_form_starts_from_the_signed_in_account() = runTest {
        val vm = ProfileViewModel(signedIn())
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(listOf("jean@chantier.dev", "Jean", "+237 699 00 11 22"), listOf(state.email, state.name, state.phone))
        assertEquals(ProfileLanguage.ENGLISH, state.language)
    }

    @Test
    fun an_account_without_phone_or_language_starts_empty_and_in_french() = runTest {
        val vm = ProfileViewModel(signedIn(phone = null, language = null))
        advanceUntilIdle()

        assertEquals("", vm.state.value.phone)
        assertEquals(ProfileLanguage.FRENCH, vm.state.value.language)
    }

    @Test
    fun saving_sends_the_trimmed_values_and_closes_the_form() = runTest {
        val auth = signedIn()
        val vm = ProfileViewModel(auth)
        advanceUntilIdle()

        vm.onNameChange("  Jean Bizwa ")
        vm.onPhoneChange(" +33 6 12 34 56 78 ")
        vm.onLanguageChange(ProfileLanguage.FRENCH)
        vm.submit()
        advanceUntilIdle()

        assertEquals(listOf(ProfileUpdate("Jean Bizwa", "+33 6 12 34 56 78", ProfileLanguage.FRENCH)), auth.profileUpdates)
        assertTrue(vm.state.value.saved)
    }

    @Test
    fun an_empty_phone_is_allowed_and_clears_the_number() = runTest {
        val auth = signedIn()
        val vm = ProfileViewModel(auth)
        advanceUntilIdle()

        vm.onPhoneChange("")
        vm.submit()
        advanceUntilIdle()

        assertEquals("", auth.profileUpdates.single().phone)
    }

    @Test
    fun an_empty_name_or_an_invalid_phone_is_stopped_before_sending() = runTest {
        val auth = signedIn()
        val vm = ProfileViewModel(auth)
        advanceUntilIdle()

        vm.onNameChange("   ")
        vm.onPhoneChange("12")
        vm.submit()
        advanceUntilIdle()

        assertEquals(Res.string.profile_name_invalid, vm.state.value.nameError)
        assertEquals(Res.string.profile_phone_invalid, vm.state.value.phoneError)
        assertTrue(auth.profileUpdates.isEmpty())
        assertFalse(vm.state.value.saved)
    }

    @Test
    fun a_name_longer_than_255_characters_is_stopped_too() = runTest {
        val vm = ProfileViewModel(signedIn())
        advanceUntilIdle()

        vm.onNameChange("a".repeat(256))
        vm.submit()
        advanceUntilIdle()

        assertEquals(Res.string.profile_name_invalid, vm.state.value.nameError)
    }

    @Test
    fun the_fields_the_server_refuses_are_flagged_under_each_field() = runTest {
        val auth = signedIn().apply { error = DomainException.InvalidProfile(setOf(ProfileField.PHONE, ProfileField.LANGUAGE)) }
        val vm = ProfileViewModel(auth)
        advanceUntilIdle()

        vm.submit()
        advanceUntilIdle()

        val state = vm.state.value
        assertNull(state.nameError)
        assertEquals(Res.string.profile_phone_invalid, state.phoneError)
        assertEquals(Res.string.profile_language_invalid, state.languageError)
        assertNull(state.formError)
        assertFalse(state.saved)
        assertFalse(state.isSubmitting)
    }

    @Test
    fun editing_a_field_clears_its_error() = runTest {
        val vm = ProfileViewModel(signedIn())
        advanceUntilIdle()
        vm.onPhoneChange("12")
        vm.submit()
        advanceUntilIdle()

        vm.onPhoneChange("+237 699 00 11 22")

        assertNull(vm.state.value.phoneError)
    }

    @Test
    fun offline_the_form_says_so_and_keeps_the_values() = runTest {
        val auth = signedIn().apply { error = DomainException.Network }
        val vm = ProfileViewModel(auth)
        advanceUntilIdle()
        vm.onNameChange("Jean B")

        vm.submit()
        advanceUntilIdle()

        assertEquals(DomainException.Network, vm.state.value.formError)
        assertEquals("Jean B", vm.state.value.name)
        assertFalse(vm.state.value.saved)
    }
}
