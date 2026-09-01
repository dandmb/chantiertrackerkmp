package com.dmb.chantiertracker.core

enum class ApiEnvironment(val baseUrl: String) {
    Production("https://api.chantiertracker.com/api/v1"),
    Development("http://localhost:8080/api/v1"),
}

object AppConfig {
    // TODO(auth): piloter environment + enableNetworkLogging par le build type.
    val environment: ApiEnvironment = ApiEnvironment.Production
    val baseUrl: String get() = environment.baseUrl
    val enableNetworkLogging: Boolean get() = environment != ApiEnvironment.Production
}
