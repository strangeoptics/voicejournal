package com.example.voicejournal.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.UUID

@Database(
    entities = [JournalEntry::class, Category::class, GpsTrackPoint::class, JournalEntryCategoryCrossRef::class],
    version = 16,
    exportSchema = true
)
@TypeConverters(UuidConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun journalEntryDao(): JournalEntryDao
    abstract fun gpsTrackPointDao(): GpsTrackPointDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `category_aliases` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `category` TEXT NOT NULL, `alias` TEXT NOT NULL)"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `journal_entries` ADD COLUMN `imageUri` TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE `journal_entries_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `content` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `hasImage` INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL(
                    "INSERT INTO `journal_entries_new` (`id`, `title`, `content`, `timestamp`, `hasImage`) SELECT `id`, `title`, `content`, `timestamp`, CASE WHEN `imageUri` IS NOT NULL AND `imageUri` != '' THEN 1 ELSE 0 END FROM `journal_entries`"
                )
                db.execSQL("DROP TABLE `journal_entries`")
                db.execSQL("ALTER TABLE `journal_entries_new` RENAME TO `journal_entries`")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE `categories` (`category` TEXT NOT NULL, `aliases` TEXT NOT NULL, PRIMARY KEY(`category`))"
                )
                db.execSQL(
                    "INSERT INTO `categories` (`category`, `aliases`) SELECT `category`, COALESCE(GROUP_CONCAT(`alias`), '') FROM `category_aliases` GROUP BY `category`"
                )
                db.execSQL("DROP TABLE `category_aliases`")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `categories` ADD COLUMN `showAll` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `categories` ADD COLUMN `orderIndex` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `gps_track_points` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `timestamp` INTEGER NOT NULL)"
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Ensure any category referenced by journal_entries.title exists in categories before FK creation
                db.execSQL(
                    "INSERT OR IGNORE INTO `categories` (`category`, `aliases`, `showAll`, `orderIndex`) SELECT DISTINCT `title`, '', 0, 0 FROM `journal_entries` WHERE `title` NOT IN (SELECT `category` FROM `categories`)"
                )

                // Create the cross-ref table
                db.execSQL(
                    "CREATE TABLE `journal_entry_category_cross_ref` (`entryId` INTEGER NOT NULL, `categoryName` TEXT NOT NULL, PRIMARY KEY(`entryId`, `categoryName`), FOREIGN KEY(`entryId`) REFERENCES `journal_entries`(`id`) ON DELETE CASCADE, FOREIGN KEY(`categoryName`) REFERENCES `categories`(`category`) ON DELETE CASCADE)"
                )

                // Populate the cross-ref table from the old title column
                db.execSQL(
                    "INSERT OR IGNORE INTO `journal_entry_category_cross_ref` (`entryId`, `categoryName`) SELECT `id`, `title` FROM `journal_entries`"
                )

                // Create a new journal_entries table without the title column
                db.execSQL(
                    "CREATE TABLE `journal_entries_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `content` TEXT NOT NULL, `start_datetime` INTEGER NOT NULL, `hasImage` INTEGER NOT NULL)"
                )

                // Copy data to the new table
                db.execSQL(
                    "INSERT INTO `journal_entries_new` (`id`, `content`, `start_datetime`, `hasImage`) SELECT `id`, `content`, `timestamp`, `hasImage` FROM `journal_entries`"
                )

                // Drop the old table
                db.execSQL("DROP TABLE `journal_entries`")

                // Rename the new table
                db.execSQL("ALTER TABLE `journal_entries_new` RENAME TO `journal_entries`")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create new categories table with auto-incrementing ID
                db.execSQL(
                    "CREATE TABLE `categories_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `category` TEXT NOT NULL, `aliases` TEXT NOT NULL, `showAll` INTEGER NOT NULL, `orderIndex` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX `index_categories_category` ON `categories_new` (`category`)")
                // 2. Copy data from old to new, letting the ID be generated
                db.execSQL(
                    "INSERT INTO `categories_new` (`category`, `aliases`, `showAll`, `orderIndex`) SELECT `category`, `aliases`, `showAll`, `orderIndex` FROM `categories`"
                )
                // 3. Drop old table
                db.execSQL("DROP TABLE `categories`")
                // 4. Rename new table
                db.execSQL("ALTER TABLE `categories_new` RENAME TO `categories`")

                // 5. Create new cross-ref table with categoryId
                db.execSQL(
                    "CREATE TABLE `journal_entry_category_cross_ref_new` (`entryId` INTEGER NOT NULL, `categoryId` INTEGER NOT NULL, PRIMARY KEY(`entryId`, `categoryId`), FOREIGN KEY(`entryId`) REFERENCES `journal_entries`(`id`) ON DELETE CASCADE, FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX `index_journal_entry_category_cross_ref_categoryId` ON `journal_entry_category_cross_ref_new` (`categoryId`)"
                )
                // 6. Populate the new cross-ref table by looking up the new category IDs
                db.execSQL(
                    "INSERT INTO `journal_entry_category_cross_ref_new` (`entryId`, `categoryId`) SELECT T1.`entryId`, T2.`id` FROM `journal_entry_category_cross_ref` AS T1 JOIN `categories` AS T2 ON T1.`categoryName` = T2.`category`"
                )
                // 7. Drop the old cross-ref table
                db.execSQL("DROP TABLE `journal_entry_category_cross_ref`")
                // 8. Rename the new cross-ref table
                db.execSQL("ALTER TABLE `journal_entry_category_cross_ref_new` RENAME TO `journal_entry_category_cross_ref`")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `journal_entries` ADD COLUMN `stop_datetime` INTEGER")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Read existing integer-keyed entries and assign stable UUIDs
                val idMapping = HashMap<Long, String>()
                db.execSQL(
                    "CREATE TABLE `journal_entries_new` (`id` TEXT NOT NULL, `content` TEXT NOT NULL, `start_datetime` INTEGER NOT NULL, `stop_datetime` INTEGER, `hasImage` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )

                db.query(
                    "SELECT `id`, `content`, `start_datetime`, `stop_datetime`, `hasImage` FROM `journal_entries`"
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val oldId = cursor.getLong(0)
                        val content = cursor.getString(1)
                        val startDatetime = cursor.getLong(2)
                        val stopDatetime: Long? = if (cursor.isNull(3)) null else cursor.getLong(3)
                        val hasImage = cursor.getInt(4)
                        val newUuid = UUID.randomUUID().toString()
                        idMapping[oldId] = newUuid

                        db.execSQL(
                            "INSERT INTO `journal_entries_new` (`id`, `content`, `start_datetime`, `stop_datetime`, `hasImage`) VALUES (?, ?, ?, ?, ?)",
                            arrayOf<Any?>(newUuid, content, startDatetime, stopDatetime, hasImage)
                        )
                    }
                }

                // 2. Read existing integer entryId -> categoryId cross-references before dropping old tables
                val crossRefs = ArrayList<Pair<String, Int>>()
                db.query(
                    "SELECT `entryId`, `categoryId` FROM `journal_entry_category_cross_ref`"
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val oldEntryId = cursor.getLong(0)
                        val categoryId = cursor.getInt(1)
                        val mappedUuid = idMapping[oldEntryId]
                        if (mappedUuid != null) {
                            crossRefs.add(mappedUuid to categoryId)
                        }
                    }
                }

                // 3. Replace old tables (drop child cross-ref first, then parent journal_entries)
                db.execSQL("DROP TABLE `journal_entry_category_cross_ref`")
                db.execSQL("DROP TABLE `journal_entries`")
                db.execSQL("ALTER TABLE `journal_entries_new` RENAME TO `journal_entries`")

                // 4. Recreate cross-ref table referencing UUID-based journal_entries(id)
                db.execSQL(
                    "CREATE TABLE `journal_entry_category_cross_ref` (`entryId` TEXT NOT NULL, `categoryId` INTEGER NOT NULL, PRIMARY KEY(`entryId`, `categoryId`), FOREIGN KEY(`entryId`) REFERENCES `journal_entries`(`id`) ON DELETE CASCADE, FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON DELETE CASCADE)"
                )
                db.execSQL(
                    "CREATE INDEX `index_journal_entry_category_cross_ref_categoryId` ON `journal_entry_category_cross_ref` (`categoryId`)"
                )
                for ((entryUuid, categoryId) in crossRefs) {
                    db.execSQL(
                        "INSERT OR IGNORE INTO `journal_entry_category_cross_ref` (`entryId`, `categoryId`) VALUES (?, ?)",
                        arrayOf<Any?>(entryUuid, categoryId)
                    )
                }
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `categories` ADD COLUMN `color` TEXT NOT NULL DEFAULT '#FFFFFF'")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `categories` ADD COLUMN `checkable` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `journal_entries` ADD COLUMN `checked` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `categories` ADD COLUMN `showDate` INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `journal_entries` ADD COLUMN `deletedAt` INTEGER DEFAULT NULL")
            }
        }

        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13,
            MIGRATION_13_14,
            MIGRATION_14_15,
            MIGRATION_15_16
        )

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "journal_database"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}