package com.example.voicejournal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val sharedPreferences = context.getSharedPreferences(
                MainViewModel.PREFS_NAME,
                Context.MODE_PRIVATE
            )
            val isWebServerEnabled = sharedPreferences.getBoolean(
                MainViewModel.KEY_WEBSERVER_ENABLED,
                false
            )

            if (isWebServerEnabled) {
                WebServerService.start(context)
            }
        }
    }
}
