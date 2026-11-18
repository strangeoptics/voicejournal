package com.example.voicejournal

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.voicejournal.data.JournalRepository
import com.example.voicejournal.data.resolvedZoneId
import com.example.voicejournal.data.startLocalDateTime
import com.example.voicejournal.data.stopLocalDateTime
import com.example.voicejournal.di.Injector
import com.example.voicejournal.ui.components.Appointment
import com.example.voicejournal.util.TimeZoneUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

class CalendarViewModel(private val repository: JournalRepository) : ViewModel() {

    private val _dateRange = MutableStateFlow<Pair<LocalDate, LocalDate>>(
        LocalDate.now() to LocalDate.now().plusDays(6)
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val appointments: StateFlow<List<Appointment>> = _dateRange.flatMapLatest { (startDate, endDate) ->
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

    fun setDateRange(startDate: LocalDate, numberOfDays: Int) {
        val endDate = startDate.plusDays(numberOfDays.toLong() - 1)
        _dateRange.value = startDate to endDate
    }

    fun updateAppointment(appointment: Appointment) {
        viewModelScope.launch {
            val entry = appointment.entry
            if (entry != null) {
                val entryZone = entry.resolvedZoneId()
                val newStartMillis = appointment.startDateTime
                    .atZone(entryZone).toInstant().toEpochMilli()

                val newEndMillis = appointment.endDateTime
                    .atZone(entryZone).toInstant().toEpochMilli()

                val updatedEntry = entry.copy(
                    start_datetime = newStartMillis,
                    stop_datetime = newEndMillis
                )
                repository.updateJournalEntry(updatedEntry)
            }
        }
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
