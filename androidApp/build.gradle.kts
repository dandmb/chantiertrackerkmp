import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import java.net.URI
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.firebaseCrashlytics)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)

    releaseImplementation(platform(libs.firebase.bom))
    releaseImplementation(libs.firebase.crashlytics)
    releaseImplementation(libs.kotlinx.coroutines.core)
    releaseImplementation(libs.koin.core)
}

// Debug builds only: the shipped APK must not carry a local address.
val devApiBaseUrl = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use { load(it) }
}.getProperty("chantiertracker.devApiBaseUrl").orEmpty()

abstract class DebugNetworkSecurityConfigTask : DefaultTask() {
    @get:InputFile abstract val template: RegularFileProperty
    @get:Input abstract val localApiHost: Property<String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun write() {
        val host = localApiHost.get()
        val text = template.get().asFile.readText().let { config ->
            if (host.isBlank()) config.replace(Regex("""\s*<domain-config[\s\S]*?</domain-config>"""), "")
            else config.replace("@LOCAL_API_HOST@", host)
        }
        outputDir.get().file("xml/network_security_config.xml").asFile.apply {
            parentFile.mkdirs()
            writeText(text)
        }
    }
}

// Debug builds only: cleartext is allowed to the host of devApiBaseUrl and nowhere else.
val debugNetworkSecurityConfig = tasks.register<DebugNetworkSecurityConfigTask>("generateDebugNetworkSecurityConfig") {
    template.set(file("network/debug_network_security_config.xml"))
    localApiHost.set(runCatching { URI(devApiBaseUrl).host }.getOrNull().orEmpty())
    outputDir.set(layout.buildDirectory.dir("generated/debugNetworkSecurityConfig/res"))
}

androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(debugNetworkSecurityConfig, DebugNetworkSecurityConfigTask::outputDir)
    }
}

// Distribution signing: values come from CI env vars (see .github/workflows/cd-stage.yml).
// Absent locally → the build stays unsigned, which is fine for local inspection.
val distributionKeystore = providers.environmentVariable("ANDROID_KEYSTORE_FILE").orNull
    ?.let(::file)
    ?.takeIf { it.exists() }

// Play Store signing (upload key): same idea, its own variables (see .github/workflows/release-android.yml).
val uploadKeystore = providers.environmentVariable("RELEASE_KEYSTORE_FILE").orNull
    ?.let(::file)
    ?.takeIf { it.exists() }

// Firebase of the Play Store build, from CI variables: without them Crashlytics stays off.
val firebaseRelease = listOf("FIREBASE_RELEASE_APP_ID", "FIREBASE_RELEASE_API_KEY", "FIREBASE_PROJECT_ID")
    .map { providers.environmentVariable(it).orNull.orEmpty() }
    .takeIf { values -> values.all { it.isNotBlank() } }
// Firebase of the staging build (package .staging, its own Firebase app): without them Crashlytics stays off.
val firebaseStaging = listOf("FIREBASE_STAGING_APP_ID", "FIREBASE_STAGING_API_KEY", "FIREBASE_PROJECT_ID")
    .map { providers.environmentVariable(it).orNull.orEmpty() }
    .takeIf { values -> values.all { it.isNotBlank() } }
    ?: listOf("", "", "")
val crashlyticsTestCrash = providers.gradleProperty("crashlyticsTestCrash").orNull == "true"

android {
    namespace = "com.dmb.chantiertracker"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.dmb.chantiertracker"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // CI passes -PversionCode / -PversionName; local builds fall back to 1 / "1.0.0".
        versionCode = providers.gradleProperty("versionCode").orNull?.toInt() ?: 1
        versionName = providers.gradleProperty("versionName").orNull ?: "1.0.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        if (distributionKeystore != null) {
            create("distribution") {
                storeFile = distributionKeystore
                storePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
            }
        }
        if (uploadKeystore != null) {
            create("upload") {
                storeFile = uploadKeystore
                storePassword = providers.environmentVariable("RELEASE_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").orNull
            }
        }
    }
    buildTypes {
        debug {
            resValue("string", "dev_api_base_url", devApiBaseUrl)
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // No Google-Services plugin here, so the upload task cannot even be created.
            configure<CrashlyticsExtension> { mappingFileUploadEnabled = false }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("upload")
            firebaseRelease?.let { (appId, apiKey, projectId) ->
                resValue("string", "google_app_id", appId)
                resValue("string", "google_api_key", apiKey)
                resValue("string", "project_id", projectId)
            }
            resValue("bool", "crashlytics_test_crash", crashlyticsTestCrash.toString())
        }
        // The build shipped to testers via Firebase App Distribution (branch `stage`).
        // Same code and same (production) backend as a real release — the `.staging`
        // package id, the distinct launcher name (src/staging/res) and the permanent
        // in-app PRÉPROD banner keep it unmistakable. See MOBILE_CONTEXT §14 / ADR-24.
        create("staging") {
            initWith(getByName("release"))
            applicationIdSuffix = ".staging"
            matchingFallbacks += "release"
            signingConfig = signingConfigs.findByName("distribution")
            configure<CrashlyticsExtension> { mappingFileUploadEnabled = false }
            val (appId, apiKey, projectId) = firebaseStaging
            resValue("string", "google_app_id", appId)
            resValue("string", "google_api_key", apiKey)
            resValue("string", "project_id", projectId)
        }
    }
    sourceSets {
        getByName("staging").kotlin.srcDir("src/release/kotlin")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        resValues = true
    }
}

dependencies {
    "stagingImplementation"(platform(libs.firebase.bom))
    "stagingImplementation"(libs.firebase.crashlytics)
    "stagingImplementation"(libs.kotlinx.coroutines.core)
    "stagingImplementation"(libs.koin.core)
}
