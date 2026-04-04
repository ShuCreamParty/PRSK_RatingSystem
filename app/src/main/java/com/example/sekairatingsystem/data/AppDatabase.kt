package com.example.sekairatingsystem.data

import android.content.Context
import androidx.room.Room
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.sekairatingsystem.data.dao.AppDao
import com.example.sekairatingsystem.data.entity.ScoreRecord
import com.example.sekairatingsystem.data.entity.SongMaster

@Database(
    entities = [
        SongMaster::class,
        ScoreRecord::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        const val DATABASE_NAME = "sekai_rating.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME,
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build().also { database ->
                    INSTANCE = database
                }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_score_records_imageUri ON score_records(imageUri)"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE score_records ADD COLUMN date INTEGER"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE score_records ADD COLUMN isBestFrame INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE score_records ADD COLUMN isReservedFrame INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "UPDATE score_records SET isBestFrame = 1 WHERE status = 'BEST'"
                )
                db.execSQL(
                    "UPDATE score_records SET isReservedFrame = 1 WHERE status = 'RESERVED'"
                )
                db.execSQL(
                    "UPDATE score_records SET status = 'CALCULATED' WHERE status IN ('BEST', 'RESERVED')"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_score_records_imageUri")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_score_records_imageUri ON score_records(imageUri)"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_score_records_isBestFrame_singleRate_id ON score_records(isBestFrame, singleRate, id)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_score_records_isReservedFrame_date_id ON score_records(isReservedFrame, date, id)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_score_records_songName_difficulty_singleRate ON score_records(songName, difficulty, singleRate)"
                )
            }
        }
    }
}