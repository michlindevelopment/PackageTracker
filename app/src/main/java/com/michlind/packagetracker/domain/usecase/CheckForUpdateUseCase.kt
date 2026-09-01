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
     * Releases carry a single APK named [RELEASE_APK_ASSET]. The fallback to
     * "the only .apk on the release" covers releases whose asset was named
     * differently — historically the flavored app-full-release.apk — so a
     * rename doesn't silently stop the update prompt from ever appearing.
     */
    private fun pickApkForThisBuild(assets: List<GitHubAsset>): GitHubAsset? {
        assets.firstOrNull { it.name == RELEASE_APK_ASSET }?.let { return it }
        return assets.filter { it.name.endsWith(".apk") }.singleOrNull()
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

        /** Asset name produced by the `stageReleaseApks` Gradle task. */
        const val RELEASE_APK_ASSET = "app-release.apk"
    }
}
