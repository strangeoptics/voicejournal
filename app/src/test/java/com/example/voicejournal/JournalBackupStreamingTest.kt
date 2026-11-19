package com.example.voicejournal

import androidx.paging.PagingSource
import com.example.voicejournal.data.Category
import com.example.voicejournal.data.CategoryDao
import com.example.voicejournal.data.EntryWithCategories
import com.example.voicejournal.data.JournalBackupManager
import com.example.voicejournal.data.JournalEntry
import com.example.voicejournal.data.JournalEntryCategoryCrossRef
import com.example.voicejournal.data.JournalEntryDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID

class JournalBackupStreamingTest {

    private class FakeCategoryDao(initialCategories: List<Category> = emptyList()) : CategoryDao {
        private var nextId = (initialCategories.maxOfOrNull { it.id } ?: 0) + 1
        val categoriesById = LinkedHashMap<Int, Category>().apply {
            initialCategories.forEach { put(it.id, it) }
        }

        var getAllCategoriesListCalls = 0
        var getCategoryByNameCalls = 0
        var insertCategoryCalls = 0

        override suspend fun insertCategory(category: Category): Long {
            insertCategoryCalls++
            val existing = categoriesById.values.find { it.category == category.category }
            if (existing != null) return -1L
            val assignedId = if (category.id > 0 && !categoriesById.containsKey(category.id)) {
                category.id
            } else {
                nextId++
            }
            categoriesById[assignedId] = category.copy(id = assignedId)
            return assignedId.toLong()
        }

        override suspend fun updateCategory(category: Category) {
            categoriesById[category.id] = category
        }

        override suspend fun updateCategories(categories: List<Category>) {
            categories.forEach { categoriesById[it.id] = it }
        }

        override fun getAllCategories(): Flow<List<Category>> = emptyFlow()

        override suspend fun getAllCategoriesList(): List<Category> {
            getAllCategoriesListCalls++
            return categoriesById.values.sortedBy { it.orderIndex }
        }

        override suspend fun getCategoryByName(categoryName: String): Category? {
            getCategoryByNameCalls++
            return categoriesById.values.find { it.category == categoryName }
        }

        override suspend fun getCategoriesByIdsChunk(categoryIds: Collection<Int>): List<Category> {
            return categoryIds.mapNotNull { categoriesById[it] }
        }

        override suspend fun deleteCategory(categoryId: Int) {
            categoriesById.remove(categoryId)
        }
    }

    private class FakeJournalEntryDao(private val categoryDao: FakeCategoryDao) : JournalEntryDao {
        val entriesById = LinkedHashMap<UUID, JournalEntry>()
        val crossRefs = LinkedHashSet<JournalEntryCategoryCrossRef>()

        var singleInsertOrIgnoreCalls = 0
        var batchInsertOrIgnoreCalls = 0
        var singleCrossRefInsertCalls = 0
        var batchCrossRefInsertCalls = 0
        var batchGetEntriesByIdsCalls = 0

        private fun toEntryWithCategories(entry: JournalEntry): EntryWithCategories {
            val cats = crossRefs
                .filter { it.entryId == entry.id }
                .mapNotNull { categoryDao.categoriesById[it.categoryId] }
            return EntryWithCategories(entry = entry, categories = cats)
        }

        override suspend fun getAllEntriesWithCategories(): List<EntryWithCategories> {
            return entriesById.values
                .filter { it.deletedAt == null }
                .map(::toEntryWithCategories)
        }

        override suspend fun getEntryById(entryId: UUID): EntryWithCategories? {
            return entriesById[entryId]?.takeIf { it.deletedAt == null }?.let(::toEntryWithCategories)
        }

        override suspend fun getEntryByIdIncludingDeleted(entryId: UUID): EntryWithCategories? {
            return entriesById[entryId]?.let(::toEntryWithCategories)
        }

        override suspend fun getEntriesByIds(ids: Collection<UUID>): List<EntryWithCategories> {
            batchGetEntriesByIdsCalls++
            return ids.mapNotNull { entriesById[it] }
                .sortedBy { it.start_datetime }
                .map(::toEntryWithCategories)
        }

        override suspend fun insertOrIgnore(entry: JournalEntry): Long {
            singleInsertOrIgnoreCalls++
            if (entriesById.containsKey(entry.id)) return -1L
            entriesById[entry.id] = entry
            return 1L
        }

        override suspend fun insertEntriesOrIgnore(entries: List<JournalEntry>): List<Long> {
            batchInsertOrIgnoreCalls++
            return entries.map { entry ->
                if (entriesById.containsKey(entry.id)) {
                    -1L
                } else {
                    entriesById[entry.id] = entry
                    1L
                }
            }
        }

        override suspend fun update(entry: JournalEntry) {
            if (entriesById.containsKey(entry.id)) {
                entriesById[entry.id] = entry
            }
        }

        override suspend fun updateEntries(entries: List<JournalEntry>) {
            entries.forEach { entry ->
                if (entriesById.containsKey(entry.id)) {
                    entriesById[entry.id] = entry
                }
            }
        }

        override suspend fun insertJournalEntryCategoryCrossRef(crossRef: JournalEntryCategoryCrossRef) {
            singleCrossRefInsertCalls++
            crossRefs.add(crossRef)
        }

        override suspend fun insertJournalEntryCategoryCrossRefs(crossRefs: List<JournalEntryCategoryCrossRef>) {
            batchCrossRefInsertCalls++
            this.crossRefs.addAll(crossRefs)
        }

        override suspend fun deleteCrossRefsForEntry(entryId: UUID) {
            crossRefs.removeAll { it.entryId == entryId }
        }

        override suspend fun deleteCrossRefsForEntries(entryIds: Collection<UUID>) {
            val idSet = entryIds.toSet()
            crossRefs.removeAll { it.entryId in idSet }
        }

        // Unused query methods in backup tests
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
        override suspend fun softDelete(id: UUID, timestamp: Long) = throw UnsupportedOperationException()
        override suspend fun softDeleteByIds(ids: Collection<UUID>, timestamp: Long) = throw UnsupportedOperationException()
        override suspend fun hardDeleteByIds(ids: Collection<UUID>) = throw UnsupportedOperationException()
        override suspend fun restoreEntry(id: UUID) = throw UnsupportedOperationException()
        override suspend fun restoreByIds(ids: Collection<UUID>) = throw UnsupportedOperationException()
        override suspend fun getLatestDeletedEntry(): EntryWithCategories? = throw UnsupportedOperationException()
        override fun getDeletedEntriesCount(): Flow<Int> = throw UnsupportedOperationException()
        override suspend fun softDeleteAll(timestamp: Long) = throw UnsupportedOperationException()
        override fun getEntriesWithCategoriesInDateRangeForCategory(categoryId: Int, startDateMillis: Long, endDateMillis: Long): Flow<List<EntryWithCategories>> = throw UnsupportedOperationException()
        override fun getEntriesWithCategoriesInDateRange(startDateMillis: Long, endDateMillis: Long): Flow<List<EntryWithCategories>> = throw UnsupportedOperationException()
        override fun searchEntriesNormalized(normalizedEscapedQuery: String): Flow<List<EntryWithCategories>> = throw UnsupportedOperationException()
        override suspend fun getPositionOfEntry(entryId: UUID): Int = throw UnsupportedOperationException()
        override suspend fun getPositionOfEntryInCategory(entryId: UUID, categoryId: Int): Int = throw UnsupportedOperationException()
        override suspend fun getPositionOfDeletedEntry(entryId: UUID): Int = throw UnsupportedOperationException()
    }

    private fun createManager(
        entryDao: FakeJournalEntryDao,
        categoryDao: FakeCategoryDao
    ): JournalBackupManager {
        return JournalBackupManager(
            entryDao = entryDao,
            categoryDao = categoryDao,
            context = null,
            runInTransaction = { block -> block() }
        )
    }

    @Test
    fun v3ExportAndImportStream_roundTripsMetadataAndEliminatesNPlusOneQueries() = runBlocking {
        val sourceCatDao = FakeCategoryDao(
            listOf(
                Category(id = 1, category = "journal", aliases = "tagebuch", orderIndex = 0, color = "#FFCDD2"),
                Category(id = 2, category = "todo", aliases = "aufgabe", orderIndex = 1, checkable = true)
            )
        )
        val sourceEntryDao = FakeJournalEntryDao(sourceCatDao)

        // Generate 1,200 entries across 2 categories (> 2 batches of 500)
        repeat(1200) { idx ->
            val id = UUID.nameUUIDFromBytes("entry-$idx".toByteArray())
            val entry = JournalEntry(
                id = id,
                content = "Eintrag #$idx mit Umlauten äöüß",
                start_datetime = 1_700_000_000_000L + idx * 60_000L,
                stop_datetime = if (idx % 2 == 0) 1_700_000_030_000L + idx * 60_000L else null,
                hasImage = idx % 5 == 0,
                checked = idx % 3 == 0,
                zoneId = "Europe/Berlin"
            )
            sourceEntryDao.entriesById[id] = entry
            sourceEntryDao.crossRefs.add(JournalEntryCategoryCrossRef(id, 1))
            if (idx % 2 == 0) {
                sourceEntryDao.crossRefs.add(JournalEntryCategoryCrossRef(id, 2))
            }
        }

        val sourceManager = createManager(sourceEntryDao, sourceCatDao)
        val exportedBytes = ByteArrayOutputStream().also { out ->
            // Prepend UTF-8 BOM before exported JSON to verify BOM handling as well
            out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            sourceManager.exportToStream(out)
        }.toByteArray()

        val targetCatDao = FakeCategoryDao()
        val targetEntryDao = FakeJournalEntryDao(targetCatDao)
        val targetManager = createManager(targetEntryDao, targetCatDao)

        targetManager.importFromStream(ByteArrayInputStream(exportedBytes))

        assertEquals(1200, targetEntryDao.entriesById.size)
        assertEquals(2, targetCatDao.categoriesById.size)

        // Verify N+1 elimination: 1 category preload, 0 single-row category/entry/crossRef queries, 3 chunks (500 + 500 + 200)
        assertEquals(1, targetCatDao.getAllCategoriesListCalls)
        assertEquals(0, targetCatDao.getCategoryByNameCalls)
        assertEquals(0, targetEntryDao.singleInsertOrIgnoreCalls)
        assertEquals(0, targetEntryDao.singleCrossRefInsertCalls)
        assertEquals(3, targetEntryDao.batchInsertOrIgnoreCalls)
        assertEquals(3, targetEntryDao.batchCrossRefInsertCalls)
    }

    @Test
    fun importFromStream_supportsV2ObjectV2ArrayAndEmptyArray() = runBlocking {
        val catDao = FakeCategoryDao()
        val entryDao = FakeJournalEntryDao(catDao)
        val manager = createManager(entryDao, catDao)

        // Empty array with whitespace -> clean no-op
        manager.importFromStream(ByteArrayInputStream("   [ \n ]  ".toByteArray(Charsets.UTF_8)))
        assertEquals(0, entryDao.entriesById.size)

        // V2 object where "entries" precedes "version"
        val v2Obj = """
            {
              "entries": [
                {"id": 10, "title": "kaufen", "content": "Hafermilch", "timestamp": 1700000000000, "hasImage": true}
              ],
              "version": 2
            }
        """.trimIndent()
        manager.importFromStream(ByteArrayInputStream(v2Obj.toByteArray(Charsets.UTF_8)))
        assertEquals(1, entryDao.entriesById.size)
        val firstImported = entryDao.getAllEntriesWithCategories().single()
        assertEquals("Hafermilch", firstImported.entry.content)
        assertTrue(firstImported.entry.hasImage)
        assertEquals(listOf("kaufen"), firstImported.categories.map { it.category })

        // V2 array
        val v2Array = """
            [
              {"id": 11, "title": "", "content": "Fallback Journal", "timestamp": 1700000060000}
            ]
        """.trimIndent()
        manager.importFromStream(ByteArrayInputStream(v2Array.toByteArray(Charsets.UTF_8)))
        assertEquals(2, entryDao.entriesById.size)
        val fallbackEntry = entryDao.getAllEntriesWithCategories().first { it.entry.content == "Fallback Journal" }
        assertEquals(listOf("journal"), fallbackEntry.categories.map { it.category })
    }

    @Test
    fun importFromStream_supportsV3ArrayPreservingExistingCategoriesAndBatchImportToCategory() = runBlocking {
        val existingCat = Category(id = 1, category = "privat", aliases = "", orderIndex = 0)
        val targetCat = Category(id = 2, category = "reise", aliases = "", orderIndex = 1)
        val catDao = FakeCategoryDao(listOf(existingCat, targetCat))
        val entryDao = FakeJournalEntryDao(catDao)
        val manager = createManager(entryDao, catDao)

        val existingId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val newId = UUID.fromString("223e4567-e89b-12d3-a456-426614174001")

        // Seed existing entry with "privat" category (soft-deleted before re-import)
        entryDao.entriesById[existingId] = JournalEntry(
            id = existingId,
            content = "Alt",
            start_datetime = 1_700_000_000_000L,
            deletedAt = 1_700_000_500_000L
        )
        entryDao.crossRefs.add(JournalEntryCategoryCrossRef(existingId, existingCat.id))

        val v3ArrayJson = """
            [
              {"id":"$existingId","content":"Aktualisiert","start_datetime":1700000000000,"zoneId":"Asia/Tokyo"},
              {"id":"$newId","content":"Neu","start_datetime":1700000100000,"zoneId":"Invalid/Zone"}
            ]
        """.trimIndent()

        manager.importFromStream(ByteArrayInputStream(v3ArrayJson.toByteArray(Charsets.UTF_8)))

        val updatedExisting = entryDao.getEntryById(existingId)!!
        assertEquals("Aktualisiert", updatedExisting.entry.content)
        assertEquals("Asia/Tokyo", updatedExisting.entry.zoneId)
        assertEquals(listOf("privat"), updatedExisting.categories.map { it.category })

        val insertedNew = entryDao.getEntryById(newId)!!
        assertEquals("Neu", insertedNew.entry.content)
        assertEquals(null, insertedNew.entry.zoneId) // Invalid zone sanitized to null
        assertEquals(listOf("journal"), insertedNew.categories.map { it.category })

        // Now import existingId into targetCat ("reise") -> should merge ["privat", "reise"]
        manager.importEntriesToCategory(
            entries = listOf(updatedExisting.entry.copy(content = "Mit Reise-Tag")),
            targetCategory = targetCat
        )
        val merged = entryDao.getEntryById(existingId)!!
        assertEquals("Mit Reise-Tag", merged.entry.content)
        assertEquals(setOf("privat", "reise"), merged.categories.map { it.category }.toSet())
        assertFalse(merged.categories.any { it.category == "Gelöscht" })
    }

    @Test
    fun importFromStream_parsesLegacyBracketedLinesStartingWithSquareBracket() = runBlocking {
        val catDao = FakeCategoryDao()
        val entryDao = FakeJournalEntryDao(catDao)
        val manager = createManager(entryDao, catDao)

        val legacyText = """
            [2026-11-19 08:15] Guten Morgen beim Kaffee
            Ungültige Zwischenzeile wird übersprungen
            [2026-11-19 19:45] Abendspaziergang im Regen
        """.trimIndent()

        manager.importFromStream(ByteArrayInputStream(legacyText.toByteArray(Charsets.UTF_8)))

        val imported = entryDao.getAllEntriesWithCategories()
        assertEquals(2, imported.size)
        assertEquals("Guten Morgen beim Kaffee", imported[0].entry.content)
        assertEquals("Abendspaziergang im Regen", imported[1].entry.content)
        assertTrue(imported.all { it.categories.map { c -> c.category } == listOf("journal") })
    }

    @Test
    fun importFromStream_rejectsOversizedAndEmptyStreams() {
        val catDao = FakeCategoryDao()
        val entryDao = FakeJournalEntryDao(catDao)
        val manager = createManager(entryDao, catDao)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                manager.importFromStream(ByteArrayInputStream("   \n\t  ".toByteArray(Charsets.UTF_8)))
            }
        }

        val largeJson = """{"version":3,"categories":[],"entries":[{"id":"123e4567-e89b-12d3-a456-426614174000","content":"Zu lang","start_datetime":1700000000000,"hasImage":false,"categories":[]}]}"""
        assertThrows(JournalBackupManager.FileTooLargeException::class.java) {
            runBlocking {
                manager.importFromStream(
                    ByteArrayInputStream(largeJson.toByteArray(Charsets.UTF_8)),
                    maxBytes = 32L
                )
            }
        }
    }
}
