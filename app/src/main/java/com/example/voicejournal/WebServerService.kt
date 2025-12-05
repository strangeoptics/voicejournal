package com.example.voicejournal

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.voicejournal.di.Injector

class WebServerService : Service() {

    private lateinit var webServer: WebServer

    override fun onCreate() {
        super.onCreate()
        val db = Injector.getDatabase(applicationContext)
        webServer = WebServer(db)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startServer()
            ACTION_STOP -> stopServer()
        }
        return START_NOT_STICKY
    }

    private fun startServer() {
        if (!webServer.isRunning) {
            startForeground(NOTIFICATION_ID, createNotification())
            webServer.start()
        }
    }

    private fun stopServer() {
        if (webServer.isRunning) {
            webServer.stop()
            stopForeground(true)
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (webServer.isRunning) {
            webServer.stop()
        }
    }

    private fun createNotification(): Notification {
        val notificationChannelId = "WEB_SERVER_CHANNEL"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                notificationChannelId,
                "Web Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification for the running web server"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE)
            }

        return NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle("Voice Journal Server")
            .setContentText("The web server is running.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    companion object {
        const val ACTION_START = "com.example.voicejournal.action.START_WEBSERVER"
        const val ACTION_STOP = "com.example.voicejournal.action.STOP_WEBSERVER"
        private const val NOTIFICATION_ID = 1
    }
}