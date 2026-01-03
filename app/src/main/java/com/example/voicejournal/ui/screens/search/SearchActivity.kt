package com.example.voicejournal.ui.screens.search

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.voicejournal.data.AppDatabase
import com.example.voicejournal.ui.theme.VoicejournalTheme

class SearchActivity : ComponentActivity() {
    private val viewModel: SearchViewModel by viewModels {
        val journalEntryDao = AppDatabase.getDatabase(applicationContext).journalEntryDao()
        SearchViewModelFactory(this, journalEntryDao)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VoicejournalTheme {
                SearchScreen(viewModel = viewModel)
            }
        }
    }
}