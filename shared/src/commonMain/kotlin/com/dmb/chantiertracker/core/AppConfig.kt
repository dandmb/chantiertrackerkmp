package com.dmb.chantiertracker.core

enum class ApiEnvironment { Production, Development }

expect val platformDevApiBaseUrl: String

private const val PROD_API_BASE_URL = "https://api.chantiertracker.com/api/v1"

class AppConfig(buildInfo: BuildInfo) {

    val environment: ApiEnvironment =
        if (buildInfo.isDebug) ApiEnvironment.Development else ApiEnvironment.Production

    val baseUrl: String = when (environment) {
        ApiEnvironment.Production -> PROD_API_BASE_URL
        ApiEnvironment.Development -> buildInfo.devApiBaseUrl
    }

    val enableNetworkLogging: Boolean = environment != ApiEnvironment.Production

    val appVersion: String = buildInfo.appVersion
}
