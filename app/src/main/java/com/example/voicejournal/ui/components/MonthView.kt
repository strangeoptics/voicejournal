package com.example.voicejournal.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voicejournal.ui.theme.VoicejournalTheme
import com.example.voicejournal.util.DateFormatters
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs

private const val MAX_ANIMATED_MONTH_JUMP = 3
private val CHIP_SHAPE = RoundedCornerShape(4.dp)
private val DARK_CHIP_TEXT = Color(0xFF1C1B1F)
private val LIGHT_CHIP_TEXT = Color(0xFFF5EFF7)
private val CELL_VERTICAL_PADDING = 3.dp
private val DAY_BADGE_BOTTOM_SPACING = 2.dp
private val CHIP_VERTICAL_GAP = 2.dp

fun weekdayHeaderOrder(locale: Locale = Locale.getDefault()): List<DayOfWeek> {
    val effectiveLocale = if (locale.country.isEmpty() && locale.language.equals("de", ignoreCase = true)) {
        Locale.GERMANY
    } else {
        locale
    }
    val firstDayOfWeek = WeekFields.of(effectiveLocale).firstDayOfWeek
    return (0L until 7L).map { offset -> firstDayOfWeek + offset }
}

fun monthGridWeeks(
    month: YearMonth,
    locale: Locale = Locale.getDefault(),
): List<List<LocalDate>> {
    val gridStart = startOfWeek(month.atDay(1), locale)
    val lastWeekStart = startOfWeek(month.atEndOfMonth(), locale)
    val weekCount = (ChronoUnit.DAYS.between(gridStart, lastWeekStart).toInt() / 7) + 1
    return (0 until weekCount).map { weekIndex ->
        (0 until 7).map { dayIndex ->
            gridStart.plusDays(((weekIndex * 7) + dayIndex).toLong())
        }
    }
}

fun monthForPage(anchorMonth: YearMonth, pageOffset: Int): YearMonth =
    anchorMonth.plusMonths(pageOffset.toLong())

fun pageIndexForMonth(anchorMonth: YearMonth, targetMonth: YearMonth): Int? {
    val diffMonths = ChronoUnit.MONTHS.between(anchorMonth, targetMonth)
    val targetPage = PAGER_CENTER_PAGE.toLong() + diffMonths
    return if (targetPage in 0L until PAGER_PAGE_COUNT.toLong()) targetPage.toInt() else null
}

fun computeVisibleChipBudget(
    availableHeightPx: Int,
    chipSlotPx: Int,
    overflowLabelHeightPx: Int,
    totalEntries: Int,
): Int {
    if (totalEntries <= 0 || availableHeightPx <= 0 || chipSlotPx <= 0) return 0
    val maxWithoutOverflow = availableHeightPx / chipSlotPx
    if (totalEntries <= maxWithoutOverflow) {
        return totalEntries
    }
    val remainingForChips = (availableHeightPx - overflowLabelHeightPx).coerceAtLeast(0)
    return (remainingForChips / chipSlotPx).coerceAtMost(totalEntries)
}

@Composable
fun MonthView(
    currentMonth: YearMonth,
    modifier: Modifier = Modifier,
    appointments: List<Appointment> = emptyList(),
    today: LocalDate = LocalDate.now(),
    onDayClick: (LocalDate) -> Unit = {},
    onDayLongClick: (DayEntriesPreviewState) -> Unit = {},
    onMonthChanged: (YearMonth) -> Unit = {},
) {
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0] ?: Locale.getDefault()
    val weekdays = remember(locale) { weekdayHeaderOrder(locale) }

    var anchorMonth by remember { mutableStateOf(currentMonth) }
    var pagerGeneration by remember { mutableIntStateOf(0) }

    if (pageIndexForMonth(anchorMonth, currentMonth) == null) {
        anchorMonth = currentMonth
        pagerGeneration++
    }

    val pagerState = key(pagerGeneration) {
        rememberPagerState(
            initialPage = pageIndexForMonth(anchorMonth, currentMonth) ?: PAGER_CENTER_PAGE,
            pageCount = { PAGER_PAGE_COUNT },
        )
    }

    var isProgrammaticScroll by remember(pagerGeneration) { mutableStateOf(value = false) }
    val currentRequestedMonth by rememberUpdatedState(currentMonth)
    val currentOnMonthChanged by rememberUpdatedState(onMonthChanged)

    LaunchedEffect(pagerState, anchorMonth) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settledPage ->
                if (isProgrammaticScroll) {
                    return@collect
                }
                val settledMonth = monthForPage(anchorMonth, settledPage - PAGER_CENTER_PAGE)
                if (settledMonth != currentRequestedMonth) {
                    currentOnMonthChanged(settledMonth)
                }
            }
    }

    LaunchedEffect(currentMonth, pagerGeneration) {
        val targetPage = pageIndexForMonth(anchorMonth, currentMonth) ?: return@LaunchedEffect
        if (targetPage != pagerState.settledPage) {
            isProgrammaticScroll = true
            try {
                if (abs(targetPage - pagerState.currentPage) <= MAX_ANIMATED_MONTH_JUMP) {
                    pagerState.animateScrollToPage(targetPage)
                } else {
                    pagerState.scrollToPage(targetPage)
                }
            } finally {
                isProgrammaticScroll = false
            }
        }
    }

    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.surface),
    ) {
        MonthWeekdayHeaderRow(
            weekdays = weekdays,
            locale = locale,
        )
        HorizontalDivider(color = colorScheme.outlineVariant)

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            val pageMonth = remember(anchorMonth, page) {
                monthForPage(anchorMonth, page - PAGER_CENTER_PAGE)
            }
            val weeks = remember(pageMonth, locale) {
                monthGridWeeks(pageMonth, locale)
            }
            val appointmentsByDate = remember(appointments, weeks) {
                val allGridDates = weeks.flatten()
                val gridStart = allGridDates.first()
                val gridEnd = allGridDates.last()
                val relevant = appointments
                    .asSequence()
                    .filter { !it.startDate.isAfter(gridEnd) && !it.endDate.isBefore(gridStart) }
                    .sortedWith(
                        compareBy<Appointment> { it.startDateTime }
                            .thenBy { it.endDateTime }
                            .thenBy { it.id },
                    )
                    .toList()
                allGridDates.associateWith { date ->
                    relevant.filter { !it.startDate.isAfter(date) && !it.endDate.isBefore(date) }
                }
            }

            MonthPageGrid(
                month = pageMonth,
                weeks = weeks,
                appointmentsByDate = appointmentsByDate,
                today = today,
                locale = locale,
                onDayClick = onDayClick,
                onDayLongClick = onDayLongClick,
            )
        }
    }
}

@Composable
private fun MonthWeekdayHeaderRow(
    weekdays: List<DayOfWeek>,
    locale: Locale,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        weekdays.forEach { dayOfWeek ->
            Text(
                text = dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MonthPageGrid(
    month: YearMonth,
    weeks: List<List<LocalDate>>,
    appointmentsByDate: Map<LocalDate, List<Appointment>>,
    today: LocalDate,
    locale: Locale,
    onDayClick: (LocalDate) -> Unit,
    onDayLongClick: (DayEntriesPreviewState) -> Unit,
) {
    val density = LocalDensity.current
    val idealBadgeSize = with(density) { maxOf(24.dp, 13.sp.toDp() + 8.dp) }
    val chipHeight = with(density) { maxOf(18.dp, 11.sp.toDp() + 6.dp) }
    val overflowHeight = with(density) { maxOf(14.dp, 10.sp.toDp() + 4.dp) }
    val chipSlotPx = with(density) { (chipHeight + CHIP_VERTICAL_GAP).roundToPx().coerceAtLeast(1) }
    val overflowHeightPx = with(density) { overflowHeight.roundToPx().coerceAtLeast(1) }

    val colorScheme = MaterialTheme.colorScheme
    val gridLineColor = colorScheme.outlineVariant.copy(alpha = 0.55f)

    SubcomposeLayout(modifier = Modifier.fillMaxSize()) { constraints ->
        val weekCount = weeks.size.coerceAtLeast(1)
        val rowHeightPx = constraints.maxHeight / weekCount
        val rowHeightDp = rowHeightPx.toDp()
        val verticalPadDp = if (rowHeightDp < 36.dp) 1.dp else CELL_VERTICAL_PADDING
        val effectiveBadgeSize = minOf(idealBadgeSize, (rowHeightDp - verticalPadDp * 2).coerceAtLeast(16.dp))
        val dayHeaderReservedPx =
            (effectiveBadgeSize + verticalPadDp * 2 + DAY_BADGE_BOTTOM_SPACING).roundToPx()
        val availableChipAreaPx = (rowHeightPx - dayHeaderReservedPx).coerceAtLeast(0)

        val placeables = subcompose("month_rows") {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        val strokePx = 1.dp.toPx()
                        val colWidth = size.width / 7f
                        for (col in 1 until 7) {
                            val x = col * colWidth
                            drawLine(
                                color = gridLineColor,
                                start = Offset(x, 0f),
                                end = Offset(x, size.height),
                                strokeWidth = strokePx,
                            )
                        }
                        val rowH = size.height / weekCount
                        for (row in 1 until weekCount) {
                            val y = row * rowH
                            drawLine(
                                color = gridLineColor,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = strokePx,
                            )
                        }
                    },
            ) {
                weeks.forEachIndexed { weekIndex, weekDates ->
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        weekDates.forEachIndexed { dayIndex, date ->
                            val dayAppointments = appointmentsByDate[date].orEmpty()
                            val pivotX = (dayIndex + 0.5f) / 7f
                            val pivotY = (((weekIndex + 0.5f) / weekCount.toFloat()) * 0.85f) + 0.12f
                            MonthDayCell(
                                date = date,
                                isCurrentMonth = (date.year == month.year) && (date.month == month.month),
                                isToday = date == today,
                                dailyAppointments = dayAppointments,
                                availableChipAreaPx = availableChipAreaPx,
                                chipSlotPx = chipSlotPx,
                                overflowHeightPx = overflowHeightPx,
                                badgeSize = effectiveBadgeSize,
                                verticalPadding = verticalPadDp,
                                chipHeight = chipHeight,
                                overflowHeight = overflowHeight,
                                locale = locale,
                                onClick = { onDayClick(date) },
                                onLongClick = if (dayAppointments.isNotEmpty()) {
                                    {
                                        onDayLongClick(
                                            DayEntriesPreviewState(
                                                date = date,
                                                pivotFractionX = pivotX,
                                                pivotFractionY = pivotY,
                                            ),
                                        )
                                    }
                                } else {
                                    null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }.map { it.measure(constraints) }

        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEach { it.placeRelative(0, 0) }
        }
    }
}

@Composable
private fun MonthDayCell(
    date: LocalDate,
    isCurrentMonth: Boolean,
    isToday: Boolean,
    dailyAppointments: List<Appointment>,
    availableChipAreaPx: Int,
    chipSlotPx: Int,
    overflowHeightPx: Int,
    badgeSize: Dp,
    verticalPadding: Dp,
    chipHeight: Dp,
    overflowHeight: Dp,
    locale: Locale,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val cellBackground = when {
        isToday -> colorScheme.primaryContainer.copy(alpha = 0.16f)
        !isCurrentMonth -> colorScheme.surfaceVariant.copy(alpha = 0.25f)
        else -> Color.Transparent
    }
    val badgeContainerColor = if (isToday) colorScheme.primary else Color.Transparent
    val badgeContentColor = when {
        isToday -> colorScheme.onPrimary
        isCurrentMonth -> colorScheme.onSurface
        else -> colorScheme.onSurfaceVariant
    }

    val visibleChipCount = computeVisibleChipBudget(
        availableHeightPx = availableChipAreaPx,
        chipSlotPx = chipSlotPx,
        overflowLabelHeightPx = overflowHeightPx,
        totalEntries = dailyAppointments.size,
    )
    val overflowCount = dailyAppointments.size - visibleChipCount

    val accessibilityDescription = remember(date, dailyAppointments, locale) {
        val dateLabel = date.format(DateFormatters.calendarFullDate(locale))
        if (dailyAppointments.isEmpty()) {
            dateLabel
        } else {
            val previewTitles = dailyAppointments
                .take(3)
                .joinToString(", ") { it.title.ifBlank { it.startTime.format(DateFormatters.TIME_HM) } }
            "$dateLabel: $previewTitles"
        }
    }

    Column(
        modifier = modifier
            .background(cellBackground)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick?.let { callback ->
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        callback()
                    }
                },
            )
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = accessibilityDescription
            }
            .padding(horizontal = 2.dp, vertical = verticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(badgeSize)
                .clip(CircleShape)
                .background(badgeContainerColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = if (isToday) {
                    FontWeight.Bold
                } else if (isCurrentMonth) {
                    FontWeight.SemiBold
                } else {
                    FontWeight.Normal
                },
                color = badgeContentColor,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }

        if (dailyAppointments.isNotEmpty() && ((visibleChipCount > 0) || (availableChipAreaPx >= overflowHeightPx))) {
            Spacer(modifier = Modifier.height(DAY_BADGE_BOTTOM_SPACING))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(CHIP_VERTICAL_GAP),
            ) {
                for (i in 0 until visibleChipCount) {
                    MonthAppointmentChip(
                        appointment = dailyAppointments[i],
                        isCurrentMonth = isCurrentMonth,
                        surfaceColor = colorScheme.surface,
                        outlineColor = colorScheme.outline,
                        chipHeight = chipHeight,
                    )
                }
                if ((overflowCount > 0) && (availableChipAreaPx >= overflowHeightPx)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(overflowHeight)
                            .padding(horizontal = 2.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = "+$overflowCount",
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthAppointmentChip(
    appointment: Appointment,
    isCurrentMonth: Boolean,
    surfaceColor: Color,
    outlineColor: Color,
    chipHeight: Dp,
) {
    val isLightSurface = surfaceColor.luminance() > 0.5f
    val tintAlpha = when {
        isLightSurface && isCurrentMonth -> 0.22f
        isLightSurface -> 0.14f
        isCurrentMonth -> 0.28f
        else -> 0.18f
    }
    val containerColor = appointment.color.copy(alpha = tintAlpha).compositeOver(surfaceColor)
    val contentColor = if (containerColor.luminance() > 0.4f) DARK_CHIP_TEXT else LIGHT_CHIP_TEXT
    val lowContrastAccent = abs(appointment.color.luminance() - surfaceColor.luminance()) < 0.12f
    val accentColor = if (lowContrastAccent) outlineColor else appointment.color
    val label = appointment.title.ifBlank { appointment.startTime.format(DateFormatters.TIME_HM) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(chipHeight)
            .clip(CHIP_SHAPE)
            .background(containerColor)
            .border(
                width = 0.5.dp,
                color = accentColor.copy(alpha = if (lowContrastAccent) 0.7f else 0.45f),
                shape = CHIP_SHAPE,
            )
            .drawBehind {
                val stripeWidth = 3.dp.toPx()
                drawRect(
                    color = accentColor.copy(alpha = if (isCurrentMonth) 1f else 0.65f),
                    topLeft = Offset.Zero,
                    size = Size(stripeWidth, size.height),
                )
            }
            .padding(start = 6.dp, end = 2.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SampleMonthViewPreviewContent() {
    val targetMonth = YearMonth.of(2026, 11)
    val today = LocalDate.of(2026, 11, 19)

    val sampleAppointments = listOf(
        Appointment(
            id = "1",
            startDateTime = LocalDate.of(2026, 11, 2).atTime(9, 0),
            endDateTime = LocalDate.of(2026, 11, 2).atTime(10, 0),
            title = "Wochenplanung",
            color = Color(0xFF64B5F6),
        ),
        Appointment(
            id = "2",
            startDateTime = LocalDate.of(2026, 11, 7).atTime(11, 0),
            endDateTime = LocalDate.of(2026, 11, 8).atTime(17, 0),
            title = "Wochenend-Ausflug",
            color = Color(0xFF81C784),
        ),
        Appointment(
            id = "3",
            startDateTime = today.atTime(8, 30),
            endDateTime = today.atTime(9, 30),
            title = "Morgenreflexion",
            color = Color(0xFFE57373),
        ),
        Appointment(
            id = "4",
            startDateTime = today.atTime(12, 0),
            endDateTime = today.atTime(13, 0),
            title = "Mittagspause Notiz",
            color = Color(0xFFFFF176),
        ),
        Appointment(
            id = "5",
            startDateTime = today.atTime(15, 0),
            endDateTime = today.atTime(16, 0),
            title = "Projekt-Ideen",
            color = Color(0xFFFFFFFF),
        ),
        Appointment(
            id = "6",
            startDateTime = today.atTime(18, 30),
            endDateTime = today.atTime(19, 30),
            title = "Sport & Training",
            color = Color(0xFF4DB6AC),
        ),
        Appointment(
            id = "7",
            startDateTime = today.atTime(20, 0),
            endDateTime = today.atTime(20, 30),
            title = "Abendessen",
            color = Color(0xFFFFB74D),
        ),
        Appointment(
            id = "8",
            startDateTime = today.atTime(21, 0),
            endDateTime = today.atTime(21, 30),
            title = "Tagesrückblick",
            color = Color(0xFFBA68C8),
        ),
        Appointment(
            id = "9",
            startDateTime = LocalDate.of(2026, 10, 30).atTime(16, 0),
            endDateTime = LocalDate.of(2026, 10, 30).atTime(17, 0),
            title = "Oktober-Rückblick",
            color = Color(0xFFFFB74D),
        ),
    )

    Surface {
        MonthView(
            currentMonth = targetMonth,
            appointments = sampleAppointments,
            today = today,
        )
    }
}

@Preview(name = "MonthView (Light)", locale = "de-rDE", widthDp = 412, heightDp = 760)
@Composable
fun MonthViewLightPreview() {
    VoicejournalTheme(darkTheme = false, dynamicColor = false) {
        SampleMonthViewPreviewContent()
    }
}

@Preview(
    name = "MonthView (Dark)",
    locale = "de-rDE",
    widthDp = 412,
    heightDp = 760,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun MonthViewDarkPreview() {
    VoicejournalTheme(darkTheme = true, dynamicColor = false) {
        SampleMonthViewPreviewContent()
    }
}
