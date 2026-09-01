package com.dmb.chantiertracker.domain.model

sealed interface AuthState {
    data object Unknown : AuthState
    data class Authenticated(val user: User) : AuthState
    data object Unauthenticated : AuthState
}
