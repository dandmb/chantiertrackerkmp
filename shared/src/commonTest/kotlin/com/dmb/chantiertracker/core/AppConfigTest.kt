package com.dmb.chantiertracker.core

import com.dmb.chantiertracker.support.FakeBuildInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppConfigTest {

    @Test
    fun release_build_targets_production_api() {
        val config = AppConfig(FakeBuildInfo(isDebug = false))
        assertEquals(ApiEnvironment.Production, config.environment)
        assertEquals("https://api.chantiertracker.com/api/v1", config.baseUrl)
    }

    @Test
    fun debug_build_targets_development_api() {
        val config = AppConfig(FakeBuildInfo(isDebug = true))
        assertEquals(ApiEnvironment.Development, config.environment)
        assertEquals(devApiBaseUrl, config.baseUrl)
    }

    @Test
    fun network_logging_only_outside_production() {
        assertFalse(AppConfig(FakeBuildInfo(isDebug = false)).enableNetworkLogging)
        assertTrue(AppConfig(FakeBuildInfo(isDebug = true)).enableNetworkLogging)
    }
}
