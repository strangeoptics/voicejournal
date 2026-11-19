package com.example.voicejournal

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.voicejournal.ui.components.Appointment
import com.example.voicejournal.ui.components.DayEntriesPreviewOverlay
import com.example.voicejournal.ui.components.DayEntriesPreviewState
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

    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val currentMonth by viewModel.currentMonth.collectAsStateWithLifecycle()
    val appointments by viewModel.appointments.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val visibleDates by viewModel.visibleDates.collectAsStateWithLifecycle()

    var previewEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var previewPivotX by rememberSaveable { mutableFloatStateOf(0.5f) }
    var previewPivotY by rememberSaveable { mutableFloatStateOf(0.5f) }
    var focusOverrideEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }

    if (selectedDate == null && focusOverrideEpochDay != null) {
        focusOverrideEpochDay = null
    }

    val previewState = remember(previewEpochDay, previewPivotX, previewPivotY) {
        previewEpochDay?.let { epochDay ->
            DayEntriesPreviewState(
                date = LocalDate.ofEpochDay(epochDay),
                pivotFractionX = previewPivotX,
                pivotFractionY = previewPivotY,
            )
        }
    }

    val focusOverrideDate = remember(focusOverrideEpochDay) {
        focusOverrideEpochDay?.let(LocalDate::ofEpochDay)
    }

    BackHandler(enabled = (previewState != null) || (selectedDate != null)) {
        if (previewEpochDay != null) {
            previewEpochDay = null
        } else {
            viewModel.selectDate(null)
        }
    }

    CalendarScreenContent(
        viewMode = viewMode,
        currentMonth = currentMonth,
        visibleDates = visibleDates,
        appointments = appointments,
        selectedDate = selectedDate,
        focusOverrideDate = focusOverrideDate,
        previewState = previewState,
        onBackClick = {
            when {
                previewEpochDay != null -> previewEpochDay = null
                selectedDate != null -> viewModel.selectDate(null)
                else -> onFinish()
            }
        },
        onJumpToToday = {
            previewEpochDay = null
            viewModel.jumpToToday()
        },
        onSelectViewMode = { mode ->
            previewEpochDay = null
            viewModel.setViewMode(mode)
        },
        onDayClick = viewModel::selectDate,
        onDayLongClick = { state ->
            previewPivotX = state.pivotFractionX
            previewPivotY = state.pivotFractionY
            previewEpochDay = state.date.toEpochDay()
        },
        onDismissPreview = { previewEpochDay = null },
        onOpenDayFromPreview = { date ->
            previewEpochDay = null
            focusOverrideEpochDay = date.toEpochDay()
            if (selectedDate != date) {
                viewModel.selectDate(date)
            }
        },
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
    focusOverrideDate: LocalDate? = null,
    previewState: DayEntriesPreviewState? = null,
    today: LocalDate = LocalDate.now(),
    onBackClick: () -> Unit = {},
    onJumpToToday: () -> Unit = {},
    onSelectViewMode: (CalendarViewMode) -> Unit = {},
    onDayClick: (LocalDate) -> Unit = {},
    onDayLongClick: (DayEntriesPreviewState) -> Unit = {},
    onDismissPreview: () -> Unit = {},
    onOpenDayFromPreview: (LocalDate) -> Unit = {},
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

    val effectiveFocusDate = if (viewMode == CalendarViewMode.MONTH) {
        selectedDate
    } else {
        focusOverrideDate
    }

    val isAlreadyViewingPreviewSingleDay = (!isShowingMonthGrid) &&
        (visibleDates.size == 1) &&
        (visibleDates.firstOrNull() == previewState?.date)

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
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
                    onDayLongClick = onDayLongClick,
                    onMonthChanged = onMonthChanged,
                )
            } else {
                WeekView(
                    visibleDates = visibleDates,
                    modifier = Modifier.padding(innerPadding),
                    appointments = appointments,
                    selectedDate = selectedDate,
                    initialFocusDate = effectiveFocusDate,
                    today = today,
                    onDayClick = onDayClick,
                    onDayLongClick = onDayLongClick,
                    onAppointmentClick = onAppointmentClick,
                    onPeriodStartChanged = onPeriodStartChanged,
                    onNextPeriod = onNextPeriod,
                    onPreviousPeriod = onPreviousPeriod,
                )
            }
        }

        DayEntriesPreviewOverlay(
            previewState = previewState,
            appointments = appointments,
            showOpenDayViewButton = !isAlreadyViewingPreviewSingleDay,
            onDismiss = onDismissPreview,
            onOpenDayView = onOpenDayFromPreview,
            onAppointmentClick = onAppointmentClick,
        )
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

@Preview(name = "Calendar Long-Tap Overlay (Light)", locale = "de-rDE", widthDp = 412, heightDp = 820)
@Composable
private fun CalendarScreenLongTapOverlayPreview() {
    val month = YearMonth.of(2026, 11)
    val today = LocalDate.of(2026, 11, 19)
    val appointments = listOf(
        Appointment(
            id = "1",
            startDateTime = today.atTime(9, 0),
            endDateTime = today.atTime(10, 0),
            title = "Morgennotiz",
            fullText = "Morgennotiz: Ruhiger Start in den Tag und Prioritäten für das Projekt festgelegt.",
            color = Color(0xFFE57373),
        ),
        Appointment(
            id = "2",
            startDateTime = today.atTime(14, 0),
            endDateTime = today.atTime(15, 30),
            title = "Ideensammlung",
            fullText = "Ideensammlung für die Kalender-Monatsansicht inklusive Long-Tap-Vorschau im Vordergrund.",
            color = Color(0xFF64B5F6),
            hasExplicitEndTime = true,
        ),
    )
    VoicejournalTheme(darkTheme = false, dynamicColor = false) {
        CalendarScreenContent(
            viewMode = CalendarViewMode.MONTH,
            currentMonth = month,
            visibleDates = (1..month.lengthOfMonth()).map { month.atDay(it) },
            appointments = appointments,
            previewState = DayEntriesPreviewState(date = today, pivotFractionX = 0.5f, pivotFractionY = 0.5f),
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
