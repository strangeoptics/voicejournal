package com.example.voicejournal.data

import kotlinx.serialization.Serializable
import java.util.UUID

// REST-API DTOs (synchronisiert mit api/openapi.yaml)
@Serializable
data class CategoryDto(
    val id: Int,
    val category: String,
    val aliases: String,
    val showAll: Boolean = false,
    val orderIndex: Int = 0,
    val color: String = "#FFFFFF",
    val checkable: Boolean = false,
    val showDate: Boolean = true,
    val deleteLineOnDoubleTap: Boolean = false
)

fun Category.toDto(): CategoryDto = CategoryDto(
    id = id,
    category = category,
    aliases = aliases,
    showAll = showAll,
    orderIndex = orderIndex,
    color = color,
    checkable = checkable,
    showDate = showDate,
    deleteLineOnDoubleTap = deleteLineOnDoubleTap
)

// DTO für einen einzelnen Journaleintrag
@Serializable
data class JournalEntryDto(
    @Serializable(with = UuidSerializer::class)
    val id: UUID,
    val content: String,
    val start_datetime: Long,
    val stop_datetime: Long? = null,
    val hasImage: Boolean,
    val checked: Boolean = false,
    val categoryIds: List<Int>,
    val zoneId: String? = null
)

@Serializable
data class CreateJournalEntryDto(
    val content: String,
    val start_datetime: Long,
    val stop_datetime: Long? = null,
    val hasImage: Boolean,
    val checked: Boolean = false,
    val categoryIds: List<Int>,
    val zoneId: String? = null
)
