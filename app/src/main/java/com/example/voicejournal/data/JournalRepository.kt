package com.example.voicejournal.data

import android.net.Uri
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.room.withTransaction
import com.example.voicejournal.util.TimeZoneUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

object JournalConstants {
    const val CATEGORY_DELETED_ID = -1
    const val CATEGORY_DELETED_NAME = "Gelöscht"
    const val SQLITE_MAX_BIND_PARAMS = 900

    fun isVirtualDeletedCategory(category: Category): Boolean {
        return category.id == CATEGORY_DELETED_ID ||
            category.category.trim().equals(CATEGORY_DELETED_NAME, ignoreCase = true)
    }

    fun isVirtualDeletedName(name: String?): Boolean {
        return name != null && name.trim().equals(CATEGORY_DELETED_NAME, ignoreCase = true)
    }

    fun nextSafeOrderIndex(categories: Collection<Category>): Int {
        val realCategories = categories.filterNot { isVirtualDeletedCategory(it) }
        if (realCategories.isEmpty()) return 0
        val maxExisting = realCategories.maxOf { it.orderIndex }.coerceAtMost(Int.MAX_VALUE - 2)
        return maxOf(maxExisting + 1, realCategories.size)
    }

    /**
     * Normalisiert und escaped eine Suchanfrage für SQLite `LIKE ... ESCAPE '\'`
     * (inkl. deutscher Umlaute und `ẞ` -> `ß`). Gibt `null` zurück, wenn die Anfrage
     * nicht-unterstützte Unicode-Zeichen enthält, sodass auf In-Memory-Filterung ausgewichen wird.
     */
    fun normalizeSearchQueryForSql(trimmedQuery: String): String? {
        if (trimmedQuery.isEmpty()) return ""
        val lowered = trimmedQuery.lowercase(Locale.GERMAN).replace("ẞ", "ß")
        val hasUnsupportedNonAscii = lowered.any { ch -> ch.code > 127 && ch !in "äöüß" }
        if (hasUnsupportedNonAscii) return null

        return buildString(lowered.length) {
            for (ch in lowered) {
                when (ch) {
                    '\\', '%', '_' -> {
                        append('\\')
                        append(ch)
                    }
                    else -> append(ch)
                }
            }
        }
    }
}

class JournalRepository internal constructor(
    private val entryDao: JournalEntryDao,
    private val categoryDao: CategoryDao,
    private val gpsTrackPointDao: GpsTrackPointDao,
    private val backupManager: JournalBackupManager,
    private val runInTransaction: suspend (suspend () -> Unit) -> Unit
) {

    constructor(
        database: AppDatabase,
        entryDao: JournalEntryDao,
        categoryDao: CategoryDao,
        gpsTrackPointDao: GpsTrackPointDao,
        backupManager: JournalBackupManager
    ) : this(
        entryDao = entryDao,
        categoryDao = categoryDao,
        gpsTrackPointDao = gpsTrackPointDao,
        backupManager = backupManager,
        runInTransaction = { block -> database.withTransaction { block() } }
    )

    private val virtualDeletedCategory = Category(
        id = JournalConstants.CATEGORY_DELETED_ID,
        category = JournalConstants.CATEGORY_DELETED_NAME,
        aliases = "",
        orderIndex = Int.MAX_VALUE, // Put it at the end
        color = "#FFCDD2", // Light red
        checkable = false,
        showDate = true,
        deleteLineOnDoubleTap = false
    )

    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories().map { categories ->
        categories.filterNot { JournalConstants.isVirtualDeletedCategory(it) } + virtualDeletedCategory
    }

    val realCategories: Flow<List<Category>> = categoryDao.getAllCategories().map { categories ->
        categories.filterNot { JournalConstants.isVirtualDeletedCategory(it) }
    }

    private val _entryCreatedEvents = MutableSharedFlow<UUID>(extraBufferCapacity = 1)
    val entryCreatedEvents: SharedFlow<UUID> = _entryCreatedEvents.asSharedFlow()

    fun notifyEntryCreated(entryId: UUID) {
        _entryCreatedEvents.tryEmit(entryId)
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

    fun getEntriesForCalendar(startTime: Long, endTime: Long): Flow<List<EntryWithCategories>> =
        entryDao.getEntriesForCalendar(startTime, endTime)

    suspend fun getEntryById(entryId: UUID): EntryWithCategories? = entryDao.getEntryById(entryId)

    suspend fun getEntryByIdIncludingDeleted(entryId: UUID): EntryWithCategories? =
        entryDao.getEntryByIdIncludingDeleted(entryId)

    suspend fun getEntriesByIds(ids: Set<UUID>): List<EntryWithCategories> {
        if (ids.isEmpty()) return emptyList()
        if (ids.size <= JournalConstants.SQLITE_MAX_BIND_PARAMS) {
            return entryDao.getEntriesByIds(ids)
        }
        return ids.chunked(JournalConstants.SQLITE_MAX_BIND_PARAMS)
            .flatMap { chunk -> entryDao.getEntriesByIds(chunk) }
            .sortedBy { it.entry.start_datetime }
    }

    suspend fun getEntriesForClipboard(
        categoryId: Int?,
        selectedDate: LocalDate?
    ): List<EntryWithCategories> {
        if (categoryId == JournalConstants.CATEGORY_DELETED_ID) {
            val deleted = entryDao.getPaginatedDeletedEntries(500, 0)
            return if (selectedDate != null) {
                deleted.filter { it.entry.localDate() == selectedDate }
            } else {
                deleted
            }
        }

        val validCategoryId = categoryId?.takeIf { it > 0 }

        if (selectedDate != null) {
            val (queryStartMillis, queryEndMillis) = TimeZoneUtils.utcQueryBoundsForLocalDates(selectedDate)
            val candidates = if (validCategoryId != null) {
                entryDao.getEntriesWithCategoriesInDateRangeForCategory(validCategoryId, queryStartMillis, queryEndMillis).first()
            } else {
                entryDao.getEntriesWithCategoriesInDateRange(queryStartMillis, queryEndMillis).first()
            }
            return candidates.filter { it.entry.localDate() == selectedDate }
        }

        return if (validCategoryId != null) {
            entryDao.getEntriesForCategory(validCategoryId)
        } else {
            entryDao.getPaginatedEntriesWithCategories(500, 0)
        }
    }

    fun getEntriesForLocalDate(selectedDate: LocalDate): Flow<List<EntryWithCategories>> {
        val (queryStartMillis, queryEndMillis) = TimeZoneUtils.utcQueryBoundsForLocalDates(selectedDate)
        return entryDao.getEntriesWithCategoriesInDateRange(queryStartMillis, queryEndMillis)
            .map { entries -> entries.filter { it.entry.localDate() == selectedDate } }
    }

    suspend fun importJournal(uri: Uri) = backupManager.importJournal(uri)

    suspend fun importEntriesToCategory(entries: List<JournalEntry>, targetCategory: Category) =
        backupManager.importEntriesToCategory(entries, targetCategory)

    suspend fun exportJournal(uri: Uri) = backupManager.exportJournal(uri)

    suspend fun insertCategory(category: Category) {
        if (!JournalConstants.isVirtualDeletedCategory(category)) {
            val safeOrderIndex = category.orderIndex.coerceIn(0, Int.MAX_VALUE - 2)
            categoryDao.insertCategory(category.copy(orderIndex = safeOrderIndex))
        }
    }

    suspend fun updateCategory(category: Category) {
        if (!JournalConstants.isVirtualDeletedCategory(category)) {
            val safeOrderIndex = category.orderIndex.coerceIn(0, Int.MAX_VALUE - 2)
            categoryDao.updateCategory(category.copy(orderIndex = safeOrderIndex))
        }
    }

    suspend fun updateCategories(categories: List<Category>) {
        val filtered = categories
            .filterNot { JournalConstants.isVirtualDeletedCategory(it) }
            .map { it.copy(orderIndex = it.orderIndex.coerceIn(0, Int.MAX_VALUE - 2)) }
        categoryDao.updateCategories(filtered)
    }

    suspend fun insert(entry: JournalEntry, categories: List<Category>): List<Category> {
        val filtered = categories.filterNot { JournalConstants.isVirtualDeletedCategory(it) }
        return entryDao.insertWithCategories(entry, filtered, categoryDao)
    }

    suspend fun update(entry: JournalEntry, categories: List<Category>) {
        val filtered = categories.filterNot { JournalConstants.isVirtualDeletedCategory(it) }
        entryDao.updateWithCategories(entry, filtered, categoryDao)
    }

    suspend fun updateEntryChecked(entryId: UUID, checked: Boolean) = entryDao.updateChecked(entryId, checked)

    suspend fun updateEntriesZoneId(ids: Set<UUID>, zoneId: String?) {
        if (ids.isEmpty()) return
        val sanitized = TimeZoneUtils.sanitizeZoneId(zoneId)
        runInTransaction {
            ids.chunked(JournalConstants.SQLITE_MAX_BIND_PARAMS).forEach { chunk ->
                entryDao.updateZoneIdByIds(chunk, sanitized)
            }
        }
    }

    suspend fun deleteEntries(ids: Set<UUID>) {
        if (ids.isEmpty()) return
        val timestamp = System.currentTimeMillis()
        runInTransaction {
            ids.chunked(JournalConstants.SQLITE_MAX_BIND_PARAMS).forEach { chunk ->
                entryDao.softDeleteByIds(chunk, timestamp)
            }
        }
    }

    suspend fun hardDeleteEntries(ids: Set<UUID>) {
        if (ids.isEmpty()) return
        runInTransaction {
            ids.chunked(JournalConstants.SQLITE_MAX_BIND_PARAMS).forEach { chunk ->
                entryDao.hardDeleteByIds(chunk)
            }
        }
    }

    suspend fun restoreLatestDeletedEntry() {
        val latestDeleted = entryDao.getLatestDeletedEntry()
        if (latestDeleted != null) {
            entryDao.restoreEntry(latestDeleted.entry.id)
        }
    }

    suspend fun restoreEntries(ids: Set<UUID>) {
        if (ids.isEmpty()) return
        runInTransaction {
            ids.chunked(JournalConstants.SQLITE_MAX_BIND_PARAMS).forEach { chunk ->
                entryDao.restoreByIds(chunk)
            }
        }
    }

    fun getDeletedEntriesCount(): Flow<Int> = entryDao.getDeletedEntriesCount()

    suspend fun deleteCategory(categoryId: Int) {
        if (categoryId != JournalConstants.CATEGORY_DELETED_ID) categoryDao.deleteCategory(categoryId)
    }

    suspend fun deleteAll() {
        entryDao.softDeleteAll(System.currentTimeMillis())
    }

    suspend fun insertGpsPoint(point: GpsTrackPoint) {
        gpsTrackPointDao.insert(point)
    }

    fun getTrackPointsForDay(dayStartMillis: Long, dayEndMillis: Long): Flow<List<GpsTrackPoint>> {
        return gpsTrackPointDao.getTrackPointsForDay(dayStartMillis, dayEndMillis)
    }

    fun searchEntries(query: String): Flow<List<EntryWithCategories>> = entryDao.searchEntries(query)

    suspend fun getPaginatedEntries(limit: Int, offset: Int): List<EntryWithCategories> {
        return entryDao.getPaginatedEntriesWithCategories(
            limit = limit.coerceAtLeast(1),
            offset = offset.coerceAtLeast(0)
        )
    }

    suspend fun getPaginatedEntriesForCategory(
        categoryId: Int,
        limit: Int,
        offset: Int
    ): List<EntryWithCategories> {
        val safeLimit = limit.coerceAtLeast(1)
        val safeOffset = offset.coerceAtLeast(0)
        return if (categoryId == JournalConstants.CATEGORY_DELETED_ID) {
            entryDao.getPaginatedDeletedEntries(safeLimit, safeOffset)
        } else {
            entryDao.getPaginatedEntriesForCategory(categoryId, safeLimit, safeOffset)
        }
    }

    suspend fun getCategoriesByIds(categoryIds: Collection<Int>): List<Category> {
        return categoryDao.getCategoriesByIds(categoryIds)
            .filterNot { JournalConstants.isVirtualDeletedCategory(it) }
    }

    suspend fun softDeleteEntry(entryId: UUID) {
        entryDao.softDelete(entryId, System.currentTimeMillis())
    }

    suspend fun getAllCategoriesList(): List<Category> {
        val dbList = categoryDao.getAllCategoriesList()
            .filterNot { JournalConstants.isVirtualDeletedCategory(it) }
        return dbList + virtualDeletedCategory
    }

    suspend fun getRealCategoriesList(): List<Category> {
        return categoryDao.getAllCategoriesList()
            .filterNot { JournalConstants.isVirtualDeletedCategory(it) }
    }

    suspend fun normalizeCategoryOrderIndicesIfNeeded() {
        val dbCategories = getRealCategoriesList()
        if (dbCategories.any { it.orderIndex < 0 || it.orderIndex >= Int.MAX_VALUE - 1 }) {
            val normalized = dbCategories.mapIndexed { index, cat ->
                cat.copy(orderIndex = index)
            }
            categoryDao.updateCategories(normalized)
        }
    }
}
