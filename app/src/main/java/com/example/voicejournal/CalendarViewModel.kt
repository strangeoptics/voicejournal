package com.example.voicejournal

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.voicejournal.data.JournalRepository
import com.example.voicejournal.di.Injector
import com.example.voicejournal.ui.components.Appointment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class CalendarViewModel(private val repository: JournalRepository) : ViewModel() {

    private val _dateRange = MutableStateFlow<Pair<LocalDate, LocalDate>>(
        LocalDate.now() to LocalDate.now().plusDays(6))

    @OptIn(ExperimentalCoroutinesApi::class)
    val appointments: StateFlow<List<Appointment>> = _dateRange.flatMapLatest { (startDate, endDate) ->
        val startMillis = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMillis = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        repository.getEntriesForCalendar(startMillis, endMillis)
    }.map { entriesWithCategories ->
        entriesWithCategories.map { entryWithCategories ->
            val entry = entryWithCategories.entry
            val startDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(entry.start_datetime), ZoneId.systemDefault())
            val endDateTime = entry.stop_datetime?.let {
                LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
            }

            Appointment(
                id = entry.id.toString(),
                startDateTime = startDateTime,
                endDateTime = endDateTime ?: startDateTime.plusHours(1),
                title = entry.content.take(12),
                description = entry.content,
                entry = entry,
                // You can add logic here to assign colors based on categories, for example
                // color = getColorForCategories(entryWithCategories.categories)
            )
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
                val newStartMillis = appointment.startDateTime
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

                val newEndMillis = appointment.endDateTime
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

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
