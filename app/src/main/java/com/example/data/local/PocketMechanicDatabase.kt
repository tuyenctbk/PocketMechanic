package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DiagnosisHistoryEntity::class, AppEventEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(DiagnosisHistoryConverters::class)
abstract class PocketMechanicDatabase : RoomDatabase() {
    abstract fun diagnosisHistoryDao(): DiagnosisHistoryDao
    abstract fun appEventDao(): AppEventDao

    companion object {
        @Volatile
        private var instance: PocketMechanicDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `app_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `user_id` TEXT NOT NULL,
                        `event_name` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `metadata_json` TEXT NOT NULL,
                        `created_at_millis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_app_events_user_id_created_at_millis` ON `app_events` (`user_id`, `created_at_millis`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_app_events_event_name` ON `app_events` (`event_name`)"
                )
            }
        }

        fun getInstance(context: Context): PocketMechanicDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PocketMechanicDatabase::class.java,
                    "pocket_mechanic.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
