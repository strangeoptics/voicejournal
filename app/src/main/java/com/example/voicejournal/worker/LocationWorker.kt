package com.example.voicejournal.worker

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.voicejournal.data.GpsTrackPoint
import com.example.voicejournal.di.Injector
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LocationWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val fusedLocationClient by lazy { LocationServices.getFusedLocationProviderClient(appContext) }
    private val repository by lazy { Injector.provideJournalRepository(appContext) }

    companion object {
        const val WORK_NAME = "LocationWorker"
    }

    @SuppressLint("MissingPermission")
    override suspend fun doWork(): Result {
        val hasPermission = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Log.e(WORK_NAME, "Location permission not granted. Worker is failing.")
            return Result.failure()
        }

        return try {
            val location = getCurrentLocation()
            if (location != null) {
                val point = GpsTrackPoint(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    timestamp = System.currentTimeMillis()
                )
                repository.insertGpsPoint(point)
                Log.d(WORK_NAME, "Successfully captured and saved location: $point")
                Result.success()
            } else {
                Log.w(WORK_NAME, "Location was null, retrying later.")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(WORK_NAME, "Error getting location, failing.", e)
            Result.failure()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLocation(): Location? {
        return suspendCancellableCoroutine { continuation ->
            val cancellationTokenSource = CancellationTokenSource()

            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellationTokenSource.token
            ).addOnSuccessListener { location ->
                continuation.resume(location) // Simply resume with the location, which can be null
            }.addOnFailureListener { e ->
                continuation.resumeWithException(e) // Resume with exception for actual failures
            }

            continuation.invokeOnCancellation {
                cancellationTokenSource.cancel()
            }
        }
    }
}