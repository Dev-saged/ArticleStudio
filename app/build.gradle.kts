import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val props = Properties().apply { file("../gradle.properties").inputStream().use { load(it) } }
val keystorePath: String? = System.getenv("SIGNING_STORE_FILE")?.takeIf { it.isNotBlank() }

android {
    namespace = "com.sajed.articlestudio"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.sajed.articlestudio"
        minSdk = 26
        targetSdk = 36
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: props.getProperty("VERSION_CODE").toInt()
        versionName = props.getProperty("VERSION_NAME")
    }
    signingConfigs {
        if (keystorePath != null) create("release") {
            storeFile = file(keystorePath)
            storePassword = System.getenv("SIGNING_STORE_PASSWORD")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets { getByName("debug").assets.srcDir("$projectDir/schemas") }
    testOptions { unitTests { isReturnDefaultValues = true; isIncludeAndroidResources = true } }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.tooling)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.vm)
    implementation(libs.lifecycle.rt)
    implementation(libs.navigation)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.nav)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.coroutines)
    implementation(libs.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.play.auth)
    implementation(libs.work)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.test.core)
}
