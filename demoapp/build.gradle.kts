// A harmless stand-in for a scam "trading app", used in tests and the live demo (PRD 22).
// It has no permissions and does nothing but say what it is.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.tripwiredemo.satfinpro"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.tripwiredemo.satfinpro"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
