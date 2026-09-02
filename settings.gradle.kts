pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "PackageTracker"
include(":app")
// Separate APK that holds READ_SMS and serves matches to :app over a
// ContentProvider. Split out because Play Protect blocks the *install* of any
// sideloaded APK declaring READ_SMS — keeping it out of :app means the main
// app installs and self-updates normally, and only this small, rarely-changing
// APK has to be installed the awkward way, once.
include(":smsplugin")
 