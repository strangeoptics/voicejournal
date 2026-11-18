package com.example.voicejournal

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.voicejournal.di.Injector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class WebServerService : Service() {

    private lateinit var webServer: WebServer
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val repository = Injector.provideJournalRepository(applicationContext)
        webServer = WebServer(repository)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Injector.provideSettingsRepository(applicationContext).setWebServerEnabled(false)
                stopServer()
            }
            ACTION_START, null -> startServer()
        }
        return START_NOT_STICKY
    }

    private fun startServer() {
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } catch (e: ForegroundServiceStartNotAllowedException) {
            Log.w(TAG, "Foreground service start not allowed by system", e)
            stopSelf()
            return
        }

        serviceScope.launch {
            try {
                webServer.start()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start WebServer", e)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun stopServer() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onTimeout(startId: Int) {
        Log.w(TAG, "Foreground service reached timeout (startId=$startId), stopping.")
        stopServer()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "Foreground service reached timeout (startId=$startId, fgsType=$fgsType), stopping.")
        stopServer()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        if (::webServer.isInitialized && webServer.isRunning) {
            val serverInstance = webServer
            CoroutineScope(Dispatchers.IO).launch {
                serverInstance.stop()
            }
        }
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        val notificationChannelId = "WEB_SERVER_CHANNEL"
        val channel = NotificationChannel(
            notificationChannelId,
            "Web Server",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notification for the running web server"
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        val openAppPendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(
                    this,
                    0,
                    notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )
            }

        val stopIntent = Intent(this, WebServerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle("Voice Journal Server")
            .setContentText("The web server is running.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppPendingIntent)
            .addAction(0, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "WebServerService"
        const val ACTION_START = "com.example.voicejournal.action.START_WEBSERVER"
        const val ACTION_STOP = "com.example.voicejournal.action.STOP_WEBSERVER"
        private const val NOTIFICATION_ID = 2

        fun start(context: Context): Boolean {
            val serviceIntent = Intent(context, WebServerService::class.java).apply {
                action = ACTION_START
            }
            return try {
                ContextCompat.startForegroundService(context, serviceIntent)
                true
            } catch (e: ForegroundServiceStartNotAllowedException) {
                Log.w(TAG, "Cannot start WebServerService from background on Android 15+", e)
                false
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WebServerService::class.java))
        }
    }
}
