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
    data object InvalidCode : DomainException()
    data object Validation : DomainException()
    data object RateLimited : DomainException()
    data object Network : DomainException()
    data object Unexpected : DomainException()

    override val message: String get() = this::class.simpleName ?: "DomainException"
}
