package com.michlind.packagetracker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PackageEntity::class],
    version = 2,
    exportSchema = false
)
abstract class PackageDatabase : RoomDatabase() {
    abstract fun packageDao(): PackageDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE packages ADD COLUMN externalOrderId TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_packages_externalOrderId ON packages(externalOrderId)")
            }
        }
    }
}
