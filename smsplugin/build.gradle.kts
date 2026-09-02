import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Signed with the SAME key as :app — that is load-bearing, not incidental.
// TrackingSmsProvider authorises callers with PackageManager.checkSignatures(),
// so if the two APKs are signed with different keys the main app is refused
// and the plugin silently does nothing.
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

// Versioned independently of the app. This APK should change almost never —
// every change costs each user another trip through the Play Protect prompt.
android {
    namespace = "com.michlind.packagetracker.smsplugin"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.michlind.packagetracker.smsplugin"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
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
            // Mirrors :app's suffix so a debug pair can be installed alongside
            // a release pair. SmsRepository derives the authority the same way.
            applicationIdSuffix = ".debug"
        }
        release {
            // Worth having even for two classes: a smaller download is a
            // shorter walk past the Play Protect warning, and this APK is the
            // one users have to install the awkward way.
            // PluginActivity and TrackingSmsProvider are named in the manifest,
            // so AGP generates keep rules for them automatically — no manual
            // -keep needed.
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
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Deliberately no Compose, no Hilt, no AndroidX beyond core: this APK exists to
// be small and boring so it rarely needs reinstalling.
dependencies {
    implementation(libs.androidx.core.ktx)
}
