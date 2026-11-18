package com.example.voicejournal.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.voicejournal.R

@Composable
fun AppDrawer(
    isDeveloperModeEnabled: Boolean,
    onSettingsClicked: () -> Unit,
    onManageCategoriesClicked: () -> Unit,
    onCalendarClicked: () -> Unit,
    onSearchClicked: () -> Unit,
    onImportJournalClicked: () -> Unit,
    onExportJournalClicked: () -> Unit,
    onShowNotificationClicked: () -> Unit,
    onAddTestDataClicked: () -> Unit,
    onShowGpsTrackClicked: () -> Unit,
    onDeleteAllClicked: () -> Unit
) {
    ModalDrawerSheet {
        NavigationDrawerItem(
            icon = { Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.drawer_search)) },
            label = { Text(stringResource(R.string.drawer_search)) },
            selected = false,
            onClick = onSearchClicked,
            modifier = Modifier.padding(12.dp)
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.drawer_settings)) },
            label = { Text(stringResource(R.string.drawer_settings)) },
            selected = false,
            onClick = onSettingsClicked,
            modifier = Modifier.padding(12.dp)
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Filled.Category, contentDescription = stringResource(R.string.drawer_manage_categories)) },
            label = { Text(stringResource(R.string.drawer_manage_categories)) },
            selected = false,
            onClick = onManageCategoriesClicked,
            modifier = Modifier.padding(12.dp)
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Filled.DateRange, contentDescription = stringResource(R.string.drawer_calendar)) },
            label = { Text(stringResource(R.string.drawer_calendar)) },
            selected = false,
            onClick = onCalendarClicked,
            modifier = Modifier.padding(12.dp)
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.drawer_import_journal)) },
            label = { Text(stringResource(R.string.drawer_import_journal)) },
            selected = false,
            onClick = onImportJournalClicked,
            modifier = Modifier.padding(12.dp)
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Filled.Upload, contentDescription = stringResource(R.string.drawer_export_journal)) },
            label = { Text(stringResource(R.string.drawer_export_journal)) },
            selected = false,
            onClick = onExportJournalClicked,
            modifier = Modifier.padding(12.dp)
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Filled.Notifications, contentDescription = stringResource(R.string.drawer_show_notification)) },
            label = { Text(stringResource(R.string.drawer_show_notification)) },
            selected = false,
            onClick = onShowNotificationClicked,
            modifier = Modifier.padding(12.dp)
        )
        if (isDeveloperModeEnabled) {
            NavigationDrawerItem(
                icon = { Icon(Icons.Filled.Map, contentDescription = stringResource(R.string.drawer_show_gps_track)) },
                label = { Text(stringResource(R.string.drawer_show_gps_track)) },
                selected = false,
                onClick = onShowGpsTrackClicked,
                modifier = Modifier.padding(12.dp)
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = stringResource(R.string.drawer_add_test_data)) },
                label = { Text(stringResource(R.string.drawer_add_test_data)) },
                selected = false,
                onClick = onAddTestDataClicked,
                modifier = Modifier.padding(12.dp)
            )
            NavigationDrawerItem(
                icon = { Icon(Icons.Filled.DeleteForever, contentDescription = stringResource(R.string.drawer_delete_all_data)) },
                label = { Text(stringResource(R.string.drawer_delete_all_data)) },
                selected = false,
                onClick = onDeleteAllClicked,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}
