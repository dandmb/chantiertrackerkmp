package com.dmb.chantiertracker.domain.model

sealed interface AuthState {
    data object Unknown : AuthState
    data class Authenticated(val user: User) : AuthState
    // The backend forces a password change for any admin-issued account
    // (bootstrap super-admin, or POST /admin/users) — every endpoint except
    // /auth/change-password and /auth/logout stays 403'd until it succeeds.
    // Never carries a User: /users/me is itself blocked in this state.
    data class MustChangePassword(val email: String) : AuthState
    data object Unauthenticated : AuthState
}
