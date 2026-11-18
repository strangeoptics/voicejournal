package com.example.voicejournal

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.voicejournal.data.JournalRepository
import com.example.voicejournal.data.startLocalDateTime
import com.example.voicejournal.data.stopLocalDateTime
import com.example.voicejournal.di.Injector
import com.example.voicejournal.ui.components.Appointment
import com.example.voicejournal.util.TimeZoneUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

class CalendarViewModel(private val repository: JournalRepository) : ViewModel() {

    private val _numberOfDays = MutableStateFlow(7)
    val numberOfDays: StateFlow<Int> = _numberOfDays.asStateFlow()

    private val _currentDate = MutableStateFlow(LocalDate.now())
    val currentDate: StateFlow<LocalDate> = _currentDate.asStateFlow()

    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    val selectedDate: StateFlow<LocalDate?> = _selectedDate.asStateFlow()

    private val dateRange: Flow<Pair<LocalDate, LocalDate>> = combine(
        _currentDate,
        _numberOfDays,
        _selectedDate
    ) { date, days, selected ->
        if (selected != null) {
            selected to selected
        } else {
            val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
            val startDate = if (days == 7) date.with(firstDayOfWeek) else date
            val endDate = startDate.plusDays(days.toLong() - 1)
            startDate to endDate
        }
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val appointments: StateFlow<List<Appointment>> = dateRange.flatMapLatest { (startDate, endDate) ->
        val (queryStartMillis, queryEndMillis) = TimeZoneUtils.utcQueryBoundsForLocalDates(startDate, endDate)
        repository.getEntriesForCalendar(queryStartMillis, queryEndMillis).map { entriesWithCategories ->
            entriesWithCategories.mapNotNull { entryWithCategories ->
                val entry = entryWithCategories.entry
                val startDateTime = entry.startLocalDateTime()
                val endDateTime = entry.stopLocalDateTime() ?: startDateTime.plusHours(1)
                if (endDateTime.toLocalDate().isBefore(startDate) || startDateTime.toLocalDate().isAfter(endDate)) {
                    return@mapNotNull null
                }
                val appointmentColor = entryWithCategories.categories
                    .filter { it.color.isNotBlank() }
                    .minByOrNull { it.orderIndex }
                    ?.let {
                        try {
                            Color(android.graphics.Color.parseColor(it.color))
                        } catch (e: IllegalArgumentException) {
                            Color.Gray // Fallback for invalid color string
                        }
                    } ?: Color.Blue // Default color if no categories or no valid color

                Appointment(
                    id = entry.id.toString(),
                    startDateTime = startDateTime,
                    endDateTime = endDateTime,
                    title = entry.content.take(12),
                    description = entry.content,
                    entry = entry,
                    color = appointmentColor
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setNumberOfDays(days: Int) {
        _numberOfDays.value = days
        _selectedDate.value = null
        if (days == 7) {
            val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
            _currentDate.value = _currentDate.value.with(firstDayOfWeek)
        }
    }

    fun selectDate(date: LocalDate?) {
        _selectedDate.value = date
    }

    fun navigateNext() {
        _currentDate.value = _currentDate.value.plusDays(_numberOfDays.value.toLong())
        _selectedDate.value = null
    }

    fun navigatePrevious() {
        _currentDate.value = _currentDate.value.minusDays(_numberOfDays.value.toLong())
        _selectedDate.value = null
    }
}

class CalendarViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CalendarViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            val repository = Injector.provideJournalRepository(context)
            return CalendarViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
