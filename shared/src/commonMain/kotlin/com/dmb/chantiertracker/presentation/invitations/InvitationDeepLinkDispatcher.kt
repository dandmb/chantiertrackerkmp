package com.dmb.chantiertracker.presentation.invitations

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ADR-59 — same shape as CheckoutDeepLinkDispatcher: a singleton, not
// scoped to any screen, because the token can arrive before MainScreen
// exists yet (cold start) or long after whichever screen was current has
// been left. Only ever fed a token once InvitationDeepLinkBridge has
// already confirmed the current user is authenticated — see that file for
// why the logged-out case never reaches this dispatcher at all.
class InvitationDeepLinkDispatcher {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun dispatch(token: String) {
        _pending.value = token
    }

    // Called once MainScreen has navigated to the acceptance screen, so the
    // same token isn't replayed on the next recomposition (rotation, tab
    // switch, etc.).
    fun consume() {
        _pending.value = null
    }
}
