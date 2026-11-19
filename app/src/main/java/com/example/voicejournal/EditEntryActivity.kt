package com.example.voicejournal

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.voicejournal.data.Category
import com.example.voicejournal.di.Injector
import com.example.voicejournal.ui.components.PublicIcon
import com.example.voicejournal.ui.components.TimeZonePickerDialog
import com.example.voicejournal.ui.theme.VoicejournalTheme
import com.example.voicejournal.util.DateFormatters
import com.example.voicejournal.util.SpeechRecognitionManager
import com.example.voicejournal.util.TimeZoneUtils
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

class EditEntryActivity : ComponentActivity() {

    private val entryId: UUID? by lazy {
        intent.getStringExtra(EXTRA_ENTRY_ID)?.let { UUID.fromString(it) }
    }

    private val initialCategoryId: Int? by lazy {
        intent.takeIf { it.hasExtra(EXTRA_CATEGORY_ID) }
            ?.getIntExtra(EXTRA_CATEGORY_ID, -1)
            ?.takeIf { it > 0 }
    }

    private val viewModel: EditEntryViewModel by viewModels {
        EditEntryViewModelFactory(
            Injector.provideJournalRepository(this),
            Injector.provideSettingsRepository(this),
            entryId,
            initialCategoryId
        )
    }

    private lateinit var speechRecognitionManager: SpeechRecognitionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        speechRecognitionManager = SpeechRecognitionManager(
            context = applicationContext,
            onTextRecognized = viewModel::insertDictatedText,
            scope = lifecycleScope,
            onError = { error ->
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        Toast.makeText(
                            this,
                            SpeechRecognitionManager.errorMessageForCode(error),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )

        setContent {
            VoicejournalTheme {
                val entry by viewModel.entry.collectAsStateWithLifecycle()
                val allCategories by viewModel.allCategories.collectAsStateWithLifecycle()

                entry?.let {
                    EditEntryScreen(
                        isNewEntry = entryId == null,
                        allCategories = allCategories,
                        onNavigateUp = { finish() },
                        onSaveCompleted = { createdId ->
                            if (createdId != null) {
                                setResult(
                                    RESULT_OK,
                                    Intent().putExtra(MainActivity.EXTRA_ENTRY_ID, createdId.toString())
                                )
                            } else {
                                setResult(RESULT_OK)
                            }
                            finish()
                        },
                        speechRecognitionManager = speechRecognitionManager,
                        viewModel = viewModel
                    )
                } ?: run {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (::speechRecognitionManager.isInitialized) {
            speechRecognitionManager.stopListening()
        }
    }

    override fun onDestroy() {
        if (::speechRecognitionManager.isInitialized) {
            speechRecognitionManager.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_ENTRY_ID = "extra_entry_id"
        private const val EXTRA_CATEGORY_ID = "extra_category_id"

        fun newIntent(context: Context, entryId: UUID): Intent {
            return Intent(context, EditEntryActivity::class.java).apply {
                putExtra(EXTRA_ENTRY_ID, entryId.toString())
            }
        }

        fun newIntentForNewEntry(context: Context, categoryId: Int? = null): Intent {
            return Intent(context, EditEntryActivity::class.java).apply {
                if (categoryId != null && categoryId > 0) {
                    putExtra(EXTRA_CATEGORY_ID, categoryId)
                }
            }
        }
    }
}

private enum class ActiveEntryPicker {
    START_DATE,
    START_TIME,
    STOP_DATE,
    STOP_TIME,
    TIME_ZONE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditEntryScreen(
    isNewEntry: Boolean,
    allCategories: List<Category>,
    onNavigateUp: () -> Unit,
    onSaveCompleted: (UUID?) -> Unit = { onNavigateUp() },
    speechRecognitionManager: SpeechRecognitionManager,
    viewModel: EditEntryViewModel
) {
    val entryContent by viewModel.entryContent.collectAsStateWithLifecycle()
    val hasImage by viewModel.hasImage.collectAsStateWithLifecycle()
    val startDateTimeMillis by viewModel.startDateTime.collectAsStateWithLifecycle()
    val stopDateTimeMillis by viewModel.stopDateTime.collectAsStateWithLifecycle()
    val zoneId by viewModel.zoneId.collectAsStateWithLifecycle()
    val selectedCategoryIds by viewModel.selectedCategoryIds.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val saveCompleted by viewModel.saveCompleted.collectAsStateWithLifecycle()
    val createdEntryId by viewModel.createdEntryId.collectAsStateWithLifecycle()
    val saveError by viewModel.saveError.collectAsStateWithLifecycle()

    val activeZone = remember(zoneId) {
        TimeZoneUtils.resolveZoneId(zoneId)
    }

    val startDateTime = remember(startDateTimeMillis, activeZone) {
        LocalDateTime.ofInstant(Instant.ofEpochMilli(startDateTimeMillis), activeZone)
    }

    val stopDateTime = remember(stopDateTimeMillis, activeZone) {
        stopDateTimeMillis?.let {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(it), activeZone)
        }
    }

    var activePicker by rememberSaveable { mutableStateOf<ActiveEntryPicker?>(null) }

    EntryPickerDialogs(
        activePicker = activePicker,
        startDateTime = startDateTime,
        stopDateTime = stopDateTime,
        activeZone = activeZone,
        zoneId = zoneId,
        referenceMillis = startDateTimeMillis,
        onUpdateStartMillis = viewModel::updateStartDateTime,
        onUpdateStopMillis = viewModel::updateStopDateTime,
        onUpdateZoneId = viewModel::updateZoneId,
        onDismiss = { activePicker = null }
    )

    val context = LocalContext.current
    val isRecording by speechRecognitionManager.isRecording.collectAsStateWithLifecycle()

    LaunchedEffect(saveCompleted) {
        if (saveCompleted) {
            onSaveCompleted(createdEntryId)
        }
    }

    LaunchedEffect(saveError) {
        saveError?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.clearSaveError()
        }
    }

    val speechService by viewModel.speechService.collectAsStateWithLifecycle()
    val googleCloudApiKey by viewModel.googleCloudApiKey.collectAsStateWithLifecycle()
    val maxRecordingTime by viewModel.maxRecordingTime.collectAsStateWithLifecycle()
    val silenceThreshold by viewModel.silenceThreshold.collectAsStateWithLifecycle()
    val silenceTimeRequired by viewModel.silenceTimeRequired.collectAsStateWithLifecycle()

    val startDictation = {
        speechRecognitionManager.startListening(
            service = speechService,
            apiKey = googleCloudApiKey,
            maxRecordingTimeSeconds = maxRecordingTime,
            silenceThreshold = silenceThreshold,
            silenceTimeRequired = silenceTimeRequired
        )
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                startDictation()
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNewEntry) "New Entry" else "Edit Entry") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp, enabled = !isSaving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (isRecording) {
                            speechRecognitionManager.stopListening()
                        } else {
                            when (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)) {
                                PackageManager.PERMISSION_GRANTED -> startDictation()
                                else -> recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    }) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (isRecording) "Stop Recording" else "Start Recording"
                        )
                    }
                    IconButton(
                        onClick = { viewModel.saveEntry() },
                        enabled = !isSaving
                    ) {
                        Icon(Icons.Default.Done, contentDescription = "Save Entry")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            val configuration = LocalConfiguration.current
            val screenHeight = configuration.screenHeightDp.dp
            OutlinedTextField(
                value = entryContent,
                onValueChange = { viewModel.updateContent(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(screenHeight / 3),
                label = { Text("Content") }
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReadOnlyPickerField(
                    value = startDateTime.format(DateFormatters.ISO_DATE),
                    label = "Start Date",
                    trailingIcon = Icons.Default.DateRange,
                    trailingIconContentDescription = "Select Start Date",
                    onClick = { activePicker = ActiveEntryPicker.START_DATE },
                    modifier = Modifier.weight(1f)
                )
                ReadOnlyPickerField(
                    value = startDateTime.format(DateFormatters.TIME_HM),
                    label = "Start Time",
                    trailingIcon = Icons.Default.Schedule,
                    trailingIconContentDescription = "Select Start Time",
                    onClick = { activePicker = ActiveEntryPicker.START_TIME },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ReadOnlyPickerField(
                    value = stopDateTime?.format(DateFormatters.ISO_DATE) ?: "",
                    label = "Stop Date",
                    trailingIcon = Icons.Default.DateRange,
                    trailingIconContentDescription = "Select Stop Date",
                    onClick = { activePicker = ActiveEntryPicker.STOP_DATE },
                    modifier = Modifier.weight(1f)
                )
                ReadOnlyPickerField(
                    value = stopDateTime?.format(DateFormatters.TIME_HM) ?: "",
                    label = "Stop Time",
                    trailingIcon = Icons.Default.Schedule,
                    trailingIconContentDescription = "Select Stop Time",
                    onClick = { activePicker = ActiveEntryPicker.STOP_TIME },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { viewModel.updateStopDateTime(null) }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear Stop Time")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val zoneDisplayText = remember(zoneId, activeZone, startDateTimeMillis) {
                val formatted = TimeZoneUtils.formatZoneLabel(activeZone, startDateTimeMillis)
                if (zoneId == null) "System ($formatted)" else formatted
            }
            ReadOnlyPickerField(
                value = zoneDisplayText,
                label = "Time Zone",
                trailingIcon = PublicIcon,
                trailingIconContentDescription = "Select Time Zone",
                onClick = { activePicker = ActiveEntryPicker.TIME_ZONE },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = hasImage, onCheckedChange = { viewModel.updateHasImage(it) })
                Text("Has Image")
            }
            Spacer(modifier = Modifier.height(16.dp))

            Text("Categories:", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            allCategories.forEach { category ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(
                        checked = selectedCategoryIds.contains(category.id),
                        onCheckedChange = { isChecked ->
                            viewModel.toggleCategory(category.id, isChecked)
                        }
                    )
                    Text(category.category)
                }
            }
        }
    }
}

@Composable
private fun ReadOnlyPickerField(
    value: String,
    label: String,
    trailingIcon: ImageVector,
    trailingIconContentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val currentOnClick by rememberUpdatedState(onClick)
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) {
                currentOnClick()
            }
        }
    }
    OutlinedTextField(
        value = value,
        onValueChange = {},
        label = { Text(label) },
        readOnly = true,
        modifier = modifier,
        interactionSource = interactionSource,
        trailingIcon = { Icon(trailingIcon, contentDescription = trailingIconContentDescription) }
    )
}

@Composable
private fun EntryPickerDialogs(
    activePicker: ActiveEntryPicker?,
    startDateTime: LocalDateTime,
    stopDateTime: LocalDateTime?,
    activeZone: ZoneId,
    zoneId: String?,
    referenceMillis: Long,
    onUpdateStartMillis: (Long) -> Unit,
    onUpdateStopMillis: (Long?) -> Unit,
    onUpdateZoneId: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    when (activePicker) {
        ActiveEntryPicker.START_DATE -> {
            EntryDatePickerDialog(
                initialDate = startDateTime.toLocalDate(),
                onDateSelected = { selectedDate ->
                    onUpdateStartMillis(startDateTime.with(selectedDate).atZone(activeZone).toInstant().toEpochMilli())
                    onDismiss()
                },
                onDismiss = onDismiss
            )
        }
        ActiveEntryPicker.START_TIME -> {
            EntryTimePickerDialog(
                initialHour = startDateTime.hour,
                initialMinute = startDateTime.minute,
                onTimeSelected = { hour, minute ->
                    val updated = startDateTime.withHour(hour).withMinute(minute)
                    onUpdateStartMillis(updated.atZone(activeZone).toInstant().toEpochMilli())
                    onDismiss()
                },
                onDismiss = onDismiss
            )
        }
        ActiveEntryPicker.STOP_DATE -> {
            val baseDateTime = stopDateTime ?: LocalDateTime.now(activeZone)
            EntryDatePickerDialog(
                initialDate = baseDateTime.toLocalDate(),
                onDateSelected = { selectedDate ->
                    val updated = (stopDateTime ?: LocalDateTime.now(activeZone)).with(selectedDate)
                    onUpdateStopMillis(updated.atZone(activeZone).toInstant().toEpochMilli())
                    onDismiss()
                },
                onDismiss = onDismiss
            )
        }
        ActiveEntryPicker.STOP_TIME -> {
            val baseDateTime = stopDateTime ?: LocalDateTime.now(activeZone)
            EntryTimePickerDialog(
                initialHour = baseDateTime.hour,
                initialMinute = baseDateTime.minute,
                onTimeSelected = { hour, minute ->
                    val updated = (stopDateTime ?: LocalDateTime.now(activeZone))
                        .withHour(hour)
                        .withMinute(minute)
                    onUpdateStopMillis(updated.atZone(activeZone).toInstant().toEpochMilli())
                    onDismiss()
                },
                onDismiss = onDismiss
            )
        }
        ActiveEntryPicker.TIME_ZONE -> {
            TimeZonePickerDialog(
                selectedZoneId = zoneId,
                referenceMillis = referenceMillis,
                onZoneSelected = { selected ->
                    onUpdateZoneId(selected)
                    onDismiss()
                },
                onDismiss = onDismiss
            )
        }
        null -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryDatePickerDialog(
    initialDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = TimeZoneUtils.localDateToDatePickerMillis(initialDate)
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val selectedMillis = datePickerState.selectedDateMillis
                if (selectedMillis != null) {
                    onDateSelected(TimeZoneUtils.datePickerMillisToLocalDate(selectedMillis))
                } else {
                    onDismiss()
                }
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onTimeSelected: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Time") },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = timePickerState)
            }
        },
        confirmButton = {
            TextButton(onClick = { onTimeSelected(timePickerState.hour, timePickerState.minute) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
