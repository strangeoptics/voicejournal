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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.voicejournal.ui.components.WeekView
import com.example.voicejournal.ui.theme.VoicejournalTheme
import com.example.voicejournal.util.DateFormatters

class CalendarActivity : ComponentActivity() {
    private val viewModel: CalendarViewModel by viewModels { CalendarViewModelFactory(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VoicejournalTheme {
                CalendarScreen(
                    viewModel = viewModel,
                    onFinish = { finish() },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(viewModel: CalendarViewModel, onFinish: () -> Unit) {
    val context = LocalContext.current
    val appointments by viewModel.appointments.collectAsState()
    val numberOfDays by viewModel.numberOfDays.collectAsState()
    val currentDate by viewModel.currentDate.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val visibleDates by viewModel.visibleDates.collectAsState()
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
                            },
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (selectedDate != null) {
                                viewModel.selectDate(null)
                            } else {
                                onFinish()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back),
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.calendar_more_options),
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.calendar_today)) },
                                onClick = {
                                    viewModel.jumpToToday()
                                    showMenu = false
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.calendar_day_view)) },
                                onClick = {
                                    viewModel.setNumberOfDays(1)
                                    showMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.calendar_three_day_view)) },
                                onClick = {
                                    viewModel.setNumberOfDays(3)
                                    showMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.calendar_week_view)) },
                                onClick = {
                                    viewModel.setNumberOfDays(7)
                                    showMenu = false
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        WeekView(
            visibleDates = visibleDates,
            modifier = Modifier.padding(innerPadding),
            appointments = appointments,
            selectedDate = selectedDate,
            onDayClick = viewModel::selectDate,
            onAppointmentClick = { appointment ->
                appointment.entryId?.let { entryId ->
                    context.startActivity(EditEntryActivity.newIntent(context, entryId))
                }
            },
            onNextPeriod = viewModel::navigateNext,
            onPreviousPeriod = viewModel::navigatePrevious,
        )
    }
}
