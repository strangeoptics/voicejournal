package com.example.voicejournal.ui.screens.search

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.voicejournal.di.Injector
import com.example.voicejournal.ui.theme.VoicejournalTheme

class SearchActivity : ComponentActivity() {
    private val viewModel: SearchViewModel by viewModels {
        val repository = Injector.provideJournalRepository(applicationContext)
        SearchViewModelFactory(this, repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VoicejournalTheme {
                SearchScreen(
                    viewModel = viewModel,
                    onNavigateUp = { finish() }
                )
            }
        }
    }
}