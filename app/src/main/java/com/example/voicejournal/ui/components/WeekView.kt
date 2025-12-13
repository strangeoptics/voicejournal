package com.example.voicejournal.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voicejournal.data.JournalEntry
import java.time.LocalDate // New import
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields // New import
import java.util.*
import kotlin.math.abs


// Data class for an appointment
data class Appointment(
    val id: String,
    val date: LocalDate, // The date of the appointment is the single source of truth
    val startTime: LocalTime,
    val endTime: LocalTime,
    val title: String,
    val description: String? = null,
    val color: Color = Color.Blue, // Example color
    val entry: JournalEntry? = null
)

// New data class to hold layout information for an appointment
data class AppointmentLayoutInfo(
    val appointment: Appointment,
    val column: Int, // The horizontal column this appointment occupies
    val totalColumns: Int, // Total columns needed for this overlap group
    val offsetX: Dp, // Calculated horizontal offset
    val width: Dp // Calculated width
)

// Helper function to calculate layout for overlapping appointments
fun calculateOverlappingAppointmentLayout(
    dailyAppointments: List<Appointment>,
    dayWidth: Dp
): List<AppointmentLayoutInfo> {
    if (dailyAppointments.isEmpty()) return emptyList()

    val sortedAppointments = dailyAppointments.sortedBy { it.startTime }
    val layoutInfoList = mutableListOf<AppointmentLayoutInfo>()

    // Simplified collision detection and column assignment
    val columns = mutableListOf<MutableList<Appointment>>()

    for (appointment in sortedAppointments) {
        var placed = false
        for (i in columns.indices) {
            val column = columns[i]
            // Check if this appointment overlaps with any appointment already in this column
            val overlapsWithColumn = column.any { existingAppt ->
                !(appointment.endTime.isBefore(existingAppt.startTime) || appointment.startTime.isAfter(existingAppt.endTime))
            }
            if (!overlapsWithColumn) {
                // Place in this column
                column.add(appointment)
                placed = true
                break
            }
        }
        if (!placed) {
            // Create a new column for this appointment
            columns.add(mutableListOf(appointment))
        }
    }

    val maxColumnsNeeded = columns.size
    val individualWidth = dayWidth / maxColumnsNeeded.toFloat()

    // Assign final layout info based on assigned columns
    for (appointment in sortedAppointments) {
        var columnIndex = -1
        for (i in columns.indices) {
            if (columns[i].contains(appointment)) {
                columnIndex = i
                break
            }
        }
        if (columnIndex != -1) {
            layoutInfoList.add(
                AppointmentLayoutInfo(
                    appointment = appointment,
                    column = columnIndex,
                    totalColumns = maxColumnsNeeded,
                    offsetX = individualWidth * columnIndex.toFloat(),
                    width = individualWidth
                )
            )
        } else {
            // Fallback for appointments not placed (should not happen with this algorithm if all are processed)
            layoutInfoList.add(
                AppointmentLayoutInfo(
                    appointment = appointment,
                    column = 0,
                    totalColumns = 1,
                    offsetX = 0.dp,
                    width = dayWidth
                )
            )
        }
    }

    return layoutInfoList
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekView(
    modifier: Modifier = Modifier,
    appointments: List<Appointment> = emptyList(),
    hourHeight: Dp = 60.dp, // Height for one hour slot content
    numberOfDays: Int,
    onAppointmentUpdate: (Appointment) -> Unit, // Callback for updates
    currentDate: LocalDate, // New parameter for current week's date
    selectedDate: LocalDate? = null, // Changed from selectedDay: DayOfWeek?
    onDayClick: (LocalDate?) -> Unit, // Changed to pass LocalDate?
    onNextWeek: () -> Unit,
    onPreviousWeek: () -> Unit
) {
    // This logic now ensures the week view always starts on the correct day of the week
    val daysInWeek = (0L until numberOfDays).map {
        if (numberOfDays == 7) {
            val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
            currentDate.with(firstDayOfWeek).plusDays(it)
        } else {
            currentDate.plusDays(it)
        }
    }
    val daysToDisplay = selectedDate?.let { listOf(it) } ?: daysInWeek

    val hoursOfDay = (0..23).map { LocalTime.of(it, 0) }
    val scrollState = rememberScrollState()

    var showEditDialog by remember { mutableStateOf(false) }
    var selectedAppointment by remember { mutableStateOf<Appointment?>(null) }
    var totalDrag by remember { mutableFloatStateOf(0f) }

    val slotHeight = hourHeight + 1.dp // Total height of an hour slot (content + divider)
    val totalDayHeight = slotHeight * 24 // Total height for a full 24-hour day

    Column(modifier = modifier.fillMaxSize()) {
        // Weekday Header
        Row(modifier = Modifier.fillMaxWidth()) {
            // Spacer for hour column alignment
            Spacer(modifier = Modifier.width(50.dp))

            daysToDisplay.forEach { dateInWeek -> // Iterate over LocalDate now
                val dayOfWeek = dateInWeek.dayOfWeek // Get DayOfWeek from LocalDate
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onDayClick(dateInWeek) }, // Pass LocalDate
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Text(
                            text = dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()),
                            fontSize = 12.sp
                        )
                        Text(
                            text = dateInWeek.dayOfMonth.toString(), // Display day of month
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }

        HorizontalDivider(thickness = 1.dp)

        // Main Content Area: Hours + Days (now with synchronized scrolling)
        Row(modifier = Modifier
            .fillMaxSize()
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    totalDrag += delta
                },
                onDragStarted = {
                    totalDrag = 0f
                },
                onDragStopped = {
                    if (abs(totalDrag) > 100f) { // Swipe threshold in pixels
                        if (totalDrag < 0) {
                            onNextWeek()
                        } else {
                            onPreviousWeek()
                        }
                    }
                }
            )
        ) {
            // Hour Column (always visible)
            Column(modifier = Modifier
                .width(50.dp)
                .verticalScroll(scrollState)) {
                hoursOfDay.forEach { hour ->
                    Box(
                        modifier = Modifier
                            .height(hourHeight) // Height of the text content area
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        contentAlignment = androidx.compose.ui.Alignment.TopEnd // Align hour text to top right
                    ) {
                        Text(
                            text = hour.format(DateTimeFormatter.ofPattern("HH:mm")),
                            fontSize = 10.sp
                        )
                    }
                    HorizontalDivider(thickness = 1.dp) // Explicit thickness
                }
            }

            // Appointment Grid (for each day)
            Box(modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)) {
                // This box will contain the entire grid for appointments
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Background grid lines for hours
                    repeat(hoursOfDay.size) { // repeat for each hour
                        Spacer(modifier = Modifier.height(hourHeight)) // Spacer for content area
                        HorizontalDivider(thickness = 1.dp) // Explicit thickness
                    }
                }

                // Overlay appointments for each day
                Row(modifier = Modifier
                    .fillMaxWidth()
                    .height(totalDayHeight)) { // Explicitly set height
                    daysToDisplay.forEach { dateInWeek -> // Use dateInWeek here
                        BoxWithConstraints( // Use BoxWithConstraints to get the width of the day slot
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(Color.LightGray.copy(alpha = 0.1f)) // Visual separation for days
                                .padding(horizontal = 2.dp)
                        ) {
                            val dayWidth = this.maxWidth // Get the width of the current day slot

                            // Filter appointments for the current date ONLY
                            val dailyAppointments = appointments.filter { it.date == dateInWeek }
                            val appointmentLayoutInfos = remember(dailyAppointments, dayWidth) { // Re-calculate when appointments or dayWidth changes
                                calculateOverlappingAppointmentLayout(dailyAppointments, dayWidth)
                            }

                            appointmentLayoutInfos.forEach { layoutInfo ->
                                val appointment = layoutInfo.appointment

                                val startMinuteOfDay = appointment.startTime.toSecondOfDay() / 60.0
                                val endMinuteOfDay = appointment.endTime.toSecondOfDay() / 60.0
                                val durationMinutes = endMinuteOfDay - startMinuteOfDay

                                val topOffset = (startMinuteOfDay / 60.0 * slotHeight.value).dp
                                val appointmentHeight = (durationMinutes / 60.0 * slotHeight.value).dp

                                Box(
                                    modifier = Modifier
                                        .offset(x = layoutInfo.offsetX, y = topOffset)
                                        .width(layoutInfo.width)
                                        .height(appointmentHeight)
                                        .background(appointment.color.copy(alpha = 0.6f))
                                        .padding(4.dp)
                                        .clickable { // Make appointment clickable
                                            selectedAppointment = appointment
                                            showEditDialog = true
                                        }
                                ) {
                                    Text(
                                        text = appointment.title,
                                        fontSize = 12.sp,
                                        color = Color.White
                                    )
                                    appointment.description?.let {
                                        Text(
                                            text = it,
                                            fontSize = 10.sp,
                                            color = Color.White,
                                            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomStart)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Appointment Edit Dialog
    if (showEditDialog && selectedAppointment != null) {
        var editedTitle by remember(selectedAppointment) { mutableStateOf(selectedAppointment!!.title) }
        var editedDescription by remember(selectedAppointment) { mutableStateOf(selectedAppointment!!.description ?: "") }
        var editedStartTime by remember(selectedAppointment) { mutableStateOf(selectedAppointment!!.startTime) }
        var editedEndTime by remember(selectedAppointment) { mutableStateOf(selectedAppointment!!.endTime) }

        var showStartTimePickerDialog by remember { mutableStateOf(false) }
        var showEndTimePickerDialog by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Termin bearbeiten") },
            text = {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = editedTitle,
                        onValueChange = { editedTitle = it },
                        label = { Text("Titel") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editedDescription,
                        onValueChange = { editedDescription = it },
                        label = { Text("Beschreibung") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        TextButton(onClick = { showStartTimePickerDialog = true }) {
                            Text("Startzeit: ${editedStartTime.format(DateTimeFormatter.ofPattern("HH:mm"))}")
                        }
                        TextButton(onClick = { showEndTimePickerDialog = true }) {
                            Text("Endzeit: ${editedEndTime.format(DateTimeFormatter.ofPattern("HH:mm"))}")
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val updatedAppointment = selectedAppointment!!.copy(
                        title = editedTitle,
                        description = editedDescription.ifEmpty { null }, // Set to null if empty
                        startTime = editedStartTime,
                        endTime = editedEndTime,
                        date = selectedAppointment!!.date // Copy the original date
                    )
                    onAppointmentUpdate(updatedAppointment)
                    showEditDialog = false
                }) {
                    Text("Speichern")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )

        // Start Time Picker Dialog
        if (showStartTimePickerDialog) {
            val timePickerState = rememberTimePickerState(initialHour = editedStartTime.hour, initialMinute = editedStartTime.minute)
            TimePickerDialog(
                onDismissRequest = { showStartTimePickerDialog = false },
                title = { Text("Startzeit auswählen") },
                confirmButton = {
                    TextButton(onClick = {
                        editedStartTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                        showStartTimePickerDialog = false
                    }) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showStartTimePickerDialog = false }) {
                        Text("Abbrechen")
                    }
                }
            ) {
                TimePicker(state = timePickerState)
            }
        }

        // End Time Picker Dialog
        if (showEndTimePickerDialog) {
            val timePickerState = rememberTimePickerState(initialHour = editedEndTime.hour, initialMinute = editedEndTime.minute)
            TimePickerDialog(
                onDismissRequest = { showEndTimePickerDialog = false },
                title = { Text("Endzeit auswählen") },
                confirmButton = {
                    TextButton(onClick = {
                        editedEndTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                        showEndTimePickerDialog = false
                    }) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEndTimePickerDialog = false }) {
                        Text("Abbrechen")
                    }
                }
            ) {
                TimePicker(state = timePickerState)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 700, heightDp = 500)
@Composable
fun WeekViewWithAppointmentsPreview() {
    val previewDate = LocalDate.now()
    val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

    // Calculate dates for the week based on previewDate
    val mondayDate = previewDate.with(firstDayOfWeek)
    val tuesdayDate = mondayDate.plusDays(1)
    val wednesdayDate = mondayDate.plusDays(2)
    val thursdayDate = mondayDate.plusDays(3)
    val fridayDate = mondayDate.plusDays(4)
    val saturdayDate = mondayDate.plusDays(5)

    val sampleAppointments = listOf(
        Appointment(
            id = "1",
            date = mondayDate,
            startTime = LocalTime.of(9, 30),
            endTime = LocalTime.of(10, 30),
            title = "Meeting with John",
            description = "Project discussion",
            color = Color(0xFFE57373) // Light Red
        ),
        Appointment(
            id = "2",
            date = tuesdayDate,
            startTime = LocalTime.of(14, 0),
            endTime = LocalTime.of(15, 0),
            title = "Team Standup",
            color = Color(0xFF81C784) // Light Green
        ),
        Appointment(
            id = "3",
            date = wednesdayDate,
            startTime = LocalTime.of(11, 0),
            endTime = LocalTime.of(12, 30),
            title = "Client Call",
            description = "Review new features",
            color = Color(0xFF64B5F6) // Light Blue
        ),
        Appointment(
            id = "4",
            date = mondayDate.plusDays(3),
            startTime = LocalTime.of(11, 0),
            endTime = LocalTime.of(11, 45),
            title = "Quick Sync",
            color = Color(0xFFFFF176) // Light Yellow
        ),
        Appointment(
            id = "5",
            date = thursdayDate,
            startTime = LocalTime.of(16, 0),
            endTime = LocalTime.of(17, 0),
            title = "Gym Session",
            color = Color(0xFFFFB74D) // Light Orange
        ),
        Appointment(
            id = "6",
            date = fridayDate,
            startTime = LocalTime.of(9, 0),
            endTime = LocalTime.of(17, 0),
            title = "All Day Workshop",
            color = Color(0xFFBA68C8) // Light Purple
        )
    )
    WeekView(
        appointments = sampleAppointments,
        onAppointmentUpdate = {},
        onDayClick = {},
        currentDate = LocalDate.now().with(WeekFields.of(Locale.getDefault()).firstDayOfWeek),
        selectedDate = null,
        onNextWeek = {},
        onPreviousWeek = {},
        numberOfDays = 7
    ) // Updated preview
}
