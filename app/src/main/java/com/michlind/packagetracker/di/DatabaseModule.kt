package com.michlind.packagetracker.di

import android.content.Context
import androidx.room.Room
import com.michlind.packagetracker.data.db.PackageDao
import com.michlind.packagetracker.data.db.PackageDatabase
import com.michlind.packagetracker.data.db.TrackingSmsDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PackageDatabase =
        Room.databaseBuilder(
            context,
            PackageDatabase::class.java,
            "package_tracker.db"
        )
            .addMigrations(
                PackageDatabase.MIGRATION_1_2,
                PackageDatabase.MIGRATION_2_3,
                PackageDatabase.MIGRATION_3_4,
                PackageDatabase.MIGRATION_4_5,
                PackageDatabase.MIGRATION_5_6
            )
            .build()

    @Provides
    fun providePackageDao(db: PackageDatabase): PackageDao = db.packageDao()

    @Provides
    fun provideTrackingSmsDao(db: PackageDatabase): TrackingSmsDao = db.trackingSmsDao()
}
