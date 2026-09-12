package com.dmb.chantiertracker.presentation.billing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Bridges platform-level URL capture (Android MainActivity.onNewIntent, iOS
// onOpenURL) — neither of which composes anything — into Compose state that
// MainScreen can react to. A singleton, not scoped to any screen: the URL
// can arrive before MainScreen even exists yet (cold start while the app was
// killed) or long after BillingScreen itself has been left, so nothing
// screen-scoped (a ViewModel included) could reliably hold it.
class CheckoutDeepLinkDispatcher {
    private val _pending = MutableStateFlow<CheckoutDeepLink?>(null)
    val pending: StateFlow<CheckoutDeepLink?> = _pending.asStateFlow()

    // Silently ignores anything parseCheckoutDeepLink doesn't recognize —
    // this is the single entry point for every chantiertracker:// open the
    // OS ever delivers, not just the ones this feature defined.
    fun dispatch(url: String) {
        parseCheckoutDeepLink(url)?.let { _pending.value = it }
    }

    // Called once MainScreen has acted on the pending value, so it isn't
    // replayed on the next recomposition (rotation, tab switch, etc.).
    fun consume() {
        _pending.value = null
    }
}
