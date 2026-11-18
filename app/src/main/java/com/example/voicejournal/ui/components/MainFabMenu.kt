package com.example.voicejournal.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.voicejournal.R

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainFabMenu(
    isRecording: Boolean,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onCreateManualEntryClicked: () -> Unit,
    onStartVoiceEntryClicked: () -> Unit,
    onStopRecordingClicked: () -> Unit
) {
    Column(horizontalAlignment = Alignment.End) {
        AnimatedVisibility(visible = isExpanded) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        onExpandedChange(false)
                        onCreateManualEntryClicked()
                    }
                ) {
                    Icon(Icons.Filled.Create, contentDescription = stringResource(R.string.fab_create_manual))
                }
                FloatingActionButton(
                    onClick = {
                        onExpandedChange(false)
                        onStartVoiceEntryClicked()
                    }
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = stringResource(R.string.fab_speak))
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        FloatingActionButton(onClick = {}) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .combinedClickable(
                        onClick = {
                            if (isRecording) {
                                onStopRecordingClicked()
                            } else {
                                onExpandedChange(!isExpanded)
                            }
                        },
                        onLongClick = onStartVoiceEntryClicked
                    ),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isRecording -> Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.fab_stop_recording))
                    isExpanded -> Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.fab_close_menu))
                    else -> Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.fab_add_entry))
                }
            }
        }
    }
}
