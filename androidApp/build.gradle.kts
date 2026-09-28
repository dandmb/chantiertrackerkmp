import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
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
}

// Distribution signing: values come from CI env vars (see .github/workflows/cd-stage.yml).
// Absent locally → the build stays unsigned, which is fine for local inspection.
val distributionKeystore = providers.environmentVariable("ANDROID_KEYSTORE_FILE").orNull
    ?.let(::file)
    ?.takeIf { it.exists() }

android {
    namespace = "com.dmb.chantiertracker"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.dmb.chantiertracker"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // CI passes -PversionCode / -PversionName; local builds fall back to 1 / "1.0".
        versionCode = providers.gradleProperty("versionCode").orNull?.toInt() ?: 1
        versionName = providers.gradleProperty("versionName").orNull ?: "1.0"
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
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}
