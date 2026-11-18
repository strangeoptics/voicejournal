package com.example.voicejournal.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ShareExportHelper {

    private const val SHARED_EXPORTS_DIR = "shared_exports"
    private const val EXPORT_FILE_NAME = "VoiceJournal_Export.vj"

    suspend fun writeSharedExportUri(context: Context, jsonContent: String): Uri =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val exportDir = File(appContext.cacheDir, SHARED_EXPORTS_DIR).apply { mkdirs() }
            val file = File(exportDir, EXPORT_FILE_NAME)
            file.writeText(jsonContent, Charsets.UTF_8)
            FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                file
            )
        }

    fun createPlainTextShareIntent(text: String): Intent {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        return Intent.createChooser(shareIntent, "Einträge teilen")
    }

    fun createVjFileShareIntent(uri: Uri): Intent {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vj"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(shareIntent, "Einträge exportieren")
    }
}
