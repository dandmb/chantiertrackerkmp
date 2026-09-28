package com.dmb.chantiertracker.domain.model

/**
 * Type d'erreur métier, sans texte : le message affiché est résolu côté présentation
 * dans la langue de l'app (voir `presentation/i18n/ErrorText.kt`), jamais le texte
 * brut renvoyé par le backend.
 */
sealed class DomainException : Exception() {
    data object InvalidCredentials : DomainException()
    data object EmailAlreadyUsed : DomainException()
    data object AccountNotVerified : DomainException()
    data object AccountLocked : DomainException()
    // Cross-cutting, any endpoint could return it (a security filter, not a
    // specific business rule) — mapped globally in ApiError.kt, same posture
    // as AccountLocked/AccountNotVerified. Caught internally by
    // AuthRepositoryImpl (login/bootstrap) to drive AuthState.MustChangePassword,
    // never actually shown to the user as an error banner.
    data object MustChangePassword : DomainException()
    // Specific to POST /auth/change-password — the generic 400-without-field-
    // errors mapping (InvalidCode) would say "invalid or expired code",
    // nonsensical for a wrong current password. Remapped locally in
    // AuthRepositoryImpl.changePassword, same posture as other local remaps
    // (BillingRepositoryImpl, AdminRepositoryImpl).
    data object InvalidCurrentPassword : DomainException()
    data object InvalidCode : DomainException()
    data object Validation : DomainException()
    data class RateLimited(val retryAfterSeconds: Int? = null) : DomainException()
    data object PlanLimitReached : DomainException()
    data object Forbidden : DomainException()
    data object NotFound : DomainException()
    data object Network : DomainException()
    data object Unexpected : DomainException()

    override val message: String get() = this::class.simpleName ?: "DomainException"
}
