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

// Every release carries BOTH APKs:
//
//   AliTrack.apk              the app, under its real name. The website links
//                             here and CheckForUpdateUseCase prefers it.
//   app-release.apk           the SAME bytes under the old name, kept only so
//                             installs of 1.3.1 and earlier can still find an
//                             update — their updater matches that exact string
//                             and gives up silently if it is missing. Removable
//                             once nobody is left on <= 1.3.1; anyone still on
//                             an older build when it goes is stranded for good.
//   AliTrack-SMS-Plugin.apk   the companion that holds READ_SMS. Named for
//                             humans because users download it by hand.
//
// They ship together rather than the plugin getting its own GitHub Release,
// and that is a deliberate safety choice. GitHub grants "Latest" to exactly one
// release, the in-app updater asks for /releases/latest, and the website's
// download button resolves through /releases/latest/download/. A separate
// plugin release would take that slot the moment it was published, and then the
// updater would read a tag it can't parse, conclude everyone is up to date, and
// silently stop offering updates forever. One release can't do that to itself.
//
// The plugin versions independently and rarely changes; re-uploading the same
// ~60 KB each time is the price of that guarantee.
//
// Deliberately not a Copy task: Copy is skipped as NO-SOURCE when a source file
// is missing, which would publish a release with an APK quietly absent. A plain
// task always runs its action, so the checks below can't be bypassed.
val stageReleaseApks by tasks.registering {
    group = "publishing"
    description = "Stages the app and SMS plugin APKs under outputs/release-apks."
    dependsOn("assembleRelease", ":smsplugin:assembleRelease")

    val appApk = layout.buildDirectory.file("outputs/apk/release/app-release.apk")
    val pluginApk = rootProject.file("smsplugin/build/outputs/apk/release/smsplugin-release.apk")
    val outDir = layout.buildDirectory.dir("outputs/release-apks")

    doLast {
        // Wipe first: the directory is never cleaned by Gradle, so APKs from
        // older layouts (the nosms flavour, renamed assets) sit around looking
        // publishable long after they stopped being built.
        val dest = outDir.get().asFile
        dest.deleteRecursively()
        dest.mkdirs()
        val staged = listOf(
            appApk.get().asFile to "AliTrack.apk",
            appApk.get().asFile to "app-release.apk",
            pluginApk to "AliTrack-SMS-Plugin.apk"
        )
        staged.forEach { (src, publishedName) ->
            check(src.exists()) {
                "Expected release APK not found: $src\n" +
                    "Release signing must be configured in local.properties — an unsigned " +
                    "build emits *-release-unsigned.apk instead."
            }
            src.copyTo(dest.resolve(publishedName), overwrite = true)
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
        staged.file("AliTrack.apk").asFile.absolutePath,
        staged.file("app-release.apk").asFile.absolutePath,
        staged.file("AliTrack-SMS-Plugin.apk").asFile.absolutePath,
        "--title", tag,
        "--generate-notes"
    )
}
