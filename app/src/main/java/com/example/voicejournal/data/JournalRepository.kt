package com.example.voicejournal.data

import android.content.Context
import android.net.Uri
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.FileOutputStream
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
    private val entryDao: JournalEntryDao,
    private val categoryDao: CategoryDao,
    private val gpsTrackPointDao: GpsTrackPointDao,
    private val context: Context
) {

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

    suspend fun importJournal(uri: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                val content = reader.readText()
                try {
                    val json = Json { ignoreUnknownKeys = true }
                    if (content.contains("\"version\"")) {
                        val version = json.decodeFromString<VersionCheck>(content).version
                        if (version == 3) {
                            val journalExport = json.decodeFromString<JournalExportV3>(content)
                            journalExport.categories.forEach { categoryDao.insertCategory(it) }
                            journalExport.entries.forEach { entryExport ->
                                val entry = JournalEntry(
                                    id = entryExport.id,
                                    content = entryExport.content,
                                    start_datetime = entryExport.start_datetime,
                                    stop_datetime = entryExport.stop_datetime,
                                    hasImage = entryExport.hasImage,
                                    checked = entryExport.checked
                                )
                                val categories = entryExport.categories.map { categoryName ->
                                    Category(category = categoryName, aliases = "")
                                }
                                entryDao.insertWithCategories(entry, categories, categoryDao)
                            }
                        } else { // V2
                            val journalExport = json.decodeFromString<JournalExport>(content)
                            journalExport.categories.forEach { categoryDao.insertCategory(it) }
                            journalExport.entries.forEach { entry ->
                                val v2Entry = json.decodeFromString<JournalEntryV2>(Json.encodeToString(entry))
                                val newEntry = JournalEntry(content = v2Entry.content, start_datetime = v2Entry.timestamp, hasImage = v2Entry.hasImage)
                                val category = Category(category = v2Entry.title, aliases = "")
                                entryDao.insertWithCategories(newEntry, listOf(category), categoryDao)
                            }
                        }
                    } else { // V1 or legacy
                        val entries = Json.decodeFromString<List<JournalEntryV2>>(content)
                        entries.forEach { v2Entry ->
                             val newEntry = JournalEntry(content = v2Entry.content, start_datetime = v2Entry.timestamp, hasImage = v2Entry.hasImage)
                             val category = Category(category = v2Entry.title, aliases = "")
                             entryDao.insertWithCategories(newEntry, listOf(category), categoryDao)
                        }
                    }
                } catch (e: Exception) {
                    // Fallback to legacy text format
                    parseLegacyFormat(content)
                }
            }
        }
    }

    private suspend fun parseLegacyFormat(content: String) {
        val pattern = Pattern.compile("\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})\\] (.*)")
        content.lines().forEach { line ->
            val matcher = pattern.matcher(line)
            if (matcher.matches()) {
                val dateTimeString = matcher.group(1)
                val contentString = matcher.group(2)
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                val start_datetime = LocalDateTime.parse(dateTimeString, formatter)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()

                val entry = JournalEntry(
                    content = contentString,
                    start_datetime = start_datetime
                )
                entryDao.insertWithCategories(entry, listOf(Category(category="journal", aliases = "")), categoryDao)
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
        val jsonString = Json {
            prettyPrint = true
            encodeDefaults = true
        }.encodeToString(exportData)
        context.contentResolver.openFileDescriptor(uri, "w")?.use {
            FileOutputStream(it.fileDescriptor).use { fos ->
                fos.write(jsonString.toByteArray())
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

    fun getDeletedEntriesCount(): Flow<Int> = entryDao.getDeletedEntriesCount()

    suspend fun updateAliasesForCategory(categoryId: Int, aliases: List<String>) {
        val aliasesString = aliases.joinToString(",")
        if (categoryId != JournalConstants.CATEGORY_DELETED_ID) categoryDao.updateAliasesForCategory(categoryId, aliasesString)
    }

    suspend fun deleteCategory(categoryId: Int) {
        if (categoryId != JournalConstants.CATEGORY_DELETED_ID) categoryDao.deleteCategory(categoryId)
    }

    suspend fun deleteAll() = entryDao.deleteAll() // Should also delete categories? For now, it doesn't.

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

@kotlinx.serialization.Serializable
private data class JournalEntryV2(
    val id: Int = 0,
    val title: String,
    val content: String,
    val timestamp: Long,
    val hasImage: Boolean = false
)