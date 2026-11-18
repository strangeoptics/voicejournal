package com.example.voicejournal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.voicejournal.di.Injector

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val isWebServerEnabled = Injector.provideSettingsRepository(context).isWebServerEnabled.value
            if (isWebServerEnabled) {
                WebServerService.start(context)
            }
        }
    }
}
