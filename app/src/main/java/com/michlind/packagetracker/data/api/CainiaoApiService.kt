package com.michlind.packagetracker.data.api

import retrofit2.http.GET
import retrofit2.http.Query

interface CainiaoApiService {
    @GET("global/detail.json")
    suspend fun trackPackage(
        @Query("mailNos") mailNo: String,
        @Query("lang") lang: String = "en"
    ): CainiaoResponse
}
