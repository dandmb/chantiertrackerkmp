package com.dmb.chantiertracker.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.domain.model.GlobalRole
import com.dmb.chantiertracker.domain.model.ProfileField
import com.dmb.chantiertracker.domain.model.ProfileLanguage
import com.dmb.chantiertracker.domain.model.ProfileUpdate
import com.dmb.chantiertracker.domain.model.User
import com.dmb.chantiertracker.presentation.account.ProfileScreen
import com.dmb.chantiertracker.presentation.account.ProfileViewModel
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.main.AccountMenuBody
import com.dmb.chantiertracker.presentation.theme.AppTheme
import com.dmb.chantiertracker.support.FakeAuthRepository
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ProfileScreenUiTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    private val auth = FakeAuthRepository().apply {
        emitState(AuthState.Authenticated(User(7, "jean@chantier.dev", "Jean", true, GlobalRole.USER, "+237 699 00 11 22", ProfileLanguage.FRENCH)))
    }
    private var saved = 0

    private fun ComposeUiTest.showProfile(locale: String = "fr") {
        val vm = ProfileViewModel(auth)
        setContent {
            customAppLocale = locale
            AppEnvironment { AppTheme { ProfileScreen(onSaved = { saved++ }, viewModel = vm) } }
        }
        waitForIdle()
    }

    @Test
    fun the_email_is_shown_but_cannot_be_edited() = runComposeUiTest {
        showProfile()

        onNodeWithText("jean@chantier.dev").assertIsDisplayed().assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.SetText).not())
        onNodeWithText("L'adresse e-mail ne peut pas être modifiée ici.").assertIsDisplayed()
        assertEquals(2, onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size, "only the name and the phone are fields")
    }

    @Test
    fun saving_sends_the_name_the_phone_and_the_language_then_closes() = runComposeUiTest {
        showProfile()

        onNode(hasSetTextAction() and hasText("Jean")).performTextReplacement("Jean Bizwa")
        onNode(hasSetTextAction() and hasText("+237 699 00 11 22")).performTextReplacement("")
        onNodeWithText("English").performScrollTo().performClick()
        onNodeWithText("Enregistrer").performScrollTo().performClick()
        waitForIdle()

        assertEquals(listOf(ProfileUpdate("Jean Bizwa", "", ProfileLanguage.ENGLISH)), auth.profileUpdates)
        assertEquals(1, saved)
    }

    @Test
    fun the_refused_fields_are_explained_under_each_field() = runComposeUiTest {
        auth.error = DomainException.InvalidProfile(setOf(ProfileField.NAME, ProfileField.PHONE))
        showProfile()

        onNodeWithText("Enregistrer").performScrollTo().performClick()
        waitForIdle()

        onNodeWithText("Le nom ne peut pas être vide (255 caractères au plus).").assertIsDisplayed()
        onNodeWithText("Ce numéro de téléphone n'est pas valide.").assertIsDisplayed()
        assertEquals(0, saved)
    }

    @Test
    fun in_english_the_screen_and_its_errors_are_in_english() = runComposeUiTest {
        showProfile(locale = "en")

        onNode(hasSetTextAction() and hasText("+237 699 00 11 22")).performTextReplacement("12")
        onNodeWithText("Save").performScrollTo().performClick()
        waitForIdle()

        onNodeWithText("Your email address can't be changed here.").assertIsDisplayed()
        onNodeWithText("This phone number isn't valid.").assertIsDisplayed()
        assertEquals(emptyList(), auth.profileUpdates)
    }

    @Test
    fun the_account_menu_opens_the_profile() = runComposeUiTest {
        var opened = 0
        setContent {
            customAppLocale = "fr"
            AppEnvironment {
                AppTheme {
                    Column {
                        AccountMenuBody(userName = "Jean", email = "jean@chantier.dev", plan = null, onSubscription = null, onLogout = {}, onProfile = { opened++ })
                    }
                }
            }
        }

        onNodeWithText("Mon profil").performClick()

        assertEquals(1, opened)
    }
}
