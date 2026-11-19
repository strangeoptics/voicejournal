package com.example.voicejournal

import android.app.Application
import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.voicejournal.di.Injector
import com.example.voicejournal.worker.LocationWorker
import java.util.concurrent.TimeUnit

class VoiceJournalApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        setupLocationWorker(this, replaceExisting = false)
    }

    companion object {
        fun setupLocationWorker(context: Context, replaceExisting: Boolean = true) {
            val appContext = context.applicationContext
            val settingsRepository = Injector.provideSettingsRepository(appContext)
            val isGpsTrackingEnabled = settingsRepository.isGpsTrackingEnabled.value
            val interval = settingsRepository.gpsIntervalMinutes.value
                .coerceAtLeast(LocationWorker.MIN_INTERVAL_MINUTES)
                .toLong()

            if (isGpsTrackingEnabled) {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()

                val periodicWorkRequest = PeriodicWorkRequestBuilder<LocationWorker>(interval, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build()

                val policy = if (replaceExisting) {
                    ExistingPeriodicWorkPolicy.UPDATE
                } else {
                    ExistingPeriodicWorkPolicy.KEEP
                }

                WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
                    LocationWorker.WORK_NAME,
                    policy,
                    periodicWorkRequest
                )
            } else if (replaceExisting) {
                WorkManager.getInstance(appContext).cancelUniqueWork(LocationWorker.WORK_NAME)
            }
        }
    }
}
