package com.example.voicejournal.di

import android.content.Context
import com.example.voicejournal.data.AppDatabase
import com.example.voicejournal.data.JournalRepository

object Injector {

    fun getDatabase(context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    fun provideJournalRepository(context: Context): JournalRepository {
        val database = getDatabase(context)
        return JournalRepository(
            entryDao = database.journalEntryDao(),
            categoryDao = database.categoryDao(),
            gpsTrackPointDao = database.gpsTrackPointDao(),
            context = context.applicationContext
        )
    }
}