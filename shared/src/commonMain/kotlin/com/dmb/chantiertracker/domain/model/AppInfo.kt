package com.dmb.chantiertracker.domain.model

data class AppInfo(
    val platformName: String,
    val apiBaseUrl: String,
)

data class WelcomeMessage(
    val title: String,
    val platformName: String,
    val apiBaseUrl: String,
)
