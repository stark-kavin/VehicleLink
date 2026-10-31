package com.vehiclelink.app.core

import java.nio.charset.Charset
import java.util.Locale

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

    /** Missed-call counter, type '5': "5" + count (2 digits min), e.g. "\n503\n". Original sendMissCallData. */
    fun missedCalls(count: Int): ByteArray = wrap("5" + "%02d".format(Locale.US, count.coerceAtLeast(0)))

    /** New-SMS counter, type '6': "60" + count, e.g. "\n603\n". Original getSMSText. */
    fun smsCount(count: Int): ByteArray = wrap("60" + count.coerceAtLeast(0))

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
    /** @param noSignal GPS fix lost — raises the cluster's "no signal" flag (original isGpsSignalLost). */
    fun nav(n: VehicleBus.NavInstruction, noSignal: Boolean = false): ByteArray {
        var dir = dirCode(n.maneuver)
        // Original lowercases the arrow when the turn is imminent (< 200 m).
        val meters = distanceMeters(n.distance)
        if (meters in 0 until 200) dir = dir.lowercase()
        val round = exitNumber(n.maneuver + " " + n.road)
        val (dist, unit) = formatDistance(n.distance)
        val (rDist, rUnit) = formatRemaining(n.remainingM)
        return wrap(navBody(dir, dist, unit, eta(n.etaHour, n.etaMinute), round, rDist, rUnit, field(n.road, 18), noSignal))
    }

    /**
     * Final packet when Google Maps stops guiding: 'H' = arrived, 'G' = cancelled. Mirrors the original's
     * handleNavigationFinished / handleNavigationCancelled (distance 00000 m, remainders and road reset).
     */
    fun navEnd(arrived: Boolean, last: VehicleBus.NavInstruction?): ByteArray =
        wrap(navBody(if (arrived) "H" else "G", "00000", "1", eta(last?.etaHour ?: -1, last?.etaMinute ?: -1),
            "00", "00000", "1", "~".repeat(18)))

    /** Nav packet with an explicit direction letter — for the debug arrow-mapping tool. */
    fun navRaw(dir: String, distanceText: String, road: String): ByteArray {
        val (dist, unit) = formatDistance(distanceText)
        return wrap(navBody(dir, dist, unit, eta(-1, -1), "00", "~~~~~", "~", field(road, 18)))
    }

    /**
     * Assemble the 48-byte nav body (original NavigationPacket.toPacketString, music-feature cluster):
     * id dir dist[5] unit etaH[2] mer etaM[2] demer roundabout[2] noSignal remainDist[5] remainUnit
     * remainTime[4] nextDir secondRoundabout[2] road[18]. unit "1" = metres, "k" = km.
     */
    private fun navBody(
        dir: String, dist: String, unit: String, eta: String, roundabout: String,
        remainDist: String, remainUnit: String, road18: String, noSignal: Boolean = false,
    ): String {
        val d = dir.ifBlank { "G" }.substring(0, 1)
        return "1" + d + dist + unit +        // id, binaryDir, distance, unit
            eta +                              // etaHour, meridian, etaMinute, demeridian (6)
            field2(roundabout) + (if (noSignal) "1" else "0") + // roundabout exit, noSignal
            remainDist + remainUnit + "~~~~" + // remainDistance[5], remainUnit[1], remainTime[4] (original: always ~)
            "~" + "04" +                       // nextBinaryDir (original: ~), secondRoundabout (original constant "04")
            road18                             // roadName[18]
    }

    /** "06" + 'p' + "26" + 'm' for 18:26. The music-feature cluster gets lower-case meridian letters. */
    private fun eta(h24: Int, min: Int): String {
        if (h24 !in 0..23 || min !in 0..59) return "~~~~~~"
        val h12 = (h24 % 12).let { if (it == 0) 12 else it }
        return "%02d".format(Locale.US, h12) + (if (h24 >= 12) "p" else "a") + "%02d".format(Locale.US, min) + "m"
    }

    /** Trip remainder: ≤ 999 m -> "%05d" + '1'; else km "%05.1f" (or "%05.0f" when whole) + 'k'. */
    private fun formatRemaining(m: Int): Pair<String, String> {
        if (m < 0) return "~~~~~" to "~"
        if (m <= 999) return "%05d".format(Locale.US, m) to "1"
        val km = Math.round(m / 100.0) / 10.0
        return (if (km % 1.0 == 0.0) "%05.0f" else "%05.1f").format(Locale.US, km) to "k"
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

    /**
     * Maneuver text -> cluster arrow letter. Reproduces the original app's chain
     * routing step -> Mappls maneuver ID (short-instruction templates) -> setDirBinary letter,
     * for left-hand traffic (India). See docs/original-app-analysis.md §13.
     */
    private fun dirCode(maneuver: String): String {
        val t = maneuver.lowercase()
        val left = "left" in t
        val right = "right" in t
        return when {
            "u-turn" in t || "u turn" in t || "uturn" in t -> "P"          // 41
            "roundabout" in t || "rotary" in t -> "U"                       // 65-72 (left-hand traffic)
            "ferry" in t -> "Y"                                             // 36
            "ramp" in t || ("exit" in t && "take" in t) ->                  // on/off ramp 42-44, 73-75
                if (right) "L" else "K"
            "merge" in t -> "V"                                             // 26-31
            "keep" in t || "fork" in t || "bear" in t ->                    // fork/continue slight 15/16/19/20
                if (right) "X" else if (left) "Z" else "G"
            "arrive" in t || "destination" in t ->                          // 8/9/10
                if (left) "I" else if (right) "J" else "G"
            "slight" in t -> if (right) "D" else "C"                        // 2 / 5
            "sharp" in t -> if (right) "F" else "E"                         // 1 / 4
            left -> "I"                                                     // 0, 13
            right -> "J"                                                    // 3, 14
            else -> "G"                                                     // straight / continue / head (7, 21)
        }
    }

    /** "take the 2nd exit" -> "02" (original roundAboutExit: needs both "Nth" and "exit"). */
    private fun exitNumber(text: String): String {
        if (!text.contains("exit", ignoreCase = true)) return "00"
        val m = Regex("""\b([1-9])(st|nd|rd|th)\b""", RegexOption.IGNORE_CASE).find(text)
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
