package com.dmb.chantiertracker.domain.model

enum class ProfileLanguage(val code: String) {
    FRENCH("fr"),
    ENGLISH("en"),
    ;

    companion object {
        fun fromCode(code: String?): ProfileLanguage? = entries.firstOrNull { it.code == code?.lowercase() }
    }
}

enum class ProfileField(val serverName: String) {
    NAME("name"),
    PHONE("phone"),
    LANGUAGE("language"),
}

data class ProfileUpdate(val name: String, val phone: String, val language: ProfileLanguage)
