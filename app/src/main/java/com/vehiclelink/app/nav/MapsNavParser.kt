package com.vehiclelink.app.nav

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.vehiclelink.app.core.VehicleBus

/**
 * Extracts turn-by-turn data from the Google Maps ongoing navigation notification.
 *
 * Google Maps exposes no public "current guidance" API, but while it is guiding it
 * posts an ongoing notification. Observed on Maps (Android 17, Oct 2026):
 *   title    "150 m · Turn left onto MG Road"
 *   subText  "Arrive 6:26 pm"
 *   progress 2 / progressMax 11256   (metres travelled / trip length, ProgressStyle)
 * Older versions put the distance and maneuver in separate fields, so we parse defensively
 * and keep the raw text so the vehicle side can fall back to displaying it verbatim.
 */
object MapsNavParser {

    const val MAPS_PKG = "com.google.android.apps.maps"

    private val DISTANCE = Regex(
        """\d+[.,]?\d*\s?(?:km|m|mi|ft|yd)\b""",
        RegexOption.IGNORE_CASE
    )

    /** "Arrive 6:26 pm", "Arrive 18:26", "ETA 6:26 PM". */
    private val ETA = Regex("""(\d{1,2})[:.](\d{2})\s*([ap]\.?\s?m\.?)?""", RegexOption.IGNORE_CASE)

    /** @return true if this notification was a Maps navigation update we handled. */
    fun handle(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName != MAPS_PKG) return false
        val extras = sbn.notification?.extras ?: return false

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val sub = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim().orEmpty()
        val big = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()

        val blob = listOf(title, text, big, sub).filter { it.isNotEmpty() }.joinToString(" · ")
        if (blob.isEmpty()) return false

        // A real guidance notification almost always carries a distance token
        // ("450 m", "1.2 km"). Non-nav Maps notifications (traffic, updates) don't.
        val hasDistance = DISTANCE.containsMatchIn(blob)
        val looksOngoing = sbn.isOngoing || (sbn.notification.flags and Notification.FLAG_ONGOING_EVENT) != 0
        if (!hasDistance && !looksOngoing) return false

        // Current Maps: "150 m · Turn left onto X" in the title. Older: distance in text.
        val maneuverLine = title.ifEmpty { text }
        val distance = (DISTANCE.find(maneuverLine) ?: DISTANCE.find(blob))?.value?.replace(",", ".")?.trim().orEmpty()
        val instruction = maneuverLine.split(" · ", " • ")
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !DISTANCE.matches(it) }
            ?: maneuverLine
        val (maneuver, road) = splitManeuverRoad(instruction)

        val eta = parseEta(sub.ifEmpty { text })
        val max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val done = extras.getInt(Notification.EXTRA_PROGRESS, 0)

        VehicleBus.postNav(
            VehicleBus.NavInstruction(
                maneuver = maneuver.ifEmpty { "Continue" },
                distance = distance,
                road = road,
                raw = blob,
                etaHour = eta?.first ?: -1,
                etaMinute = eta?.second ?: -1,
                remainingM = if (max > 0) (max - done).coerceAtLeast(0) else -1,
                // Normal guidance never mentions GPS; Maps only does when the fix is gone.
                mapsNoGps = blob.contains("GPS", ignoreCase = true),
            )
        )
        return true
    }

    fun onRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == MAPS_PKG) VehicleBus.clearNav()
    }

    /** "Arrive 6:26 pm" -> (18, 26); "Arrive 18:26" -> (18, 26). */
    private fun parseEta(s: String): Pair<Int, Int>? {
        val m = ETA.find(s) ?: return null
        var h = m.groupValues[1].toIntOrNull() ?: return null
        val min = m.groupValues[2].toIntOrNull() ?: return null
        val ampm = m.groupValues[3].lowercase().filter { it.isLetter() }
        if (ampm == "pm" && h < 12) h += 12
        if (ampm == "am" && h == 12) h = 0
        return if (h in 0..23 && min in 0..59) h to min else null
    }

    /** "Turn right onto MG Road" -> ("Turn right", "MG Road"). Locale-tolerant. */
    private fun splitManeuverRoad(line: String): Pair<String, String> {
        if (line.isBlank()) return "" to ""
        val connectors = listOf(" onto ", " on to ", " on ", " toward ", " towards ", " to ")
        for (c in connectors) {
            val i = line.indexOf(c, ignoreCase = true)
            if (i > 0) return line.substring(0, i).trim() to line.substring(i + c.length).trim()
        }
        return line.trim() to ""
    }
}
