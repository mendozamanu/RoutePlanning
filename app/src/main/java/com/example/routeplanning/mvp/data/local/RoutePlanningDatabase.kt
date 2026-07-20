package com.example.routeplanning.mvp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [SavedCommuteEntity::class],
    version = 3,
    exportSchema = true
)
abstract class RoutePlanningDatabase : RoomDatabase() {
    abstract fun savedCommuteDao(): SavedCommuteDao

    companion object {
        fun create(context: Context): RoutePlanningDatabase {
            System.loadLibrary("sqlcipher")
            val passphrase = DatabaseKeyManager(context).getOrCreatePassphrase()
            val factory = SupportOpenHelperFactory(passphrase)
            return Room.databaseBuilder(
                context.applicationContext,
                RoutePlanningDatabase::class.java,
                DATABASE_NAME
            )
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE saved_commutes ADD COLUMN origin_latitude REAL")
                database.execSQL("ALTER TABLE saved_commutes ADD COLUMN origin_longitude REAL")
                database.execSQL("ALTER TABLE saved_commutes ADD COLUMN destination_latitude REAL")
                database.execSQL("ALTER TABLE saved_commutes ADD COLUMN destination_longitude REAL")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE saved_commutes ADD COLUMN mode TEXT NOT NULL DEFAULT 'TRANSIT'"
                )
            }
        }

        const val DATABASE_NAME = "route_planning.db"
    }
}
