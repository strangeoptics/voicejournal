package com.example.voicejournal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.voicejournal.ui.components.WeekView
import com.example.voicejournal.ui.theme.VoicejournalTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
class CalendarActivity : ComponentActivity() {
    private val viewModel: CalendarViewModel by viewModels { CalendarViewModelFactory(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VoicejournalTheme {
                CalendarScreen(
                    viewModel = viewModel,
                    onFinish = { finish() } // Provide the finish callback
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(viewModel: CalendarViewModel, onFinish: () -> Unit) {
    val appointments by viewModel.appointments.collectAsState()

    var numberOfDays by remember { mutableStateOf(7) }
    var currentDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    // Update the ViewModel when the date range changes
    LaunchedEffect(currentDate, numberOfDays) {
        val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        val startDate = if (numberOfDays == 7) currentDate.with(firstDayOfWeek) else currentDate
        viewModel.setDateRange(startDate, numberOfDays)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = selectedDate?.let { date ->
                            date.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN))
                        } ?: when (numberOfDays) {
                            1 -> currentDate.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN))
                            3 -> "3-Tages-Ansicht"
                            7 -> "Wochenansicht"
                            else -> ""
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedDate != null) {
                            selectedDate = null
                        } else {
                            onFinish()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Zurück"
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Weitere Optionen")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Tagesansicht") },
                                onClick = {
                                    numberOfDays = 1
                                    selectedDate = null
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("3-Tages-Ansicht") },
                                onClick = {
                                    numberOfDays = 3
                                    selectedDate = null
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Wochenansicht") },
                                onClick = {
                                    numberOfDays = 7
                                    selectedDate = null
                                    // Adjust date to the start of the week when switching to week view
                                    val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
                                    currentDate = currentDate.with(firstDayOfWeek)
                                    showMenu = false
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        WeekView(
            modifier = Modifier.padding(innerPadding),
            appointments = appointments,
            numberOfDays = numberOfDays,
            onAppointmentUpdate = { updatedAppointment ->
                viewModel.updateAppointment(updatedAppointment)
            },
            currentDate = currentDate,
            selectedDate = selectedDate,
            onDayClick = { date ->
                // If a day is clicked, switch to single-day view
                selectedDate = date
            },
            onNextWeek = {
                currentDate = currentDate.plusDays(numberOfDays.toLong())
                 selectedDate = null
            },
            onPreviousWeek = {
                currentDate = currentDate.minusDays(numberOfDays.toLong())
                 selectedDate = null
            }
        )
    }
}