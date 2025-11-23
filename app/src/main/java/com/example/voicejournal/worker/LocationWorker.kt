package com.example.voicejournal.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.voicejournal.data.GpsTrackPoint
import com.example.voicejournal.di.Injector
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocationWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val fusedLocationClient by lazy { LocationServices.getFusedLocationProviderClient(appContext) }
    private val repository by lazy { Injector.provideJournalRepository(appContext) }

    companion object {
        const val WORK_NAME = "LocationWorker"
    }

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            val hasFineLocationPermission = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasFineLocationPermission || !hasCoarseLocationPermission) {
                Log.e(WORK_NAME, "Location permission not granted")
                return@withContext Result.failure()
            }

            try {
                val location = Tasks.await(fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null))
                location?.let {
                    val point = GpsTrackPoint(
                        latitude = it.latitude,
                        longitude = it.longitude,
                        timestamp = System.currentTimeMillis()
                    )
                    repository.insertGpsPoint(point)
                    Log.d(WORK_NAME, "Location captured: $point")
                    return@withContext Result.success()
                } ?: run {
                    Log.e(WORK_NAME, "Failed to get location, it was null.")
                    return@withContext Result.failure()
                }
            } catch (e: Exception) {
                Log.e(WORK_NAME, "Exception getting location", e)
                return@withContext Result.failure()
            }
        }
    }
}