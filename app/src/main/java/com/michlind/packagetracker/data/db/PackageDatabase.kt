package com.michlind.packagetracker.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PackageEntity::class, TrackingSmsEntity::class],
    version = 11,
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

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE packages ADD COLUMN destCarrierName TEXT")
                db.execSQL("ALTER TABLE packages ADD COLUMN destCarrierPhone TEXT")
                db.execSQL("ALTER TABLE packages ADD COLUMN destCarrierUrl TEXT")
                db.execSQL("ALTER TABLE packages ADD COLUMN destCarrierEmail TEXT")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE packages ADD COLUMN localTrackingNumber TEXT")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE packages ADD COLUMN rawApiJson TEXT")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE packages ADD COLUMN itemCount INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE packages ADD COLUMN itemPhotosJson TEXT")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE packages ADD COLUMN receivedAt INTEGER NOT NULL DEFAULT 0")
                // Best available approximation for packages already in Received:
                // lastUpdated is stamped when a package is marked received (by
                // hand or automatically on DELIVERED). Rows imported as already
                // completed carry 0 and stay at the bottom.
                db.execSQL("UPDATE packages SET receivedAt = lastUpdated WHERE isReceived = 1")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE packages ADD COLUMN priceAmount REAL")
                db.execSQL("ALTER TABLE packages ADD COLUMN priceCurrency TEXT")
                db.execSQL("ALTER TABLE packages ADD COLUMN storeName TEXT")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE packages ADD COLUMN aliUrl TEXT")
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
