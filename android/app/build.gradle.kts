import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "dev.aprax.tmaii"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.aprax.tmaii"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Backend base URL is build-time default ONLY. No secrets live here —
        // provider API keys exist exclusively on the backend.
        buildConfigField("String", "DEFAULT_BASE_URL", "\"https://your-backend.example.com\"")
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("RELEASE_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
                ?: (rootProject.projectDir.resolve("release.keystore").takeIf { it.exists() }?.absolutePath)
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "aprax"
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")?.takeIf { it.isNotBlank() }
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
                .takeIf { it.storeFile?.exists() == true && !it.storePassword.isNullOrEmpty() }
                ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":feature"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.compose.bom)
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(platform(libs.compose.bom))

    implementation(libs.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.ext.compiler)

    implementation(libs.work.runtime.ktx)
    implementation(libs.androidx.lifecycle.work)

    testImplementation(libs.junit)
}
