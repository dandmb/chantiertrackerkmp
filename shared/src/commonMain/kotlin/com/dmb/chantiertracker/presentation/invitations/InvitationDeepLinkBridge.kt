package com.dmb.chantiertracker.presentation.invitations

import com.dmb.chantiertracker.data.sync.AppCoroutineScope
import com.dmb.chantiertracker.domain.model.AuthState
import com.dmb.chantiertracker.domain.repository.AuthRepository
import com.dmb.chantiertracker.presentation.billing.UrlOpener
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatformTools

// ADR-59 — platform entry points (Android MainActivity.onNewIntent/onCreate)
// call this directly, same reasoning as dispatchCheckoutDeepLink: androidApp
// has no Koin on its own compile classpath. The one thing this bridge does
// that dispatchCheckoutDeepLink doesn't is decide, right here, whether the
// current user can even use the in-app acceptance screen — a real App Link
// can fire while logged out (the app was merely installed, not necessarily
// signed in), unlike chantiertracker://, which only ever returns from a
// checkout this same session started. Authenticated: hand the token to
// InvitationDeepLinkDispatcher, same funnel-into-Compose idiom as billing.
// Anything else (Unauthenticated, MustChangePassword, still Unknown at cold
// start): reopen the same URL in the system browser, which already handles
// every one of those states correctly (InvitationAcceptPage web) — no native
// login/registration flow to duplicate.
fun dispatchInvitationDeepLink(url: String) {
    val token = parseInvitationToken(url) ?: return
    val koin = KoinPlatformTools.defaultContext().getOrNull() ?: return
    when (koin.get<AuthRepository>().authState.value) {
        is AuthState.Authenticated -> koin.get<InvitationDeepLinkDispatcher>().dispatch(token)
        else -> {
            val urlOpener = koin.get<UrlOpener>()
            koin.get<AppCoroutineScope>().launch { urlOpener.open(url) }
        }
    }
}
