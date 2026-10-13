plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}
val ldplayerCompat = providers.gradleProperty("ldplayer").map(String::toBoolean).orElse(false).get()
val mobileCompat = providers.gradleProperty("mobile").map(String::toBoolean).orElse(false).get()
val targetAbi = providers.gradleProperty("abi").orElse("").get()
require(!(ldplayerCompat && mobileCompat)) { "Choose either -Pldplayer=true or -Pmobile=true" }
android {
    namespace = "dev.joyfin"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.joyfin"
        minSdk = if (ldplayerCompat) 28 else 31
        manifestPlaceholders["leanbackRequired"] = false
        manifestPlaceholders["browseOrientation"] = if (mobileCompat) "portrait" else "unspecified"
        val abi = targetAbi.ifBlank { if (mobileCompat) "arm64-v8a" else "" }
        if (abi.isNotBlank()) ndk { abiFilters += abi }
        targetSdk = 37
        versionCode = 3
        versionName = when {
            ldplayerCompat -> "0.2.1-ldplayer"
            mobileCompat -> "0.2.1-mobile"
            else -> "0.2.1"
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Device comparison build: optimized like release, signed for upgrading existing debug installs.
        create("preview") {
            initWith(getByName("release"))
            signingConfig = getByName("debug").signingConfig
            matchingFallbacks += "release"
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions { unitTests.isReturnDefaultValues = true }
}
kotlin {
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.tv:tv-material:1.1.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:3.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")
    implementation("androidx.media3:media3-effect:1.11.1")
    implementation("io.github.peerless2012:ass-media:0.5.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
