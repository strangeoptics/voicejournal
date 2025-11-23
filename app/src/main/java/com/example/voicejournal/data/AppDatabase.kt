package com.example.voicejournal.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [JournalEntry::class, Category::class], version = 7, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun journalEntryDao(): JournalEntryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE journal_entries ADD COLUMN imageUri TEXT")
            }
        }
        
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create new table with the new schema
                database.execSQL("CREATE TABLE `journal_entries_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `content` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `hasImage` INTEGER NOT NULL DEFAULT 0)")
                // Copy the data from the old table to the new table, converting imageUri to hasImage boolean
                database.execSQL("INSERT INTO journal_entries_new (id, title, content, timestamp, hasImage) SELECT id, title, content, timestamp, CASE WHEN imageUri IS NOT NULL AND imageUri != '' THEN 1 ELSE 0 END FROM journal_entries")
                // Remove the old table
                database.execSQL("DROP TABLE `journal_entries`")
                // Rename the new table to the original table name
                database.execSQL("ALTER TABLE `journal_entries_new` RENAME TO `journal_entries`")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create the new table
                database.execSQL("CREATE TABLE `categories` (`category` TEXT NOT NULL, `aliases` TEXT NOT NULL, PRIMARY KEY(`category`))")

                // Migrate the data
                database.execSQL("INSERT INTO `categories` (`category`, `aliases`) SELECT `category`, GROUP_CONCAT(`alias`) FROM `category_aliases` GROUP BY `category`")

                // Drop the old table
                database.execSQL("DROP TABLE `category_aliases`")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `categories` ADD COLUMN `showAll` INTEGER NOT NULL DEFAULT 0")
            }
        }
        
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `categories` ADD COLUMN `orderIndex` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "journal_database"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}