package com.vehiclelink.app.core

/**
 * Tiny newline-delimited line protocol, trivial for an ESP32 to parse.
 * Every frame is ASCII/UTF-8, ends with '\n', fields separated by '|'.
 *
 * Phone -> vehicle:
 *   N|<maneuver>|<distance>|<road>     navigation instruction
 *   NX                                 navigation ended / cleared
 *   M|<1|0>|<title>|<artist>|<posSec>|<durSec>   media state (1=playing)
 *   P|<ms>                             heartbeat / ping
 *
 * Vehicle -> phone (handlebar buttons):
 *   C|NEXT  C|PREV  C|PLAYPAUSE  C|PLAY  C|PAUSE
 */
object Protocol {
    private const val SEP = '|'

    fun frame(s: String): ByteArray = (s + "\n").toByteArray(Charsets.UTF_8)

    fun nav(n: VehicleBus.NavInstruction): ByteArray =
        frame("N$SEP${clean(n.maneuver)}$SEP${clean(n.distance)}$SEP${clean(n.road)}")

    fun navClear(): ByteArray = frame("NX")

    fun media(m: VehicleBus.MediaInfo): ByteArray =
        frame("M$SEP${if (m.playing) 1 else 0}$SEP${clean(m.title)}$SEP${clean(m.artist)}" +
            "$SEP${m.positionMs / 1000}$SEP${m.durationMs / 1000}")

    fun ping(): ByteArray = frame("P$SEP${System.currentTimeMillis()}")

    private fun clean(s: String): String =
        s.replace('|', '/').replace('\n', ' ').replace('\r', ' ').trim().take(64)
}
