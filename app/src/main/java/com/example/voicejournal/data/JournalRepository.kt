package com.example.voicejournal.data

import android.content.Context
import android.net.Uri
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.regex.Pattern

object JournalConstants {
    const val CATEGORY_DELETED_ID = -1
}

class JournalRepository(
    private val database: AppDatabase,
    private val entryDao: JournalEntryDao,
    private val categoryDao: CategoryDao,
    private val gpsTrackPointDao: GpsTrackPointDao,
    private val context: Context
) {

    private val importJson = Json { ignoreUnknownKeys = true }
    private val exportJson = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    private val virtualDeletedCategory = Category(
        id = JournalConstants.CATEGORY_DELETED_ID,
        category = "Gelöscht",
        aliases = "",
        showAll = true,
        orderIndex = Int.MAX_VALUE, // Put it at the end
        color = "#FFCDD2", // Light red
        checkable = false,
        showDate = true
    )

    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories().map { categories ->
        categories + virtualDeletedCategory
    }

    suspend fun getEntriesPager(
        categoryId: Int?,
        initialEntryId: UUID?
    ): Flow<PagingData<EntryWithCategories>> {
        val initialKey = initialEntryId?.let {
            if (categoryId == JournalConstants.CATEGORY_DELETED_ID) {
                entryDao.getPositionOfDeletedEntry(it)
            } else if (categoryId != null && categoryId != -1) {
                entryDao.getPositionOfEntryInCategory(it, categoryId)
            } else {
                entryDao.getPositionOfEntry(it)
            }
        }

        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
                initialLoadSize = 20
            ),
            initialKey = initialKey,
            pagingSourceFactory = {
                if (categoryId == JournalConstants.CATEGORY_DELETED_ID) {
                    entryDao.getDeletedEntriesPagingSource()
                } else if (categoryId != null && categoryId != -1) {
                    entryDao.getEntriesPagingSourceForCategory(categoryId)
                } else {
                    entryDao.getEntriesPagingSource()
                }
            }
        ).flow
    }

    fun getAllEntriesWithCategories(): Flow<List<EntryWithCategories>> = entryDao.getEntriesWithCategories()
    
    fun getEntriesForCalendar(startTime: Long, endTime: Long): Flow<List<EntryWithCategories>> =
        entryDao.getEntriesForCalendar(startTime, endTime)

    suspend fun getEntryById(entryId: UUID): EntryWithCategories? = entryDao.getEntryById(entryId)

    suspend fun getEntriesByIds(ids: Set<UUID>): List<EntryWithCategories> = entryDao.getEntriesByIds(ids)

    private suspend fun ensureCategoryExists(exportedCategory: Category) {
        if (exportedCategory.id == JournalConstants.CATEGORY_DELETED_ID ||
            exportedCategory.category.equals("Gelöscht", ignoreCase = true) ||
            exportedCategory.category.isBlank()
        ) {
            return
        }
        val existing = categoryDao.getCategoryByName(exportedCategory.category)
        if (existing == null) {
            // Reset auto-generated ID to 0 to avoid primary key collisions across devices while preserving metadata
            categoryDao.insertCategory(exportedCategory.copy(id = 0))
        }
    }

    suspend fun importJournal(uri: Uri) = withContext(Dispatchers.IO) {
        val content = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                reader.readText()
            }
        } ?: throw IOException("Die ausgewählte Datei konnte nicht geöffnet werden.")

        val trimmed = content.trim().removePrefix("\uFEFF")
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("Die ausgewählte Datei ist leer.")
        }

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                database.withTransaction {
                    if (trimmed.startsWith("{") && trimmed.contains("\"version\"")) {
                        val version = importJson.decodeFromString<VersionCheck>(trimmed).version
                        if (version >= 3) {
                            val journalExport = importJson.decodeFromString<JournalExportV3>(trimmed)
                            journalExport.categories.forEach { ensureCategoryExists(it) }
                            journalExport.entries.forEach { entryExport ->
                                val entry = JournalEntry(
                                    id = entryExport.id,
                                    content = entryExport.content,
                                    start_datetime = entryExport.start_datetime,
                                    stop_datetime = entryExport.stop_datetime,
                                    hasImage = entryExport.hasImage,
                                    checked = entryExport.checked,
                                    deletedAt = null
                                )
                                val categories = entryExport.categories
                                    .filter { it.isNotBlank() && !it.equals("Gelöscht", ignoreCase = true) }
                                    .map { categoryName ->
                                        Category(category = categoryName, aliases = "")
                                    }
                                entryDao.insertWithCategories(entry, categories, categoryDao)
                            }
                        } else {
                            val journalExport = importJson.decodeFromString<JournalExport>(trimmed)
                            journalExport.categories.forEach { ensureCategoryExists(it) }
                            journalExport.entries.forEach { v2Entry ->
                                val newEntry = JournalEntry(
                                    content = v2Entry.content,
                                    start_datetime = v2Entry.timestamp,
                                    hasImage = v2Entry.hasImage
                                )
                                val categoryName = v2Entry.title.ifBlank { "journal" }
                                entryDao.insertWithCategories(
                                    newEntry,
                                    listOf(Category(category = categoryName, aliases = "")),
                                    categoryDao
                                )
                            }
                        }
                    } else if (trimmed.startsWith("[")) {
                        if (trimmed.contains("\"start_datetime\"")) {
                            val entries = importJson.decodeFromString<List<JournalEntry>>(trimmed)
                            val fallbackCategory = Category(category = "journal", aliases = "")
                            entries.forEach { imported ->
                                val cleanEntry = imported.copy(deletedAt = null)
                                val existing = entryDao.getEntryByIdIncludingDeleted(cleanEntry.id)
                                if (existing != null && existing.categories.isNotEmpty()) {
                                    entryDao.updateWithCategories(cleanEntry, existing.categories, categoryDao)
                                } else {
                                    entryDao.insertWithCategories(cleanEntry, listOf(fallbackCategory), categoryDao)
                                }
                            }
                        } else {
                            val entries = importJson.decodeFromString<List<JournalEntryV2>>(trimmed)
                            entries.forEach { v2Entry ->
                                val newEntry = JournalEntry(
                                    content = v2Entry.content,
                                    start_datetime = v2Entry.timestamp,
                                    hasImage = v2Entry.hasImage
                                )
                                val categoryName = v2Entry.title.ifBlank { "journal" }
                                entryDao.insertWithCategories(
                                    newEntry,
                                    listOf(Category(category = categoryName, aliases = "")),
                                    categoryDao
                                )
                            }
                        }
                    } else {
                        throw IllegalArgumentException("Unbekanntes JSON-Backup-Format.")
                    }
                }
            } catch (e: SerializationException) {
                throw IllegalArgumentException("Ungültiges JSON-Backup-Format: ${e.message}", e)
            }
        } else {
            val importedCount = database.withTransaction {
                parseLegacyFormat(trimmed)
            }
            if (importedCount == 0) {
                throw IllegalArgumentException("Keine gültigen Tagebucheinträge in der Datei gefunden.")
            }
        }
    }

    private suspend fun parseLegacyFormat(content: String): Int {
        val pattern = Pattern.compile("\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})\\] (.*)")
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        var importedCount = 0
        content.lines().forEach { line ->
            val matcher = pattern.matcher(line)
            if (matcher.matches()) {
                val dateTimeString = matcher.group(1) ?: return@forEach
                val contentString = matcher.group(2) ?: ""
                val startDatetime = LocalDateTime.parse(dateTimeString, formatter)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()

                val entry = JournalEntry(
                    content = contentString,
                    start_datetime = startDatetime
                )
                entryDao.insertWithCategories(
                    entry,
                    listOf(Category(category = "journal", aliases = "")),
                    categoryDao
                )
                importedCount++
            }
        }
        return importedCount
    }

    suspend fun importEntriesToCategory(entries: List<JournalEntry>, targetCategory: Category) = withContext(Dispatchers.IO) {
        if (targetCategory.id == JournalConstants.CATEGORY_DELETED_ID ||
            targetCategory.category.equals("Gelöscht", ignoreCase = true)
        ) {
            throw IllegalArgumentException("Einträge können nicht in die Kategorie 'Gelöscht' importiert werden.")
        }
        database.withTransaction {
            entries.forEach { imported ->
                val cleanEntry = imported.copy(deletedAt = null)
                val existing = entryDao.getEntryByIdIncludingDeleted(cleanEntry.id)
                if (existing != null) {
                    val mergedCategories = (existing.categories + targetCategory)
                        .filter { it.id != JournalConstants.CATEGORY_DELETED_ID && !it.category.equals("Gelöscht", ignoreCase = true) }
                        .distinctBy { it.category }
                    entryDao.updateWithCategories(cleanEntry, mergedCategories, categoryDao)
                } else {
                    entryDao.insertWithCategories(cleanEntry, listOf(targetCategory), categoryDao)
                }
            }
        }
    }

    suspend fun exportJournal(uri: Uri) = withContext(Dispatchers.IO) {
        val entriesWithCategories = entryDao.getAllEntriesWithCategories()
        val categories = categoryDao.getAllCategoriesList()
        val exportEntries = entriesWithCategories.map {
            JournalEntryExport(
                id = it.entry.id,
                content = it.entry.content,
                start_datetime = it.entry.start_datetime,
                stop_datetime = it.entry.stop_datetime,
                hasImage = it.entry.hasImage,
                checked = it.entry.checked,
                categories = it.categories.map { c -> c.category }
            )
        }
        val exportData = JournalExportV3(entries = exportEntries, categories = categories)
        val jsonString = exportJson.encodeToString(exportData)

        // Use "wt" (write + truncate) so overwriting an existing longer file never leaves trailing bytes
        val pfd = context.contentResolver.openFileDescriptor(uri, "wt")
            ?: throw IOException("Die Zieldatei konnte nicht zum Schreiben geöffnet werden.")
        pfd.use { descriptor ->
            FileOutputStream(descriptor.fileDescriptor).use { fos ->
                fos.write(jsonString.toByteArray(Charsets.UTF_8))
                fos.flush()
            }
        }
    }

    fun getEntriesWithCategoriesSince(since: Long) = entryDao.getEntriesWithCategoriesSince(since)

    suspend fun insertCategory(category: Category) {
        if (category.id != JournalConstants.CATEGORY_DELETED_ID) categoryDao.insertCategory(category)
    }

    suspend fun updateCategory(category: Category) {
        if (category.id != JournalConstants.CATEGORY_DELETED_ID) categoryDao.updateCategory(category)
    }

    suspend fun updateCategories(categories: List<Category>) {
        val filtered = categories.filter { it.id != JournalConstants.CATEGORY_DELETED_ID }
        categoryDao.updateCategories(filtered)
    }

    suspend fun insert(entry: JournalEntry, categories: List<Category>) {
        val filtered = categories.filter { it.id != JournalConstants.CATEGORY_DELETED_ID }
        entryDao.insertWithCategories(entry, filtered, categoryDao)
    }

    suspend fun update(entry: JournalEntry, categories: List<Category>) {
        val filtered = categories.filter { it.id != JournalConstants.CATEGORY_DELETED_ID }
        entryDao.updateWithCategories(entry, filtered, categoryDao)
    }
    
    suspend fun updateJournalEntry(entry: JournalEntry) = entryDao.update(entry)
    
    suspend fun updateEntryChecked(entryId: UUID, checked: Boolean) = entryDao.updateChecked(entryId, checked)

    suspend fun delete(entry: JournalEntry) {
        entryDao.softDelete(entry.id, System.currentTimeMillis())
    }
    
    suspend fun hardDelete(entry: JournalEntry) {
        entryDao.hardDeleteById(entry.id)
    }

    suspend fun restoreLatestDeletedEntry() {
        val latestDeleted = entryDao.getLatestDeletedEntry()
        if (latestDeleted != null) {
            entryDao.restoreEntry(latestDeleted.entry.id)
        }
    }

    suspend fun restoreEntry(id: UUID) {
        entryDao.restoreEntry(id)
    }

    fun getDeletedEntriesCount(): Flow<Int> = entryDao.getDeletedEntriesCount()

    suspend fun updateAliasesForCategory(categoryId: Int, aliases: List<String>) {
        val aliasesString = aliases.joinToString(",")
        if (categoryId != JournalConstants.CATEGORY_DELETED_ID) categoryDao.updateAliasesForCategory(categoryId, aliasesString)
    }

    suspend fun deleteCategory(categoryId: Int) {
        if (categoryId != JournalConstants.CATEGORY_DELETED_ID) categoryDao.deleteCategory(categoryId)
    }

    suspend fun deleteAll() {
        entryDao.softDeleteAll(System.currentTimeMillis())
    }

    // GPS Track Point methods
    suspend fun insertGpsPoint(point: GpsTrackPoint) {
        gpsTrackPointDao.insert(point)
    }

    fun getTrackPointsForDay(dayStartMillis: Long, dayEndMillis: Long): Flow<List<GpsTrackPoint>> {
        return gpsTrackPointDao.getTrackPointsForDay(dayStartMillis, dayEndMillis)
    }

    suspend fun getLatestEntryDatetimeForCategory(categoryId: Int): Long? {
        if (categoryId == JournalConstants.CATEGORY_DELETED_ID) return null
        return entryDao.getLatestEntryDatetimeForCategory(categoryId)
    }

    fun getEntriesWithCategoriesInDateRangeForCategory(categoryId: Int, startDateMillis: Long, endDateMillis: Long): Flow<List<EntryWithCategories>> {
        return entryDao.getEntriesWithCategoriesInDateRangeForCategory(categoryId, startDateMillis, endDateMillis)
    }

    fun getEntriesWithCategoriesInDateRange(startDateMillis: Long, endDateMillis: Long): Flow<List<EntryWithCategories>> {
        return entryDao.getEntriesWithCategoriesInDateRange(startDateMillis, endDateMillis)
    }

    suspend fun getAllCategoriesList(): List<Category> {
        val dbList = categoryDao.getAllCategoriesList()
        return dbList + virtualDeletedCategory
    }
}

@kotlinx.serialization.Serializable
private data class VersionCheck(val version: Int)