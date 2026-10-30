package com.vehiclelink.app.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory hub shared across the whole app (single process):
 *   BLE service        -> connection state, scan results, drains [outbound]
 *   MediaHub           -> media state (and applies vehicle button commands)
 *   Notification svc   -> nav state
 */
object VehicleBus {

    enum class Conn { DISCONNECTED, SCANNING, CONNECTING, CONNECTED }

    data class NavInstruction(
        val maneuver: String,
        val distance: String,
        val road: String,
        val raw: String = "",
        val ts: Long = System.currentTimeMillis(),
    )

    data class MediaInfo(
        val playing: Boolean,
        val title: String,
        val artist: String,
        val positionMs: Long,
        val durationMs: Long,
        val app: String = "",
    )

    data class CallInfo(
        val ringing: Boolean,
        val active: Boolean,
        val name: String,
        /** Off-hook without a preceding ring: the cluster shows it as state '4'. */
        val outgoing: Boolean = false,
    )

    data class ScanDevice(
        val address: String,
        val name: String,
        val rssi: Int,
        val hasVehicleService: Boolean = false,
    )

    private val _conn = MutableStateFlow(Conn.DISCONNECTED)
    val conn: StateFlow<Conn> = _conn.asStateFlow()

    /** Short human-readable status line for the UI (errors, hints). */
    private val _status = MutableStateFlow("Not connected")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _scan = MutableStateFlow<List<ScanDevice>>(emptyList())
    val scan: StateFlow<List<ScanDevice>> = _scan.asStateFlow()

    private val _nav = MutableStateFlow<NavInstruction?>(null)
    val nav: StateFlow<NavInstruction?> = _nav.asStateFlow()

    private val _media = MutableStateFlow<MediaInfo?>(null)
    val media: StateFlow<MediaInfo?> = _media.asStateFlow()

    private val _call = MutableStateFlow<CallInfo?>(null)
    val call: StateFlow<CallInfo?> = _call.asStateFlow()
    fun setCall(c: CallInfo?) { _call.value = c }

    /** GATT services/characteristics discovered on the last connected device. */
    private val _discovered = MutableStateFlow<List<String>>(emptyList())
    val discovered: StateFlow<List<String>> = _discovered.asStateFlow()

    private val _outbound = MutableSharedFlow<ByteArray>(extraBufferCapacity = 128)
    val outbound: SharedFlow<ByteArray> = _outbound.asSharedFlow()

    fun setConn(c: Conn) { _conn.value = c }
    fun setStatus(s: String) { _status.value = s }

    fun setDiscovered(l: List<String>) { _discovered.value = l }
    fun clearDiscovered() { _discovered.value = emptyList() }

    fun setScan(list: List<ScanDevice>) { _scan.value = list }
    fun clearScan() { _scan.value = emptyList() }
    fun upsertScan(d: ScanDevice) {
        val cur = _scan.value.toMutableList()
        val i = cur.indexOfFirst { it.address == d.address }
        if (i >= 0) cur[i] = d else cur.add(d)
        cur.sortByDescending { it.rssi }
        _scan.value = cur
    }

    fun postNav(n: NavInstruction) {
        _nav.value = n
        _outbound.tryEmit(Protocol.nav(n))
    }

    fun clearNav() {
        if (_nav.value != null) {
            _nav.value = null
            _outbound.tryEmit(Protocol.navClear())
        }
    }

    fun postMedia(m: MediaInfo) {
        _media.value = m
        _outbound.tryEmit(Protocol.media(m))
    }

    /** A frame the BLE service should push to the vehicle. */
    fun send(frame: ByteArray) { _outbound.tryEmit(frame) }

    // ---- debug / test tools ----
    private val _debugHold = MutableStateFlow(false)
    val debugHold: StateFlow<Boolean> = _debugHold.asStateFlow()
    fun setDebugHold(b: Boolean) { _debugHold.value = b }

    private val _debugOut = MutableSharedFlow<ByteArray>(extraBufferCapacity = 32)
    val debugOut: SharedFlow<ByteArray> = _debugOut.asSharedFlow()
    fun emitDebug(bytes: ByteArray) { _debugOut.tryEmit(bytes) }

    /** When set, the cluster sender streams THIS packet every cycle (test tool). */
    private val _debugStream = MutableStateFlow<ByteArray?>(null)
    val debugStream: StateFlow<ByteArray?> = _debugStream.asStateFlow()
    fun setDebugStream(b: ByteArray?) { _debugStream.value = b }

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()
    fun addLog(line: String) {
        val l = ArrayList<String>(_log.value)
        l.add(0, line)
        while (l.size > 40) l.removeAt(l.size - 1)
        _log.value = l
    }
    fun clearLog() { _log.value = emptyList() }
}
