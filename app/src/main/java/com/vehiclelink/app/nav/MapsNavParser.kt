package com.vehiclelink.app.nav

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.vehiclelink.app.core.VehicleBus

/**
 * Extracts turn-by-turn data from the Google Maps ongoing navigation notification.
 *
 * Google Maps exposes no public "current guidance" API, but while it is guiding it
 * posts an ongoing notification whose extras carry the next maneuver, the distance
 * to it and the road. Layout varies by locale/version, so we parse defensively and
 * also keep the raw text so the vehicle side can fall back to displaying it verbatim.
 */
object MapsNavParser {

    const val MAPS_PKG = "com.google.android.apps.maps"

    private val DISTANCE = Regex(
        """\d+[.,]?\d*\s?(?:km|m|mi|ft|yd)\b""",
        RegexOption.IGNORE_CASE
    )

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

        // Heuristic field split:
        //  - Maps usually puts the maneuver/road in the title ("Turn right onto MG Road")
        //    and the distance+ETA in the text ("450 m · 12 min").
        val distance = DISTANCE.find(blob)?.value?.replace(",", ".")?.trim().orEmpty()

        val maneuverLine = title.ifEmpty { text }
        val (maneuver, road) = splitManeuverRoad(maneuverLine)

        VehicleBus.postNav(
            VehicleBus.NavInstruction(
                maneuver = maneuver.ifEmpty { "Continue" },
                distance = distance,
                road = road,
                raw = blob,
            )
        )
        return true
    }

    fun onRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == MAPS_PKG) VehicleBus.clearNav()
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
