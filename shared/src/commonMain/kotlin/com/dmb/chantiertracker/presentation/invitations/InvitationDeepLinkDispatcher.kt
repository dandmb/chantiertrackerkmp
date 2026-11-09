package com.dmb.chantiertracker.presentation.invitations

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
        _pending.value?.let { held = it }
        _pending.value = null
    }

    fun release(token: String) {
        if (held == token) held = null
    }

    fun reopenHeld() {
        held?.let { if (_pending.value == null) _pending.value = it }
    }

    private var held: String? = null
}
