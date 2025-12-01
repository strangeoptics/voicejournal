package com.example.voicejournal.worker

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.voicejournal.R
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
        const val NOTIFICATION_ID = 1
        const val NOTIFICATION_CHANNEL_ID = "LocationTrackingChannel"
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

        // Promote to a foreground service
        // setForeground(createForegroundInfo()) // Deaktiviert, um Absturz zu vermeiden

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

    private fun createForegroundInfo(): ForegroundInfo {
        val notification = createNotification()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotification(): Notification {
        val channelId = NOTIFICATION_CHANNEL_ID
        val channelName = "Location Tracking"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(appContext, channelId)
            .setContentTitle("GPS Tracking Aktiv")
            .setContentText("Standort wird in regelmäßigen Abständen erfasst.")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Ensure you have this drawable
            .setOngoing(true)
            .build()
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