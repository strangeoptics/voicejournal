package com.example.voicejournal

import android.content.res.Configuration
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.voicejournal.ui.components.Appointment
import com.example.voicejournal.ui.components.MonthView
import com.example.voicejournal.ui.components.WeekView
import com.example.voicejournal.ui.theme.VoicejournalTheme
import com.example.voicejournal.util.DateFormatters
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

class CalendarActivity : ComponentActivity() {
    private val viewModel: CalendarViewModel by viewModels { CalendarViewModelFactory(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VoicejournalTheme {
                CalendarScreen(viewModel = viewModel) {
                    finish()
                }
            }
        }
    }
}

@Composable
fun CalendarScreen(viewModel: CalendarViewModel, onFinish: () -> Unit) {
    val context = LocalContext.current

    val viewMode by viewModel.viewMode.collectAsState()
    val currentMonth by viewModel.currentMonth.collectAsState()
    val appointments by viewModel.appointments.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val visibleDates by viewModel.visibleDates.collectAsState()

    BackHandler(enabled = selectedDate != null) {
        viewModel.selectDate(null)
    }

    CalendarScreenContent(
        viewMode = viewMode,
        currentMonth = currentMonth,
        visibleDates = visibleDates,
        appointments = appointments,
        selectedDate = selectedDate,
        onBackClick = {
            if (selectedDate != null) {
                viewModel.selectDate(null)
            } else {
                onFinish()
            }
        },
        onJumpToToday = viewModel::jumpToToday,
        onSelectViewMode = viewModel::setViewMode,
        onDayClick = viewModel::selectDate,
        onAppointmentClick = { appointment ->
            appointment.entryId?.let { entryId ->
                context.startActivity(EditEntryActivity.newIntent(context, entryId))
            }
        },
        onMonthChanged = viewModel::navigateToMonth,
        onPeriodStartChanged = viewModel::navigateToPeriodStart,
        onNextPeriod = viewModel::navigateNext,
        onPreviousPeriod = viewModel::navigatePrevious,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreenContent(
    viewMode: CalendarViewMode,
    currentMonth: YearMonth,
    visibleDates: List<LocalDate>,
    modifier: Modifier = Modifier,
    appointments: List<Appointment> = emptyList(),
    selectedDate: LocalDate? = null,
    today: LocalDate = LocalDate.now(),
    onBackClick: () -> Unit = {},
    onJumpToToday: () -> Unit = {},
    onSelectViewMode: (CalendarViewMode) -> Unit = {},
    onDayClick: (LocalDate) -> Unit = {},
    onAppointmentClick: (Appointment) -> Unit = {},
    onMonthChanged: (YearMonth) -> Unit = {},
    onPeriodStartChanged: (LocalDate) -> Unit = {},
    onNextPeriod: () -> Unit = {},
    onPreviousPeriod: () -> Unit = {},
) {
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0] ?: Locale.getDefault()
    var showMenu by rememberSaveable { mutableStateOf(value = false) }

    val isShowingMonthGrid = (viewMode == CalendarViewMode.MONTH) && (selectedDate == null)

    val topBarTitle = remember(isShowingMonthGrid, currentMonth, visibleDates, locale) {
        if (isShowingMonthGrid) {
            DateFormatters.formatCalendarMonthTitle(currentMonth, locale)
        } else {
            DateFormatters.formatCalendarPeriodTitle(visibleDates, locale)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(text = topBarTitle)
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
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
                                    onJumpToToday()
                                    showMenu = false
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.calendar_day_view)) },
                                onClick = {
                                    onSelectViewMode(CalendarViewMode.DAY)
                                    showMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.calendar_three_day_view)) },
                                onClick = {
                                    onSelectViewMode(CalendarViewMode.THREE_DAY)
                                    showMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.calendar_week_view)) },
                                onClick = {
                                    onSelectViewMode(CalendarViewMode.WEEK)
                                    showMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.calendar_month_view)) },
                                onClick = {
                                    onSelectViewMode(CalendarViewMode.MONTH)
                                    showMenu = false
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (isShowingMonthGrid) {
            MonthView(
                currentMonth = currentMonth,
                modifier = Modifier.padding(innerPadding),
                appointments = appointments,
                today = today,
                onDayClick = onDayClick,
                onMonthChanged = onMonthChanged,
            )
        } else {
            WeekView(
                visibleDates = visibleDates,
                modifier = Modifier.padding(innerPadding),
                appointments = appointments,
                selectedDate = selectedDate,
                initialFocusDate = if (viewMode == CalendarViewMode.MONTH) selectedDate else null,
                today = today,
                onDayClick = onDayClick,
                onAppointmentClick = onAppointmentClick,
                onPeriodStartChanged = onPeriodStartChanged,
                onNextPeriod = onNextPeriod,
                onPreviousPeriod = onPreviousPeriod,
            )
        }
    }
}

@Preview(name = "Calendar Month Screen (Light)", locale = "de-rDE", widthDp = 412, heightDp = 820)
@Composable
private fun CalendarScreenMonthLightPreview() {
    val month = YearMonth.of(2026, 11)
    val today = LocalDate.of(2026, 11, 19)
    val appointments = listOf(
        Appointment("1", today.atTime(9, 0), today.atTime(10, 0), "Morgennotiz", Color(0xFFE57373)),
        Appointment("2", today.atTime(14, 0), today.atTime(15, 0), "Ideensammlung", Color(0xFF64B5F6)),
        Appointment("3", LocalDate.of(2026, 11, 24).atTime(18, 0), LocalDate.of(2026, 11, 24).atTime(19, 0), "Sport", Color(0xFF81C784)),
    )
    VoicejournalTheme(darkTheme = false, dynamicColor = false) {
        CalendarScreenContent(
            viewMode = CalendarViewMode.MONTH,
            currentMonth = month,
            visibleDates = (1..month.lengthOfMonth()).map { month.atDay(it) },
            appointments = appointments,
            today = today,
        )
    }
}

@Preview(
    name = "Calendar Month Screen (Dark)",
    locale = "de-rDE",
    widthDp = 412,
    heightDp = 820,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun CalendarScreenMonthDarkPreview() {
    val month = YearMonth.of(2026, 11)
    val today = LocalDate.of(2026, 11, 19)
    val appointments = listOf(
        Appointment("1", today.atTime(9, 0), today.atTime(10, 0), "Morgennotiz", Color(0xFFE57373)),
        Appointment("2", today.atTime(14, 0), today.atTime(15, 0), "Ideensammlung", Color(0xFF64B5F6)),
    )
    VoicejournalTheme(darkTheme = true, dynamicColor = false) {
        CalendarScreenContent(
            viewMode = CalendarViewMode.MONTH,
            currentMonth = month,
            visibleDates = (1..month.lengthOfMonth()).map { month.atDay(it) },
            appointments = appointments,
            today = today,
        )
    }
}
