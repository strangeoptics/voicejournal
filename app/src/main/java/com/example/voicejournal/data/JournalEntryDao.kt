package com.example.voicejournal.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalEntryDao {
    @Transaction
    @Query("SELECT * FROM journal_entries ORDER BY timestamp DESC")
    fun getEntriesWithCategories(): Flow<List<EntryWithCategories>>

    @Transaction
    @Query("SELECT * FROM journal_entries")
    suspend fun getAllEntriesWithCategories(): List<EntryWithCategories>

    @Transaction
    @Query("SELECT * FROM journal_entries WHERE timestamp >= :since ORDER BY timestamp DESC")
    fun getEntriesWithCategoriesSince(since: Long): Flow<List<EntryWithCategories>>

    @Transaction
    @Query("SELECT * FROM journal_entries WHERE id IN (SELECT entryId FROM journal_entry_category_cross_ref WHERE categoryId = :categoryId) ORDER BY timestamp DESC")
    suspend fun getEntriesForCategory(categoryId: Int): List<EntryWithCategories>

    @Transaction
    @Query("SELECT * FROM journal_entries WHERE id = :entryId")
    suspend fun getEntryById(entryId: Int): EntryWithCategories?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: JournalEntry): Long

    @Update
    suspend fun update(entry: JournalEntry)

    @Delete
    suspend fun delete(entry: JournalEntry)

    @Query("DELETE FROM journal_entries")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJournalEntryCategoryCrossRef(crossRef: JournalEntryCategoryCrossRef)

    @Query("DELETE FROM journal_entry_category_cross_ref WHERE entryId = :entryId")
    suspend fun deleteCrossRefsForEntry(entryId: Int)

    // Category DAO methods
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(category: Category): Long

    @Update
    suspend fun updateCategories(categories: List<Category>)

    @Query("SELECT * FROM categories ORDER BY orderIndex ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY orderIndex ASC")
    suspend fun getAllCategoriesList(): List<Category>
    
    @Query("SELECT * FROM categories WHERE category = :categoryName LIMIT 1")
    suspend fun getCategoryByName(categoryName: String): Category?

    @Query("UPDATE categories SET aliases = :aliases WHERE id = :categoryId")
    suspend fun updateAliasesForCategory(categoryId: Int, aliases: String)

    @Query("DELETE FROM categories WHERE id = :categoryId")
    suspend fun deleteCategory(categoryId: Int)

    @Transaction
    suspend fun insertWithCategories(entry: JournalEntry, categories: List<Category>) {
        val entryId = insert(entry).toInt()
        categories.forEach { category ->
            var cat = getCategoryByName(category.category)
            if (cat == null) {
                val newId = insertCategory(category)
                cat = category.copy(id = newId.toInt())
            }
            insertJournalEntryCategoryCrossRef(
                JournalEntryCategoryCrossRef(entryId, cat.id)
            )
        }
    }

    @Transaction
    suspend fun updateWithCategories(entry: JournalEntry, categories: List<Category>) {
        update(entry)
        deleteCrossRefsForEntry(entry.id)
        categories.forEach { category ->
             var cat = getCategoryByName(category.category)
            if (cat == null) {
                val newId = insertCategory(category)
                cat = category.copy(id = newId.toInt())
            }
            insertJournalEntryCategoryCrossRef(
                JournalEntryCategoryCrossRef(entry.id, cat.id)
            )
        }
    }
}