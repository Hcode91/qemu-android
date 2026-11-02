// SPDX-License-Identifier: GPL-2.0-or-later
plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "org.qemu.android"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    ndkVersion = "28.2.13676358"
    defaultConfig {
        applicationId = "org.qemu.android"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += "arm64-v8a" }
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }
    buildTypes {
        debug { applicationIdSuffix = ".dev"; versionNameSuffix = "-dev"; resValue("string", "app_name", "QEMU-Android Development") }
        release {
            isMinifyEnabled = false
            // Deliberately unsigned. CI signs with the persistent publisher key only.
            resValue("string", "app_name", "QEMU-Android")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { jniLibs { useLegacyPackaging = true; keepDebugSymbols += "**/libqemu-system-arm.so" } }
    lint { abortOnError = true; checkReleaseBuilds = true }
    testOptions { unitTests.isReturnDefaultValues = false }
}
dependencies { testImplementation("junit:junit:4.13.2") }
val verifyBackend by tasks.registering {
    doLast {
        val backend = file("src/main/jniLibs/arm64-v8a/libqemu-system-arm.so")
        check(backend.isFile && backend.length() > 1_000_000) { "Genuine QEMU backend missing: run the GitHub Actions native build." }
        check(file("src/main/assets/qemu/keymaps/en-us").let { it.isFile && it.length() > 0 }) {
            "QEMU runtime keymap missing: run the GitHub Actions native build."
        }
    }
}
// Source compilation, unit tests and lint can run before the expensive native build.
// Packaging always requires the genuine compiled backend; no placeholder is used.
tasks.matching { it.name == "packageDebug" || it.name == "packageRelease" }
    .configureEach { dependsOn(verifyBackend) }
