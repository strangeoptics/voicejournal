package com.example.voicejournal.util

import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Thread-safe cached [DateTimeFormatter] instances reused across UI recompositions,
 * ViewModels, and backup/export utilities.
 */
object DateFormatters {
    val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
    val TIME_HM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    val ENTRY_DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.ROOT)
    val DAY_HEADER_GERMAN: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd, EE", Locale.GERMAN)
    val CALENDAR_FULL_DATE_GERMAN: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN)
    val CALENDAR_MONTH_YEAR_GERMAN: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)
    val EXPORT_FILE_TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm", Locale.ROOT)
    val ISO_LOCAL_DATE_TIME_SECONDS: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT)
    val LEGACY_TEXT_EXPORT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

    fun googlePhotosQuery(locale: Locale = Locale.getDefault()): DateTimeFormatter =
        DateTimeFormatter.ofPattern("d. MMMM yyyy", locale)
}
