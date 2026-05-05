import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val signingProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val signingStoreFile: String? = signingProps.getProperty("signing.storeFile")
val signingStorePassword: String? = signingProps.getProperty("signing.storePassword")
val signingKeyAlias: String? = signingProps.getProperty("signing.keyAlias")
val signingKeyPassword: String? = signingProps.getProperty("signing.keyPassword")
val hasReleaseSigning = listOf(
    signingStoreFile, signingStorePassword, signingKeyAlias, signingKeyPassword
).all { !it.isNullOrBlank() }

// Version is stored in version.properties (committed). The patch number
// auto-bumps whenever `publishRelease` is invoked; major/minor are edited
// by hand. versionCode is derived as major*10000 + minor*100 + patch so
// 1.0.10 (10010) > 1.0.9 (10009) and 1.1.0 (10100) > 1.0.99 (10099).
val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) versionPropsFile.inputStream().use { load(it) }
}
val versionMajor = versionProps.getProperty("version.major", "1").toInt()
val versionMinor = versionProps.getProperty("version.minor", "0").toInt()
var versionPatch = versionProps.getProperty("version.patch", "0").toInt()

val isPublishing = gradle.startParameter.taskNames.any {
    it == "publishRelease" || it.endsWith(":publishRelease")
}
if (isPublishing) {
    versionPatch += 1
    versionProps.setProperty("version.patch", versionPatch.toString())
    versionPropsFile.outputStream().use { versionProps.store(it, "Version — patch auto-bumped by publishRelease") }
}

val computedVersionName = "$versionMajor.$versionMinor.$versionPatch"
val computedVersionCode = versionMajor * 10000 + versionMinor * 100 + versionPatch

android {
    namespace = "com.michlind.packagetracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.michlind.packagetracker"
        minSdk = 26
        targetSdk = 36
        versionCode = computedVersionCode
        versionName = computedVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(signingStoreFile!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)

    // Compose BOM
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime)

    // Retrofit / OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Coil
    implementation(libs.coil.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines)

    // Gson
    implementation(libs.gson)

    // Accompanist
    implementation(libs.accompanist.permissions)

    // Image Cropper (CanHub fork of Edmodo cropper) — requires AppCompat for its activity
    implementation(libs.image.cropper)
    implementation(libs.androidx.appcompat)


    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

// Build a signed release APK and upload it to GitHub Releases via the gh CLI.
// Prereqs (one-time): install gh, then run `gh auth login` once.
// Usage: ./gradlew publishRelease
val publishRelease by tasks.registering(Exec::class) {
    group = "publishing"
    description = "Builds the release APK and creates a GitHub Release with it attached."
    dependsOn("assembleRelease")

    val versionName = android.defaultConfig.versionName ?: "0.0"
    val tag = "v$versionName"
    val apk = layout.buildDirectory.file("outputs/apk/release/app-release.apk")

    workingDir = rootDir
    commandLine(
        "gh", "release", "create", tag,
        apk.get().asFile.absolutePath,
        "--title", tag,
        "--generate-notes"
    )
}
