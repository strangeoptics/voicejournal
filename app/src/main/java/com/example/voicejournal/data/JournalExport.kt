package com.example.voicejournal.data

import kotlinx.serialization.Serializable
import java.util.UUID

// For V2 and below
@Serializable
data class JournalEntryV2(
    val id: Int = 0,
    val title: String,
    val content: String,
    val timestamp: Long,
    val hasImage: Boolean = false
)

@Serializable
data class JournalExport(
    val version: Int = 2,
    val entries: List<JournalEntryV2>,
    val categories: List<Category> = emptyList()
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
    val checked: Boolean = false,
    val categories: List<String>,
    val zoneId: String? = null
)

@Serializable
data class JournalExportV3(
    val version: Int = 3,
    val entries: List<JournalEntryExport>,
    val categories: List<Category>
)
