package com.example.voicejournal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.voicejournal.ui.components.Appointment
import com.example.voicejournal.ui.components.calculateAppointmentColumns
import com.example.voicejournal.ui.components.calculateOverlappingAppointmentLayout
import com.example.voicejournal.ui.components.startOfWeek
import com.example.voicejournal.ui.components.summarizeDayHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

class WeekViewBugFixTest {

    @Test
    fun startOfWeek_withUsLocale_returnsPreviousOrSameSundayInsteadOfEndOfIsoWeek() {
        val wednesday = LocalDate.of(2026, 11, 18)
        assertEquals(LocalDate.of(2026, 11, 15), startOfWeek(wednesday, Locale.US))

        val sunday = LocalDate.of(2026, 11, 15)
        assertEquals(sunday, startOfWeek(sunday, Locale.US))

        val saturday = LocalDate.of(2026, 11, 21)
        assertEquals(sunday, startOfWeek(saturday, Locale.US))
    }

    @Test
    fun startOfWeek_withGermanLocale_returnsPreviousOrSameMonday() {
        val monday = LocalDate.of(2026, 11, 16)
        val thursday = LocalDate.of(2026, 11, 19)
        val sunday = LocalDate.of(2026, 11, 22)

        assertEquals(monday, startOfWeek(monday, Locale.GERMANY))
        assertEquals(monday, startOfWeek(thursday, Locale.GERMANY))
        assertEquals(monday, startOfWeek(sunday, Locale.GERMANY))
    }

    @Test
    fun calculateOverlappingAppointmentLayout_touchingAppointments_doNotOverlap() {
        val date = LocalDate.of(2026, 11, 19)
        val appointments = listOf(
            Appointment(
                id = "1",
                startDateTime = date.atTime(10, 0),
                endDateTime = date.atTime(11, 0),
                title = "First",
            ),
            Appointment(
                id = "2",
                startDateTime = date.atTime(11, 0),
                endDateTime = date.atTime(12, 0),
                title = "Second",
            ),
        )

        val layout = calculateOverlappingAppointmentLayout(appointments, 120.dp, date)
        assertEquals(2, layout.size)
        layout.forEach { info ->
            assertEquals(0, info.column)
            assertEquals(1, info.totalColumns)
            assertEquals(0.dp, info.offsetX)
            assertEquals(120.dp, info.width)
        }
    }

    @Test
    fun calculateOverlappingAppointmentLayout_independentClusters_useClusterLocalWidths() {
        val date = LocalDate.of(2026, 11, 19)
        val appointments = listOf(
            Appointment("m1", date.atTime(9, 0), date.atTime(10, 30), "M1"),
            Appointment("m2", date.atTime(9, 15), date.atTime(10, 15), "M2"),
            Appointment("m3", date.atTime(9, 30), date.atTime(10, 0), "M3"),
            Appointment("e1", date.atTime(20, 0), date.atTime(21, 0), "Evening"),
        )

        val layoutById = calculateOverlappingAppointmentLayout(appointments, 120.dp, date)
            .associateBy { it.appointment.id }

        assertEquals(3, layoutById.getValue("m1").totalColumns)
        assertEquals(40.dp, layoutById.getValue("m1").width)
        assertEquals(40.dp, layoutById.getValue("m2").width)
        assertEquals(40.dp, layoutById.getValue("m3").width)

        val evening = layoutById.getValue("e1")
        assertEquals(0, evening.column)
        assertEquals(1, evening.totalColumns)
        assertEquals(0.dp, evening.offsetX)
        assertEquals(120.dp, evening.width)
    }

    @Test
    fun calculateOverlappingAppointmentLayout_reusesFreedColumnWithinCluster() {
        val date = LocalDate.of(2026, 11, 19)
        val appointments = listOf(
            Appointment("long", date.atTime(9, 0), date.atTime(11, 0), "Long"),
            Appointment("short1", date.atTime(9, 0), date.atTime(10, 0), "Short 1"),
            Appointment("short2", date.atTime(10, 0), date.atTime(11, 0), "Short 2"),
        )

        val layoutById = calculateOverlappingAppointmentLayout(appointments, 100.dp, date)
            .associateBy { it.appointment.id }

        assertEquals(2, layoutById.getValue("long").totalColumns)
        assertEquals(0, layoutById.getValue("long").column)
        assertEquals(1, layoutById.getValue("short1").column)
        assertEquals(1, layoutById.getValue("short2").column)
        assertEquals(50.dp, layoutById.getValue("short2").offsetX)
        assertEquals(50.dp, layoutById.getValue("short2").width)
    }

    @Test
    fun calculateOverlappingAppointmentLayout_zeroDurationAppointment_collidesWithConcurrentAppointment() {
        val date = LocalDate.of(2026, 11, 19)
        val appointments = listOf(
            Appointment("zero", date.atTime(10, 0), date.atTime(10, 0), "Zero"),
            Appointment("regular", date.atTime(10, 0), date.atTime(10, 30), "Regular"),
        )

        val layoutById = calculateOverlappingAppointmentLayout(appointments, 100.dp, date)
            .associateBy { it.appointment.id }

        assertEquals(2, layoutById.getValue("zero").totalColumns)
        assertEquals(2, layoutById.getValue("regular").totalColumns)
    }

    @Test
    fun calculateAppointmentColumns_multiDayAppointment_clampsStartAndEndMinutesPerDay() {
        val entryUuid = UUID.randomUUID()
        val day1 = LocalDate.of(2026, 11, 19)
        val day2 = LocalDate.of(2026, 11, 20)
        val day3 = LocalDate.of(2026, 11, 21)

        val multiDay = Appointment(
            id = entryUuid.toString(),
            startDateTime = day1.atTime(18, 30),
            endDateTime = day3.atTime(9, 15),
            title = "Retreat",
            entryId = entryUuid,
        )

        val p1 = calculateAppointmentColumns(listOf(multiDay), day1).single()
        assertEquals(18 * 60.0 + 30.0, p1.startMinutes, 0.001)
        assertEquals(24 * 60.0, p1.endMinutes, 0.001)
        assertEquals(entryUuid, p1.appointment.entryId)

        val p2 = calculateAppointmentColumns(listOf(multiDay), day2).single()
        assertEquals(0.0, p2.startMinutes, 0.001)
        assertEquals(24 * 60.0, p2.endMinutes, 0.001)

        val p3 = calculateAppointmentColumns(listOf(multiDay), day3).single()
        assertEquals(0.0, p3.startMinutes, 0.001)
        assertEquals(9 * 60.0 + 15.0, p3.endMinutes, 0.001)
    }

    @Test
    fun summarizeDayHeader_prioritizesDistinctCategoryColorsAndMarksOverflowAboveThree() {
        val date = LocalDate.of(2026, 11, 19)

        val emptySummary = summarizeDayHeader(emptyList())
        assertEquals(0, emptySummary.entryCount)
        assertTrue(emptySummary.dotColors.isEmpty())
        assertFalse(emptySummary.hasOverflow)

        val twoSameColor = calculateAppointmentColumns(
            listOf(
                Appointment("1", date.atTime(8, 0), date.atTime(9, 0), "A", color = Color.Red),
                Appointment("2", date.atTime(10, 0), date.atTime(11, 0), "B", color = Color.Red),
            ),
            date,
        )
        val twoSummary = summarizeDayHeader(twoSameColor)
        assertEquals(2, twoSummary.entryCount)
        assertEquals(listOf(Color.Red, Color.Red), twoSummary.dotColors)
        assertFalse(twoSummary.hasOverflow)

        val fiveMixed = calculateAppointmentColumns(
            listOf(
                Appointment("1", date.atTime(8, 0), date.atTime(8, 30), "A", color = Color.Red),
                Appointment("2", date.atTime(9, 0), date.atTime(9, 30), "B", color = Color.Red),
                Appointment("3", date.atTime(10, 0), date.atTime(10, 30), "C", color = Color.Red),
                Appointment("4", date.atTime(14, 0), date.atTime(14, 30), "D", color = Color.Blue),
                Appointment("5", date.atTime(21, 0), date.atTime(21, 30), "E", color = Color.Green),
            ),
            date,
        )
        val fiveSummary = summarizeDayHeader(fiveMixed)
        assertEquals(5, fiveSummary.entryCount)
        assertEquals(listOf(Color.Red, Color.Blue, Color.Green), fiveSummary.dotColors)
        assertTrue(fiveSummary.hasOverflow)
    }
}
