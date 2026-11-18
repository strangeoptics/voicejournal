package com.example.voicejournal.di

import android.content.Context
import com.example.voicejournal.data.AppDatabase
import com.example.voicejournal.data.JournalBackupManager
import com.example.voicejournal.data.JournalRepository
import com.example.voicejournal.data.SettingsRepository

object Injector {

    @Volatile
    private var journalRepositoryInstance: JournalRepository? = null

    @Volatile
    private var settingsRepositoryInstance: SettingsRepository? = null

    private fun getDatabase(context: Context): AppDatabase {
        return AppDatabase.getDatabase(context.applicationContext)
    }

    fun provideJournalRepository(context: Context): JournalRepository {
        return journalRepositoryInstance ?: synchronized(this) {
            journalRepositoryInstance ?: run {
                val appContext = context.applicationContext
                val database = getDatabase(appContext)
                val entryDao = database.journalEntryDao()
                val categoryDao = database.categoryDao()
                val backupManager = JournalBackupManager(
                    database = database,
                    entryDao = entryDao,
                    categoryDao = categoryDao,
                    context = appContext
                )
                JournalRepository(
                    database = database,
                    entryDao = entryDao,
                    categoryDao = categoryDao,
                    gpsTrackPointDao = database.gpsTrackPointDao(),
                    backupManager = backupManager
                ).also { journalRepositoryInstance = it }
            }
        }
    }

    fun provideSettingsRepository(context: Context): SettingsRepository {
        return settingsRepositoryInstance ?: synchronized(this) {
            settingsRepositoryInstance ?: SettingsRepository(context.applicationContext).also {
                settingsRepositoryInstance = it
            }
        }
    }
}
