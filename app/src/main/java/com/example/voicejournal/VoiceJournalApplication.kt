package com.example.voicejournal

import android.app.Application
import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.voicejournal.worker.LocationWorker
import java.util.concurrent.TimeUnit

class VoiceJournalApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        setupLocationWorker(this)
    }

    companion object {
        fun setupLocationWorker(context: Context) {
            val sharedPreferences = context.getSharedPreferences(MainViewModel.PREFS_NAME, MODE_PRIVATE)
            val isGpsTrackingEnabled = sharedPreferences.getBoolean(MainViewModel.KEY_GPS_TRACKING_ENABLED, true)
            val interval = sharedPreferences.getInt(MainViewModel.KEY_GPS_INTERVAL_MINUTES, 20).toLong()

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
                    ExistingPeriodicWorkPolicy.UPDATE, // Use UPDATE to allow changing the interval
                    periodicWorkRequest
                )
            } else {
                workManager.cancelUniqueWork(LocationWorker.WORK_NAME)
            }
        }
    }
}