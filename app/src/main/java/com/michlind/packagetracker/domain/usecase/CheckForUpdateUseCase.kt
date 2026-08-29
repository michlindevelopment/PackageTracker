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
     * A release carries one APK per flavor (app-release.apk and
     * app-release-nosms.apk), so an exact name match is what keeps a no-SMS
     * install from updating itself into the SMS build — which would silently
     * hand the app back the READ_SMS permission the user chose to avoid.
     *
     * Only the SMS build falls back to "the single APK on the release". That
     * fallback exists for releases cut before the flavor split, which carry
     * one unnamed-for-flavor APK — and that APK is always the SMS build. For
     * nosms the same fallback would be a downgrade in privacy, so it reports
     * no update instead and waits for a release that includes its own APK.
     */
    private fun pickApkForThisBuild(assets: List<GitHubAsset>): GitHubAsset? {
        assets.firstOrNull { it.name == BuildConfig.UPDATE_APK_ASSET }?.let { return it }
        if (!BuildConfig.SMS_ENABLED) return null
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
    }
}
