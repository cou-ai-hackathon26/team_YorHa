package com.medidiary.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DiarySession::class, DiaryRecord::class, DgdaMedicine::class, DgdaAlias::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao
    abstract fun dgdaDao(): DgdaDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "medidiary.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** v2: family-sharing columns. Existing rows get a random uuid and updatedAt = loggedDate. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val uuid = "lower(hex(randomblob(16)))"
                db.execSQL("ALTER TABLE diary_sessions ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE diary_sessions SET uuid = $uuid WHERE uuid = ''")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_diary_sessions_uuid ON diary_sessions (uuid)")
                db.execSQL("ALTER TABLE diary_records ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE diary_records ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE diary_records ADD COLUMN updatedBy TEXT")
                db.execSQL("ALTER TABLE diary_records ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE diary_records ADD COLUMN authorName TEXT")
                db.execSQL("ALTER TABLE diary_records ADD COLUMN authorDevice TEXT")
                db.execSQL("ALTER TABLE diary_records ADD COLUMN syncState TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
                db.execSQL("UPDATE diary_records SET uuid = $uuid, updatedAt = loggedDate WHERE uuid = ''")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_diary_records_uuid ON diary_records (uuid)")
            }
        }
    }
}
