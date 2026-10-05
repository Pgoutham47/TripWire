plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.tripwire.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tripwire.app"
        // PRD 14.3: Android 10 minimum, to be confirmed against the features used.
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The reference phone (iQOO 15) and Apple-silicon emulators are arm64; LiteRT-LM is large per ABI.
        ndk { abiFilters += "arm64-v8a" }

        // Optional endpoints, set in ~/.gradle/gradle.properties or with -P. Empty means "not configured":
        // the model is then pushed with adb, and only the bundled script pack is used.
        fun prop(name: String) = "\"" + (project.findProperty(name) as String? ?: "") + "\""
        buildConfigField("String", "MODEL_URL", prop("tripwire.modelUrl"))
        buildConfigField("String", "MODEL_SHA256", prop("tripwire.modelSha256"))
        buildConfigField("String", "PACK_URL", prop("tripwire.packUrl"))
        buildConfigField("String", "PACK_PUBLIC_KEY", prop("tripwire.packPublicKey"))
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Signed with the debug key until a release key exists; never ship this to Play.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }

    packaging {
        resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1", "META-INF/LICENSE*", "META-INF/NOTICE*")
        // LiteRT-LM ships native libraries; keep them uncompressed so they load from the APK.
        jniLibs.useLegacyPackaging = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher)
    implementation(libs.androidx.sqlite)

    implementation(libs.work.runtime)
    implementation(libs.datastore.preferences)

    implementation(libs.litertlm)

    testImplementation(libs.junit)
}
