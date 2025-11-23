package com.example.voicejournal.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppDrawer(
    onSettingsClicked: () -> Unit,
    onManageCategoriesClicked: () -> Unit,
    onImportJournalClicked: () -> Unit,
    onExportJournalClicked: () -> Unit,
    onShowNotificationClicked: () -> Unit,
    onAddTestDataClicked: () -> Unit,
    onShowGpsTrackClicked: () -> Unit
) {
    ModalDrawerSheet {
        NavigationDrawerItem(
            icon = { Icon(Icons.Filled.Settings, contentDescription = "Einstellungen") },
            label = { Text("Settings") },
            selected = false,
            onClick = onSettingsClicked,
            modifier = Modifier.padding(12.dp)
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Filled.Category, contentDescription = "Manage Categories") },
            label = { Text("Manage Categories") },
            selected = false,
            onClick = onManageCategoriesClicked,
            modifier = Modifier.padding(12.dp)
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Filled.Download, contentDescription = "Import Journal") },
            label = { Text("Import Journal") },
            selected = false,
            onClick = onImportJournalClicked,
            modifier = Modifier.padding(12.dp)
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Filled.Upload, contentDescription = "Export Journal") },
            label = { Text("Export Journal") },
            selected = false,
            onClick = onExportJournalClicked,
            modifier = Modifier.padding(12.dp)
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Filled.Notifications, contentDescription = "Benachrichtigung anzeigen") },
            label = { Text("Show Notification") },
            selected = false,
            onClick = onShowNotificationClicked,
            modifier = Modifier.padding(12.dp)
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add test data") },
            label = { Text("Add Test Data") },
            selected = false,
            onClick = onAddTestDataClicked,
            modifier = Modifier.padding(12.dp)
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Filled.Map, contentDescription = "Show GPS Track") },
            label = { Text("Show GPS Track") },
            selected = false,
            onClick = onShowGpsTrackClicked,
            modifier = Modifier.padding(12.dp)
        )
    }
}