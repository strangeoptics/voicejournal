package com.example.voicejournal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.example.voicejournal.ui.components.WeekView
import com.example.voicejournal.ui.theme.VoicejournalTheme
import com.example.voicejournal.util.DateFormatters

@OptIn(ExperimentalMaterial3Api::class)
class CalendarActivity : ComponentActivity() {
    private val viewModel: CalendarViewModel by viewModels { CalendarViewModelFactory(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VoicejournalTheme {
                CalendarScreen(
                    viewModel = viewModel,
                    onFinish = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(viewModel: CalendarViewModel, onFinish: () -> Unit) {
    val appointments by viewModel.appointments.collectAsState()
    val numberOfDays by viewModel.numberOfDays.collectAsState()
    val currentDate by viewModel.currentDate.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    var showMenu by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = selectedDate != null) {
        viewModel.selectDate(null)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = selectedDate?.format(DateFormatters.CALENDAR_FULL_DATE_GERMAN)
                            ?: when (numberOfDays) {
                                1 -> currentDate.format(DateFormatters.CALENDAR_FULL_DATE_GERMAN)
                                3, 7 -> currentDate.format(DateFormatters.CALENDAR_MONTH_YEAR_GERMAN)
                                else -> ""
                            }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedDate != null) {
                            viewModel.selectDate(null)
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
                                    viewModel.setNumberOfDays(1)
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("3-Tages-Ansicht") },
                                onClick = {
                                    viewModel.setNumberOfDays(3)
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Wochenansicht") },
                                onClick = {
                                    viewModel.setNumberOfDays(7)
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
            currentDate = currentDate,
            selectedDate = selectedDate,
            onDayClick = { date ->
                viewModel.selectDate(date)
            },
            onNextWeek = viewModel::navigateNext,
            onPreviousWeek = viewModel::navigatePrevious
        )
    }
}
