package com.michlind.packagetracker.domain.model

sealed interface UpdateCheckResult {
    object UpToDate : UpdateCheckResult
    data class Available(
        val latestVersion: String,
        val currentVersion: String,
        val downloadUrl: String,
        val sizeBytes: Long,
        val releaseUrl: String?
    ) : UpdateCheckResult
}
