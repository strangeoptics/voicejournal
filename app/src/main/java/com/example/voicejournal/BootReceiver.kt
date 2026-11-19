package com.example.voicejournal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.voicejournal.di.Injector

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            VoiceJournalApplication.setupLocationWorker(context, replaceExisting = false)

            val isWebServerEnabled = Injector.provideSettingsRepository(context).isWebServerEnabled.value
            if (isWebServerEnabled) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    WebServerService.start(context)
                } else {
                    Log.i(
                        TAG,
                        "Skipping specialUse WebServerService start on BOOT_COMPLETED (Android 15+); will start when app opens."
                    )
                }
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
