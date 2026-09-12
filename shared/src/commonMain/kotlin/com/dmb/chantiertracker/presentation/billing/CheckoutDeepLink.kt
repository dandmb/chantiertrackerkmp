package com.dmb.chantiertracker.presentation.billing

// ADR-51 point 4 — the three destinations chantiertracker:// can carry back
// into the app: a completed checkout, a cancelled one, or a return from the
// "Gérer mon abonnement" portal (which has no success/cancel of its own —
// Stripe's portal API only ever gives a single unconditional return point).
sealed interface CheckoutDeepLink {
    data object CheckoutSuccess : CheckoutDeepLink
    data object CheckoutCancelled : CheckoutDeepLink
    data object PortalReturn : CheckoutDeepLink
}

private const val SCHEME_PREFIX = "chantiertracker://"

// Pure, tolerant by design (same idiom as resolveCheckoutIntent/
// LoginNotice.fromArg — never throws on an unrecognized URL). Matches on the
// host only, not the whole string: a browser or OS URI layer could append a
// trailing slash or stray query string to what Stripe was actually given, and
// none of that should turn a real return into a silently dropped one.
fun parseCheckoutDeepLink(url: String): CheckoutDeepLink? {
    if (!url.startsWith(SCHEME_PREFIX, ignoreCase = true)) return null
    val host = url.substring(SCHEME_PREFIX.length).substringBefore('/').substringBefore('?').lowercase()
    return when (host) {
        "checkout-success" -> CheckoutDeepLink.CheckoutSuccess
        "checkout-cancel" -> CheckoutDeepLink.CheckoutCancelled
        "billing-return" -> CheckoutDeepLink.PortalReturn
        else -> null
    }
}
