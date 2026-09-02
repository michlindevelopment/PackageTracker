package com.michlind.packagetracker.domain.usecase

import com.michlind.packagetracker.BuildConfig
import com.michlind.packagetracker.data.api.GitHubAsset
import com.michlind.packagetracker.data.api.GitHubReleaseService
import com.michlind.packagetracker.domain.model.UpdateCheckResult
import javax.inject.Inject

class CheckForUpdateUseCase @Inject constructor(
    private val service: GitHubReleaseService
) {
    suspend operator fun invoke(): Result<UpdateCheckResult> = runCatching {
        val release = service.latestRelease(REPO_OWNER, REPO_NAME)
        val latest = release.tagName.removePrefix("v")
        val current = BuildConfig.VERSION_NAME
        val apkAsset = pickApkForThisBuild(release.assets)

        if (apkAsset != null && isNewer(latest, current)) {
            UpdateCheckResult.Available(
                latestVersion = latest,
                currentVersion = current,
                downloadUrl = apkAsset.browserDownloadUrl,
                sizeBytes = apkAsset.size,
                releaseUrl = release.htmlUrl
            )
        } else {
            UpdateCheckResult.UpToDate
        }
    }

    /**
     * Pick the app's own APK off the release, by exact name, in preference
     * order. Never by pattern: a release also carries the SMS plugin, and
     * "the .apk on the release" would happily hand the user a 59 KB plugin as
     * an app update.
     *
     * Two names are accepted while the rename settles — [APP_ASSET] is what
     * releases lead with now, [LEGACY_APP_ASSET] is the same bytes under the
     * old name so installs of 1.3.1 and earlier can still find themselves an
     * update. The legacy entry can go once nobody is running <= 1.3.1.
     */
    private fun pickApkForThisBuild(assets: List<GitHubAsset>): GitHubAsset? =
        APP_ASSET_NAMES.firstNotNullOfOrNull { wanted ->
            assets.firstOrNull { it.name == wanted }
        }

    // Compare semantic version strings like "1.0.10" > "1.0.9". Splits on '.',
    // pads to equal length with zeros, and compares each segment as an int.
    private fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split('.').map { it.toIntOrNull() ?: 0 }
        val l = local.split('.').map { it.toIntOrNull() ?: 0 }
        val len = maxOf(r.size, l.size)
        for (i in 0 until len) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private companion object {
        const val REPO_OWNER = "michlindevelopment"
        const val REPO_NAME = "PackageTracker"

        /** Current asset name, produced by the `stageReleaseApks` Gradle task. */
        const val APP_ASSET = "AliTrack.apk"

        /** Pre-1.3.2 name, still published as a duplicate for older installs. */
        const val LEGACY_APP_ASSET = "app-release.apk"

        /** Checked in order; first match wins. */
        val APP_ASSET_NAMES = listOf(APP_ASSET, LEGACY_APP_ASSET)
    }
}
