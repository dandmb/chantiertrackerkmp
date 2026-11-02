package com.dmb.chantiertracker.domain.model

data class User(
    val id: Long,
    val email: String,
    val name: String,
    val active: Boolean,
    val globalRole: GlobalRole,
    val phone: String? = null,
    val language: ProfileLanguage? = null,
)

enum class GlobalRole { USER, SUPER_ADMIN, UNKNOWN }
