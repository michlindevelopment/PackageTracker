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
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    // Two shipping builds of the same app, same applicationId — installing one
    // replaces the other:
    //   full  — the normal app, reads the SMS inbox to match tracking numbers.
    //   nosms — SMS stripped entirely. READ_SMS is not in the merged manifest
    //           (it lives in src/full/AndroidManifest.xml), and SMS_ENABLED is
    //           a compile-time false so R8 removes the UI and the scanner from
    //           the release build rather than merely hiding them.
    // The Room schema is identical across flavors: the tracking_sms table is
    // still created, just never written to. That keeps migrations in lockstep
    // so a user can move between the two builds without a reinstall.
    flavorDimensions += "sms"
    productFlavors {
        create("full") {
            dimension = "sms"
            isDefault = true
            buildConfigField("boolean", "SMS_ENABLED", "true")
            buildConfigField("String", "UPDATE_APK_ASSET", "\"app-release.apk\"")
        }
        create("nosms") {
            dimension = "sms"
            buildConfigField("boolean", "SMS_ENABLED", "false")
            buildConfigField("String", "UPDATE_APK_ASSET", "\"app-release-nosms.apk\"")
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

// Gradle names flavored outputs app-<flavor>-<buildType>.apk. Rename them to
// the asset names the in-app updater looks for on the GitHub release —
// CheckForUpdateUseCase matches BuildConfig.UPDATE_APK_ASSET exactly, so each
// build only ever offers itself as an update.
// Deliberately not a Copy task: Copy is skipped as NO-SOURCE when a source
// file is missing, which would publish a release with one APK quietly absent.
// A plain task always runs its action, so the check below can't be bypassed.
val stageReleaseApks by tasks.registering {
    group = "publishing"
    description = "Collects both release APKs under outputs/release-apks with their published names."
    dependsOn("assembleFullRelease", "assembleNosmsRelease")

    val buildDir = layout.buildDirectory
    val outDir = layout.buildDirectory.dir("outputs/release-apks")
    val apks = mapOf(
        "outputs/apk/full/release/app-full-release.apk" to "app-release.apk",
        "outputs/apk/nosms/release/app-nosms-release.apk" to "app-release-nosms.apk"
    )

    doLast {
        val dest = outDir.get().asFile.apply { mkdirs() }
        apks.forEach { (from, to) ->
            val src = buildDir.file(from).get().asFile
            check(src.exists()) {
                "Expected release APK not found: $src\n" +
                    "Release signing must be configured in local.properties — an unsigned " +
                    "build emits app-<flavor>-release-unsigned.apk instead."
            }
            src.copyTo(dest.resolve(to), overwrite = true)
        }
    }
}

// Build both signed release APKs and upload them to GitHub Releases via the gh
// CLI. Prereqs (one-time): install gh, then run `gh auth login` once.
// Usage: ./gradlew publishRelease
val publishRelease by tasks.registering(Exec::class) {
    group = "publishing"
    description = "Builds both release APKs and creates a GitHub Release with them attached."
    dependsOn(stageReleaseApks)

    val versionName = android.defaultConfig.versionName ?: "0.0"
    val tag = "v$versionName"
    val staged = layout.buildDirectory.dir("outputs/release-apks").get()

    workingDir = rootDir
    commandLine(
        "gh", "release", "create", tag,
        staged.file("app-release.apk").asFile.absolutePath,
        staged.file("app-release-nosms.apk").asFile.absolutePath,
        "--title", tag,
        "--generate-notes"
    )
}
