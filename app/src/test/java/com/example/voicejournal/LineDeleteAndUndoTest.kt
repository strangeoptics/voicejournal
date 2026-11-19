package com.example.voicejournal

import androidx.paging.PagingSource
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.voicejournal.data.AppDatabase
import com.example.voicejournal.data.Category
import com.example.voicejournal.data.CategoryDao
import com.example.voicejournal.data.EntryWithCategories
import com.example.voicejournal.data.GpsTrackPoint
import com.example.voicejournal.data.GpsTrackPointDao
import com.example.voicejournal.data.JournalBackupManager
import com.example.voicejournal.data.JournalEntry
import com.example.voicejournal.data.JournalEntryCategoryCrossRef
import com.example.voicejournal.data.JournalEntryDao
import com.example.voicejournal.data.JournalRepository
import com.example.voicejournal.ui.components.shouldTruncateEntryContent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.reflect.Proxy
import java.util.UUID

class LineDeleteAndUndoTest {

    private class InMemoryCategoryDao(initial: List<Category> = emptyList()) : CategoryDao {
        private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1
        val categoriesState = MutableStateFlow(
            LinkedHashMap<Int, Category>().apply {
                initial.forEach { put(it.id, it) }
            }
        )

        override suspend fun insertCategory(category: Category): Long {
            val map = LinkedHashMap(categoriesState.value)
            if (map.values.any { it.category == category.category }) return -1L
            val assignedId = if (category.id > 0 && !map.containsKey(category.id)) category.id else nextId++
            map[assignedId] = category.copy(id = assignedId)
            categoriesState.value = map
            return assignedId.toLong()
        }

        override suspend fun updateCategory(category: Category) {
            val map = LinkedHashMap(categoriesState.value)
            map[category.id] = category
            categoriesState.value = map
        }

        override suspend fun updateCategories(categories: List<Category>) {
            val map = LinkedHashMap(categoriesState.value)
            categories.forEach { map[it.id] = it }
            categoriesState.value = map
        }

        override fun getAllCategories(): Flow<List<Category>> =
            categoriesState.map { it.values.sortedBy { c -> c.orderIndex } }

        override suspend fun getAllCategoriesList(): List<Category> =
            categoriesState.value.values.sortedBy { it.orderIndex }

        override suspend fun getCategoryByName(categoryName: String): Category? =
            categoriesState.value.values.find { it.category == categoryName }

        override suspend fun getCategoriesByIdsChunk(categoryIds: Collection<Int>): List<Category> =
            categoryIds.mapNotNull { categoriesState.value[it] }

        override suspend fun deleteCategory(categoryId: Int) {
            val map = LinkedHashMap(categoriesState.value)
            map.remove(categoryId)
            categoriesState.value = map
        }
    }

    private class InMemoryJournalEntryDao(private val categoryDao: InMemoryCategoryDao) : JournalEntryDao {
        private val entriesState = MutableStateFlow(LinkedHashMap<UUID, JournalEntry>())
        val crossRefs = LinkedHashSet<JournalEntryCategoryCrossRef>()

        private fun toEntryWithCategories(entry: JournalEntry): EntryWithCategories {
            val cats = crossRefs
                .filter { it.entryId == entry.id }
                .mapNotNull { categoryDao.categoriesState.value[it.categoryId] }
            return EntryWithCategories(entry = entry, categories = cats)
        }

        override suspend fun getAllEntriesWithCategories(): List<EntryWithCategories> =
            entriesState.value.values.filter { it.deletedAt == null }.map(::toEntryWithCategories)

        override suspend fun getEntryById(entryId: UUID): EntryWithCategories? =
            entriesState.value[entryId]?.takeIf { it.deletedAt == null }?.let(::toEntryWithCategories)

        override suspend fun getEntryByIdIncludingDeleted(entryId: UUID): EntryWithCategories? =
            entriesState.value[entryId]?.let(::toEntryWithCategories)

        override suspend fun getEntriesByIds(ids: Collection<UUID>): List<EntryWithCategories> =
            ids.mapNotNull { entriesState.value[it] }
                .sortedBy { it.start_datetime }
                .map(::toEntryWithCategories)

        override suspend fun insertOrIgnore(entry: JournalEntry): Long {
            val map = LinkedHashMap(entriesState.value)
            if (map.containsKey(entry.id)) return -1L
            map[entry.id] = entry
            entriesState.value = map
            return 1L
        }

        override suspend fun insertEntriesOrIgnore(entries: List<JournalEntry>): List<Long> {
            val map = LinkedHashMap(entriesState.value)
            val result = entries.map { entry ->
                if (map.containsKey(entry.id)) {
                    -1L
                } else {
                    map[entry.id] = entry
                    1L
                }
            }
            entriesState.value = map
            return result
        }

        override suspend fun update(entry: JournalEntry) {
            val map = LinkedHashMap(entriesState.value)
            if (map.containsKey(entry.id)) {
                map[entry.id] = entry
                entriesState.value = map
            }
        }

        override suspend fun updateEntries(entries: List<JournalEntry>) {
            val map = LinkedHashMap(entriesState.value)
            entries.forEach { entry ->
                if (map.containsKey(entry.id)) {
                    map[entry.id] = entry
                }
            }
            entriesState.value = map
        }

        override suspend fun softDelete(id: UUID, timestamp: Long) {
            softDeleteByIds(listOf(id), timestamp)
        }

        override suspend fun softDeleteByIds(ids: Collection<UUID>, timestamp: Long) {
            val map = LinkedHashMap(entriesState.value)
            ids.forEach { id ->
                map[id]?.let { map[id] = it.copy(deletedAt = timestamp) }
            }
            entriesState.value = map
        }

        override suspend fun hardDeleteByIds(ids: Collection<UUID>) {
            val map = LinkedHashMap(entriesState.value)
            val idSet = ids.toSet()
            idSet.forEach { map.remove(it) }
            crossRefs.removeAll { it.entryId in idSet }
            entriesState.value = map
        }

        override suspend fun restoreEntry(id: UUID) {
            restoreByIds(listOf(id))
        }

        override suspend fun restoreByIds(ids: Collection<UUID>) {
            val map = LinkedHashMap(entriesState.value)
            ids.forEach { id ->
                map[id]?.let { map[id] = it.copy(deletedAt = null) }
            }
            entriesState.value = map
        }

        override suspend fun getLatestDeletedEntry(): EntryWithCategories? =
            entriesState.value.values
                .filter { it.deletedAt != null }
                .maxByOrNull { it.deletedAt!! }
                ?.let(::toEntryWithCategories)

        override fun getDeletedEntriesCount(): Flow<Int> =
            entriesState.map { map -> map.values.count { it.deletedAt != null } }

        override suspend fun insertJournalEntryCategoryCrossRef(crossRef: JournalEntryCategoryCrossRef) {
            crossRefs.add(crossRef)
        }

        override suspend fun insertJournalEntryCategoryCrossRefs(crossRefs: List<JournalEntryCategoryCrossRef>) {
            this.crossRefs.addAll(crossRefs)
        }

        override suspend fun deleteCrossRefsForEntry(entryId: UUID) {
            crossRefs.removeAll { it.entryId == entryId }
        }

        override suspend fun deleteCrossRefsForEntries(entryIds: Collection<UUID>) {
            val idSet = entryIds.toSet()
            crossRefs.removeAll { it.entryId in idSet }
        }

        override fun getEntriesWithCategories(): Flow<List<EntryWithCategories>> = throw UnsupportedOperationException()
        override fun getEntriesPagingSource(): PagingSource<Int, EntryWithCategories> = throw UnsupportedOperationException()
        override fun getEntriesPagingSourceForCategory(categoryId: Int): PagingSource<Int, EntryWithCategories> = throw UnsupportedOperationException()
        override fun getDeletedEntriesPagingSource(): PagingSource<Int, EntryWithCategories> = throw UnsupportedOperationException()
        override suspend fun getPaginatedEntriesWithCategories(limit: Int, offset: Int): List<EntryWithCategories> = throw UnsupportedOperationException()
        override suspend fun getEntriesForCategory(categoryId: Int): List<EntryWithCategories> = throw UnsupportedOperationException()
        override suspend fun getPaginatedEntriesForCategory(categoryId: Int, limit: Int, offset: Int): List<EntryWithCategories> = throw UnsupportedOperationException()
        override suspend fun getPaginatedDeletedEntries(limit: Int, offset: Int): List<EntryWithCategories> = throw UnsupportedOperationException()
        override fun getEntriesForCalendar(startTime: Long, endTime: Long): Flow<List<EntryWithCategories>> = throw UnsupportedOperationException()
        override suspend fun updateChecked(id: UUID, isChecked: Boolean) = throw UnsupportedOperationException()
        override suspend fun updateZoneIdByIds(ids: Collection<UUID>, zoneId: String?) = throw UnsupportedOperationException()
        override suspend fun softDeleteAll(timestamp: Long) = throw UnsupportedOperationException()
        override fun getEntriesWithCategoriesInDateRangeForCategory(categoryId: Int, startDateMillis: Long, endDateMillis: Long): Flow<List<EntryWithCategories>> = throw UnsupportedOperationException()
        override fun getEntriesWithCategoriesInDateRange(startDateMillis: Long, endDateMillis: Long): Flow<List<EntryWithCategories>> = throw UnsupportedOperationException()
        override fun searchEntriesNormalized(normalizedEscapedQuery: String): Flow<List<EntryWithCategories>> = throw UnsupportedOperationException()
        override suspend fun getPositionOfEntry(entryId: UUID): Int = throw UnsupportedOperationException()
        override suspend fun getPositionOfEntryInCategory(entryId: UUID, categoryId: Int): Int = throw UnsupportedOperationException()
        override suspend fun getPositionOfDeletedEntry(entryId: UUID): Int = throw UnsupportedOperationException()
    }

    private object NoOpGpsTrackPointDao : GpsTrackPointDao {
        override suspend fun insert(point: GpsTrackPoint) = Unit
        override fun getTrackPointsForDay(dayStartMillis: Long, dayEndMillis: Long): Flow<List<GpsTrackPoint>> = emptyFlow()
    }

    private fun createRepository(
        categoryDao: InMemoryCategoryDao,
        entryDao: InMemoryJournalEntryDao
    ): JournalRepository {
        val backupManager = JournalBackupManager(
            entryDao = entryDao,
            categoryDao = categoryDao,
            context = null,
            runInTransaction = { block -> block() }
        )
        return JournalRepository(
            entryDao = entryDao,
            categoryDao = categoryDao,
            gpsTrackPointDao = NoOpGpsTrackPointDao,
            backupManager = backupManager,
            runInTransaction = { block -> block() }
        )
    }

    @Test
    fun removeLineAt_deletesRequestedLineAndDeletesEntryWhenSingleOrOnlyBlankLinesRemain() {
        assertEquals(
            LineRemovalResult.ContentUpdated("Milch\nEier"),
            MainViewModel.removeLineAt("Milch\nBrot\nEier", 1)
        )
        assertEquals(
            LineRemovalResult.ContentUpdated("Brot\nEier"),
            MainViewModel.removeLineAt("Milch\r\nBrot\r\nEier", 0)
        )
        assertTrue(MainViewModel.removeLineAt("Milch", 0) is LineRemovalResult.DeleteEntireEntry)
        assertTrue(MainViewModel.removeLineAt("Milch\n   ", 0) is LineRemovalResult.DeleteEntireEntry)
        assertTrue(MainViewModel.removeLineAt("Milch\nBrot", 5) is LineRemovalResult.NoChange)
        assertTrue(MainViewModel.removeLineAt("Milch\nBrot", -1) is LineRemovalResult.NoChange)
    }

    @Test
    fun shouldTruncateEntryContent_disablesTruncationWhenCategoryFlagIsSet() {
        val regularCat = Category(id = 1, category = "journal", aliases = "", deleteLineOnDoubleTap = false)
        val shoppingCat = Category(id = 2, category = "kaufen", aliases = "", deleteLineOnDoubleTap = true)

        assertTrue(
            shouldTruncateEntryContent(
                categories = listOf(regularCat),
                isExpanded = false,
                truncationLength = 50,
                contentLength = 200
            )
        )
        assertFalse(
            shouldTruncateEntryContent(
                categories = listOf(regularCat, shoppingCat),
                isExpanded = false,
                truncationLength = 50,
                contentLength = 200
            )
        )
    }

    @Test
    fun shoppingListWorkflow_deletesLinesStepByStepSoftDeletesLastLineAndUndoesInLifoOrder() = runBlocking {
        val shoppingCat = Category(id = 1, category = "kaufen", aliases = "einkaufen", deleteLineOnDoubleTap = true)
        val journalCat = Category(id = 2, category = "journal", aliases = "", deleteLineOnDoubleTap = false)
        val categoryDao = InMemoryCategoryDao(listOf(shoppingCat, journalCat))
        val entryDao = InMemoryJournalEntryDao(categoryDao)
        val repository = createRepository(categoryDao, entryDao)
        val undoManager = LineDeleteUndoManager(repository)

        val shoppingEntry = JournalEntry(content = "Milch\nBrot\nEier", start_datetime = 1_000L)
        repository.insert(shoppingEntry, listOf(shoppingCat))

        val regularEntry = JournalEntry(content = "Zeile A\nZeile B", start_datetime = 2_000L)
        repository.insert(regularEntry, listOf(journalCat))

        assertFalse(undoManager.canUndo.first())

        // 1. Regular category without deleteLineOnDoubleTap flag must NOT delete lines
        val regularSnapshot = repository.getEntryById(regularEntry.id)!!
        val ignoredOutcome = undoManager.deleteLineInEntry(regularSnapshot, lineIndex = 0, isRecycleBinSelected = false)
        assertEquals(LineDeleteOutcome.Unchanged, ignoredOutcome)
        assertEquals("Zeile A\nZeile B", repository.getEntryById(regularEntry.id)!!.entry.content)

        // 2. Delete middle line ("Brot") from shopping list
        val snap1 = repository.getEntryById(shoppingEntry.id)!!
        val outcome1 = undoManager.deleteLineInEntry(snap1, lineIndex = 1, isRecycleBinSelected = false)
        assertTrue(outcome1 is LineDeleteOutcome.EntryUpdated)
        assertEquals("Milch\nEier", repository.getEntryById(shoppingEntry.id)!!.entry.content)
        assertTrue(undoManager.canUndo.first())

        // Stale snapshot (snap1 still has "Milch\nBrot\nEier") must be rejected safely
        val staleOutcome = undoManager.deleteLineInEntry(snap1, lineIndex = 1, isRecycleBinSelected = false)
        assertEquals(LineDeleteOutcome.Unchanged, staleOutcome)
        assertEquals("Milch\nEier", repository.getEntryById(shoppingEntry.id)!!.entry.content)

        // 3. Delete first line ("Milch") -> leaves single line "Eier"
        val snap2 = repository.getEntryById(shoppingEntry.id)!!
        val outcome2 = undoManager.deleteLineInEntry(snap2, lineIndex = 0, isRecycleBinSelected = false)
        assertTrue(outcome2 is LineDeleteOutcome.EntryUpdated)
        assertEquals("Eier", repository.getEntryById(shoppingEntry.id)!!.entry.content)

        // 4. Delete last remaining line ("Eier") -> soft-deletes the entire entry
        val snap3 = repository.getEntryById(shoppingEntry.id)!!
        val outcome3 = undoManager.deleteLineInEntry(snap3, lineIndex = 0, isRecycleBinSelected = false)
        assertEquals(LineDeleteOutcome.EntrySoftDeleted(shoppingEntry.id), outcome3)
        assertEquals(3, undoManager.undoStack.value.size)
        assertNull(repository.getEntryById(shoppingEntry.id))
        assertNotNull(repository.getEntryByIdIncludingDeleted(shoppingEntry.id)?.entry?.deletedAt)

        // 5. Undo #1 -> restores soft-deleted entry with "Eier"
        undoManager.undo()
        val afterUndo1 = repository.getEntryById(shoppingEntry.id)
        assertNotNull(afterUndo1)
        assertEquals("Eier", afterUndo1!!.entry.content)
        assertTrue(undoManager.canUndo.first())

        // 6. Undo #2 -> restores "Milch\nEier"
        val restored2 = undoManager.undo()
        assertEquals("Milch\nEier", restored2?.entry?.content)
        assertEquals("Milch\nEier", repository.getEntryById(shoppingEntry.id)!!.entry.content)
        assertTrue(undoManager.canUndo.first())

        // 7. Undo #3 -> restores original "Milch\nBrot\nEier"
        val restored3 = undoManager.undo()
        assertEquals("Milch\nBrot\nEier", restored3?.entry?.content)
        assertEquals("Milch\nBrot\nEier", repository.getEntryById(shoppingEntry.id)!!.entry.content)
        assertFalse(undoManager.canUndo.first())
    }

    @Test
    fun backupExportImport_preservesDeleteLineOnDoubleTapFlag() = runBlocking {
        val sourceCatDao = InMemoryCategoryDao(
            listOf(
                Category(
                    id = 1,
                    category = "kaufen",
                    aliases = "einkaufen",
                    orderIndex = 0,
                    checkable = false,
                    showDate = false,
                    deleteLineOnDoubleTap = true
                )
            )
        )
        val sourceEntryDao = InMemoryJournalEntryDao(sourceCatDao)
        val sourceRepo = createRepository(sourceCatDao, sourceEntryDao)
        sourceRepo.insert(
            JournalEntry(content = "Äpfel\nBananen", start_datetime = 10_000L),
            sourceCatDao.getAllCategoriesList()
        )

        val sourceBackup = JournalBackupManager(
            entryDao = sourceEntryDao,
            categoryDao = sourceCatDao,
            context = null,
            runInTransaction = { block -> block() }
        )
        val output = ByteArrayOutputStream()
        sourceBackup.exportToStream(output)
        val exportedJson = output.toString(Charsets.UTF_8.name())
        assertTrue(exportedJson.contains("\"deleteLineOnDoubleTap\": true"))

        val targetCatDao = InMemoryCategoryDao()
        val targetEntryDao = InMemoryJournalEntryDao(targetCatDao)
        val targetBackup = JournalBackupManager(
            entryDao = targetEntryDao,
            categoryDao = targetCatDao,
            context = null,
            runInTransaction = { block -> block() }
        )
        targetBackup.importFromStream(ByteArrayInputStream(exportedJson.toByteArray(Charsets.UTF_8)))

        val importedCategory = targetCatDao.getCategoryByName("kaufen")
        assertNotNull(importedCategory)
        assertTrue(importedCategory!!.deleteLineOnDoubleTap)
        assertFalse(importedCategory.showDate)
    }

    @Test
    fun migration18To19_executesAlterTableAndExportsSchema19() {
        val executedStatements = mutableListOf<String>()
        val recordingDb = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedStatements += args[0] as String
            }
            null
        } as SupportSQLiteDatabase

        AppDatabase.MIGRATION_18_19.migrate(recordingDb)
        assertEquals(
            listOf("ALTER TABLE `categories` ADD COLUMN `deleteLineOnDoubleTap` INTEGER NOT NULL DEFAULT 0"),
            executedStatements
        )

        val schemaFile = listOf(
            File("schemas/com.example.voicejournal.data.AppDatabase/19.json"),
            File("app/schemas/com.example.voicejournal.data.AppDatabase/19.json")
        ).firstOrNull { it.exists() }
        assertTrue("Expected exported Room schema 19.json to exist", schemaFile != null)
        val schemaText = schemaFile!!.readText()
        assertTrue(schemaText.contains("\"version\": 19"))
        assertTrue(schemaText.contains("\"columnName\": \"deleteLineOnDoubleTap\""))
    }
}
