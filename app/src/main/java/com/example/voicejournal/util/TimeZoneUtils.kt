package com.example.voicejournal.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeZoneUtils {

    /**
     * Maximum possible offset from UTC across all IANA timezones (UTC-12:00 to UTC+14:00),
     * padded to 15 hours to safely bound SQLite UTC millisecond range queries for a [LocalDate].
     */
    const val MAX_UTC_OFFSET_HOURS = 15L

    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun resolveZoneId(zoneId: String?, fallback: ZoneId = ZoneId.systemDefault()): ZoneId {
        if (zoneId.isNullOrBlank()) return fallback
        return runCatching { ZoneId.of(zoneId.trim()) }.getOrDefault(fallback)
    }

    fun sanitizeZoneId(zoneId: String?): String? {
        if (zoneId.isNullOrBlank()) return null
        return runCatching { ZoneId.of(zoneId.trim()).id }.getOrNull()
    }

    fun formatUtcOffset(offset: ZoneOffset): String {
        val totalSeconds = offset.totalSeconds
        if (totalSeconds == 0) return "UTC"
        return if (totalSeconds % 3600 == 0) {
            String.format(Locale.ROOT, "UTC%+d", totalSeconds / 3600)
        } else {
            "UTC${offset.id}"
        }
    }

    fun formatZoneLabel(zoneId: ZoneId, referenceMillis: Long = System.currentTimeMillis()): String {
        val offset = zoneId.rules.getOffset(Instant.ofEpochMilli(referenceMillis))
        return "${zoneId.id} (${formatUtcOffset(offset)})"
    }

    /**
     * Formats start (and optional stop) timestamps in [entryZone].
     * Appends the entry's UTC offset (e.g. ` (UTC+11)`) only when its offset at [startMillis]
     * differs from [viewerZone]'s offset at the same instant, keeping local entries uncluttered.
     */
    fun formatEntryTimeText(
        startMillis: Long,
        stopMillis: Long?,
        entryZone: ZoneId,
        viewerZone: ZoneId = ZoneId.systemDefault()
    ): String {
        val startInstant = Instant.ofEpochMilli(startMillis)
        val startLocal = LocalDateTime.ofInstant(startInstant, entryZone)
        val baseText = if (stopMillis != null) {
            val stopLocal = LocalDateTime.ofInstant(Instant.ofEpochMilli(stopMillis), entryZone)
            "${startLocal.format(timeFormatter)} - ${stopLocal.format(timeFormatter)}"
        } else {
            startLocal.format(timeFormatter)
        }

        val entryOffset = entryZone.rules.getOffset(startInstant)
        val viewerOffset = viewerZone.rules.getOffset(startInstant)
        return if (entryOffset != viewerOffset) {
            "$baseText (${formatUtcOffset(entryOffset)})"
        } else {
            baseText
        }
    }

    /**
     * Computes conservative UTC epoch millisecond bounds `[startMillis, endMillis)` guaranteed
     * to include every instant whose local date in any Earth timezone falls within `[startDate, endDateInclusive]`.
     */
    fun utcQueryBoundsForLocalDates(
        startDate: LocalDate,
        endDateInclusive: LocalDate = startDate
    ): Pair<Long, Long> {
        val minInstant = startDate.atStartOfDay(ZoneOffset.UTC)
            .minusHours(MAX_UTC_OFFSET_HOURS)
            .toInstant()
            .toEpochMilli()
        val maxInstant = endDateInclusive.plusDays(1).atStartOfDay(ZoneOffset.UTC)
            .plusHours(MAX_UTC_OFFSET_HOURS)
            .toInstant()
            .toEpochMilli()
        return minInstant to maxInstant
    }

    /**
     * Converts a Material 3 DatePicker UTC-midnight timestamp to the picked calendar [LocalDate]
     * without shifting across local timezones.
     */
    fun datePickerMillisToLocalDate(utcMidnightMillis: Long): LocalDate {
        return Instant.ofEpochMilli(utcMidnightMillis).atZone(ZoneOffset.UTC).toLocalDate()
    }

    /**
     * Converts a [LocalDate] to UTC-midnight epoch milliseconds expected by Material 3 DatePicker.
     */
    fun localDateToDatePickerMillis(date: LocalDate): Long {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    fun availableZoneIdsSorted(): List<String> {
        return ZoneId.getAvailableZoneIds()
            .filter { it == "UTC" || (it.contains('/') && !it.startsWith("Etc/") && !it.startsWith("SystemV/")) }
            .sorted()
    }
}
