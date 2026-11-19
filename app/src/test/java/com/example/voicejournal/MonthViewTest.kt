package com.example.voicejournal

import com.example.voicejournal.ui.components.PAGER_CENTER_PAGE
import com.example.voicejournal.ui.components.computeVisibleChipBudget
import com.example.voicejournal.ui.components.monthForPage
import com.example.voicejournal.ui.components.monthGridWeeks
import com.example.voicejournal.ui.components.pageIndexForMonth
import com.example.voicejournal.ui.components.weekdayHeaderOrder
import com.example.voicejournal.util.DateFormatters
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

class MonthViewTest {

    @Test
    fun weekdayHeaderOrder_respectsLocaleFirstDayOfWeek() {
        val deDays = weekdayHeaderOrder(Locale.GERMANY)
        assertEquals(7, deDays.size)
        assertEquals(DayOfWeek.MONDAY, deDays.first())
        assertEquals(DayOfWeek.SUNDAY, deDays.last())

        val deCountrylessDays = weekdayHeaderOrder(Locale.forLanguageTag("de"))
        assertEquals(DayOfWeek.MONDAY, deCountrylessDays.first())

        val usDays = weekdayHeaderOrder(Locale.US)
        assertEquals(7, usDays.size)
        assertEquals(DayOfWeek.SUNDAY, usDays.first())
        assertEquals(DayOfWeek.SATURDAY, usDays.last())
    }

    @Test
    fun monthGridWeeks_handlesFourFiveAndSixWeekMonthsAccurately() {
        // February 2021: Feb 1 is Monday, Feb 28 is Sunday -> exact 4 weeks in German locale
        val feb2021De = monthGridWeeks(YearMonth.of(2021, 2), Locale.GERMANY)
        assertEquals(4, feb2021De.size)
        assertEquals(LocalDate.of(2021, 2, 1), feb2021De.first().first())
        assertEquals(LocalDate.of(2021, 2, 28), feb2021De.last().last())

        // November 2026: Nov 1 is Sunday, Nov 30 is Monday
        // German locale (Mon-Sun): Oct 26 .. Dec 6 -> 6 weeks
        val nov2026De = monthGridWeeks(YearMonth.of(2026, 11), Locale.GERMANY)
        assertEquals(6, nov2026De.size)
        assertEquals(LocalDate.of(2026, 10, 26), nov2026De.first().first())
        assertEquals(LocalDate.of(2026, 12, 6), nov2026De.last().last())

        // US locale (Sun-Sat): Nov 1 .. Dec 5 -> 5 weeks
        val nov2026Us = monthGridWeeks(YearMonth.of(2026, 11), Locale.US)
        assertEquals(5, nov2026Us.size)
        assertEquals(LocalDate.of(2026, 11, 1), nov2026Us.first().first())
        assertEquals(LocalDate.of(2026, 12, 5), nov2026Us.last().last())
    }

    @Test
    fun monthPagerMapping_resolvesPositiveAndNegativeMonthOffsets() {
        val anchor = YearMonth.of(2026, 11)
        assertEquals(anchor, monthForPage(anchor, 0))
        assertEquals(YearMonth.of(2027, 2), monthForPage(anchor, 3))
        assertEquals(YearMonth.of(2025, 11), monthForPage(anchor, -12))

        assertEquals(PAGER_CENTER_PAGE, pageIndexForMonth(anchor, anchor))
        assertEquals(PAGER_CENTER_PAGE + 3, pageIndexForMonth(anchor, YearMonth.of(2027, 2)))
        assertEquals(PAGER_CENTER_PAGE - 12, pageIndexForMonth(anchor, YearMonth.of(2025, 11)))
    }

    @Test
    fun computeVisibleChipBudget_reservesOverflowRowOnlyWhenEntriesExceedCapacity() {
        val chipSlotPx = 20
        val overflowHeightPx = 14

        // 3 entries fit in 60px without needing an overflow row (3 * 20 <= 60)
        assertEquals(
            3,
            computeVisibleChipBudget(
                availableHeightPx = 60,
                chipSlotPx = chipSlotPx,
                overflowLabelHeightPx = overflowHeightPx,
                totalEntries = 3,
            ),
        )

        // 4 entries do not fit in 60px -> reserves 14px for "+2", leaving 46px -> 2 chips fit
        assertEquals(
            2,
            computeVisibleChipBudget(
                availableHeightPx = 60,
                chipSlotPx = chipSlotPx,
                overflowLabelHeightPx = overflowHeightPx,
                totalEntries = 4,
            ),
        )

        // Very compact cell (18px) with 2 entries -> 0 chips fit, "+2" overflow shown
        assertEquals(
            0,
            computeVisibleChipBudget(
                availableHeightPx = 18,
                chipSlotPx = chipSlotPx,
                overflowLabelHeightPx = overflowHeightPx,
                totalEntries = 2,
            ),
        )
    }

    @Test
    fun formatCalendarMonthTitle_formatsMonthAndYearPerLocale() {
        val nov2026 = YearMonth.of(2026, 11)
        assertEquals("November 2026", DateFormatters.formatCalendarMonthTitle(nov2026, Locale.GERMANY))
        assertEquals("November 2026", DateFormatters.formatCalendarMonthTitle(nov2026, Locale.US))

        val mar2026 = YearMonth.of(2026, 3)
        assertEquals("März 2026", DateFormatters.formatCalendarMonthTitle(mar2026, Locale.GERMANY))
        assertEquals("March 2026", DateFormatters.formatCalendarMonthTitle(mar2026, Locale.US))
    }

    @Test
    fun computeDayOpenScrollTargetPx_centersNoonWhenEmptyAndScrollsToEntriesWhenPresent() {
        val date = LocalDate.of(2026, 11, 19)
        val hourSlotPx = 120
        val viewportHeightPx = hourSlotPx * 10 // 10 hours visible
        val maxScrollPx = (hourSlotPx * 24) - viewportHeightPx // 14 * 120 = 1680

        // Empty day -> centers 12:00 in viewport (12 * 120 - 5 * 120 = 7 * 120 = 840 = maxScrollPx / 2)
        assertEquals(
            hourSlotPx * 7,
            com.example.voicejournal.ui.components.computeDayOpenScrollTargetPx(
                placements = emptyList(),
                viewportHeightPx = viewportHeightPx,
                hourSlotPx = hourSlotPx,
                maxScrollPx = maxScrollPx,
            ),
        )

        // Day with entry at 15:30 -> scrolls to 15:00 (15 * 120 = 1800 -> clamped to maxScrollPx = 1680)
        val afternoonPlacements = com.example.voicejournal.ui.components.calculateAppointmentColumns(
            listOf(
                com.example.voicejournal.ui.components.Appointment(
                    id = "1",
                    startDateTime = date.atTime(15, 30),
                    endDateTime = date.atTime(16, 30),
                    title = "Nachmittag",
                ),
            ),
            date,
        )
        assertEquals(
            maxScrollPx,
            com.example.voicejournal.ui.components.computeDayOpenScrollTargetPx(
                placements = afternoonPlacements,
                viewportHeightPx = viewportHeightPx,
                hourSlotPx = hourSlotPx,
                maxScrollPx = maxScrollPx,
            ),
        )

        // Day with entry at 09:00 and 18:00 -> scrolls to earliest entry with 30 min context (08:30 = 1020px)
        val multiEntryPlacements = com.example.voicejournal.ui.components.calculateAppointmentColumns(
            listOf(
                com.example.voicejournal.ui.components.Appointment(
                    id = "1",
                    startDateTime = date.atTime(9, 0),
                    endDateTime = date.atTime(10, 0),
                    title = "Morgens",
                ),
                com.example.voicejournal.ui.components.Appointment(
                    id = "2",
                    startDateTime = date.atTime(18, 0),
                    endDateTime = date.atTime(19, 0),
                    title = "Abends",
                ),
            ),
            date,
        )
        assertEquals(
            (8.5 * hourSlotPx).toInt(),
            com.example.voicejournal.ui.components.computeDayOpenScrollTargetPx(
                placements = multiEntryPlacements,
                viewportHeightPx = viewportHeightPx,
                hourSlotPx = hourSlotPx,
                maxScrollPx = maxScrollPx,
            ),
        )
    }
}
