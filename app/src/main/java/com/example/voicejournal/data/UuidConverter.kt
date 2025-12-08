package com.example.voicejournal.data

import androidx.room.TypeConverter
import java.util.UUID

class UuidConverter {
    @TypeConverter
    fun fromUuid(uuid: UUID?): String? {
        return uuid?.toString()
    }

    @TypeConverter
    fun toUuid(uuid: String?): UUID? {
        return uuid?.let { UUID.fromString(it) }
    }
}