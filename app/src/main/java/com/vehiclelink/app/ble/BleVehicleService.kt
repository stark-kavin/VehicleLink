package com.vehiclelink.app.ble

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import com.vehiclelink.app.MainActivity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.Settings
import android.telephony.TelephonyManager
import android.content.pm.PackageManager
import android.media.AudioManager
import android.telecom.TelecomManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.vehiclelink.app.R
import com.vehiclelink.app.core.ClusterProtocol
import com.vehiclelink.app.core.Protocol
import com.vehiclelink.app.core.VehicleBus
import com.vehiclelink.app.media.MediaHub
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BleVehicleService : LifecycleService() {

    private val adapter: BluetoothAdapter? by lazy {
        (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }
    private val audio: AudioManager? by lazy {
        getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    private val telecom: TelecomManager? by lazy {
        getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
    }
    private var callStartMs = 0L
    private var gatt: BluetoothGatt? = null
    private var scanning = false
    private val main = Handler(Looper.getMainLooper())
    private var targetAddress: String? = null
    private var filterPrefix: String = BleConstants.DEFAULT_NAME_FILTER

    // Auto-reconnect (mirrors the original): MAC-filtered scan with no timeout, connect 2 s after a hit.
    // Both publish to VehicleBus.reconnecting so the UI can say "Waiting for your bike".
    private var reconnectScanning = false
        set(v) { field = v; VehicleBus.setReconnecting(v || reconnectPending) }
    private var reconnectPending = false
        set(v) { field = v; VehicleBus.setReconnecting(v || reconnectScanning) }
    private val prefs by lazy { getSharedPreferences("vehicle_link", Context.MODE_PRIVATE) }

    private val adapterStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                BluetoothAdapter.STATE_OFF -> {
                    // Scanner and GATT are invalid now; keep targetAddress so we resume on ON.
                    reconnectScanning = false; reconnectPending = false
                    scanning = false
                    cleanupGatt()
                    VehicleBus.setConn(VehicleBus.Conn.DISCONNECTED)
                    VehicleBus.setStatus("Bluetooth off — will reconnect when it's back on")
                }
                BluetoothAdapter.STATE_ON -> if (targetAddress != null) startReconnectScan()
            }
        }
    }

    // ESP32 UART
    private var uartRx: BluetoothGattCharacteristic? = null
    private val rxBuffer = StringBuilder()

    // Hero cluster (TIO)
    private var clusterMode = false
    private var cDataRx: BluetoothGattCharacteristic? = null
    private var cCreditsRx: BluetoothGattCharacteristic? = null
    private var cDataTx: BluetoothGattCharacteristic? = null
    private var cCreditsTx: BluetoothGattCharacteristic? = null
    private enum class Tio { NONE, INDICATE, NOTIFY, READY }
    private var tio = Tio.NONE
    private var senderRunning = false

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        MediaHub.ensureStarted(this)
        running = true
        VehicleLinkTileService.refresh(this)
        registerReceiver(adapterStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        // A restarted (START_STICKY) service resumes reconnecting to the last bike.
        targetAddress = prefs.getString(PREF_LAST_ADDRESS, null)
        if (targetAddress != null && VehicleBus.conn.value == VehicleBus.Conn.DISCONNECTED) startReconnectScan()
        lifecycleScope.launch { VehicleBus.outbound.collectLatest { if (!clusterMode) enqueueUart(it) } }
        lifecycleScope.launch {
            VehicleBus.debugOut.collectLatest {
                if (clusterMode) { clusterWrite(it); VehicleBus.addLog("TX " + ClusterProtocol.preview(it)) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_SCAN -> { filterPrefix = intent.getStringExtra(EXTRA_FILTER) ?: ""; startScan() }
            // User cancelled the search: drop straight back to idle instead of waiting for the 12 s timer.
            ACTION_STOP_SCAN -> {
                stopScan()
                if (VehicleBus.conn.value == VehicleBus.Conn.SCANNING) {
                    VehicleBus.setConn(VehicleBus.Conn.DISCONNECTED)
                    VehicleBus.setStatus("Not connected")
                }
            }
            ACTION_CONNECT -> intent.getStringExtra(EXTRA_ADDRESS)?.let { connectAddress(it) }
            // Quick Settings tile "on": go back to the last bike; if it's off, the reconnect scan waits for it.
            ACTION_RESUME -> lastBike(this)?.let { if (gatt == null && !reconnectPending) connectAddress(it) }
            ACTION_DISCONNECT -> teardown()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent) = super.onBind(intent)

    override fun onDestroy() {
        try { unregisterReceiver(adapterStateReceiver) } catch (_: Exception) {}
        stopReconnectScan()
        running = false
        VehicleLinkTileService.refresh(this)
        super.onDestroy()
    }

    // ---- scanning ------------------------------------------------------------
    @SuppressLint("MissingPermission")
    private fun startScan() {
        val a = adapter ?: run { VehicleBus.setStatus("No Bluetooth adapter"); return }
        if (!a.isEnabled) { VehicleBus.setStatus("Turn on Bluetooth"); return }
        if (!hasBlePerms()) { VehicleBus.setStatus("Grant Bluetooth permission"); return }
        if (scanning) return
        VehicleBus.clearScan()
        VehicleBus.setConn(VehicleBus.Conn.SCANNING)
        VehicleBus.setStatus(
            if (filterPrefix.isBlank()) "Scanning (all devices)…" else "Scanning for \"$filterPrefix…\"…"
        )
        scanning = true
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        a.bluetoothLeScanner?.startScan(null, settings, scanCallback)
        main.postDelayed({
            stopScan()
            if (VehicleBus.conn.value == VehicleBus.Conn.SCANNING) {
                VehicleBus.setConn(VehicleBus.Conn.DISCONNECTED)
                VehicleBus.setStatus(
                    if (VehicleBus.scan.value.isEmpty())
                        (if (filterPrefix.isBlank()) "No devices found"
                         else "No \"$filterPrefix…\" device — toggle Show all, or power the bike on")
                    else "Tap a device to connect"
                )
            }
        }, 12_000)
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        if (!scanning) return
        scanning = false
        if (hasBlePerms()) adapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val dev = result.device ?: return
            val name = (if (hasBlePerms()) dev.name else null) ?: result.scanRecord?.deviceName ?: ""
            val hasCluster = result.scanRecord?.serviceUuids
                ?.any { it.uuid == BleConstants.CLUSTER_SERVICE || it.uuid == BleConstants.UART_SERVICE } == true
            val pass = filterPrefix.isBlank() || name.startsWith(filterPrefix, true) || hasCluster
            if (!pass) return
            VehicleBus.upsertScan(
                VehicleBus.ScanDevice(dev.address, name.ifBlank { "(unnamed)" }, result.rssi, hasCluster)
            )
        }
    }

    // ---- auto-reconnect --------------------------------------------------------
    /**
     * Wait for the bike to advertise again: a scan filtered to its MAC, no timeout (the original's
     * reScanForAutoConnect). A connectGatt retry would time out after ~30 s while the bike is off.
     */
    @SuppressLint("MissingPermission")
    private fun startReconnectScan() {
        val addr = targetAddress ?: return
        val a = adapter ?: return
        if (!a.isEnabled || !hasBlePerms() || reconnectScanning || reconnectPending) return
        val filter = ScanFilter.Builder().setDeviceAddress(addr).build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_FEW_ADVERTISEMENT)
            .setReportDelay(0)
            .build()
        try {
            a.bluetoothLeScanner?.startScan(listOf(filter), settings, reconnectCallback) ?: return
            reconnectScanning = true
            VehicleBus.setStatus("Waiting for the bike to power on…")
        } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    private fun stopReconnectScan() {
        if (!reconnectScanning) return
        reconnectScanning = false
        try { if (hasBlePerms()) adapter?.bluetoothLeScanner?.stopScan(reconnectCallback) } catch (_: Exception) {}
    }

    private val reconnectCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val addr = targetAddress ?: return
            if (!result.device.address.equals(addr, true) || reconnectPending) return
            stopReconnectScan()
            reconnectPending = true
            VehicleBus.setStatus("Bike found — reconnecting…")
            main.postDelayed({
                reconnectPending = false
                if (targetAddress == addr) connectAddress(addr)
            }, 2_000)
        }

        override fun onScanFailed(errorCode: Int) {
            reconnectScanning = false
            // e.g. SCAN_FAILED_APPLICATION_REGISTRATION_FAILED / too many starts: try again shortly.
            main.postDelayed({ startReconnectScan() }, 5_000)
        }
    }

    // ---- connection ----------------------------------------------------------
    @SuppressLint("MissingPermission")
    private fun connectAddress(address: String) {
        if (!hasBlePerms()) { VehicleBus.setStatus("Grant Bluetooth permission"); return }
        val a = adapter ?: return
        stopScan(); stopReconnectScan(); cleanupGatt()
        VehicleBus.clearDiscovered()
        targetAddress = address
        prefs.edit().putString(PREF_LAST_ADDRESS, address).putString(PREF_LAST_BIKE, address).apply()
        val device: BluetoothDevice = try { a.getRemoteDevice(address) }
        catch (e: IllegalArgumentException) { VehicleBus.setStatus("Bad device address"); return }
        VehicleBus.setConn(VehicleBus.Conn.CONNECTING)
        VehicleBus.setStatus("Connecting to ${device.name ?: address}…")
        gatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && hasBlePerms()) {
                // Original order: settle 1 s, discover services, then MTU, then the TIO handshake.
                main.postDelayed({ if (gatt === g && hasBlePerms()) g.discoverServices() }, 1_000)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (g !== gatt) { if (hasBlePerms()) g.close(); return } // stale callback
                VehicleBus.setConn(VehicleBus.Conn.DISCONNECTED)
                VehicleBus.setStatus("Disconnected (status $status)")
                cleanupGatt()
                // Not a user disconnect (teardown clears targetAddress): wait for the bike to advertise.
                // Covers both a dropped link and a failed connect attempt.
                main.post { startReconnectScan() }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            // Cluster: services are known, MTU settled → start the TIO handshake.
            if (clusterMode && tio == Tio.NONE && hasBlePerms()) startTioHandshake(g)
            else if (!clusterMode && uartRx != null) onUartReady(g)
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS || !hasBlePerms()) return
            publishGattMap(g)

            val cluster = g.getService(BleConstants.CLUSTER_SERVICE)
            val uart = g.getService(BleConstants.UART_SERVICE)
            when {
                cluster != null -> {
                    clusterMode = true
                    cDataRx = cluster.getCharacteristic(BleConstants.CLUSTER_DATA_RX)
                    cCreditsRx = cluster.getCharacteristic(BleConstants.CLUSTER_CREDITS_RX)
                    cDataTx = cluster.getCharacteristic(BleConstants.CLUSTER_DATA_TX)
                    cCreditsTx = cluster.getCharacteristic(BleConstants.CLUSTER_CREDITS_TX)
                    VehicleBus.setStatus("Cluster found — handshaking…")
                    if (!g.requestMtu(BleConstants.DEFAULT_MTU)) startTioHandshake(g)
                }
                uart != null -> {
                    uartRx = uart.getCharacteristic(BleConstants.UART_RX_WRITE)
                    if (!g.requestMtu(BleConstants.DEFAULT_MTU)) onUartReady(g) // else in onMtuChanged
                }
                else -> {
                    VehicleBus.setConn(VehicleBus.Conn.CONNECTED)
                    VehicleBus.setStatus("Connected. No known service — see discovered GATT below.")
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            if (!clusterMode || !hasBlePerms()) return
            when (tio) {
                Tio.INDICATE -> { // credits-TX indicate enabled → enable data-TX notify
                    tio = Tio.NOTIFY
                    cDataTx?.let { enableNotify(g, it, false) }
                }
                Tio.NOTIFY -> {   // data-TX notify enabled → grant credits, go live
                    tio = Tio.READY
                    grantCredits(g)
                    VehicleBus.setConn(VehicleBus.Conn.CONNECTED)
                    VehicleBus.setStatus("Connected to cluster")
                    startMusicSender()
                }
                else -> {}
            }
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray
        ) { onInbound(c, value) }

        @Deprecated("Pre-33")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION") onInbound(c, c.value ?: ByteArray(0))
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (!clusterMode) uartPump() // ESP32 path uses write-ack pacing
        }
    }

    @SuppressLint("MissingPermission")
    private fun onUartReady(g: BluetoothGatt) {
        g.getService(BleConstants.UART_SERVICE)?.getCharacteristic(BleConstants.UART_TX_NOTIFY)
            ?.let { enableNotify(g, it, false) }
        VehicleBus.setConn(VehicleBus.Conn.CONNECTED)
        VehicleBus.setStatus("Connected (ESP32)")
    }

    // ---- TIO handshake -------------------------------------------------------
    @SuppressLint("MissingPermission")
    private fun startTioHandshake(g: BluetoothGatt) {
        tio = Tio.INDICATE
        cCreditsTx?.let { enableNotify(g, it, indicate = true) }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotify(g: BluetoothGatt, c: BluetoothGattCharacteristic, indicate: Boolean) {
        if (!hasBlePerms()) return
        g.setCharacteristicNotification(c, true)
        val value = if (indicate) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        c.getDescriptor(BleConstants.CCCD)?.let { d ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) g.writeDescriptor(d, value)
            else { @Suppress("DEPRECATION") d.value = value; @Suppress("DEPRECATION") g.writeDescriptor(d) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun grantCredits(g: BluetoothGatt) {
        val c = cCreditsRx ?: return
        if (!hasBlePerms()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(c, byteArrayOf(BleConstants.CLUSTER_CREDITS.toByte()),
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION") c.setValue(BleConstants.CLUSTER_CREDITS,
                BluetoothGattCharacteristic.FORMAT_UINT8, 0)
            @Suppress("DEPRECATION") g.writeCharacteristic(c)
        }
    }

    @SuppressLint("MissingPermission")
    private fun clusterWrite(bytes: ByteArray) {
        val g = gatt ?: return; val c = cDataRx ?: return
        if (!hasBlePerms()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(c, bytes, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
        } else {
            @Suppress("DEPRECATION") c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            @Suppress("DEPRECATION") c.value = bytes
            @Suppress("DEPRECATION") g.writeCharacteristic(c)
        }
    }

    private fun volumePercent(): Int {
        val a = audio ?: return 0
        val max = a.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val cur = a.getStreamVolume(AudioManager.STREAM_MUSIC)
        return if (max > 0) cur * 100 / max else 0
    }

    private val telephony: TelephonyManager? by lazy {
        getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    }

    private fun batteryPercent(): Int {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return 0
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level >= 0 && scale > 0) level * 100 / scale else 0
    }

    /** SignalStrength level 0..4 (same source as the original's getLevel()). */
    private fun signalLevel(): Int = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) telephony?.signalStrength?.level ?: 0 else 0
    } catch (_: Exception) { 0 }

    /** Name shown on the cluster's welcome field; the original uses the Hero profile name. */
    private fun riderName(): String =
        Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME)
            ?.replace(Regex("[^A-Za-z0-9 ]"), "")?.ifBlank { null } ?: Build.MODEL.orEmpty()

    /**
     * 1 Hz: push a call packet if a call is up, else the music packet.
     * Phone status (type 3) is sent like the original: on connect, on a battery-bucket or signal change,
     * and after a call ends — each time twice, ~2 s and ~5 s later, in place of that tick's music packet.
     */
    private fun startMusicSender() {
        if (senderRunning) return
        senderRunning = true
        lifecycleScope.launch {
            var tick = 0
            var callWasUp = false
            var lastStatus: Pair<Int, Int>? = null
            val statusDue = ArrayList<Long>()
            fun queueStatus() {
                val now = System.currentTimeMillis()
                statusDue.add(now + 2_000); statusDue.add(now + 5_000)
            }
            queueStatus()
            while (clusterMode && gatt != null) {
                try {
                    val stream = VehicleBus.debugStream.value
                    if (stream != null) {
                        clusterWrite(stream)
                        if (tick % 8 == 7) gatt?.let { grantCredits(it) }
                        if (tick % 6 == 0) VehicleBus.addLog("TX▶ " + ClusterProtocol.preview(stream))
                        tick++; delay(800); continue
                    }
                    if (VehicleBus.debugHold.value) {
                        if (tick % 8 == 7) gatt?.let { grantCredits(it) }
                        tick++; delay(800); continue
                    }
                    MediaHub.refresh()
                    val call = VehicleBus.call.value
                    val batt = batteryPercent()
                    val status = (batt.coerceIn(0, 100).let {
                        when { it <= 20 -> 0; it < 40 -> 1; it < 60 -> 2; it < 80 -> 3; else -> 4 }
                    }) to signalLevel()
                    if (status != lastStatus) { if (lastStatus != null) queueStatus(); lastStatus = status }
                    if (call != null && (call.ringing || call.active)) {
                        callWasUp = true
                        if (call.active && callStartMs == 0L) callStartMs = System.currentTimeMillis()
                        if (!call.active) callStartMs = 0L
                        val durSec = if (callStartMs > 0L)
                            ((System.currentTimeMillis() - callStartMs) / 1000).toInt() else 0
                        clusterWrite(
                            ClusterProtocol.call(call.name, call.ringing, voiceVolPercent(), durSec, call.outgoing)
                        )
                    } else if (callWasUp) {
                        // Call just ended: tell the cluster (original onCallReject → sendNoActiveData).
                        callWasUp = false
                        callStartMs = 0L
                        clusterWrite(ClusterProtocol.callNone())
                        queueStatus()
                    } else if (statusDue.isNotEmpty() && statusDue.first() <= System.currentTimeMillis()) {
                        statusDue.removeAt(0)
                        clusterWrite(ClusterProtocol.phoneStatus(batt, status.second, riderName()))
                    } else {
                        callStartMs = 0L
                        val nav = VehicleBus.nav.value
                        val navFresh = nav != null && System.currentTimeMillis() - nav.ts < 12_000
                        if (navFresh) {
                            // Cluster shows nav while guiding; refresh the music buffer every
                            // few ticks so the music screen is not stale when the rider checks it.
                            clusterWrite(ClusterProtocol.nav(nav!!))
                            if (tick % 4 == 0) clusterWrite(ClusterProtocol.music(VehicleBus.media.value, volumePercent()))
                        } else {
                            clusterWrite(ClusterProtocol.music(VehicleBus.media.value, volumePercent()))
                        }
                    }
                    if (tick % 8 == 7) gatt?.let { grantCredits(it) }
                } catch (_: Exception) {}
                tick++
                delay(800)
            }
            senderRunning = false
        }
    }

    private fun voiceVolPercent(): Int {
        val a = audio ?: return 0
        val max = a.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
        val cur = a.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
        return if (max > 0) cur * 100 / max else 0
    }

    private fun cmdName(cmd: Int): String = when (cmd) {
        35 -> "play/pause"; 40 -> "next"; 45 -> "prev"; 65 -> "music vol+"; 70 -> "music vol-"
        50 -> "mute"; 55 -> "call vol+"; 60 -> "call vol-"; 10 -> "accept call"; 15 -> "reject call"
        53 -> "keepalive"; else -> "?"
    }

    @SuppressLint("MissingPermission")
    private fun handleClusterCommand(cmd: Int) {
        when (cmd) {
            35 -> MediaHub.apply("PLAYPAUSE")
            40 -> MediaHub.apply("NEXT")       // '('
            45 -> MediaHub.apply("PREV")       // '-'
            65 -> audio?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 1)  // 'A'
            70 -> audio?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 1)  // 'F'
            50 -> audio?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 1) // '2'
            55 -> audio?.adjustStreamVolume(AudioManager.STREAM_VOICE_CALL, AudioManager.ADJUST_RAISE, 0)  // '7'
            60 -> audio?.adjustStreamVolume(AudioManager.STREAM_VOICE_CALL, AudioManager.ADJUST_LOWER, 0)  // '<'
            10 -> acceptCall()
            15 -> endCall()
            else -> {}
        }
    }

    @SuppressLint("MissingPermission")
    private fun acceptCall() {
        if (checkSelfPermission(android.Manifest.permission.ANSWER_PHONE_CALLS) !=
            PackageManager.PERMISSION_GRANTED) return
        try { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) telecom?.acceptRingingCall() } catch (_: Exception) {}
    }

    @SuppressLint("MissingPermission")
    private fun endCall() {
        if (checkSelfPermission(android.Manifest.permission.ANSWER_PHONE_CALLS) !=
            PackageManager.PERMISSION_GRANTED) return
        try { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) telecom?.endCall() } catch (_: Exception) {}
    }

    private fun onInbound(c: BluetoothGattCharacteristic, value: ByteArray) {
        // Cluster → phone (handlebar). Call-control bytes: value[2]=="10" accept, "15" end.
        // Music buttons also arrive here; mapping TBD once validated. Log for now.
        if (clusterMode && c.uuid == BleConstants.CLUSTER_DATA_TX) {
            // Handlebar command: 3rd byte (index 2) is the command code. See PROTOCOL.md.
            if (value.size > 2) {
                val cmd = value[2].toInt() and 0xFF
                VehicleBus.addLog("RX cmd=$cmd ${cmdName(cmd)}  [" + ClusterProtocol.preview(value) + "]")
                handleClusterCommand(cmd)
            }
            return
        }
        if (!clusterMode) { // ESP32 line protocol
            rxBuffer.append(String(value, Charsets.UTF_8))
            var idx = rxBuffer.indexOf("\n")
            while (idx >= 0) {
                val line = rxBuffer.substring(0, idx).trim()
                rxBuffer.delete(0, idx + 1)
                if (line.startsWith("C|")) MediaHub.apply(line.removePrefix("C|"))
                idx = rxBuffer.indexOf("\n")
            }
        }
    }

    // ---- ESP32 UART write pump ----------------------------------------------
    private val uartQueue = java.util.concurrent.ConcurrentLinkedQueue<ByteArray>()
    private var uartWriting = false
    private fun enqueueUart(frame: ByteArray) {
        val max = (BleConstants.DEFAULT_MTU - 3).coerceAtLeast(20)
        var off = 0
        while (off < frame.size) { val e = minOf(off + max, frame.size); uartQueue.add(frame.copyOfRange(off, e)); off = e }
        uartPump()
    }
    @SuppressLint("MissingPermission")
    private fun uartPump() {
        val g = gatt ?: return; val c = uartRx ?: return
        if (uartWriting || !hasBlePerms()) return
        val chunk = uartQueue.poll() ?: return
        uartWriting = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            g.writeCharacteristic(c, chunk, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
        else { @Suppress("DEPRECATION") c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            @Suppress("DEPRECATION") c.value = chunk; @Suppress("DEPRECATION") g.writeCharacteristic(c) }
        main.postDelayed({ uartWriting = false; uartPump() }, 30)
    }

    @SuppressLint("MissingPermission")
    private fun publishGattMap(g: BluetoothGatt) {
        val lines = ArrayList<String>()
        for (s in g.services) {
            lines.add("SVC ${s.uuid}")
            for (c in s.characteristics) {
                val p = c.properties
                val f = buildString {
                    if (p and BluetoothGattCharacteristic.PROPERTY_READ != 0) append("R")
                    if (p and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) append("W")
                    if (p and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) append("w")
                    if (p and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) append("N")
                    if (p and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) append("I")
                }
                lines.add("   • ${c.uuid} [$f]")
            }
        }
        VehicleBus.setDiscovered(lines)
    }

    @SuppressLint("MissingPermission")
    private fun cleanupGatt() {
        uartRx = null; uartQueue.clear(); uartWriting = false
        clusterMode = false; tio = Tio.NONE; senderRunning = false
        cDataRx = null; cCreditsRx = null; cDataTx = null; cCreditsTx = null
        if (hasBlePerms()) gatt?.close()
        gatt = null
    }

    @SuppressLint("MissingPermission")
    private fun teardown() {
        // User disconnect: forget the bike so nothing (incl. a sticky restart) reconnects to it.
        targetAddress = null
        prefs.edit().remove(PREF_LAST_ADDRESS).apply()
        main.removeCallbacksAndMessages(null)
        reconnectPending = false
        stopScan(); stopReconnectScan(); cleanupGatt()
        VehicleBus.setConn(VehicleBus.Conn.DISCONNECTED)
        VehicleBus.setStatus("Not connected")
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun hasBlePerms(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        }
        return checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Vehicle link", NotificationManager.IMPORTANCE_LOW))
        }
        val turnOff = PendingIntent.getService(
            this, 1, Intent(this, BleVehicleService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val open = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n: Notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("VehicleLink").setContentText("Linked to vehicle")
            .setSmallIcon(R.drawable.ic_launcher_foreground).setOngoing(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Turn off", turnOff).build())
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, n, type)
    }

    companion object {
        const val ACTION_SCAN = "com.vehiclelink.app.SCAN"
        const val ACTION_STOP_SCAN = "com.vehiclelink.app.STOP_SCAN"
        const val ACTION_CONNECT = "com.vehiclelink.app.CONNECT"
        const val ACTION_DISCONNECT = "com.vehiclelink.app.DISCONNECT"
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_FILTER = "filter"
        private const val CHANNEL = "vehicle_link"
        private const val NOTIF_ID = 42
        const val ACTION_RESUME = "com.vehiclelink.app.RESUME"
        private const val PREF_LAST_ADDRESS = "last_address" // auto-reconnect target; cleared on Turn off
        private const val PREF_LAST_BIKE = "last_bike"       // kept so the tile can turn the link back on

        /** True while the service exists; read by the Quick Settings tile. */
        @Volatile var running = false
            private set

        fun lastBike(ctx: Context): String? =
            ctx.getSharedPreferences("vehicle_link", Context.MODE_PRIVATE).getString(PREF_LAST_BIKE, null)

        fun resume(ctx: Context) = ctx.startForegroundService(
            Intent(ctx, BleVehicleService::class.java).setAction(ACTION_RESUME))

        fun scan(ctx: Context, filterPrefix: String) = ctx.startForegroundService(
            Intent(ctx, BleVehicleService::class.java).setAction(ACTION_SCAN).putExtra(EXTRA_FILTER, filterPrefix))
        fun connect(ctx: Context, address: String) = ctx.startForegroundService(
            Intent(ctx, BleVehicleService::class.java).setAction(ACTION_CONNECT).putExtra(EXTRA_ADDRESS, address))
        fun stopScan(ctx: Context) = ctx.startForegroundService(
            Intent(ctx, BleVehicleService::class.java).setAction(ACTION_STOP_SCAN))
        fun disconnect(ctx: Context) = ctx.startForegroundService(
            Intent(ctx, BleVehicleService::class.java).setAction(ACTION_DISCONNECT))
    }
}
