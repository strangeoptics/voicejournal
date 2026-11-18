package com.example.voicejournal.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.voicejournal.util.TimeZoneUtils
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

@Serializable
@Entity(
    tableName = "journal_entries",
    indices = [
        Index(value = ["deletedAt", "start_datetime"]),
        Index(value = ["start_datetime"]),
        Index(value = ["stop_datetime"])
    ]
)
data class JournalEntry(
    @PrimaryKey
    @Serializable(with = UuidSerializer::class)
    val id: UUID = UUID.randomUUID(),
    val content: String,
    val start_datetime: Long,
    val stop_datetime: Long? = null,
    val hasImage: Boolean = false,
    val checked: Boolean = false,
    val deletedAt: Long? = null,
    val zoneId: String? = null
)

fun JournalEntry.resolvedZoneId(fallback: ZoneId = ZoneId.systemDefault()): ZoneId =
    TimeZoneUtils.resolveZoneId(zoneId, fallback)

fun JournalEntry.startLocalDateTime(fallback: ZoneId = ZoneId.systemDefault()): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(start_datetime), resolvedZoneId(fallback))

fun JournalEntry.stopLocalDateTime(fallback: ZoneId = ZoneId.systemDefault()): LocalDateTime? =
    stop_datetime?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), resolvedZoneId(fallback)) }

fun JournalEntry.localDate(fallback: ZoneId = ZoneId.systemDefault()): LocalDate =
    startLocalDateTime(fallback).toLocalDate()
