package com.michlind.packagetracker.di

import com.google.gson.Gson
import com.michlind.packagetracker.data.api.CainiaoApiService
import com.michlind.packagetracker.data.db.PackageDao
import com.michlind.packagetracker.data.preferences.MockTrackingPreferenceRepository
import com.michlind.packagetracker.data.repository.PackageRepositoryImpl
import com.michlind.packagetracker.domain.repository.PackageRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun providePackageRepository(
        dao: PackageDao,
        api: CainiaoApiService,
        gson: Gson,
        mockPrefs: MockTrackingPreferenceRepository
    ): PackageRepository = PackageRepositoryImpl(dao, api, gson, mockPrefs)
}
