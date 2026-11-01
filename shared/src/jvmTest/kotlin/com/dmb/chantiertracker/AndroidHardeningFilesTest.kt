package com.dmb.chantiertracker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidHardeningFilesTest {

    private val androidApp = File("../androidApp")
    private fun read(path: String) = File(androidApp, path).readText()

    @Test
    fun the_shipped_manifest_refuses_cleartext_and_backups() {
        val manifest = read("src/main/AndroidManifest.xml")
        assertTrue("android:networkSecurityConfig=\"@xml/network_security_config\"" in manifest)
        assertTrue("android:usesCleartextTraffic=\"false\"" in manifest)
        assertTrue("android:allowBackup=\"false\"" in manifest)
        assertFalse("android:debuggable" in manifest)
    }

    @Test
    fun the_shipped_network_config_trusts_only_the_system_over_https() {
        val config = read("src/main/res/xml/network_security_config.xml")
        assertTrue("cleartextTrafficPermitted=\"false\"" in config)
        assertFalse("cleartextTrafficPermitted=\"true\"" in config)
        assertFalse("src=\"user\"" in config)
        assertTrue("src=\"system\"" in config)
    }

    @Test
    fun the_debug_exception_stays_in_the_debug_sources() {
        assertFalse(File(androidApp, "src/release/res/xml/network_security_config.xml").exists())
        assertFalse(File(androidApp, "src/staging/res/xml/network_security_config.xml").exists())
    }

    @Test
    fun release_builds_are_shrunk() {
        val release = read("build.gradle.kts").substringAfter("buildTypes {").substringBefore("create(\"staging\")")
        assertTrue("isMinifyEnabled = true" in release)
        assertTrue("isShrinkResources = true" in release)
    }

    @Test
    fun the_file_provider_shares_neither_the_token_file_nor_the_database() {
        val paths = read("src/main/res/xml/filekit_file_paths.xml")
        assertFalse("<files-path name=\"filekit_files\" path=\".\"" in paths)
        assertTrue("path=\"attachments/\"" in paths)
    }

    @Test
    fun no_local_address_is_written_in_the_android_sources() {
        assertFalse("192.168" in File("src/androidMain/kotlin/com/dmb/chantiertracker/core/BuildInfo.android.kt").readText())
    }
}
