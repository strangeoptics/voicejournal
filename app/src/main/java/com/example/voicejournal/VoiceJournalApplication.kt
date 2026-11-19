package com.example.voicejournal

import android.app.Application
import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.voicejournal.data.SettingsRepository
import com.example.voicejournal.di.Injector
import com.example.voicejournal.worker.LocationWorker
import java.util.concurrent.TimeUnit

class VoiceJournalApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        setupLocationWorker(this, replaceExisting = false, isAppInForeground = false)
    }

    companion object {
        /**
         * Hybrid GPS tracking coordinator:
         * - 15–60 min: Uses battery-friendly WorkManager [LocationWorker] without a persistent service.
         * - 5–14 min: Uses [LocationTrackingService] (foreground service of type `location`) for reliable
         *   sub-15-minute intervals, falling back to WorkManager (15 min) if started from background without
         *   `ACCESS_BACKGROUND_LOCATION` until the app enters foreground.
         */
        fun setupLocationWorker(
            context: Context,
            replaceExisting: Boolean = true,
            isAppInForeground: Boolean = false
        ) {
            val appContext = context.applicationContext
            val settingsRepository = Injector.provideSettingsRepository(appContext)
            val isGpsTrackingEnabled = settingsRepository.isGpsTrackingEnabled.value
            val intervalMinutes = settingsRepository.gpsIntervalMinutes.value
                .coerceIn(
                    SettingsRepository.MIN_GPS_INTERVAL_MINUTES,
                    SettingsRepository.MAX_GPS_INTERVAL_MINUTES
                )

            if (!isGpsTrackingEnabled) {
                LocationTrackingService.stop(appContext)
                if (replaceExisting) {
                    WorkManager.getInstance(appContext).cancelUniqueWork(LocationWorker.WORK_NAME)
                }
                return
            }

            if (intervalMinutes < LocationWorker.MIN_INTERVAL_MINUTES) {
                val serviceStarted = LocationTrackingService.start(
                    context = appContext,
                    intervalMinutes = intervalMinutes,
                    isAppInForeground = isAppInForeground
                )
                if (serviceStarted) {
                    WorkManager.getInstance(appContext).cancelUniqueWork(LocationWorker.WORK_NAME)
                } else {
                    // Graceful fallback when background start is blocked (e.g. missing background permission on boot)
                    enqueuePeriodicLocationWorker(
                        appContext = appContext,
                        intervalMinutes = LocationWorker.MIN_INTERVAL_MINUTES.toLong(),
                        replaceExisting = false
                    )
                }
            } else {
                LocationTrackingService.stop(appContext)
                enqueuePeriodicLocationWorker(
                    appContext = appContext,
                    intervalMinutes = intervalMinutes.toLong(),
                    replaceExisting = replaceExisting
                )
            }
        }

        private fun enqueuePeriodicLocationWorker(
            appContext: Context,
            intervalMinutes: Long,
            replaceExisting: Boolean
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            val periodicWorkRequest = PeriodicWorkRequestBuilder<LocationWorker>(
                intervalMinutes.coerceAtLeast(LocationWorker.MIN_INTERVAL_MINUTES.toLong()),
                TimeUnit.MINUTES
            )
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
        }
    }
}
