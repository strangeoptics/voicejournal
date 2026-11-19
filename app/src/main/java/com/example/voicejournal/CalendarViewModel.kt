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
import java.time.temporal.ChronoUnit

private const val MAX_CALENDAR_TITLE_LENGTH = 120
private val DEFAULT_APPOINTMENT_COLOR = Color(0xFF1976D2)

class CalendarViewModel(private val repository: JournalRepository) : ViewModel() {

    private val initialStartDate: LocalDate = startOfWeek(LocalDate.now())

    private val _numberOfDays = MutableStateFlow(7)
    val numberOfDays: StateFlow<Int> = _numberOfDays.asStateFlow()

    private val _currentDate = MutableStateFlow(initialStartDate)
    val currentDate: StateFlow<LocalDate> = _currentDate.asStateFlow()

    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val selectedDate: StateFlow<LocalDate?> = _selectedDate.asStateFlow()

    private val dateRange: Flow<Pair<LocalDate, LocalDate>> = combine(
        _currentDate,
        _numberOfDays,
        _selectedDate,
    ) { date, days, selected ->
        if (selected != null) {
            selected to selected
        } else {
            val startDate = if (days == 7) startOfWeek(date) else date
            val endDate = startDate.plusDays(days.toLong() - 1)
            startDate to endDate
        }
    }.distinctUntilChanged()

    val visibleDates: StateFlow<List<LocalDate>> = dateRange
        .map { (startDate, endDate) ->
            val count = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1
            (0 until count).map { offset -> startDate.plusDays(offset.toLong()) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = (0L until 7L).map { initialStartDate.plusDays(it) },
        )

    /**
     * Preloads a 3-period window (previous + current + next page) so adjacent
     * [com.example.voicejournal.ui.components.WeekView] pager pages already render
     * appointments and header dots seamlessly during a swipe.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val appointments: StateFlow<List<Appointment>> = dateRange.flatMapLatest { (startDate, endDate) ->
        val spanDays = (ChronoUnit.DAYS.between(startDate, endDate) + 1L).coerceAtLeast(1L)
        val preloadStart = startDate.minusDays(spanDays)
        val preloadEnd = endDate.plusDays(spanDays)
        val (queryStartMillis, queryEndMillis) = TimeZoneUtils.utcQueryBoundsForLocalDates(preloadStart, preloadEnd)

        repository.getEntriesForCalendar(queryStartMillis, queryEndMillis).map { entriesWithCategories ->
            entriesWithCategories.mapNotNull { (entry, categories) ->
                val startDateTime = entry.startLocalDateTime()
                val rawEndDateTime = entry.stopLocalDateTime() ?: startDateTime.plusHours(1)
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

                val displayTitle = entry.content
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
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setNumberOfDays(days: Int) {
        _numberOfDays.value = days
        _selectedDate.value = null
        if (days == 7) {
            _currentDate.value = startOfWeek(_currentDate.value)
        }
    }

    fun selectDate(date: LocalDate?) {
        _selectedDate.value = if (_selectedDate.value == date) null else date
    }

    fun jumpToToday() {
        val today = LocalDate.now()
        _selectedDate.value = null
        _currentDate.value = if (_numberOfDays.value == 7) startOfWeek(today) else today
    }

    fun navigateToPeriodStart(periodStart: LocalDate) {
        if (_selectedDate.value != null) {
            _selectedDate.value = periodStart
            _currentDate.value = if (_numberOfDays.value == 7) startOfWeek(periodStart) else periodStart
        } else {
            _currentDate.value = if (_numberOfDays.value == 7) startOfWeek(periodStart) else periodStart
        }
    }

    fun navigateNext() {
        val selected = _selectedDate.value
        if (selected != null) {
            navigateToPeriodStart(selected.plusDays(1))
        } else {
            _currentDate.value = _currentDate.value.plusDays(_numberOfDays.value.toLong())
        }
    }

    fun navigatePrevious() {
        val selected = _selectedDate.value
        if (selected != null) {
            navigateToPeriodStart(selected.minusDays(1))
        } else {
            _currentDate.value = _currentDate.value.minusDays(_numberOfDays.value.toLong())
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
