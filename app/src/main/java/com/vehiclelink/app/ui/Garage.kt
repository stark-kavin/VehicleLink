package com.vehiclelink.app.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vehiclelink.app.ble.BleConstants
import com.vehiclelink.app.ble.BleVehicleService
import com.vehiclelink.app.core.VehicleBus
import com.vehiclelink.app.core.VehicleStore
import com.vehiclelink.app.ui.theme.BorderWidth
import com.vehiclelink.app.ui.theme.Radius
import com.vehiclelink.app.ui.theme.Space
import com.vehiclelink.app.ui.theme.Vl
import com.vehiclelink.app.ui.theme.VlType

// ================================================================ vehicles list (home)

/** "Your vehicles": every saved vehicle, tap to connect; plus an "Add a vehicle" row. */
@Composable
fun VehiclesSection(
    vehicles: List<VehicleStore.Vehicle>,
    conn: VehicleBus.Conn,
    activeAddress: String?,
    onConnect: (VehicleStore.Vehicle) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.animateContentSize()) {
        Row(Modifier.padding(start = Space.xs, bottom = Space.s), verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Your vehicles")
            if (vehicles.isNotEmpty()) {
                Spacer(Modifier.width(Space.s))
                Text("${vehicles.size}", style = VlType.Overline, color = Vl.TextFaint)
            }
        }
        GlassCard(padding = Space.s) {
            vehicles.forEach { v ->
                val isActive = v.address.equals(activeAddress, true)
                VehicleRow(v, if (isActive) conn else VehicleBus.Conn.DISCONNECTED, onClick = { onConnect(v) })
            }
            AddRow(first = vehicles.isEmpty(), onClick = onAdd)
        }
    }
}

@Composable
private fun VehicleRow(v: VehicleStore.Vehicle, conn: VehicleBus.Conn, onClick: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val (status, color) = when (conn) {
        VehicleBus.Conn.CONNECTED -> "Connected" to Vl.Volt
        VehicleBus.Conn.CONNECTING -> "Connecting…" to Vl.Amber
        else -> "Last connected ${ago(v.lastConnected)}" to Vl.TextDim
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(Radius.Tile))
            .clickable(enabled = conn == VehicleBus.Conn.DISCONNECTED, onClick = onClick)
            .padding(horizontal = Space.s, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(Icons.Rounded.TwoWheeler, if (conn == VehicleBus.Conn.CONNECTED) Vl.Volt else Vl.TextDim, size = Space.Touch - Space.s)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(v.nickname, style = VlType.Body, color = Vl.Text, maxLines = 1)
            Text(status, style = VlType.Caption, color = color, maxLines = 1)
        }
        Box {
            Box(
                Modifier.size(Space.Touch).clip(CircleShape).clickable { menu = true },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.MoreVert, "Options for ${v.nickname}", tint = Vl.TextDim) }
            DropdownMenu(menu, { menu = false }, shape = RoundedCornerShape(Radius.Tile), containerColor = Color(0xFF151920)) {
                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = true })
                DropdownMenuItem(text = { Text("Forget vehicle", color = Vl.Coral) }, onClick = {
                    menu = false
                    VehicleStore.forget(v.address)
                })
            }
        }
    }
    if (renaming) RenameDialog(v.nickname, onDismiss = { renaming = false }) { VehicleStore.rename(v.address, it); renaming = false }
}

@Composable
private fun AddRow(first: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(Radius.Tile)).clickable(onClick = onClick)
            .padding(horizontal = Space.s, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(Space.Touch - Space.s).clip(RoundedCornerShape(Radius.Tile))
                .border(BorderWidth, Vl.Volt.copy(alpha = 0.5f), RoundedCornerShape(Radius.Tile)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Add, null, tint = Vl.Volt) }
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(if (first) "Add your vehicle" else "Add a vehicle", style = VlType.Body, color = Vl.Volt)
            Text("Pair a new bike over Bluetooth", style = VlType.Caption, color = Vl.TextDim)
        }
    }
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF151920),
        title = { Text("Rename vehicle", style = VlType.Title, color = Vl.Text) },
        text = { VlTextField(name, { name = it.take(40) }, "Name", Modifier.fillMaxWidth()) },
        confirmButton = { TextButton({ onSave(name) }, enabled = name.isNotBlank()) { Text("Save", color = Vl.Volt) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = Vl.TextDim) } },
    )
}

private fun ago(ms: Long): String =
    if (ms <= 0L) "never" else DateUtils.getRelativeTimeSpanString(ms, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

// ================================================================ add-vehicle sheet

/**
 * Pairing happens here, not on the home screen: the sheet starts a scan when it opens, lists nearby bikes,
 * and closes itself once the chosen one is connected (it's then saved to "Your vehicles").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVehicleSheet(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val conn by VehicleBus.conn.collectAsStateWithLifecycle()
    val devices by VehicleBus.scan.collectAsStateWithLifecycle()
    val active by VehicleBus.activeAddress.collectAsStateWithLifecycle()
    val saved by VehicleStore.vehicles.collectAsStateWithLifecycle()
    var showAll by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<VehicleBus.ScanDevice?>(null) }
    var failed by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val btOn by rememberBluetoothOn()
    val enableBt = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    fun search() { failed = false; chosen = null; BleVehicleService.scan(ctx, if (showAll) "" else BleConstants.DEFAULT_NAME_FILTER) }
    fun close() {
        if (VehicleBus.conn.value == VehicleBus.Conn.SCANNING) BleVehicleService.stopScan(ctx)
        onClose()
    }

    // Search when the sheet opens, and again as soon as Bluetooth is switched on.
    LaunchedEffect(btOn) { if (btOn && conn != VehicleBus.Conn.CONNECTED) search() }
    // Done: the vehicle we picked is connected.
    LaunchedEffect(conn, active) {
        val c = chosen ?: return@LaunchedEffect
        if (conn == VehicleBus.Conn.CONNECTED && c.address.equals(active, true)) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            onClose()
        }
    }
    // A pick that dropped back to idle without connecting.
    LaunchedEffect(conn) {
        if (chosen != null && conn == VehicleBus.Conn.DISCONNECTED) {
            failed = true
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        }
    }

    ModalBottomSheet(onDismissRequest = ::close, sheetState = sheet, containerColor = Color(0xFF10141A),
        dragHandle = { Box(Modifier.padding(top = Space.s).size(width = 40.dp, height = 4.dp).clip(CircleShape).background(Vl.Stroke)) }) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(horizontal = Space.Screen).padding(top = Space.m, bottom = Space.l),
        ) {
            Text("Add a vehicle", style = VlType.Title, color = Vl.Text)
            Text("Turn the bike's ignition on and keep your phone close.", style = VlType.Label, color = Vl.TextDim)
            Gap(Space.m)

            if (!btOn) {
                GlassCard(accent = Vl.Amber, padding = Space.m) {
                    Text("Bluetooth is off", style = VlType.Body, color = Vl.Text)
                    Text("Turn it on to find your bike.", style = VlType.Caption, color = Vl.TextDim)
                    Gap(Space.s)
                    PrimaryAction("Turn on Bluetooth", PrimaryKind.Go, Icons.Rounded.Bluetooth, {
                        runCatching { enableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                            .onFailure { runCatching { ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) } }
                    }, Modifier.fillMaxWidth())
                }
                return@Column
            }

            val busyText = when {
                chosen != null && conn == VehicleBus.Conn.CONNECTING -> "Connecting to ${friendlyName(chosen!!)}…"
                chosen != null && failed -> "Couldn't connect. Check the bike is on, then try again."
                conn == VehicleBus.Conn.SCANNING -> "Searching nearby…"
                devices.isEmpty() -> "No bikes found."
                else -> "Tap your bike to connect."
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = Space.Touch)) {
                if (conn == VehicleBus.Conn.SCANNING || conn == VehicleBus.Conn.CONNECTING) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Vl.Amber, strokeWidth = 2.dp)
                    Spacer(Modifier.width(Space.s))
                }
                AnimatedContent(busyText, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
                    label = "sheetStatus", modifier = Modifier.weight(1f)) {
                    Text(it, style = VlType.Label, color = if (failed) Vl.Coral else Vl.Text)
                }
                if (conn != VehicleBus.Conn.SCANNING && conn != VehicleBus.Conn.CONNECTING) {
                    TextButton(::search) { Text("Search again", color = Vl.Volt) }
                }
            }
            Gap(Space.s)

            Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                devices.forEach { d ->
                    NearbyRow(
                        d,
                        saved = saved.any { it.address.equals(d.address, true) },
                        picked = chosen?.address == d.address,
                        onClick = {
                            if (conn != VehicleBus.Conn.CONNECTING) {
                                chosen = d; failed = false
                                BleVehicleService.connect(ctx, d.address)
                            }
                        },
                    )
                }
                if (devices.isEmpty() && conn != VehicleBus.Conn.SCANNING) {
                    Text(
                        "• Switch the ignition on — the dashboard only advertises while it's powered.\n" +
                            "• Keep the phone within a few metres of the bike.\n" +
                            "• Still nothing? Turn on “Show all Bluetooth devices” below.",
                        style = VlType.Caption, color = Vl.TextDim,
                    )
                }
            }

            Gap(Space.m)
            Row(
                Modifier.fillMaxWidth().heightIn(min = Space.Touch).clickable { showAll = !showAll; search() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Show all Bluetooth devices", style = VlType.Body, color = Vl.Text)
                    Text("Off: only Hero dashboards (names starting “MCU”)", style = VlType.Caption, color = Vl.TextDim)
                }
                Switch(showAll, { showAll = it; search() }, colors = SwitchDefaults.colors(
                    checkedThumbColor = Vl.OnVolt, checkedTrackColor = Vl.Volt,
                    uncheckedThumbColor = Vl.TextDim, uncheckedTrackColor = Vl.GlassStrong, uncheckedBorderColor = Vl.Stroke))
            }
        }
    }
}

@Composable
private fun NearbyRow(d: VehicleBus.ScanDevice, saved: Boolean, picked: Boolean, onClick: () -> Unit) {
    val tint = if (d.hasVehicleService || d.name.startsWith("MCU", true)) Vl.Volt else Vl.TextDim
    GlassCard(accent = if (picked) Vl.Amber else if (tint == Vl.Volt) Vl.Volt else null, onClick = onClick, padding = Space.s) {
        Row(Modifier.heightIn(min = Space.Touch), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Bluetooth, tint, size = Space.Touch - Space.s)
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(friendlyName(d) + if (saved) " · saved" else "", style = VlType.Body, color = Vl.Text, maxLines = 1)
                Text("${d.name} · …${d.address.takeLast(5)}", style = VlType.Caption, color = Vl.TextDim, maxLines = 1)
            }
            SignalBars(rssiBars(d.rssi), color = tint)
        }
    }
}

/** Friendly label for a scan result; the raw name stays visible as secondary text. */
private fun friendlyName(d: VehicleBus.ScanDevice): String = when {
    d.name.startsWith("MCU", true) -> "Hero bike dashboard"
    d.hasVehicleService -> "Compatible device"
    d.name.isBlank() || d.name == "(unnamed)" -> "Unnamed device"
    d.name.matches(Regex("[A-Z0-9_\\-]{4,}")) -> "Bluetooth device"
    else -> d.name
}

/** Live Bluetooth on/off, updated from the adapter's STATE_CHANGED broadcast. */
@Composable
private fun rememberBluetoothOn(): androidx.compose.runtime.State<Boolean> {
    val ctx = LocalContext.current
    val adapter = remember { (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter }
    val on = remember { mutableStateOf(adapter?.isEnabled == true) }
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                on.value = i.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_ON
            }
        }
        ctx.registerReceiver(r, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        onDispose { runCatching { ctx.unregisterReceiver(r) } }
    }
    return on
}

private fun rssiBars(rssi: Int) = when { rssi >= -60 -> 4; rssi >= -70 -> 3; rssi >= -80 -> 2; rssi >= -90 -> 1; else -> 0 }

