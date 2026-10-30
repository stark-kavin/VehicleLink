package com.vehiclelink.app.core

import java.nio.charset.Charset

/**
 * Builds the Hero cluster's ASCII (Windows-1252) packets. See PROTOCOL.md.
 * Each packet is wrapped "\n" + body + "\n" and written to the Data-RX characteristic.
 */
object ClusterProtocol {
    private val CP1252: Charset = Charset.forName("windows-1252")

    /** Music packet, type '9'. 48 bytes (wrapped). */
    fun music(m: VehicleBus.MediaInfo?, volumePercent: Int): ByteArray {
        val song = field20(m?.title, "Unknown")
        val artist = field20(m?.artist, "Unknown")
        val play = if (m?.playing == true) "0" else "1"
        val prog = progressChar(m?.positionMs ?: 0L, m?.durationMs ?: 0L)
        val vol = volumeChar(volumePercent)
        val body = "9" + song + artist + "00" + play + prog + vol
        return wrap(body)
    }

    /**
     * Call packet, type '7' (48 bytes wrapped, same as music). Mirrors the original SendDataToCluster;
     * its '~' padder overshoots by one, so names are 18 chars and the duration 6:
     *   ringing  (1): 7 name[18] 1 ~[18]     0 ~[6]    d
     *   active   (2): 7 name[18] 2 name[18]  0 time[6] vol
     *   outgoing (4): 7 name[18] 4 name[18]  0 time[6] d
     * time = "0" + H + MM + SS, e.g. 1m05s -> "000105". See docs/original-app-analysis.md §7.
     */
    fun call(
        name: String, ringing: Boolean, voiceVolumePercent: Int, durationSec: Int, outgoing: Boolean = false,
    ): ByteArray {
        val who = callName(name)
        return when {
            ringing -> wrap("7" + who + "1" + "~".repeat(18) + "0" + "~".repeat(6) + "d")
            outgoing -> wrap("7" + who + "4" + who + "0" + formatDur(durationSec) + "d")
            else -> wrap("7" + who + "2" + who + "0" + formatDur(durationSec) + volumeChar(voiceVolumePercent))
        }
    }

    /** "No active call" packet, sent once when a call ends (state 0). */
    fun callNone(): ByteArray =
        wrap("7" + "~".repeat(18) + "0" + "~".repeat(18) + "0" + "~".repeat(6) + "d")

    /**
     * Phone status packet, type '3' (23 bytes wrapped): battery bucket, signal level 0..4, user name[18].
     * Battery: 0-20 '0', 21-39 '1', 40-59 '2', 60-79 '3', 80+ '4'.
     */
    fun phoneStatus(batteryPercent: Int, signalLevel: Int, userName: String): ByteArray {
        val batt = when {
            batteryPercent <= 20 -> '0'
            batteryPercent < 40 -> '1'
            batteryPercent < 60 -> '2'
            batteryPercent < 80 -> '3'
            else -> '4'
        }
        val sig = signalLevel.coerceIn(0, 4)
        val name = userName.take(15)
        return wrap("3" + batt + sig + name + "~".repeat(18 - name.length))
    }

    /** Caller name: keep only [A-Za-z0-9 ], cap to 17, '~'-pad to 18 (matches original). */
    private fun callName(name: String): String {
        val cleaned = name.replace(Regex("[^A-Za-z0-9 ]"), "").ifBlank { "Unknown" }
        val capped = if (cleaned.length > 17) cleaned.substring(0, 17) else cleaned
        return capped + "~".repeat(18 - capped.length)
    }

    /** "0" + H + MM + SS — 6 chars for calls under 10 hours. */
    private fun formatDur(sec: Int): String =
        "0" + "%d%02d%02d".format(sec / 3600, (sec % 3600) / 60, sec % 60)

    private fun field(value: String, n: Int): String {
        var s = keepSafeAscii(value)
        if (s.length > n) s = s.substring(0, n)
        if (s.length < n) s = s + "~".repeat(n - s.length)
        return s
    }

    /**
     * Navigation packet, type '1'. 16 fixed-width fields concatenated (NO separators),
     * then wrapped in \n..\n  — mirrors the original NavigationPacket.toPacketString().
     * Body = id(1) dir(1) dist(5) unit(1) etaH(2) mer(1) etaM(2) demer(1) round(2)
     *        noSig(1) remainDist(5) remainUnit(1) remainTime(4) nextDir(1) round2(2) road(18) = 48.
     */
    fun nav(n: VehicleBus.NavInstruction): ByteArray {
        var dir = dirCode(n.maneuver)
        // Original lowercases the arrow when the turn is imminent (< 200 m).
        val meters = distanceMeters(n.distance)
        if (meters in 1 until 200) dir = dir.lowercase()
        val round = exitNumber(n.maneuver + " " + n.road)
        return wrap(navBody(dir, n.distance, n.road, round))
    }

    /** Nav packet with an explicit direction letter — for the debug arrow-mapping tool. */
    fun navRaw(dir: String, distanceText: String, road: String): ByteArray =
        wrap(navBody(dir, distanceText, road, "00"))

    /** Assemble the 48-byte nav body. unit: "1"=metres, "k"=km (matches original). */
    private fun navBody(dir: String, distanceText: String, road: String, roundabout: String): String {
        val (dist, unit) = formatDistance(distanceText)
        val d = dir.ifBlank { "I" }.substring(0, 1)
        val rb = field2(roundabout)
        return "1" + d + dist + unit +     // id, binaryDir, distance, unit
            "12" + "A" + "00" + "M" +        // etaHour, meridian, etaMinute, demeridian
            rb + "0" +                        // roundabout exit, noSignal (GPS ok)
            "~~~~~" + "~" + "~~~~" +         // remainDistance[5], remainUnit[1], remainTime[4]
            "~" + "00" +                      // nextBinaryDir[1], secondRoundabout[2]
            field(road, 18)                   // roadName[18]
    }

    /** "450 m" -> ("00450","1");  "1.2 km" -> ("001.2","k"). unit "1"=metres, "k"=km. */
    private fun formatDistance(text: String): Pair<String, String> {
        val m = Regex("""(\d+[.,]?\d*)\s*(km|m|mi|ft|yd)""", RegexOption.IGNORE_CASE).find(text)
            ?: return "00000" to "1"
        val digits = m.groupValues[1].replace(",", ".")
        val isKm = m.groupValues[2].lowercase() == "km"
        return pad5(digits) to if (isKm) "k" else "1"
    }

    /** Distance to the turn in metres (km converted), or -1 if unknown. */
    private fun distanceMeters(text: String): Int {
        val m = Regex("""(\d+[.,]?\d*)\s*(km|m|mi|ft|yd)""", RegexOption.IGNORE_CASE).find(text) ?: return -1
        val v = m.groupValues[1].replace(",", ".").toDoubleOrNull() ?: return -1
        return if (m.groupValues[2].lowercase() == "km") (v * 1000).toInt() else v.toInt()
    }

    /** Left-pad digits to exactly 5 chars (keeps a decimal point, e.g. "1.2" -> "001.2"). */
    private fun pad5(d: String): String =
        if (d.length >= 5) d.takeLast(5) else "0".repeat(5 - d.length) + d

    /** Exactly 2 chars. */
    private fun field2(s: String): String = when {
        s.length == 2 -> s
        s.length > 2 -> s.substring(0, 2)
        s.length == 1 -> "0" + s
        else -> "00"
    }

    /** Maneuver text -> cluster arrow letter (matches the original setDirBinary letters). */
    private fun dirCode(maneuver: String): String {
        val t = maneuver.lowercase()
        return when {
            "u-turn" in t || "u turn" in t -> "Z"
            "roundabout" in t || "rotary" in t -> "B"
            "arrive" in t || "destination" in t -> "H"
            "slight left" in t -> "E"
            "slight right" in t -> "F"
            "sharp left" in t -> "C"
            "sharp right" in t -> "D"
            "left" in t -> "C"
            "right" in t -> "D"
            "merge" in t || "ramp" in t || "exit" in t -> "V"
            "head" in t || "straight" in t || "continue" in t -> "I"
            else -> "I"
        }
    }

    private fun exitNumber(text: String): String {
        val m = Regex("""([1-9])(st|nd|rd|th)""", RegexOption.IGNORE_CASE).find(text)
        return if (m != null) "0" + m.groupValues[1] else "00"
    }

    /** Send an arbitrary body (wrapped in \n..\n). */
    fun rawPacket(body: String): ByteArray = wrap(body)

    /** Printable preview of a packet for the on-screen log. */
    fun preview(bytes: ByteArray): String = buildString {
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            append(if (c in 32..126) c.toChar() else '.')
        }
    }

    private fun wrap(body: String): ByteArray = ("\n" + body + "\n").toByteArray(CP1252)

    /** Music text: drop anything outside [A-Za-z0-9 +-._] (original), exactly 20 chars, '~'-padded. */
    private fun field20(value: String?, fallback: String): String {
        var s = (value ?: "").replace(Regex("[^A-Za-z0-9 +\\-._]"), "")
        if (s.isBlank()) s = fallback
        s = if (s.length > 20) s.substring(0, 20) else s
        if (s.length < 20) s = s + "~".repeat(20 - s.length)
        return s
    }

    private fun keepSafeAscii(input: String): String = buildString {
        for (c in input) append(if (c.code in 32..126) c else '?')
    }

    /**
     * Track progress byte = percent played, 0..100, as a raw byte value (original: position*100/duration,
     * written to offset 45). 'd' (= 100) when the duration is unknown, as in the original.
     */
    private fun progressChar(posMs: Long, durMs: Long): Char {
        if (durMs <= 0L) return 'd'
        return (posMs * 100 / durMs).toInt().coerceIn(0, 100).toChar()
    }

    /** Stream volume percent -> cluster volume char 'd'..'n' (original ContextExtKt.O boundaries). */
    private fun volumeChar(p: Int): Char = when {
        p in 1..5 -> 'd'
        p in 6..10 -> 'e'
        p in 11..15 -> 'f'
        p in 16..40 -> 'g'
        p in 41..50 -> 'h'
        p in 51..60 -> 'i'
        p in 61..70 -> 'j'
        p in 71..80 -> 'k'
        p in 81..90 -> 'l'
        p in 91..95 -> 'm'
        p in 96..100 -> 'n'
        else -> 'd'
    }
}
