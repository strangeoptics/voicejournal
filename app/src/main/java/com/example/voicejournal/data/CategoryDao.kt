package com.example.voicejournal.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(category: Category): Long

    @Update
    suspend fun updateCategory(category: Category)

    @Update
    suspend fun updateCategories(categories: List<Category>)

    @Query("SELECT * FROM categories ORDER BY orderIndex ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY orderIndex ASC")
    suspend fun getAllCategoriesList(): List<Category>

    @Query("SELECT * FROM categories WHERE category = :categoryName LIMIT 1")
    suspend fun getCategoryByName(categoryName: String): Category?

    @Query("SELECT * FROM categories WHERE id IN (:categoryIds)")
    suspend fun getCategoriesByIdsChunk(categoryIds: Collection<Int>): List<Category>

    suspend fun getCategoriesByIds(categoryIds: Collection<Int>): List<Category> {
        if (categoryIds.isEmpty()) return emptyList()
        if (categoryIds.size <= JournalConstants.SQLITE_MAX_BIND_PARAMS) {
            return getCategoriesByIdsChunk(categoryIds)
        }
        return categoryIds.chunked(JournalConstants.SQLITE_MAX_BIND_PARAMS)
            .flatMap { chunk -> getCategoriesByIdsChunk(chunk) }
            .distinctBy { it.id }
    }

    @Query("UPDATE categories SET aliases = :aliases WHERE id = :categoryId")
    suspend fun updateAliasesForCategory(categoryId: Int, aliases: String)

    @Query("DELETE FROM categories WHERE id = :categoryId")
    suspend fun deleteCategory(categoryId: Int)
}