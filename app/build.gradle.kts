plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.droidscan"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.droidscan.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 3
        versionName = "0.9.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // Side-by-side test build: installs separately from the main app under
        // applicationId ".test" with an amber launcher icon + "droidscan test"
        // label (overrides in src/staging/). Debug-only APIs stay available so
        // it behaves like a debug build.
        create("staging") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".test"
            versionNameSuffix = "-test"
            // Dependencies (:shared-core, :telemetry-collector) only build
            // debug/release — consume their debug variants for the staging app.
            matchingFallbacks += "debug"
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
    }
}

dependencies {
    implementation(project(":shared-core"))
    implementation(project(":telemetry-collector"))
    implementation(project(":diagnostics"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.shizuku.provider)

    debugImplementation(libs.androidx.ui.tooling)
}
