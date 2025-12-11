package com.example.voicejournal.data

import kotlinx.serialization.Serializable
import java.util.UUID

// For V2 and below
@Serializable
data class JournalExport(
    val version: Int = 2,
    val entries: List<JournalEntry>, // Note: JournalEntry for V2 had a `title` field for category
    val categories: List<Category>
)

// For V3
@Serializable
data class JournalEntryExport(
    @Serializable(with = UuidSerializer::class)
    val id: UUID,
    val content: String,
    val start_datetime: Long,
    var stop_datetime: Long? = null,
    val hasImage: Boolean,
    val categories: List<String>
)

@Serializable
data class JournalExportV3(
    val version: Int = 3,
    val entries: List<JournalEntryExport>,
    val categories: List<Category>
)
