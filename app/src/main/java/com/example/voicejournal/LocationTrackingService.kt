package com.example.voicejournal

import android.Manifest
import android.annotation.SuppressLint
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import com.example.voicejournal.data.GpsTrackPoint
import com.example.voicejournal.di.Injector
import com.example.voicejournal.worker.LocationWorker
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class LocationTrackingService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var locationCallback: LocationCallback? = null
    private var activeIntervalMinutes: Int? = null
    private var lastSavedElapsedRealtimeMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            val settingsRepository = Injector.provideSettingsRepository(applicationContext)
            settingsRepository.setGpsTrackingEnabled(false)
            WorkManager.getInstance(applicationContext).cancelUniqueWork(LocationWorker.WORK_NAME)
            stopTracking()
            return START_NOT_STICKY
        }

        val settingsRepository = Injector.provideSettingsRepository(applicationContext)
        val requestedInterval = intent?.getIntExtra(EXTRA_INTERVAL_MINUTES, -1)
            ?.takeIf { it > 0 }
            ?: settingsRepository.gpsIntervalMinutes.value
        val intervalMinutes = requestedInterval.coerceAtLeast(MIN_SERVICE_INTERVAL_MINUTES)

        if (!hasForegroundLocationPermission(this)) {
            Log.w(TAG, "Location permission missing; stopping LocationTrackingService.")
            stopSelf()
            return START_NOT_STICKY
        }

        if (!startForegroundSafely(intervalMinutes)) {
            return START_NOT_STICKY
        }

        if (!settingsRepository.isGpsTrackingEnabled.value || intervalMinutes >= LocationWorker.MIN_INTERVAL_MINUTES) {
            Log.i(TAG, "GPS tracking disabled or interval >= 15 min ($intervalMinutes); stopping FGS.")
            stopTracking()
            return START_NOT_STICKY
        }

        if (activeIntervalMinutes != intervalMinutes || locationCallback == null) {
            registerLocationUpdates(intervalMinutes)
        }

        return START_STICKY
    }

    private fun startForegroundSafely(intervalMinutes: Int): Boolean {
        return try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                createNotification(intervalMinutes),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
            true
        } catch (e: ForegroundServiceStartNotAllowedException) {
            Log.w(TAG, "Foreground service start not allowed by system", e)
            stopSelf()
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException starting location foreground service", e)
            stopSelf()
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerLocationUpdates(intervalMinutes: Int) {
        unregisterLocationUpdates()

        val intervalMillis = TimeUnit.MINUTES.toMillis(intervalMinutes.toLong())
        val minUpdateMillis = (intervalMillis * 4L) / 5L
        val repository = Injector.provideJournalRepository(applicationContext)

        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, intervalMillis)
            .setMinUpdateIntervalMillis(minUpdateMillis)
            .setMaxUpdateDelayMillis(intervalMillis)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val nowElapsed = SystemClock.elapsedRealtime()
                val minSaveGapMs = intervalMillis / 2L
                if (lastSavedElapsedRealtimeMs != 0L && (nowElapsed - lastSavedElapsedRealtimeMs) < minSaveGapMs) {
                    return
                }
                lastSavedElapsedRealtimeMs = nowElapsed

                val point = GpsTrackPoint(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    timestamp = System.currentTimeMillis()
                )
                serviceScope.launch {
                    try {
                        repository.insertGpsPoint(point)
                        Log.d(TAG, "Saved high-frequency GPS point ($intervalMinutes min): $point")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to insert GPS point", e)
                    }
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                callback,
                Looper.getMainLooper()
            )
            locationCallback = callback
            activeIntervalMinutes = intervalMinutes
            Log.i(TAG, "Started location updates every $intervalMinutes minutes.")
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing location permission when requesting updates", e)
            stopTracking()
        }
    }

    private fun unregisterLocationUpdates() {
        locationCallback?.let { callback ->
            fusedLocationClient.removeLocationUpdates(callback)
        }
        locationCallback = null
        activeIntervalMinutes = null
    }

    private fun stopTracking() {
        unregisterLocationUpdates()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onTimeout(startId: Int) {
        Log.w(TAG, "LocationTrackingService reached timeout (startId=$startId), stopping.")
        stopTracking()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "LocationTrackingService reached timeout (startId=$startId, fgsType=$fgsType), stopping.")
        stopTracking()
    }

    override fun onDestroy() {
        unregisterLocationUpdates()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotification(intervalMinutes: Int): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "GPS-Tracking (Intensiv-Modus)",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Status-Anzeige für kurzes GPS-Abrufintervall unter 15 Minuten"
            setShowBadge(false)
        }
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GPS-Tracking aktiv (alle $intervalMinutes Min.)")
            .setContentText("Kurzes Intervall aktiv · Erhöhter Akkuverbrauch")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppPendingIntent)
            .addAction(0, "Beenden", stopPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "LocationTrackingService"
        const val MIN_SERVICE_INTERVAL_MINUTES = 5
        const val ACTION_START = "com.example.voicejournal.action.START_LOCATION_TRACKING"
        const val ACTION_STOP = "com.example.voicejournal.action.STOP_LOCATION_TRACKING"
        private const val EXTRA_INTERVAL_MINUTES = "extra_interval_minutes"
        private const val NOTIFICATION_ID = 3
        private const val CHANNEL_ID = "LOCATION_TRACKING_CHANNEL"

        fun hasForegroundLocationPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }

        fun hasBackgroundLocationPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }

        fun start(
            context: Context,
            intervalMinutes: Int,
            isAppInForeground: Boolean = false
        ): Boolean {
            if (!hasForegroundLocationPermission(context)) {
                Log.w(TAG, "Cannot start LocationTrackingService: location permission not granted.")
                return false
            }
            if (!isAppInForeground && !hasBackgroundLocationPermission(context)) {
                Log.w(
                    TAG,
                    "Cannot start LocationTrackingService from background without ACCESS_BACKGROUND_LOCATION."
                )
                return false
            }

            val serviceIntent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_INTERVAL_MINUTES, intervalMinutes)
            }
            return try {
                ContextCompat.startForegroundService(context, serviceIntent)
                true
            } catch (e: ForegroundServiceStartNotAllowedException) {
                Log.w(TAG, "ForegroundServiceStartNotAllowedException starting LocationTrackingService", e)
                false
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException starting LocationTrackingService", e)
                false
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationTrackingService::class.java))
        }
    }
}
