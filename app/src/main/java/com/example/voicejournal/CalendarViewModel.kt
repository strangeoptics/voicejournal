package com.example.voicejournal

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.voicejournal.data.JournalRepository
import com.example.voicejournal.data.startLocalDateTime
import com.example.voicejournal.data.stopLocalDateTime
import com.example.voicejournal.di.Injector
import com.example.voicejournal.ui.components.Appointment
import com.example.voicejournal.ui.components.startOfWeek
import com.example.voicejournal.util.TimeZoneUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

private const val MAX_CALENDAR_TITLE_LENGTH = 120
private val DEFAULT_APPOINTMENT_COLOR = Color(0xFF1976D2)

enum class CalendarViewMode {
    DAY,
    THREE_DAY,
    WEEK,
    MONTH,
}

private data class CalendarQueryWindow(
    val visibleStart: LocalDate,
    val visibleEnd: LocalDate,
    val preloadStart: LocalDate,
    val preloadEnd: LocalDate,
)

class CalendarViewModel(
    private val repository: JournalRepository,
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val initialStartDate: LocalDate = startOfWeek(todayProvider())

    private val _viewMode = MutableStateFlow(CalendarViewMode.WEEK)
    val viewMode: StateFlow<CalendarViewMode> = _viewMode.asStateFlow()

    private val _currentDate = MutableStateFlow(initialStartDate)
    val currentDate: StateFlow<LocalDate> = _currentDate.asStateFlow()

    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val selectedDate: StateFlow<LocalDate?> = _selectedDate.asStateFlow()

    val currentMonth: StateFlow<YearMonth> = _currentDate
        .map { date -> YearMonth.from(date) }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = YearMonth.from(initialStartDate),
        )

    private val queryWindow: Flow<CalendarQueryWindow> = combine(
        _currentDate,
        _viewMode,
        _selectedDate,
    ) { date, mode, selected ->
        when {
            selected != null -> CalendarQueryWindow(
                visibleStart = selected,
                visibleEnd = selected,
                preloadStart = selected.minusDays(1),
                preloadEnd = selected.plusDays(1),
            )
            mode == CalendarViewMode.MONTH -> {
                val month = YearMonth.from(date)
                val visibleStart = month.atDay(1)
                val visibleEnd = month.atEndOfMonth()
                CalendarQueryWindow(
                    visibleStart = visibleStart,
                    visibleEnd = visibleEnd,
                    preloadStart = month.minusMonths(1).atDay(1).minusDays(6),
                    preloadEnd = month.plusMonths(1).atEndOfMonth().plusDays(13),
                )
            }
            else -> {
                val days = when (mode) {
                    CalendarViewMode.DAY -> 1L
                    CalendarViewMode.THREE_DAY -> 3L
                    else -> 7L
                }
                val startDate = if (mode == CalendarViewMode.WEEK) startOfWeek(date) else date
                val endDate = startDate.plusDays(days - 1L)
                CalendarQueryWindow(
                    visibleStart = startDate,
                    visibleEnd = endDate,
                    preloadStart = startDate.minusDays(days),
                    preloadEnd = endDate.plusDays(days),
                )
            }
        }
    }.distinctUntilChanged()

    val visibleDates: StateFlow<List<LocalDate>> = queryWindow
        .map { window ->
            val count = ChronoUnit.DAYS.between(window.visibleStart, window.visibleEnd).toInt() + 1
            (0 until count).map { offset -> window.visibleStart.plusDays(offset.toLong()) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = (0L until 7L).map { initialStartDate.plusDays(it) },
        )

    /**
     * Preloads a 3-period window (previous + current + next page) so adjacent
     * [com.example.voicejournal.ui.components.WeekView] and
     * [com.example.voicejournal.ui.components.MonthView] pager pages already render
     * appointments seamlessly during a swipe.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val appointments: StateFlow<List<Appointment>> = queryWindow.flatMapLatest { window ->
        val preloadStart = window.preloadStart
        val preloadEnd = window.preloadEnd
        val (queryStartMillis, queryEndMillis) = TimeZoneUtils.utcQueryBoundsForLocalDates(preloadStart, preloadEnd)

        repository.getEntriesForCalendar(queryStartMillis, queryEndMillis).map { entriesWithCategories ->
            entriesWithCategories.mapNotNull { (entry, categories) ->
                val startDateTime = entry.startLocalDateTime()
                val explicitStopDateTime = entry.stopLocalDateTime()
                val rawEndDateTime = explicitStopDateTime ?: startDateTime.plusHours(1)
                val endDateTime = if (rawEndDateTime.isBefore(startDateTime)) startDateTime else rawEndDateTime
                if (endDateTime.toLocalDate().isBefore(preloadStart) || startDateTime.toLocalDate().isAfter(preloadEnd)) {
                    return@mapNotNull null
                }
                val appointmentColor = categories
                    .asSequence()
                    .filter { category -> category.color.isNotBlank() }
                    .minByOrNull { category -> category.orderIndex }
                    ?.let { category ->
                        try {
                            Color(category.color.toColorInt())
                        } catch (_: IllegalArgumentException) {
                            Color.Gray
                        }
                    } ?: DEFAULT_APPOINTMENT_COLOR

                val trimmedContent = entry.content.trim()
                val displayTitle = trimmedContent
                    .lineSequence()
                    .firstOrNull { it.isNotBlank() }
                    ?.trim()
                    ?.take(MAX_CALENDAR_TITLE_LENGTH)
                    ?: ""

                Appointment(
                    id = entry.id.toString(),
                    startDateTime = startDateTime,
                    endDateTime = endDateTime,
                    title = displayTitle,
                    color = appointmentColor,
                    entryId = entry.id,
                    fullText = trimmedContent,
                    hasExplicitEndTime = explicitStopDateTime != null,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setViewMode(mode: CalendarViewMode) {
        val referenceDate = _selectedDate.value ?: _currentDate.value
        _viewMode.value = mode
        _selectedDate.value = null
        _currentDate.value = when (mode) {
            CalendarViewMode.WEEK -> startOfWeek(referenceDate)
            CalendarViewMode.DAY,
            CalendarViewMode.THREE_DAY,
            CalendarViewMode.MONTH,
            -> referenceDate
        }
    }

    fun setNumberOfDays(days: Int) {
        val mode = when (days) {
            1 -> CalendarViewMode.DAY
            3 -> CalendarViewMode.THREE_DAY
            else -> CalendarViewMode.WEEK
        }
        setViewMode(mode)
    }

    fun selectDate(date: LocalDate?) {
        _selectedDate.value = if (_selectedDate.value == date) null else date
    }

    fun jumpToToday() {
        val today = todayProvider()
        _selectedDate.value = null
        _currentDate.value = if (_viewMode.value == CalendarViewMode.WEEK) startOfWeek(today) else today
    }

    fun navigateToMonth(month: YearMonth) {
        val today = todayProvider()
        _currentDate.value = if (YearMonth.from(today) == month) today else month.atDay(1)
    }

    fun navigateToPeriodStart(periodStart: LocalDate) {
        if (_selectedDate.value != null) {
            _selectedDate.value = periodStart
            _currentDate.value = if (_viewMode.value == CalendarViewMode.WEEK) startOfWeek(periodStart) else periodStart
        } else {
            _currentDate.value = if (_viewMode.value == CalendarViewMode.WEEK) startOfWeek(periodStart) else periodStart
        }
    }

    fun navigateNext() {
        val selected = _selectedDate.value
        if (selected != null) {
            navigateToPeriodStart(selected.plusDays(1))
            return
        }
        _currentDate.value = when (_viewMode.value) {
            CalendarViewMode.DAY -> _currentDate.value.plusDays(1)
            CalendarViewMode.THREE_DAY -> _currentDate.value.plusDays(3)
            CalendarViewMode.WEEK -> _currentDate.value.plusDays(7)
            CalendarViewMode.MONTH -> {
                val nextMonth = YearMonth.from(_currentDate.value).plusMonths(1)
                val today = todayProvider()
                if (YearMonth.from(today) == nextMonth) today else nextMonth.atDay(1)
            }
        }
    }

    fun navigatePrevious() {
        val selected = _selectedDate.value
        if (selected != null) {
            navigateToPeriodStart(selected.minusDays(1))
            return
        }
        _currentDate.value = when (_viewMode.value) {
            CalendarViewMode.DAY -> _currentDate.value.minusDays(1)
            CalendarViewMode.THREE_DAY -> _currentDate.value.minusDays(3)
            CalendarViewMode.WEEK -> _currentDate.value.minusDays(7)
            CalendarViewMode.MONTH -> {
                val prevMonth = YearMonth.from(_currentDate.value).minusMonths(1)
                val today = todayProvider()
                if (YearMonth.from(today) == prevMonth) today else prevMonth.atDay(1)
            }
        }
    }
}

class CalendarViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CalendarViewModel::class.java)) {
            val repository = Injector.provideJournalRepository(context)
            @Suppress("UNCHECKED_CAST")
            return CalendarViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
