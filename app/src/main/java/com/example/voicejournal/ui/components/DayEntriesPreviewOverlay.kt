package com.example.voicejournal.ui.components

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.voicejournal.R
import com.example.voicejournal.ui.theme.VoicejournalTheme
import com.example.voicejournal.util.DateFormatters
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

private val OVERLAY_CARD_SHAPE = RoundedCornerShape(24.dp)
private val PREVIEW_ENTRY_SHAPE = RoundedCornerShape(10.dp)
private val DARK_PREVIEW_TEXT = Color(0xFF1C1B1F)
private val LIGHT_PREVIEW_TEXT = Color(0xFFF5EFF7)

@Immutable
data class DayEntriesPreviewState(
    val date: LocalDate,
    val pivotFractionX: Float = 0.5f,
    val pivotFractionY: Float = 0.5f,
)

fun filterAndSortDayAppointments(
    appointments: List<Appointment>,
    date: LocalDate,
): List<Appointment> = appointments
    .asSequence()
    .filter { !it.startDate.isAfter(date) && !it.endDate.isBefore(date) }
    .sortedWith(
        compareBy<Appointment> { it.startDateTime }
            .thenBy { it.endDateTime }
            .thenBy { it.id },
    )
    .toList()

fun formatAppointmentPreviewTime(appointment: Appointment): String {
    if (!appointment.hasExplicitEndTime) {
        return appointment.startTime.format(DateFormatters.TIME_HM)
    }
    return if (appointment.startDate == appointment.endDate) {
        "${appointment.startTime.format(DateFormatters.TIME_HM)} – ${appointment.endTime.format(DateFormatters.TIME_HM)}"
    } else {
        "${appointment.startDateTime.format(DateFormatters.ENTRY_DATE_TIME)} – ${appointment.endDateTime.format(DateFormatters.ENTRY_DATE_TIME)}"
    }
}

fun computePreviewMaxLines(totalEntriesCount: Int): Int = when {
    totalEntriesCount <= 1 -> 6
    totalEntriesCount == 2 -> 4
    else -> 3
}

@Composable
fun DayEntriesPreviewOverlay(
    previewState: DayEntriesPreviewState?,
    appointments: List<Appointment>,
    modifier: Modifier = Modifier,
    showOpenDayViewButton: Boolean = true,
    onDismiss: () -> Unit = {},
    onOpenDayView: (LocalDate) -> Unit = {},
    onAppointmentClick: (Appointment) -> Unit = {},
) {
    var lastActiveState by remember { mutableStateOf(previewState) }
    if (previewState != null) {
        lastActiveState = previewState
    }

    val activeState = previewState ?: lastActiveState
    val dayAppointments = remember(appointments, activeState?.date) {
        activeState?.date?.let { date -> filterAndSortDayAppointments(appointments, date) }.orEmpty()
    }

    LaunchedEffect(previewState, dayAppointments.isEmpty()) {
        if (previewState != null && dayAppointments.isEmpty()) {
            onDismiss()
        }
    }

    val transformOrigin = remember(activeState?.pivotFractionX, activeState?.pivotFractionY) {
        TransformOrigin(
            pivotFractionX = (activeState?.pivotFractionX ?: 0.5f).coerceIn(0.08f, 0.92f),
            pivotFractionY = (activeState?.pivotFractionY ?: 0.5f).coerceIn(0.08f, 0.92f),
        )
    }

    val isVisible = (previewState != null) && dayAppointments.isNotEmpty()

    AnimatedVisibility(
        visible = isVisible,
        modifier = modifier.fillMaxSize(),
        enter = fadeIn(animationSpec = tween(durationMillis = 160)),
        exit = fadeOut(animationSpec = tween(durationMillis = 130)),
    ) {
        val currentState = activeState ?: return@AnimatedVisibility
        val dismissInteractionSource = remember { MutableInteractionSource() }
        val cardInteractionSource = remember { MutableInteractionSource() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.44f))
                .clickable(
                    interactionSource = dismissInteractionSource,
                    indication = null,
                    onClick = onDismiss,
                )
                .padding(horizontal = 20.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            DayEntriesPreviewCard(
                date = currentState.date,
                entries = dayAppointments,
                showOpenDayViewButton = showOpenDayViewButton,
                onDismiss = onDismiss,
                onOpenDayView = { onOpenDayView(currentState.date) },
                onAppointmentClick = onAppointmentClick,
                modifier = Modifier
                    .animateEnterExit(
                        enter = scaleIn(
                            animationSpec = spring(
                                dampingRatio = 0.82f,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                            initialScale = 0.72f,
                            transformOrigin = transformOrigin,
                        ),
                        exit = scaleOut(
                            animationSpec = tween(durationMillis = 130),
                            targetScale = 0.84f,
                            transformOrigin = transformOrigin,
                        ),
                    )
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = cardInteractionSource,
                        indication = null,
                        onClick = {},
                    ),
            )
        }
    }
}

@Composable
fun DayEntriesPreviewCard(
    date: LocalDate,
    entries: List<Appointment>,
    modifier: Modifier = Modifier,
    showOpenDayViewButton: Boolean = true,
    onDismiss: () -> Unit = {},
    onOpenDayView: () -> Unit = {},
    onAppointmentClick: (Appointment) -> Unit = {},
) {
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0] ?: Locale.getDefault()
    val colorScheme = MaterialTheme.colorScheme
    val formattedDate = remember(date, locale) {
        date.format(DateFormatters.calendarFullDate(locale))
    }
    val subtitleText = if (entries.size == 1) {
        stringResource(R.string.calendar_day_preview_entry_one)
    } else {
        stringResource(R.string.calendar_day_preview_entry_many, entries.size)
    }

    Surface(
        modifier = modifier,
        shape = OVERLAY_CARD_SHAPE,
        color = colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier.padding(top = 18.dp, start = 16.dp, end = 16.dp, bottom = 8.dp),
        ) {
            Text(
                text = formattedDate,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = colorScheme.outlineVariant)

            val maxLines = remember(entries.size) { computePreviewMaxLines(entries.size) }

            LazyColumn(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = entries,
                    key = { it.id },
                ) { appointment ->
                    DayPreviewEntryItem(
                        appointment = appointment,
                        surfaceColor = colorScheme.surface,
                        outlineColor = colorScheme.outline,
                        maxLines = maxLines,
                        onClick = { onAppointmentClick(appointment) },
                    )
                }
            }

            HorizontalDivider(color = colorScheme.outlineVariant)
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.calendar_close))
                }
                if (showOpenDayViewButton) {
                    TextButton(onClick = onOpenDayView) {
                        Text(text = stringResource(R.string.calendar_open_day_view))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayPreviewEntryItem(
    appointment: Appointment,
    surfaceColor: Color,
    outlineColor: Color,
    maxLines: Int,
    onClick: () -> Unit,
) {
    val isLightSurface = surfaceColor.luminance() > 0.5f
    val tintAlpha = if (isLightSurface) 0.18f else 0.26f
    val containerColor = appointment.color.copy(alpha = tintAlpha).compositeOver(surfaceColor)
    val primaryTextColor = if (containerColor.luminance() > 0.4f) DARK_PREVIEW_TEXT else LIGHT_PREVIEW_TEXT
    val secondaryTextColor = primaryTextColor.copy(alpha = 0.76f)
    val lowContrastAccent = abs(appointment.color.luminance() - surfaceColor.luminance()) < 0.12f
    val accentColor = if (lowContrastAccent) outlineColor else appointment.color

    val timeLabel = remember(appointment) { formatAppointmentPreviewTime(appointment) }
    val bodyText = remember(appointment) {
        appointment.fullText.ifBlank { appointment.title }.ifBlank { timeLabel }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(PREVIEW_ENTRY_SHAPE)
            .background(containerColor)
            .border(
                width = 0.5.dp,
                color = accentColor.copy(alpha = if (lowContrastAccent) 0.7f else 0.45f),
                shape = PREVIEW_ENTRY_SHAPE,
            )
            .drawBehind {
                val stripeWidth = 4.dp.toPx()
                drawRect(
                    color = accentColor,
                    topLeft = Offset.Zero,
                    size = Size(stripeWidth, size.height),
                )
            }
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = timeLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = secondaryTextColor,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = bodyText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = primaryTextColor,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SampleDayEntriesPreviewOverlayContent() {
    val today = LocalDate.of(2026, 11, 19)
    val sampleEntries = listOf(
        Appointment(
            id = "1",
            startDateTime = today.atTime(8, 30),
            endDateTime = today.atTime(9, 15),
            title = "Morgenreflexion",
            fullText = "Morgenreflexion: Ruhiger Start mit Kaffee auf dem Balkon und Planung der wichtigsten Aufgaben für das gesamte Quartal. Gedanken zur Strukturierung der anstehenden Releases gesammelt, Prioritäten für die Team-Absprachen notiert und den ersten Entwurf für die Roadmap fertiggestellt.",
            color = Color(0xFFE57373),
            hasExplicitEndTime = true,
        ),
        Appointment(
            id = "2",
            startDateTime = today.atTime(12, 15),
            endDateTime = today.atTime(13, 15),
            title = "Mittagspause Notiz",
            fullText = "Mittagspause: Spaziergang am Fluss und Ideen für das nächste Buchprojekt festgehalten.",
            color = Color(0xFFFFF176),
            hasExplicitEndTime = false,
        ),
        Appointment(
            id = "3",
            startDateTime = today.atTime(19, 0),
            endDateTime = today.atTime(20, 30),
            title = "Sport & Training",
            fullText = "Sport & Krafttraining im Studio – neuer Bestwert beim Rudern.",
            color = Color(0xFF4DB6AC),
            hasExplicitEndTime = true,
        ),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        DayEntriesPreviewOverlay(
            previewState = DayEntriesPreviewState(date = today, pivotFractionX = 0.6f, pivotFractionY = 0.5f),
            appointments = sampleEntries,
        )
    }
}

@Preview(name = "Day Entries Preview (Light)", locale = "de-rDE", widthDp = 412, heightDp = 760)
@Composable
private fun DayEntriesPreviewLightPreview() {
    VoicejournalTheme(darkTheme = false, dynamicColor = false) {
        SampleDayEntriesPreviewOverlayContent()
    }
}

@Preview(
    name = "Day Entries Preview (Dark)",
    locale = "de-rDE",
    widthDp = 412,
    heightDp = 760,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun DayEntriesPreviewDarkPreview() {
    VoicejournalTheme(darkTheme = true, dynamicColor = false) {
        SampleDayEntriesPreviewOverlayContent()
    }
}
