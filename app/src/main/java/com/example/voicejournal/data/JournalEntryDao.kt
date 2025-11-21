package com.example.voicejournal.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalEntryDao {
    @Query("SELECT * FROM journal_entries ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<JournalEntry>>

    @Query("SELECT * FROM journal_entries WHERE timestamp >= :since ORDER BY timestamp DESC")
    fun getEntriesSince(since: Long): Flow<List<JournalEntry>>

    @Insert
    suspend fun insert(entry: JournalEntry)

    @Update
    suspend fun update(entry: JournalEntry)

    @Delete
    suspend fun delete(entry: JournalEntry)

    @Query("DELETE FROM journal_entries WHERE id = (SELECT id FROM journal_entries WHERE title = :category ORDER BY timestamp DESC LIMIT 1)")
    suspend fun deleteLatestByCategory(category: String)

    @Query("DELETE FROM journal_entries")
    suspend fun deleteAll()

    // CategoryAlias DAO methods
    @Insert
    suspend fun insertCategoryAlias(categoryAlias: CategoryAlias)

    @Delete
    suspend fun deleteCategoryAlias(categoryAlias: CategoryAlias)

    @Query("DELETE FROM category_aliases WHERE category = :category AND alias = :alias")
    suspend fun deleteAlias(category: String, alias: String)


    @Query("SELECT * FROM category_aliases")
    fun getAllCategoryAliases(): Flow<List<CategoryAlias>>

    @Query("SELECT * FROM category_aliases WHERE category = :category")
    suspend fun getAliasesForCategory(category: String): List<CategoryAlias>

    @Transaction
    suspend fun updateAliasesForCategory(category: String, newAliases: List<String>) {
        val oldAliases = getAliasesForCategory(category).map { it.alias }
        val aliasesToDelete = oldAliases.filter { it !in newAliases }
        val aliasesToAdd = newAliases.filter { it !in oldAliases }

        aliasesToDelete.forEach { alias ->
            deleteAlias(category, alias)
        }
        aliasesToAdd.forEach { alias ->
            insertCategoryAlias(CategoryAlias(category = category, alias = alias))
        }
    }

    @Query("DELETE FROM category_aliases WHERE category = :category")
    suspend fun deleteCategory(category: String)
}