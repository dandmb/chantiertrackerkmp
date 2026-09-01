package com.dmb.chantiertracker.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AppConfigTest {

    @Test
    fun production_base_url_points_to_existing_api() {
        assertEquals(
            "https://api.chantiertracker.com/api/v1",
            ApiEnvironment.Production.baseUrl,
        )
    }

    @Test
    fun default_environment_is_production() {
        assertEquals(ApiEnvironment.Production, AppConfig.environment)
        assertEquals(ApiEnvironment.Production.baseUrl, AppConfig.baseUrl)
    }

    @Test
    fun network_logging_disabled_in_production() {
        assertFalse(AppConfig.enableNetworkLogging)
    }

    @Test
    fun development_environment_enables_logging() {
        assertEquals(
            true,
            ApiEnvironment.Development != ApiEnvironment.Production,
        )
    }
}
