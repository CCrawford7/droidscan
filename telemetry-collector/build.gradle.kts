plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.droidscan.telemetry"
    compileSdk = 35

    defaultConfig {
        minSdk = 29
    }

    buildFeatures {
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true // android.util.Log etc. no-op in JVM tests
    }
}

dependencies {
    implementation(project(":shared-core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    compileOnly(libs.shizuku.api)
    testImplementation(libs.junit)
    // MetricHistoryStore's default Json param needs the runtime on the test
    // classpath (shared-core exposes it via implementation, not api).
    testImplementation(libs.kotlinx.serialization.json)
    // ShizukuBridge's lazy holds a reference to Shizuku$UserServiceArgs that the
    // JVM resolves at class-load; make the stub resolvable so constructing a
    // TelemetryCollector (with its default ShizukuDetector) works in JVM tests.
    // The lazy is never invoked, so no binder calls happen.
    testImplementation(libs.shizuku.api)
}
