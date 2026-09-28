package com.dmb.chantiertracker.presentation.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Langue forcée par l'utilisateur, ou `null` pour suivre la langue du système.
 * Aujourd'hui toujours `null` : la détection automatique de compose-resources suffit
 * (système en français → `values-fr`, tout le reste → `values` = anglais).
 * Un futur écran de réglages n'aura qu'à appeler [applyLanguage] et persister le choix.
 */
var customAppLocale: String? by mutableStateOf(null)

enum class AppLanguage(val localeTag: String?) {
    System(null),
    French("fr"),
    English("en"),
}

fun applyLanguage(language: AppLanguage) {
    customAppLocale = language.localeTag
}

expect object LocalAppLocale {
    val current: String
        @Composable get

    @Composable
    infix fun provides(value: String?): ProvidedValue<*>
}

/**
 * Enveloppe l'arbre Compose pour que [customAppLocale] pilote la résolution des
 * ressources (`stringResource`) et des formats régionaux.
 */
@Composable
fun AppEnvironment(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalAppLocale provides customAppLocale,
    ) {
        key(customAppLocale) {
            content()
        }
    }
}
