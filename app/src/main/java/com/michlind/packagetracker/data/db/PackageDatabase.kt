package com.michlind.packagetracker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PackageEntity::class, TrackingSmsEntity::class],
    version = 4,
    exportSchema = false
)
abstract class PackageDatabase : RoomDatabase() {
    abstract fun packageDao(): PackageDao
    abstract fun trackingSmsDao(): TrackingSmsDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE packages ADD COLUMN externalOrderId TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_packages_externalOrderId ON packages(externalOrderId)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE packages ADD COLUMN progressRate REAL")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS tracking_sms (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "trackingNumber TEXT NOT NULL, " +
                        "smsId INTEGER NOT NULL, " +
                        "sender TEXT NOT NULL, " +
                        "body TEXT NOT NULL, " +
                        "timestamp INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_tracking_sms_trackingNumber " +
                        "ON tracking_sms(trackingNumber)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "index_tracking_sms_trackingNumber_smsId " +
                        "ON tracking_sms(trackingNumber, smsId)"
                )
            }
        }
    }
}
