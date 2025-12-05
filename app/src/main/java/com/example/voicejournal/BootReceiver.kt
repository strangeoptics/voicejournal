package com.example.voicejournal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val sharedPreferences: SharedPreferences = context.getSharedPreferences(MainViewModel.PREFS_NAME, Context.MODE_PRIVATE)
            val isWebServerEnabled = sharedPreferences.getBoolean(MainViewModel.KEY_WEBSERVER_ENABLED, false)

            if (isWebServerEnabled) {
                val serviceIntent = Intent(context, WebServerService::class.java).apply {
                    action = WebServerService.ACTION_START
                }
                context.startForegroundService(serviceIntent)
            }
        }
    }
}