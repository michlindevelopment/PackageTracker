package com.michlind.packagetracker.data.api

import retrofit2.http.GET
import retrofit2.http.Path

interface GitHubReleaseService {
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun latestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubRelease
}
