package com.example.voicejournal.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import com.example.voicejournal.data.GpsTrackPoint
import java.time.LocalDate
import java.util.Locale

object ExternalNavigationHelper {

    internal const val MAX_MAPS_WAYPOINTS = 9

    internal fun <T> sampleWaypoints(
        intermediate: List<T>,
        maxWaypoints: Int = MAX_MAPS_WAYPOINTS
    ): List<T> {
        if (maxWaypoints <= 0 || intermediate.isEmpty()) return emptyList()
        if (intermediate.size <= maxWaypoints) return intermediate
        if (maxWaypoints == 1) return listOf(intermediate[intermediate.size / 2])
        return (0 until maxWaypoints).map { step ->
            val index = ((step.toLong() * (intermediate.size - 1)) / (maxWaypoints - 1)).toInt()
            intermediate[index]
        }
    }

    fun buildGooglePhotosUrl(date: LocalDate, locale: Locale = Locale.getDefault()): String {
        val formatter = DateFormatters.googlePhotosQuery(locale)
        return "https://photos.google.com/search/${date.format(formatter)}"
    }

    fun openGooglePhotos(context: Context, date: LocalDate) {
        val url = buildGooglePhotosUrl(date)
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Could not open browser.", Toast.LENGTH_SHORT).show()
        }
    }

    fun buildGoogleMapsTrackUrl(points: List<GpsTrackPoint>): String? {
        if (points.size < 2) return null
        val origin = "${points.first().latitude},${points.first().longitude}"
        val destination = "${points.last().latitude},${points.last().longitude}"
        val sampledIntermediate = sampleWaypoints(points.subList(1, points.size - 1))
        val waypointsParam = if (sampledIntermediate.isNotEmpty()) {
            "&waypoints=" + sampledIntermediate.joinToString("|") { "${it.latitude},${it.longitude}" }
        } else {
            ""
        }
        return "https://www.google.com/maps/dir/?api=1&origin=$origin&destination=$destination$waypointsParam&travelmode=driving"
    }

    fun openGoogleMapsWithTrack(context: Context, points: List<GpsTrackPoint>) {
        val url = buildGoogleMapsTrackUrl(points)
        if (url == null) {
            Toast.makeText(context, "Not enough GPS points to show a track.", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Could not open Google Maps.", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyEntriesTextToClipboard(context: Context, clipboardText: String) {
        if (clipboardText.isBlank()) {
            Toast.makeText(context, "Keine Einträge zum Kopieren vorhanden", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("VoiceJournal", clipboardText)
        clipboardManager.setPrimaryClip(clip)
        Toast.makeText(context, "In die Zwischenablage kopiert", Toast.LENGTH_SHORT).show()
    }
}
