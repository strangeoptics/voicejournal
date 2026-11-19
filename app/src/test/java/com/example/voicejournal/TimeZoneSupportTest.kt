package com.example.voicejournal

import com.example.voicejournal.data.AppDatabase
import com.example.voicejournal.data.JournalEntry
import com.example.voicejournal.data.JournalEntryExport
import com.example.voicejournal.data.JournalExportV3
import com.example.voicejournal.data.localDate
import com.example.voicejournal.data.resolvedZoneId
import com.example.voicejournal.data.startLocalDateTime
import com.example.voicejournal.util.TimeZoneUtils
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

class TimeZoneSupportTest {

    private val sydneyZone = ZoneId.of("Australia/Sydney")
    private val darwinZone = ZoneId.of("Australia/Darwin")
    private val berlinZone = ZoneId.of("Europe/Berlin")

    @Test
    fun australiaMorningEntry_preservesAustralianDateAndWallClockTimeWhenViewedInGermany() {
        // 2026-11-11 08:30 AEDT (UTC+11) == 2026-11-10 21:30 UTC == 2026-11-10 22:30 CET
        val sydneyBreakfastMillis = ZonedDateTime.of(2026, 11, 11, 8, 30, 0, 0, sydneyZone)
            .toInstant()
            .toEpochMilli()

        val entry = JournalEntry(
            content = "Frühstück am Hafen in Sydney",
            start_datetime = sydneyBreakfastMillis,
            zoneId = "Australia/Sydney"
        )

        assertEquals(LocalDate.of(2026, 11, 11), entry.localDate(fallback = berlinZone))
        assertEquals(LocalDateTime.of(2026, 11, 11, 8, 30), entry.startLocalDateTime(fallback = berlinZone))

        val formattedInBerlin = TimeZoneUtils.formatEntryTimeText(
            startMillis = entry.start_datetime,
            stopMillis = null,
            entryZone = entry.resolvedZoneId(berlinZone),
            viewerZone = berlinZone
        )
        assertEquals("08:30 (UTC+11)", formattedInBerlin)

        val formattedInSydney = TimeZoneUtils.formatEntryTimeText(
            startMillis = entry.start_datetime,
            stopMillis = null,
            entryZone = entry.resolvedZoneId(sydneyZone),
            viewerZone = sydneyZone
        )
        assertEquals("08:30", formattedInSydney)
    }

    @Test
    fun halfHourTimeZoneOffset_formatsWithMinutesAndHomeDstDoesNotShowFalseOffsetBadge() {
        val darwinMillis = ZonedDateTime.of(2026, 11, 11, 19, 15, 0, 0, darwinZone)
            .toInstant()
            .toEpochMilli()
        assertEquals(
            "19:15 (UTC+09:30)",
            TimeZoneUtils.formatEntryTimeText(darwinMillis, null, darwinZone, berlinZone)
        )

        // Summer entry in Berlin (UTC+2) viewed during Winter in Berlin (UTC+1) should NOT show a foreign badge
        val berlinSummerMillis = ZonedDateTime.of(2026, 7, 15, 14, 0, 0, 0, berlinZone)
            .toInstant()
            .toEpochMilli()
        assertEquals(
            "14:00",
            TimeZoneUtils.formatEntryTimeText(berlinSummerMillis, null, berlinZone, berlinZone)
        )
    }

    @Test
    fun sanitizeAndResolveZoneId_handlesLegacyNullAndInvalidStringsGracefully() {
        assertNull(TimeZoneUtils.sanitizeZoneId(null))
        assertNull(TimeZoneUtils.sanitizeZoneId("   "))
        assertNull(TimeZoneUtils.sanitizeZoneId("Not/A_Real_Zone"))
        assertEquals("Australia/Sydney", TimeZoneUtils.sanitizeZoneId(" Australia/Sydney "))

        val legacyEntry = JournalEntry(content = "Alt", start_datetime = 1_700_000_000_000L, zoneId = null)
        assertEquals(berlinZone, legacyEntry.resolvedZoneId(fallback = berlinZone))

        val corruptedEntry = JournalEntry(content = "Defekt", start_datetime = 1_700_000_000_000L, zoneId = "Invalid/Zone")
        assertEquals(berlinZone, corruptedEntry.resolvedZoneId(fallback = berlinZone))
    }

    @Test
    fun jsonSerialization_isBackwardsCompatibleWithLegacyExportsAndPreservesZoneId() {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        val id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")

        // Legacy V3 JSON without zoneId field
        val legacyV3Json = """
            {
              "version": 3,
              "entries": [
                {
                  "id": "$id",
                  "content": "Legacy entry",
                  "start_datetime": 1700000000000,
                  "hasImage": false,
                  "checked": false,
                  "categories": ["journal"]
                }
              ],
              "categories": []
            }
        """.trimIndent()
        val decodedLegacy = json.decodeFromString<JournalExportV3>(legacyV3Json)
        assertNull(decodedLegacy.entries.single().zoneId)

        // New V3 JSON with zoneId round-trip
        val export = JournalExportV3(
            entries = listOf(
                JournalEntryExport(
                    id = id,
                    content = "Sydney Opera House",
                    start_datetime = 1_762_810_200_000L,
                    hasImage = true,
                    categories = listOf("urlaub"),
                    zoneId = "Australia/Sydney"
                )
            ),
            categories = emptyList()
        )
        val encoded = json.encodeToString(export)
        val decoded = json.decodeFromString<JournalExportV3>(encoded)
        assertEquals("Australia/Sydney", decoded.entries.single().zoneId)
    }

    @Test
    fun datePickerConversionAndUtcBounds_coverExtremeTimeZonesWithoutDayDrift() {
        val targetDate = LocalDate.of(2026, 11, 11)
        val pickerMillis = TimeZoneUtils.localDateToDatePickerMillis(targetDate)
        assertEquals(targetDate, TimeZoneUtils.datePickerMillisToLocalDate(pickerMillis))

        val (minMillis, maxMillis) = TimeZoneUtils.utcQueryBoundsForLocalDates(targetDate)
        val earliestEarthStart = ZonedDateTime.of(2026, 11, 11, 0, 0, 0, 0, ZoneId.of("Pacific/Kiritimati"))
            .toInstant().toEpochMilli()
        val latestEarthEnd = ZonedDateTime.of(2026, 11, 11, 23, 59, 59, 0, ZoneId.of("Pacific/Pago_Pago"))
            .toInstant().toEpochMilli()

        assertTrue(earliestEarthStart >= minMillis)
        assertTrue(latestEarthEnd < maxMillis)
    }

    @Test
    fun migrationChain1To18_isContiguousAndExecutesAlterTableAddZoneId() {
        val migrations = AppDatabase.ALL_MIGRATIONS
        assertEquals(18, migrations.size)
        migrations.forEachIndexed { index, migration ->
            assertEquals(index + 1, migration.startVersion)
            assertEquals(index + 2, migration.endVersion)
        }

        val executedStatements = mutableListOf<String>()
        val recordingDb = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedStatements += args[0] as String
            }
            null
        } as SupportSQLiteDatabase

        AppDatabase.MIGRATION_17_18.migrate(recordingDb)
        assertEquals(
            listOf("ALTER TABLE `journal_entries` ADD COLUMN `zoneId` TEXT DEFAULT NULL"),
            executedStatements
        )

        val schemaFile = listOf(
            File("schemas/com.example.voicejournal.data.AppDatabase/18.json"),
            File("app/schemas/com.example.voicejournal.data.AppDatabase/18.json")
        ).firstOrNull { it.exists() }
        assertTrue("Expected exported Room schema 18.json to exist", schemaFile != null)
        val schemaText = schemaFile!!.readText()
        assertTrue(schemaText.contains("\"version\": 18"))
        assertTrue(schemaText.contains("\"columnName\": \"zoneId\""))
    }
}
