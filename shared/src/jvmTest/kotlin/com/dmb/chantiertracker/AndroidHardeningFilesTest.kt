package com.dmb.chantiertracker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidHardeningFilesTest {

    private val androidApp = File("../androidApp")
    private fun read(path: String) = File(androidApp, path).readText()

    @Test
    fun the_shipped_manifest_refuses_cleartext_and_backups_without_a_network_config() {
        val manifest = read("src/main/AndroidManifest.xml")
        assertFalse("networkSecurityConfig" in manifest, "release and staging carry no network config")
        assertTrue("android:usesCleartextTraffic=\"false\"" in manifest)
        assertTrue("android:allowBackup=\"false\"" in manifest)
        assertFalse("android:debuggable" in manifest)
        assertFalse(File(androidApp, "src/main/res/xml/network_security_config.xml").exists())
    }

    @Test
    fun only_the_debug_build_declares_the_network_config() {
        assertTrue("android:networkSecurityConfig=\"@xml/network_security_config\"" in read("src/debug/AndroidManifest.xml"))
        listOf("release", "staging").forEach { type ->
            assertFalse(File(androidApp, "src/$type/AndroidManifest.xml").takeIf { it.exists() }?.readText().orEmpty().contains("networkSecurityConfig"), type)
            assertFalse(File(androidApp, "src/$type/res/xml/network_security_config.xml").exists(), type)
        }
    }

    @Test
    fun the_debug_network_config_opens_cleartext_to_the_local_api_host_only() {
        assertFalse(File(androidApp, "src/debug/res/xml/network_security_config.xml").exists(), "generated from local.properties, not versioned")
        assertTrue("debug_network_security_config.xml" in read("build.gradle.kts"))
        val config = read("network/debug_network_security_config.xml").replace("@LOCAL_API_HOST@", "10.0.2.2")
        assertTrue("<base-config cleartextTrafficPermitted=\"false\">" in config)
        assertTrue("<domain-config cleartextTrafficPermitted=\"true\">" in config)
        assertTrue("<domain includeSubdomains=\"false\">10.0.2.2</domain>" in config)
        assertFalse("src=\"user\"" in config)
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
    fun the_local_api_address_is_written_in_no_versioned_file() {
        val localAddress = Regex("""192\.168\.|10\.0\.2\.2|localhost|127\.0\.0\.1""")
        listOf(
            "src/androidMain/kotlin/com/dmb/chantiertracker/core/BuildInfo.android.kt",
            "src/jvmMain/kotlin/com/dmb/chantiertracker/core/BuildInfo.jvm.kt",
            "src/iosMain/kotlin/com/dmb/chantiertracker/core/BuildInfo.ios.kt",
            "build.gradle.kts",
            "../androidApp/build.gradle.kts",
            "../androidApp/network/debug_network_security_config.xml",
            "../desktopApp/build.gradle.kts",
        ).forEach { path -> assertFalse(localAddress.containsMatchIn(File(path).readText()), path) }
    }

    @Test
    fun every_platform_takes_the_local_api_address_from_local_properties() {
        listOf("build.gradle.kts", "../androidApp/build.gradle.kts", "../desktopApp/build.gradle.kts").forEach { path ->
            assertTrue("chantiertracker.devApiBaseUrl" in File(path).readText(), path)
        }
    }
}
