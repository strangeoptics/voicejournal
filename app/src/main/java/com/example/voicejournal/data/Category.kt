package com.example.voicejournal.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "categories",
    indices = [Index(value = ["category"], unique = true)]
)
data class Category(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id") // Explicitly name the column to avoid issues
    val id: Int = 0,
    val category: String, // The canonical category name
    val aliases: String,     // A comma-separated list of aliases
    // Retained as default column for Room schema v18 stability and legacy JSON backup compatibility
    val showAll: Boolean = false,
    val orderIndex: Int = 0,
    val color: String = "#FFFFFF", // Default color white
    val checkable: Boolean = false,
    @ColumnInfo(defaultValue = "1")
    val showDate: Boolean = true
)
