package com.example.voicejournal.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [JournalEntry::class, Category::class, GpsTrackPoint::class, JournalEntryCategoryCrossRef::class], version = 12, exportSchema = false)
@TypeConverters(UuidConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun journalEntryDao(): JournalEntryDao
    abstract fun gpsTrackPointDao(): GpsTrackPointDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // ... (Migrations 2-8) should be here
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create the cross-ref table
                database.execSQL("CREATE TABLE `journal_entry_category_cross_ref` (`entryId` INTEGER NOT NULL, `categoryName` TEXT NOT NULL, PRIMARY KEY(`entryId`, `categoryName`), FOREIGN KEY(`entryId`) REFERENCES `journal_entries`(`id`) ON DELETE CASCADE, FOREIGN KEY(`categoryName`) REFERENCES `categories`(`category`) ON DELETE CASCADE)")

                // Populate the cross-ref table from the old title column
                database.execSQL("INSERT INTO journal_entry_category_cross_ref (entryId, categoryName) SELECT id, title FROM journal_entries")

                // Create a new journal_entries table without the title column
                database.execSQL("CREATE TABLE `journal_entries_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `content` TEXT NOT NULL, `start_datetime` INTEGER NOT NULL, `hasImage` INTEGER NOT NULL)")
                
                // Copy data to the new table
                database.execSQL("INSERT INTO journal_entries_new (id, content, start_datetime, hasImage) SELECT id, content, timestamp, hasImage FROM journal_entries")
                
                // Drop the old table
                database.execSQL("DROP TABLE `journal_entries`")
                
                // Rename the new table
                database.execSQL("ALTER TABLE `journal_entries_new` RENAME TO `journal_entries`")
            }
        }
        
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. Create new categories table with auto-incrementing ID
                database.execSQL("CREATE TABLE `categories_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `category` TEXT NOT NULL, `aliases` TEXT NOT NULL, `showAll` INTEGER NOT NULL, `orderIndex` INTEGER NOT NULL)")
                database.execSQL("CREATE UNIQUE INDEX `index_categories_category` ON `categories_new` (`category`)")
                // 2. Copy data from old to new, letting the ID be generated
                database.execSQL("INSERT INTO `categories_new` (category, aliases, showAll, orderIndex) SELECT category, aliases, showAll, orderIndex FROM `categories`")
                // 3. Drop old table
                database.execSQL("DROP TABLE `categories`")
                // 4. Rename new table
                database.execSQL("ALTER TABLE `categories_new` RENAME TO `categories`")
                
                // 5. Create new cross-ref table with categoryId
                database.execSQL("CREATE TABLE `journal_entry_category_cross_ref_new` (`entryId` INTEGER NOT NULL, `categoryId` INTEGER NOT NULL, PRIMARY KEY(`entryId`, `categoryId`), FOREIGN KEY(`entryId`) REFERENCES `journal_entries`(`id`) ON DELETE CASCADE, FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON DELETE CASCADE)")
                database.execSQL("CREATE INDEX `index_journal_entry_category_cross_ref_categoryId` ON `journal_entry_category_cross_ref_new` (`categoryId`)")
                // 6. Populate the new cross-ref table by looking up the new category IDs
                database.execSQL("INSERT INTO `journal_entry_category_cross_ref_new` (entryId, categoryId) SELECT T1.entryId, T2.id FROM `journal_entry_category_cross_ref` AS T1 JOIN `categories` AS T2 ON T1.categoryName = T2.category")
                // 7. Drop the old cross-ref table
                database.execSQL("DROP TABLE `journal_entry_category_cross_ref`")
                // 8. Rename the new cross-ref table
                database.execSQL("ALTER TABLE `journal_entry_category_cross_ref_new` RENAME TO `journal_entry_category_cross_ref`")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `journal_entries` ADD COLUMN `stop_datetime` INTEGER")
            }
        }


        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "journal_database"
                )
                    .addMigrations(MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11) // Be sure to add all migrations here
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}