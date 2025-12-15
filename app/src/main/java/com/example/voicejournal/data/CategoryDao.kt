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
    suspend fun getCategoriesByIds(categoryIds: List<Int>): List<Category>

    @Query("UPDATE categories SET aliases = :aliases WHERE id = :categoryId")
    suspend fun updateAliasesForCategory(categoryId: Int, aliases: String)

    @Query("DELETE FROM categories WHERE id = :categoryId")
    suspend fun deleteCategory(categoryId: Int)
}