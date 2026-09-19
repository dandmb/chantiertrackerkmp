package com.dmb.chantiertracker.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import com.dmb.chantiertracker.domain.model.DomainException
import com.dmb.chantiertracker.presentation.i18n.AppEnvironment
import com.dmb.chantiertracker.presentation.i18n.customAppLocale
import com.dmb.chantiertracker.presentation.i18n.localizedText
import com.dmb.chantiertracker.presentation.i18n.textRes
import com.dmb.chantiertracker.resources.Res
import com.dmb.chantiertracker.resources.error_invalid_credentials
import com.dmb.chantiertracker.resources.login_title
import com.dmb.chantiertracker.resources.onboarding_p1_title
import com.dmb.chantiertracker.resources.welcome_title
import com.dmb.chantiertracker.support.installTestMainDispatcher
import com.dmb.chantiertracker.support.resetTestMainDispatcher
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class StringResourcesLocaleTest {

    @BeforeTest fun setUp() { installTestMainDispatcher() }

    @AfterTest fun tearDown() {
        customAppLocale = null
        resetTestMainDispatcher()
    }

    private fun resolve(languageTag: String, resource: StringResource): String {
        var resolved by mutableStateOf("")
        runComposeUiTest {
            setContent {
                customAppLocale = languageTag
                AppEnvironment {
                    resolved = stringResource(resource)
                }
            }
            waitForIdle()
        }
        return resolved
    }

    @Test
    fun screen_titles_resolve_in_french_and_english() {
        assertEquals("Connexion", resolve("fr", Res.string.login_title))
        assertEquals("Sign in", resolve("en", Res.string.login_title))
        assertEquals("Bon retour !", resolve("fr", Res.string.welcome_title))
        assertEquals("Welcome back", resolve("en", Res.string.welcome_title))
        assertEquals("Suis ton chantier à distance", resolve("fr", Res.string.onboarding_p1_title))
        assertEquals("Track your site from anywhere", resolve("en", Res.string.onboarding_p1_title))
    }

    @Test
    fun unsupported_system_language_falls_back_to_english() {
        assertEquals("Sign in", resolve("de", Res.string.login_title))
        assertEquals("Welcome back", resolve("es", Res.string.welcome_title))
    }

    @Test
    fun backend_error_type_maps_to_localized_message() {
        val res = DomainException.InvalidCredentials.textRes()
        assertEquals(Res.string.error_invalid_credentials, res)
        assertEquals("Email ou mot de passe incorrect.", resolve("fr", res))
        assertEquals("Incorrect email or password.", resolve("en", res))
    }

    private fun resolveError(languageTag: String, error: DomainException): String {
        var resolved by mutableStateOf("")
        runComposeUiTest {
            setContent {
                customAppLocale = languageTag
                AppEnvironment {
                    resolved = error.localizedText()
                }
            }
            waitForIdle()
        }
        return resolved
    }

    @Test
    fun rate_limited_message_states_the_wait_in_seconds_with_singular_and_plural() {
        assertEquals("Trop de tentatives. Réessayez dans 1 seconde.", resolveError("fr", DomainException.RateLimited(1)))
        assertEquals("Trop de tentatives. Réessayez dans 42 secondes.", resolveError("fr", DomainException.RateLimited(42)))
        assertEquals("Too many attempts. Try again in 1 second.", resolveError("en", DomainException.RateLimited(1)))
        assertEquals("Too many attempts. Try again in 42 seconds.", resolveError("en", DomainException.RateLimited(42)))
    }

    @Test
    fun rate_limited_message_switches_to_minutes_rounded_up_from_sixty_seconds() {
        assertEquals("Too many attempts. Try again in 1 minute.", resolveError("en", DomainException.RateLimited(60)))
        assertEquals("Too many attempts. Try again in 2 minutes.", resolveError("en", DomainException.RateLimited(61)))
        assertEquals("Trop de tentatives. Réessayez dans 3 minutes.", resolveError("fr", DomainException.RateLimited(180)))
    }

    @Test
    fun rate_limited_without_delay_falls_back_to_the_generic_message() {
        assertEquals("Trop de requêtes. Réessayez dans un instant.", resolveError("fr", DomainException.RateLimited()))
        assertEquals("Too many requests. Please try again shortly.", resolveError("en", DomainException.RateLimited()))
    }
}
