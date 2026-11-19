package com.example.voicejournal.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voicejournal.ui.theme.VoicejournalTheme
import com.example.voicejournal.util.DateFormatters
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

private const val MINUTES_PER_DAY = 24 * 60.0
const val MAX_HEADER_DOTS = 3
const val PAGER_PAGE_COUNT = 20_000
const val PAGER_CENTER_PAGE = PAGER_PAGE_COUNT / 2
private const val MAX_ANIMATED_PAGE_JUMP = 4

private val HOURS_OF_DAY: List<LocalTime> = (0..23).map { LocalTime.of(it, 0) }
private val HOUR_COLUMN_WIDTH: Dp = 52.dp
private val APPOINTMENT_SHAPE = RoundedCornerShape(4.dp)
private val DARK_TEXT_ON_LIGHT_TILE = Color(0xFF1C1B1F)
val MIN_APPOINTMENT_HEIGHT: Dp = 20.dp

fun startOfWeek(date: LocalDate, locale: Locale = Locale.getDefault()): LocalDate {
    val effectiveLocale = if (locale.country.isEmpty() && locale.language.equals("de", ignoreCase = true)) {
        Locale.GERMANY
    } else {
        locale
    }
    val firstDayOfWeek = WeekFields.of(effectiveLocale).firstDayOfWeek
    return date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
}

fun visibleDatesForPage(
    anchorStartDate: LocalDate,
    stepDays: Int,
    pageOffset: Int,
): List<LocalDate> {
    val safeStep = stepDays.coerceAtLeast(1)
    val pageStart = anchorStartDate.plusDays(pageOffset.toLong() * safeStep.toLong())
    return (0 until safeStep).map { pageStart.plusDays(it.toLong()) }
}

fun pageIndexForStartDate(
    anchorStartDate: LocalDate,
    stepDays: Int,
    targetStartDate: LocalDate,
): Int? {
    val safeStep = stepDays.coerceAtLeast(1).toLong()
    val diffDays = ChronoUnit.DAYS.between(anchorStartDate, targetStartDate)
    if (diffDays % safeStep != 0L) return null
    val targetPage = PAGER_CENTER_PAGE.toLong() + (diffDays / safeStep)
    return if (targetPage in 0L until PAGER_PAGE_COUNT.toLong()) targetPage.toInt() else null
}

/**
 * Computes a vertical scroll target (in pixels) to bring the earliest entry on a clicked day
 * into view if and only if none of that day's entries are currently visible in the viewport.
 */
fun computeAutoScrollTargetPx(
    placements: List<AppointmentColumnPlacement>,
    scrollTopPx: Int,
    viewportHeightPx: Int,
    hourSlotPx: Int,
    minHeightPx: Int,
    maxScrollPx: Int,
): Int? {
    if (placements.isEmpty() || viewportHeightPx <= 0 || maxScrollPx <= 0) return null

    val scrollBottomPx = scrollTopPx + viewportHeightPx
    val anyEntryVisible = placements.any { placement ->
        val topPx = ((placement.startMinutes / 60.0) * hourSlotPx).roundToInt()
        val durationMinutes = (placement.endMinutes - placement.startMinutes).coerceAtLeast(0.0)
        val rawHeightPx = ((durationMinutes / 60.0) * hourSlotPx).roundToInt()
        val heightPx = rawHeightPx.coerceAtLeast(minHeightPx)
        val bottomPx = topPx + heightPx
        val visibleOverlap = minOf(bottomPx, scrollBottomPx) - maxOf(topPx, scrollTopPx)
        visibleOverlap >= minOf(minHeightPx, heightPx)
    }
    if (anyEntryVisible) return null

    val earliestStartMinutes = placements.minOf { it.startMinutes }
    val earliestTopPx = ((earliestStartMinutes / 60.0) * hourSlotPx).roundToInt()
    val topContextPaddingPx = hourSlotPx / 2
    return (earliestTopPx - topContextPaddingPx).coerceIn(0, maxScrollPx)
}

@Immutable
data class Appointment(
    val id: String,
    val startDateTime: LocalDateTime,
    val endDateTime: LocalDateTime,
    val title: String,
    val color: Color = Color(0xFF1976D2),
    val entryId: UUID? = null,
) {
    val startDate: LocalDate get() = startDateTime.toLocalDate()
    val startTime: LocalTime get() = startDateTime.toLocalTime()
    val endDate: LocalDate get() = endDateTime.toLocalDate()
    val endTime: LocalTime get() = endDateTime.toLocalTime()
}

@Immutable
data class AppointmentColumnPlacement(
    val appointment: Appointment,
    val column: Int,
    val totalColumns: Int,
    val startMinutes: Double,
    val endMinutes: Double,
)

@Immutable
data class DayHeaderSummary(
    val entryCount: Int = 0,
    val dotColors: List<Color> = emptyList(),
    val hasOverflow: Boolean = false,
)

fun summarizeDayHeader(
    placements: List<AppointmentColumnPlacement>,
    maxDots: Int = MAX_HEADER_DOTS,
): DayHeaderSummary {
    if ((placements.isEmpty()) || (maxDots <= 0)) {
        return DayHeaderSummary(
            entryCount = placements.size,
            hasOverflow = placements.isNotEmpty() && maxDots == 0,
        )
    }
    val allColors = placements.map { placement -> placement.appointment.color }
    if (allColors.size <= maxDots) {
        return DayHeaderSummary(
            entryCount = allColors.size,
            dotColors = allColors,
            hasOverflow = false,
        )
    }
    val distinctColors = allColors.distinct()
    val selectedColors = if (distinctColors.size >= maxDots) {
        distinctColors.take(maxDots)
    } else {
        buildList(maxDots) {
            addAll(distinctColors)
            val usedIndices = HashSet<Int>(distinctColors.size)
            for (color in distinctColors) {
                usedIndices.add(allColors.indexOf(color))
            }
            for (i in allColors.indices) {
                if (size >= maxDots) break
                if (i !in usedIndices) {
                    add(allColors[i])
                }
            }
        }
    }
    return DayHeaderSummary(
        entryCount = allColors.size,
        dotColors = selectedColors,
        hasOverflow = true,
    )
}

@Immutable
data class AppointmentLayoutInfo(
    val appointment: Appointment,
    val column: Int,
    val totalColumns: Int,
    val offsetX: Dp,
    val width: Dp,
)

private data class EffectiveInterval(
    val appointment: Appointment,
    val startMinutes: Double,
    val displayEndMinutes: Double,
    val collisionEndMinutes: Double,
)

/**
 * Width-independent overlap clustering for appointments on [currentDate].
 * Groups genuinely overlapping `[start, end)` intervals into independent connected clusters
 * so non-overlapping times of day always retain full column width.
 */
fun calculateAppointmentColumns(
    dailyAppointments: List<Appointment>,
    currentDate: LocalDate,
): List<AppointmentColumnPlacement> {
    if (dailyAppointments.isEmpty()) return emptyList()

    val intervals = dailyAppointments
        .asSequence()
        .map { appointment ->
            val rawStart = if (appointment.startDate.isBefore(currentDate)) {
                0.0
            } else {
                appointment.startTime.toSecondOfDay() / 60.0
            }
            val rawEnd = if (appointment.endDate.isAfter(currentDate)) {
                MINUTES_PER_DAY
            } else {
                appointment.endTime.toSecondOfDay() / 60.0
            }
            val displayEnd = rawEnd.coerceAtLeast(rawStart)
            val collisionEnd = if (displayEnd <= rawStart) {
                minOf(MINUTES_PER_DAY, rawStart + 1.0)
            } else {
                displayEnd
            }
            EffectiveInterval(
                appointment = appointment,
                startMinutes = rawStart,
                displayEndMinutes = displayEnd,
                collisionEndMinutes = collisionEnd,
            )
        }
        .sortedWith(
            compareBy<EffectiveInterval> { interval -> interval.startMinutes }
                .thenByDescending { interval -> interval.collisionEndMinutes },
        )
        .toList()

    val placements = ArrayList<AppointmentColumnPlacement>(intervals.size)
    var index = 0

    while (index < intervals.size) {
        val cluster = mutableListOf(intervals[index])
        var clusterMaxEnd = intervals[index].collisionEndMinutes
        index++

        while ((index < intervals.size) && (intervals[index].startMinutes < clusterMaxEnd)) {
            val next = intervals[index]
            cluster.add(next)
            if (next.collisionEndMinutes > clusterMaxEnd) {
                clusterMaxEnd = next.collisionEndMinutes
            }
            index++
        }

        val columnEndMinutes = mutableListOf<Double>()
        val assignedColumns = IntArray(cluster.size)

        for (i in cluster.indices) {
            val item = cluster[i]
            var targetColumn = -1
            for (colIdx in columnEndMinutes.indices) {
                if (columnEndMinutes[colIdx] <= item.startMinutes) {
                    targetColumn = colIdx
                    columnEndMinutes[colIdx] = item.collisionEndMinutes
                    break
                }
            }
            if (targetColumn == -1) {
                targetColumn = columnEndMinutes.size
                columnEndMinutes.add(item.collisionEndMinutes)
            }
            assignedColumns[i] = targetColumn
        }

        val totalColumns = columnEndMinutes.size
        for (i in cluster.indices) {
            val item = cluster[i]
            placements.add(
                AppointmentColumnPlacement(
                    appointment = item.appointment,
                    column = assignedColumns[i],
                    totalColumns = totalColumns,
                    startMinutes = item.startMinutes,
                    endMinutes = item.displayEndMinutes,
                ),
            )
        }
    }

    return placements
}

fun calculateOverlappingAppointmentLayout(
    dailyAppointments: List<Appointment>,
    dayWidth: Dp,
    currentDate: LocalDate,
): List<AppointmentLayoutInfo> {
    val placements = calculateAppointmentColumns(dailyAppointments, currentDate)
    if (placements.isEmpty()) return emptyList()
    return placements.map { placement ->
        val individualWidth = dayWidth / placement.totalColumns.toFloat()
        AppointmentLayoutInfo(
            appointment = placement.appointment,
            column = placement.column,
            totalColumns = placement.totalColumns,
            offsetX = individualWidth * placement.column.toFloat(),
            width = individualWidth,
        )
    }
}

@Composable
fun WeekView(
    visibleDates: List<LocalDate>,
    modifier: Modifier = Modifier,
    appointments: List<Appointment> = emptyList(),
    selectedDate: LocalDate? = null,
    today: LocalDate = LocalDate.now(),
    currentTime: LocalTime? = null,
    hourHeight: Dp = 60.dp,
    minAppointmentHeight: Dp = MIN_APPOINTMENT_HEIGHT,
    onDayClick: (LocalDate) -> Unit = {},
    onAppointmentClick: (Appointment) -> Unit = {},
    onPeriodStartChanged: ((LocalDate) -> Unit)? = null,
    onNextPeriod: () -> Unit = {},
    onPreviousPeriod: () -> Unit = {},
) {
    if (visibleDates.isEmpty()) return

    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val hourSlotPx = with(density) { hourHeight.roundToPx().coerceAtLeast(1) }
    val scrollState = rememberScrollState(initial = hourSlotPx * 8)
    val exactHourHeight = with(density) { hourSlotPx.toDp() }
    val totalDayHeightPx = hourSlotPx * HOURS_OF_DAY.size
    val totalDayHeight = with(density) { totalDayHeightPx.toDp() }
    val minAppointmentHeightPx = with(density) { minAppointmentHeight.roundToPx().coerceAtLeast(1) }
    val horizontalTileGapPx = with(density) { 1.dp.roundToPx() }
    val verticalTileGapPx = with(density) { 1.dp.roundToPx() }
    val headerHeight = with(density) { (22.sp.toDp() + 38.dp).coerceAtLeast(60.dp) }

    val requestedStartDate = visibleDates.first()
    val stepDays = visibleDates.size

    var anchorStartDate by remember { mutableStateOf(requestedStartDate) }
    var anchorStepDays by remember { mutableIntStateOf(stepDays) }
    var pagerGeneration by remember { mutableIntStateOf(0) }

    if (
        (anchorStepDays != stepDays) ||
        (pageIndexForStartDate(anchorStartDate, stepDays, requestedStartDate) == null)
    ) {
        anchorStartDate = requestedStartDate
        anchorStepDays = stepDays
        pagerGeneration++
    }

    val pagerState = key(pagerGeneration) {
        rememberPagerState(
            initialPage = PAGER_CENTER_PAGE,
            pageCount = { PAGER_PAGE_COUNT },
        )
    }

    var isProgrammaticScroll by remember(pagerGeneration) { mutableStateOf(value = false) }
    val currentRequestedStart by rememberUpdatedState(requestedStartDate)
    val currentOnPeriodStartChanged by rememberUpdatedState(onPeriodStartChanged)
    val currentOnNextPeriod by rememberUpdatedState(onNextPeriod)
    val currentOnPreviousPeriod by rememberUpdatedState(onPreviousPeriod)

    LaunchedEffect(pagerState, anchorStartDate, anchorStepDays) {
        var lastReportedPage = pagerState.settledPage
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settledPage ->
                if (isProgrammaticScroll) {
                    lastReportedPage = settledPage
                    return@collect
                }
                val pageOffset = settledPage - PAGER_CENTER_PAGE
                val settledStart = anchorStartDate.plusDays(pageOffset.toLong() * anchorStepDays.toLong())
                if (settledStart != currentRequestedStart) {
                    val periodCallback = currentOnPeriodStartChanged
                    if (periodCallback != null) {
                        periodCallback(settledStart)
                    } else if (settledPage > lastReportedPage) {
                        currentOnNextPeriod()
                    } else if (settledPage < lastReportedPage) {
                        currentOnPreviousPeriod()
                    }
                }
                lastReportedPage = settledPage
            }
    }

    LaunchedEffect(requestedStartDate, pagerGeneration) {
        val targetPage = pageIndexForStartDate(anchorStartDate, anchorStepDays, requestedStartDate)
            ?: return@LaunchedEffect
        if (targetPage != pagerState.settledPage) {
            isProgrammaticScroll = true
            try {
                if (abs(targetPage - pagerState.currentPage) <= MAX_ANIMATED_PAGE_JUMP) {
                    pagerState.animateScrollToPage(targetPage)
                } else {
                    pagerState.scrollToPage(targetPage)
                }
            } finally {
                isProgrammaticScroll = false
            }
        }
    }

    val liveTime by produceState(initialValue = LocalTime.now(), key1 = currentTime) {
        if (currentTime != null) return@produceState
        while (true) {
            value = LocalTime.now()
            delay((60 - value.second).seconds)
        }
    }
    val effectiveCurrentTime = currentTime ?: liveTime

    val colorScheme = MaterialTheme.colorScheme
    val surfaceColor = colorScheme.surface

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(surfaceColor),
    ) {
        // Pinned Left Column: Header Corner + Synchronized Hour Labels
        Column(
            modifier = Modifier
                .width(HOUR_COLUMN_WIDTH)
                .fillMaxHeight(),
        ) {
            Spacer(modifier = Modifier.height(headerHeight))
            HorizontalDivider(color = colorScheme.outlineVariant)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
            ) {
                HOURS_OF_DAY.forEach { hour ->
                    Box(
                        modifier = Modifier
                            .height(exactHourHeight)
                            .fillMaxWidth()
                            .padding(top = 2.dp, end = 6.dp),
                        contentAlignment = Alignment.TopEnd,
                    ) {
                        Text(
                            text = hour.format(DateFormatters.TIME_HM),
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Smoothly Paged Day Headers + Synchronized 24h Time Grids
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) { page ->
            val pageDates = remember(anchorStartDate, anchorStepDays, page) {
                visibleDatesForPage(
                    anchorStartDate = anchorStartDate,
                    stepDays = anchorStepDays,
                    pageOffset = page - PAGER_CENTER_PAGE,
                )
            }
            val pagePlacementsByDate = remember(appointments, pageDates) {
                pageDates.associateWith { date ->
                    val daily = appointments.filter {
                        !it.startDate.isAfter(date) && !it.endDate.isBefore(date)
                    }
                    calculateAppointmentColumns(daily, date)
                }
            }
            val pageSummaries = remember(pagePlacementsByDate) {
                pagePlacementsByDate.mapValues { (_, placements) -> summarizeDayHeader(placements) }
            }

            WeekPageContent(
                pageDates = pageDates,
                placementsByDate = pagePlacementsByDate,
                daySummaries = pageSummaries,
                today = today,
                selectedDate = selectedDate,
                effectiveCurrentTime = effectiveCurrentTime,
                headerHeight = headerHeight,
                hourSlotPx = hourSlotPx,
                totalDayHeightPx = totalDayHeightPx,
                totalDayHeight = totalDayHeight,
                minAppointmentHeightPx = minAppointmentHeightPx,
                horizontalTileGapPx = horizontalTileGapPx,
                verticalTileGapPx = verticalTileGapPx,
                scrollState = scrollState,
                onDayClick = { clickedDate ->
                    val clickedPlacements = pagePlacementsByDate[clickedDate].orEmpty()
                    val targetScrollPx = computeAutoScrollTargetPx(
                        placements = clickedPlacements,
                        scrollTopPx = scrollState.value,
                        viewportHeightPx = scrollState.viewportSize,
                        hourSlotPx = hourSlotPx,
                        minHeightPx = minAppointmentHeightPx,
                        maxScrollPx = scrollState.maxValue,
                    )
                    onDayClick(clickedDate)
                    if (targetScrollPx != null) {
                        coroutineScope.launch {
                            scrollState.animateScrollTo(targetScrollPx)
                        }
                    }
                },
                onAppointmentClick = onAppointmentClick,
            )
        }
    }
}

@Composable
private fun WeekPageContent(
    pageDates: List<LocalDate>,
    placementsByDate: Map<LocalDate, List<AppointmentColumnPlacement>>,
    daySummaries: Map<LocalDate, DayHeaderSummary>,
    today: LocalDate,
    selectedDate: LocalDate?,
    effectiveCurrentTime: LocalTime,
    headerHeight: Dp,
    hourSlotPx: Int,
    totalDayHeightPx: Int,
    totalDayHeight: Dp,
    minAppointmentHeightPx: Int,
    horizontalTileGapPx: Int,
    verticalTileGapPx: Int,
    scrollState: ScrollState,
    onDayClick: (LocalDate) -> Unit,
    onAppointmentClick: (Appointment) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val surfaceColor = colorScheme.surface
    val gridLineColor = colorScheme.outlineVariant.copy(alpha = 0.55f)
    val halfHourLineColor = colorScheme.outlineVariant.copy(alpha = 0.22f)
    val todayColumnTint = colorScheme.primaryContainer.copy(alpha = 0.18f)
    val selectedColumnTint = colorScheme.secondaryContainer.copy(alpha = 0.16f)
    val nowIndicatorColor = colorScheme.error

    val todayIndex = remember(pageDates, today) { pageDates.indexOf(today) }
    val selectedIndex = remember(pageDates, selectedDate) {
        selectedDate?.let { pageDates.indexOf(it) } ?: -1
    }

    Column(modifier = Modifier.fillMaxSize()) {
        WeekHeaderRow(
            visibleDates = pageDates,
            today = today,
            selectedDate = selectedDate,
            daySummaries = daySummaries,
            headerHeight = headerHeight,
            onDayClick = onDayClick,
        )

        HorizontalDivider(color = colorScheme.outlineVariant)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(totalDayHeight)
                    .drawBehind {
                        val dayCount = pageDates.size
                        val colWidth = size.width / dayCount
                        val strokePx = 1.dp.toPx()

                        if (todayIndex in 0 until dayCount) {
                            drawRect(
                                color = todayColumnTint,
                                topLeft = Offset(todayIndex * colWidth, 0f),
                                size = Size(colWidth, size.height),
                            )
                        }
                        if (selectedIndex in 0 until dayCount && selectedIndex != todayIndex) {
                            drawRect(
                                color = selectedColumnTint,
                                topLeft = Offset(selectedIndex * colWidth, 0f),
                                size = Size(colWidth, size.height),
                            )
                        }

                        for (i in 0 until dayCount) {
                            val x = i * colWidth
                            drawLine(
                                color = gridLineColor,
                                start = Offset(x, 0f),
                                end = Offset(x, size.height),
                                strokeWidth = strokePx,
                            )
                        }

                        for (h in HOURS_OF_DAY.indices) {
                            val hourY = (h * hourSlotPx).toFloat()
                            if (h > 0) {
                                drawLine(
                                    color = gridLineColor,
                                    start = Offset(0f, hourY),
                                    end = Offset(size.width, hourY),
                                    strokeWidth = strokePx,
                                )
                            }
                            val halfHourY = hourY + hourSlotPx * 0.5f
                            drawLine(
                                color = halfHourLineColor,
                                start = Offset(0f, halfHourY),
                                end = Offset(size.width, halfHourY),
                                strokeWidth = strokePx,
                            )
                        }
                    }
                    .drawWithContent {
                        drawContent()
                        val dayCount = pageDates.size
                        if (todayIndex in 0 until dayCount) {
                            val colWidth = size.width / dayCount
                            val colStart = todayIndex * colWidth
                            val colEnd = colStart + colWidth
                            val nowY = ((effectiveCurrentTime.toSecondOfDay() / 3600f) * hourSlotPx)
                                .coerceIn(0f, size.height)
                            val lineStrokePx = 2.dp.toPx()
                            val dotRadiusPx = 4.dp.toPx()

                            drawLine(
                                color = nowIndicatorColor,
                                start = Offset(colStart, nowY),
                                end = Offset(colEnd, nowY),
                                strokeWidth = lineStrokePx,
                            )
                            drawCircle(
                                color = nowIndicatorColor,
                                radius = dotRadiusPx,
                                center = Offset(colStart + dotRadiusPx, nowY),
                            )
                        }
                    },
            ) {
                pageDates.forEach { dateInWeek ->
                    val placements = placementsByDate[dateInWeek].orEmpty()
                    DayColumnAppointmentsLayout(
                        placements = placements,
                        hourSlotPx = hourSlotPx,
                        totalDayHeightPx = totalDayHeightPx,
                        minHeightPx = minAppointmentHeightPx,
                        horizontalGapPx = horizontalTileGapPx,
                        verticalGapPx = verticalTileGapPx,
                        surfaceColor = surfaceColor,
                        outlineColor = colorScheme.outline,
                        onAppointmentClick = onAppointmentClick,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekHeaderRow(
    visibleDates: List<LocalDate>,
    today: LocalDate,
    selectedDate: LocalDate?,
    daySummaries: Map<LocalDate, DayHeaderSummary>,
    headerHeight: Dp,
    onDayClick: (LocalDate) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val surfaceLuminance = colorScheme.surface.luminance()
    val locale = Locale.getDefault()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(headerHeight)
            .background(colorScheme.surface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        visibleDates.forEach { dateInWeek ->
            val isToday = dateInWeek == today
            val isSelected = dateInWeek == selectedDate
            val summary = daySummaries[dateInWeek] ?: DayHeaderSummary()

            val weekdayColor = when {
                isToday -> colorScheme.primary
                isSelected -> colorScheme.onSurface
                else -> colorScheme.onSurfaceVariant
            }
            val badgeContainerColor = when {
                isToday -> colorScheme.primary
                isSelected -> colorScheme.secondaryContainer
                else -> Color.Transparent
            }
            val badgeContentColor = when {
                isToday -> colorScheme.onPrimary
                isSelected -> colorScheme.onSecondaryContainer
                else -> colorScheme.onSurface
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onDayClick(dateInWeek) }
                    .padding(top = 4.dp, bottom = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = dateInWeek.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isToday || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = weekdayColor,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(badgeContainerColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = dateInWeek.dayOfMonth.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = badgeContentColor,
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Box(
                    modifier = Modifier.height(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (summary.dotColors.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            summary.dotColors.forEach { dotColor ->
                                val lowContrast = abs(dotColor.luminance() - surfaceLuminance) < 0.12f
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                        .border(
                                            width = 0.5.dp,
                                            color = colorScheme.outline.copy(
                                                alpha = if (lowContrast) 0.85f else 0.35f,
                                            ),
                                            shape = CircleShape,
                                        ),
                                )
                            }
                            if (summary.hasOverflow) {
                                Text(
                                    text = "+",
                                    fontSize = 8.sp,
                                    lineHeight = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayColumnAppointmentsLayout(
    placements: List<AppointmentColumnPlacement>,
    hourSlotPx: Int,
    totalDayHeightPx: Int,
    minHeightPx: Int,
    horizontalGapPx: Int,
    verticalGapPx: Int,
    surfaceColor: Color,
    outlineColor: Color,
    onAppointmentClick: (Appointment) -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(
        modifier = modifier,
        content = {
            placements.forEach { placement ->
                AppointmentTile(
                    appointment = placement.appointment,
                    surfaceColor = surfaceColor,
                    outlineColor = outlineColor,
                ) {
                    onAppointmentClick(placement.appointment)
                }
            }
        },
    ) { measurables, constraints ->
        val dayWidthPx = constraints.maxWidth.coerceAtLeast(1)
        val placeables = ArrayList<Placeable>(measurables.size)
        val positionsX = IntArray(measurables.size)
        val positionsY = IntArray(measurables.size)

        for (i in measurables.indices) {
            val placement = placements[i]
            val colStartX = (dayWidthPx * placement.column) / placement.totalColumns
            val colEndX = (dayWidthPx * (placement.column + 1)) / placement.totalColumns
            val gapRight = if (placement.column < placement.totalColumns - 1) horizontalGapPx else 0
            val itemWidthPx = (colEndX - colStartX - gapRight).coerceAtLeast(1)

            val durationMinutes = (placement.endMinutes - placement.startMinutes).coerceAtLeast(0.0)
            val rawHeightPx = ((durationMinutes / 60.0) * hourSlotPx).roundToInt() - verticalGapPx
            val itemHeightPx = rawHeightPx
                .coerceAtLeast(minHeightPx)
                .coerceAtMost(totalDayHeightPx)

            val rawTopY = ((placement.startMinutes / 60.0) * hourSlotPx).roundToInt()
            val topY = rawTopY.coerceIn(0, (totalDayHeightPx - itemHeightPx).coerceAtLeast(0))

            placeables.add(measurables[i].measure(Constraints.fixed(itemWidthPx, itemHeightPx)))
            positionsX[i] = colStartX
            positionsY[i] = topY
        }

        layout(dayWidthPx, totalDayHeightPx) {
            for (i in placeables.indices) {
                placeables[i].placeRelative(positionsX[i], positionsY[i])
            }
        }
    }
}

@Composable
private fun AppointmentTile(
    appointment: Appointment,
    surfaceColor: Color,
    outlineColor: Color,
    onClick: () -> Unit,
) {
    val containerColor = appointment.color.copy(alpha = 0.84f).compositeOver(surfaceColor)
    val contentColor = if (containerColor.luminance() > 0.45f) {
        DARK_TEXT_ON_LIGHT_TILE
    } else {
        Color.White
    }
    val lowContrastAgainstSurface = abs(containerColor.luminance() - surfaceColor.luminance()) < 0.08f
    val accentColor = if (lowContrastAgainstSurface) outlineColor else appointment.color

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(APPOINTMENT_SHAPE)
            .background(containerColor)
            .border(
                width = 0.5.dp,
                color = accentColor.copy(alpha = if (lowContrastAgainstSurface) 0.65f else 0.35f),
                shape = APPOINTMENT_SHAPE,
            )
            .drawBehind {
                val stripeWidth = 3.dp.toPx()
                drawRect(
                    color = accentColor,
                    topLeft = Offset.Zero,
                    size = Size(stripeWidth, size.height),
                )
            }
            .clickable(onClick = onClick)
            .padding(start = 6.dp, end = 3.dp, top = 2.dp, bottom = 2.dp),
    ) {
        Text(
            text = appointment.title,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SampleWeekViewPreviewContent() {
    val weekStartDate = startOfWeek(LocalDate.of(2026, 11, 19))
    val visibleDates = (0L until 7L).map { weekStartDate.plusDays(it) }
    val tuesdayDate = weekStartDate.plusDays(1)
    val wednesdayDate = weekStartDate.plusDays(2)
    val thursdayDate = weekStartDate.plusDays(3)
    val fridayDate = weekStartDate.plusDays(4)
    val saturdayDate = weekStartDate.plusDays(5)

    val sampleAppointments = listOf(
        Appointment(
            id = "1",
            startDateTime = weekStartDate.atTime(9, 30),
            endDateTime = weekStartDate.atTime(10, 30),
            title = "Meeting with John",
            color = Color(0xFFE57373),
        ),
        Appointment(
            id = "2",
            startDateTime = tuesdayDate.atTime(14, 0),
            endDateTime = tuesdayDate.atTime(15, 0),
            title = "Team Standup",
            color = Color(0xFF81C784),
        ),
        Appointment(
            id = "3",
            startDateTime = wednesdayDate.atTime(11, 0),
            endDateTime = wednesdayDate.atTime(12, 30),
            title = "Client Call",
            color = Color(0xFF64B5F6),
        ),
        Appointment(
            id = "4",
            startDateTime = thursdayDate.atTime(9, 0),
            endDateTime = thursdayDate.atTime(9, 45),
            title = "Quick Sync (Light Yellow)",
            color = Color(0xFFFFF176),
        ),
        Appointment(
            id = "5",
            startDateTime = thursdayDate.atTime(9, 15),
            endDateTime = thursdayDate.atTime(10, 15),
            title = "White Category Note",
            color = Color(0xFFFFFFFF),
        ),
        Appointment(
            id = "7",
            startDateTime = thursdayDate.atTime(13, 0),
            endDateTime = thursdayDate.atTime(14, 0),
            title = "Lunch Journal",
            color = Color(0xFF4DB6AC),
        ),
        Appointment(
            id = "8",
            startDateTime = thursdayDate.atTime(21, 30),
            endDateTime = thursdayDate.atTime(22, 0),
            title = "Evening Reflection (Off-screen)",
            color = Color(0xFFFFB74D),
        ),
        Appointment(
            id = "6",
            startDateTime = fridayDate.atTime(10, 0),
            endDateTime = saturdayDate.atTime(16, 0),
            title = "All Day Workshop",
            color = Color(0xFFBA68C8),
        ),
    )

    Surface {
        WeekView(
            visibleDates = visibleDates,
            appointments = sampleAppointments,
            today = thursdayDate,
            currentTime = LocalTime.of(10, 20),
        )
    }
}

@Preview(name = "WeekView (Light)", widthDp = 700, heightDp = 500)
@Composable
fun WeekViewWithAppointmentsPreview() {
    VoicejournalTheme(darkTheme = false, dynamicColor = false) {
        SampleWeekViewPreviewContent()
    }
}

@Preview(
    name = "WeekView (Dark)",
    widthDp = 700,
    heightDp = 500,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun WeekViewDarkPreview() {
    VoicejournalTheme(darkTheme = true, dynamicColor = false) {
        SampleWeekViewPreviewContent()
    }
}
