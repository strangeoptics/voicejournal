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

    companion object {
        fun setupLocationWorker(context: Context) {
            val settingsRepository = Injector.provideSettingsRepository(context)
            val isGpsTrackingEnabled = settingsRepository.isGpsTrackingEnabled.value
            val interval = settingsRepository.gpsIntervalMinutes.value
                .coerceAtLeast(LocationWorker.MIN_INTERVAL_MINUTES)
                .toLong()

            val workManager = WorkManager.getInstance(context)

            if (isGpsTrackingEnabled) {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()

                val periodicWorkRequest = PeriodicWorkRequestBuilder<LocationWorker>(interval, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build()

                workManager.enqueueUniquePeriodicWork(
                    LocationWorker.WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    periodicWorkRequest
                )
            } else {
                workManager.cancelUniqueWork(LocationWorker.WORK_NAME)
            }
        }
    }
}
