package com.dmb.chantiertracker.domain.model

data class User(
    val id: Long,
    val email: String,
    val name: String,
    val active: Boolean,
    val globalRole: GlobalRole,
)

enum class GlobalRole { USER, SUPER_ADMIN, UNKNOWN }
