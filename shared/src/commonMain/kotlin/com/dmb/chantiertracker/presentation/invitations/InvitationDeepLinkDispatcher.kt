package com.dmb.chantiertracker.presentation.invitations

import com.dmb.chantiertracker.data.local.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class InvitationDeepLinkDispatcher(private val preferences: AppPreferences? = null) {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    private var held: String? = preferences?.read(KEPT_TOKEN_KEY)

    private val _openedByLink = MutableStateFlow<String?>(null)
    val openedByLink: StateFlow<String?> = _openedByLink.asStateFlow()

    fun dispatch(token: String) {
        _openedByLink.value = token
        keep(token)
        _pending.value = token
    }

    fun claimLinkOpening(token: String): Boolean {
        if (_openedByLink.value != token) return false
        _openedByLink.value = null
        return true
    }

    // Called once MainScreen has navigated to the acceptance screen, so the
    // same token isn't replayed on the next recomposition (rotation, tab
    // switch, etc.).
    fun consume() {
        _pending.value?.let(::keep)
        _pending.value = null
        _openedByLink.value = null
    }

    fun release(token: String) {
        if (held == token) keep(null)
        if (_pending.value == token) _pending.value = null
        if (_openedByLink.value == token) _openedByLink.value = null
    }

    fun forget() {
        (_pending.value ?: held)?.let(::release)
    }

    fun reopenHeld() {
        held?.let { if (_pending.value == null) _pending.value = it }
    }

    private fun keep(token: String?) {
        held = token
        preferences?.write(KEPT_TOKEN_KEY, token)
    }

    private companion object {
        const val KEPT_TOKEN_KEY = "pending_invitation_token"
    }
}
