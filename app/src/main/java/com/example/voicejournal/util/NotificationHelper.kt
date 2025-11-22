package com.example.voicejournal.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.voicejournal.MainActivity
import com.example.voicejournal.R

object NotificationHelper {
    const val NOTIFICATION_ACTION = "com.example.voicejournal.NOTIFICATION_ACTION"
    private const val CHANNEL_ID = "voice_journal_channel"
    private const val NOTIFICATION_ID = 1

    fun showNotification(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = NOTIFICATION_ACTION
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Voice Journal",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Voice Journal")
            .setContentText("Ready to listen!")
            .addAction(R.drawable.ic_launcher_foreground, "Start Listening", pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
