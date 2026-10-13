import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.hootkit.platform"
    compileSdk = 37
    ndkVersion = "28.2.13676358"
    defaultConfig {
        minSdk = 26
        // The native smoke package supports only the four pinned Core ABIs, not JNA historical ABIs.
        ndk { abiFilters += setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }
    testOptions { unitTests.isIncludeAndroidResources = true }
    packaging { jniLibs.keepDebugSymbols += "**/libuniffi_hootkit_core.so" }
}

dependencies {
    api(project(":core-bindings"))
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.okhttp.tls)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.junit)
    // The manifest runner needs an actual class in the instrumentation APK, not only test APIs.
    androidTestImplementation("androidx.test:runner:1.7.0")
}
