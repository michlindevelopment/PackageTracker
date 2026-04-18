package com.michlind.packagetracker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PackageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PackageDatabase : RoomDatabase() {
    abstract fun packageDao(): PackageDao
}
