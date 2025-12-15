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
    val showAll: Boolean = false,
    val orderIndex: Int = 0,
    val color: String = "#FFFFFF" // Default color white
)
