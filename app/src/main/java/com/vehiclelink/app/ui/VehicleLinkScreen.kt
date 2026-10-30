package com.vehiclelink.app.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallMade
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.North
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.TurnLeft
import androidx.compose.material.icons.rounded.TurnRight
import androidx.compose.material.icons.rounded.UTurnLeft
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vehiclelink.app.R
import com.vehiclelink.app.ble.BleConstants
import com.vehiclelink.app.ble.BleVehicleService
import com.vehiclelink.app.core.ClusterProtocol
import com.vehiclelink.app.core.VehicleBus
import com.vehiclelink.app.media.MediaHub
import com.vehiclelink.app.ui.theme.Vl
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay

private val BIKE_NAME = "Hero Karizma XMR CE"

@Composable
fun VehicleLinkScreen(onOpenNotificationAccess: () -> Unit) {
    val ctx = LocalCtx()
    val conn by VehicleBus.conn.collectAsStateWithLifecycle()
    val status by VehicleBus.status.collectAsStateWithLifecycle()
    val devices by VehicleBus.scan.collectAsStateWithLifecycle()
    val nav by VehicleBus.nav.collectAsStateWithLifecycle()
    val media by VehicleBus.media.collectAsStateWithLifecycle()
    val call by VehicleBus.call.collectAsStateWithLifecycle()
    val listenerOn = isNotificationAccessGranted(ctx)
    val mapsKey = mapsApiKey(ctx)
    val phone by rememberPhoneStatus(ctx)

    var showAll by remember { mutableStateOf(false) }
    var showDebug by remember { mutableStateOf(false) }
    var photoVersion by remember { mutableStateOf(0) }
    val vehicleBitmap = remember(photoVersion) { loadVehicleBitmap(ctx) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && saveVehiclePhoto(ctx, uri)) photoVersion++
    }

    if (showDebug) { DebugScreen(onBack = { showDebug = false }); return }

    val connected = conn == VehicleBus.Conn.CONNECTED
    val accent = if (call != null) Vl.Coral else connColor(conn)
    val bars = WindowInsets.systemBars.asPaddingValues()

    Box(Modifier.fillMaxSize()) {
        AuroraBackground(accent)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = bars.calculateTopPadding() + 12.dp, bottom = bars.calculateBottomPadding() + 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "header") {
                Header(
                    conn = conn,
                    showAll = showAll,
                    hasPhoto = vehicleBitmap != null,
                    onToggleShowAll = { showAll = !showAll },
                    onNotificationAccess = onOpenNotificationAccess,
                    onPickPhoto = { pickPhoto.launch("image/*") },
                    onResetPhoto = { deleteVehiclePhoto(ctx); photoVersion++ },
                    onDisconnect = { BleVehicleService.disconnect(ctx) },
                    onDebug = { showDebug = true },
                )
            }

            item(key = "hero") {
                Hero(
                    conn = conn,
                    status = status,
                    photo = vehicleBitmap,
                    onPickPhoto = { pickPhoto.launch("image/*") },
                    onScan = { BleVehicleService.scan(ctx, if (showAll) "" else BleConstants.DEFAULT_NAME_FILTER) },
                    onDisconnect = { BleVehicleService.disconnect(ctx) },
                )
            }

            item(key = "telemetry") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Metric("Phone battery", "${phone.battery}%", Modifier.weight(1f)) { BatteryGlyph(phone.battery) }
                    Metric("Signal", signalWord(phone.signal), Modifier.weight(1f)) { SignalBars(phone.signal) }
                    Metric("Bike link", if (connected) "Live" else "Off", Modifier.weight(1f)) {
                        Icon(
                            if (connected) Icons.Rounded.BluetoothConnected else Icons.Rounded.Bluetooth, null,
                            tint = if (connected) Vl.Volt else Vl.TextFaint, modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            val c = call
            if (c != null) item(key = "call") { CallCard(c, Modifier.animateItem()) }

            if (!listenerOn) item(key = "access") {
                GlassCard(Modifier.animateItem(), accent = Vl.Amber) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.NotificationsActive, Vl.Amber)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Allow notification access", color = Vl.Text, fontWeight = FontWeight.SemiBold)
                            Text("Lets VehicleLink read music and Google Maps turns.", color = Vl.TextDim, fontSize = 13.sp)
                        }
                    }
                    Gap(14.dp)
                    PrimaryAction("Open settings", PrimaryKind.Go, null, onOpenNotificationAccess, Modifier.fillMaxWidth())
                }
            }

            if (devices.isNotEmpty() && !connected) {
                item(key = "nearbyLabel") {
                    Row(Modifier.animateItem().padding(top = 6.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Eyebrow("Nearby")
                        Spacer(Modifier.width(8.dp))
                        Text("${devices.size}", color = Vl.TextFaint, style = MaterialTheme.typography.labelSmall)
                    }
                }
                items(devices, key = { it.address }) { d ->
                    DeviceRow(d, Modifier.animateItem()) { BleVehicleService.connect(ctx, d.address) }
                }
            }

            item(key = "media") { MediaCard(media) }
            item(key = "nav") { NavCard(nav) }

            // Map only when a Maps API key is configured (unchanged behaviour).
            if (mapsKey.isNotBlank()) item(key = "map") {
                val camera = rememberCameraPositionState {
                    position = CameraPosition.fromLatLngZoom(LatLng(20.5937, 78.9629), 4f) // neutral start view: all of India
                }
                Box(
                    Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(24.dp))
                        .border(1.dp, Vl.Stroke, RoundedCornerShape(24.dp))
                ) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = camera,
                        properties = MapProperties(isMyLocationEnabled = hasLocation(ctx)),
                        uiSettings = MapUiSettings(myLocationButtonEnabled = true, zoomControlsEnabled = false),
                    )
                }
            }
        }
    }
}

// ================================================================ header

@Composable
private fun Header(
    conn: VehicleBus.Conn,
    showAll: Boolean,
    hasPhoto: Boolean,
    onToggleShowAll: () -> Unit,
    onNotificationAccess: () -> Unit,
    onPickPhoto: () -> Unit,
    onResetPhoto: () -> Unit,
    onDisconnect: () -> Unit,
    onDebug: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Eyebrow("VehicleLink", color = Vl.Volt)
            Gap(2.dp)
            AnimatedContent(
                headline(conn),
                transitionSpec = { (fadeIn(tween(300)) + slideInVertically { it / 3 }) togetherWith fadeOut(tween(150)) },
                label = "headline"
            ) { Text(it, style = MaterialTheme.typography.headlineMedium, color = Vl.Text) }
        }
        Box {
            RoundButton(Icons.Rounded.MoreHoriz, "Menu", 44.dp, filled = false, onClick = { menuOpen = true })
            DropdownMenu(
                expanded = menuOpen, onDismissRequest = { menuOpen = false },
                shape = RoundedCornerShape(18.dp), containerColor = Color(0xFF151920),
            ) {
                DropdownMenuItem(text = { Text(if (showAll) "Show only bikes (MCU…)" else "Show all Bluetooth devices") },
                    onClick = { onToggleShowAll(); menuOpen = false })
                DropdownMenuItem(text = { Text("Notification access") }, onClick = { onNotificationAccess(); menuOpen = false })
                DropdownMenuItem(text = { Text("Set bike photo") }, onClick = { onPickPhoto(); menuOpen = false })
                if (hasPhoto) DropdownMenuItem(text = { Text("Use default silhouette") }, onClick = { onResetPhoto(); menuOpen = false })
                if (conn == VehicleBus.Conn.CONNECTED || conn == VehicleBus.Conn.CONNECTING)
                    DropdownMenuItem(text = { Text("Disconnect", color = Vl.Coral) }, onClick = { onDisconnect(); menuOpen = false })
                DropdownMenuItem(text = { Text("Debug tools") }, onClick = { onDebug(); menuOpen = false })
            }
        }
    }
}

private fun headline(conn: VehicleBus.Conn) = when (conn) {
    VehicleBus.Conn.CONNECTED -> "Ready to ride"
    VehicleBus.Conn.CONNECTING -> "Linking up…"
    VehicleBus.Conn.SCANNING -> "Looking for your bike"
    VehicleBus.Conn.DISCONNECTED -> "Your garage"
}

// ================================================================ hero

@Composable
private fun Hero(
    conn: VehicleBus.Conn,
    status: String,
    photo: ImageBitmap?,
    onPickPhoto: () -> Unit,
    onScan: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val color = connColor(conn)
    val searching = conn == VehicleBus.Conn.SCANNING || conn == VehicleBus.Conn.CONNECTING
    val connected = conn == VehicleBus.Conn.CONNECTED
    val t = rememberInfiniteTransition(label = "hover")
    val bob by t.animateFloat(-5f, 5f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().height(250.dp).clickable(onClick = onPickPhoto, indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }),
            contentAlignment = Alignment.Center
        ) {
            RadarRings(searching, color, Modifier.size(260.dp))
            FloorGlow(color, if (conn == VehicleBus.Conn.DISCONNECTED) 0.35f else 1f,
                Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp).fillMaxWidth(0.8f).height(46.dp))
            val bikeMod = Modifier.fillMaxWidth(0.94f).height(200.dp)
                .graphicsLayer { translationY = if (connected) bob.dp.toPx() else 0f }
            if (photo != null) {
                androidx.compose.foundation.Image(photo, BIKE_NAME, bikeMod, contentScale = ContentScale.Fit)
            } else {
                Image(painterResource(R.drawable.bike_hero), BIKE_NAME, bikeMod)
                Row(
                    Modifier.align(Alignment.TopEnd).clip(CircleShape).background(Vl.Glass).padding(8.dp),
                ) { Icon(Icons.Rounded.AddAPhoto, "Set photo", tint = Vl.TextDim, modifier = Modifier.size(16.dp)) }
            }
        }
        Text(BIKE_NAME, style = MaterialTheme.typography.titleLarge, color = Vl.Text)
        Gap(10.dp)
        StatusPill(conn, status)
        Gap(20.dp)
        when (conn) {
            VehicleBus.Conn.CONNECTED ->
                PrimaryAction("Disconnect", PrimaryKind.Quiet, Icons.Rounded.LinkOff, onDisconnect, Modifier.fillMaxWidth())
            VehicleBus.Conn.CONNECTING ->
                PrimaryAction("Connecting…", PrimaryKind.Busy, null, onDisconnect, Modifier.fillMaxWidth())
            VehicleBus.Conn.SCANNING ->
                PrimaryAction("Searching…", PrimaryKind.Busy, null, {}, Modifier.fillMaxWidth())
            VehicleBus.Conn.DISCONNECTED ->
                PrimaryAction("Connect to bike", PrimaryKind.Go, Icons.Rounded.BluetoothSearching, onScan, Modifier.fillMaxWidth())
        }
    }
}

// ================================================================ cards

@Composable
private fun CallCard(c: VehicleBus.CallInfo, modifier: Modifier) {
    var startedAt by remember(c.active) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(c.active) { while (c.active) { now = SystemClock.elapsedRealtime(); delay(500) } }
    val secs = ((now - startedAt) / 1000).coerceAtLeast(0)
    GlassCard(modifier, accent = Vl.Coral) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(c.name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?", Vl.Coral, ringing = c.ringing)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Eyebrow(
                    when { c.ringing -> "Incoming call"; c.outgoing -> "Outgoing call"; else -> "On call" },
                    color = Vl.Coral
                )
                Text(c.name, color = Vl.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    if (c.ringing) "Showing on your bike" else "%d:%02d · on your bike".format(secs / 60, secs % 60),
                    color = Vl.TextDim, fontSize = 13.sp
                )
            }
            Icon(if (c.outgoing) Icons.Rounded.CallMade else Icons.Rounded.Call, null, tint = Vl.Coral)
        }
    }
}

@Composable
private fun DeviceRow(d: VehicleBus.ScanDevice, modifier: Modifier, onClick: () -> Unit) {
    val tint = if (d.hasVehicleService) Vl.Volt else Vl.TextDim
    GlassCard(modifier, accent = if (d.hasVehicleService) Vl.Volt else null, onClick = onClick, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Bluetooth, tint, size = 40.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(d.name, color = Vl.Text, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    if (d.hasVehicleService) "Compatible · tap to connect" else d.address,
                    color = Vl.TextDim, fontSize = 12.sp,
                    fontFamily = if (d.hasVehicleService) FontFamily.Default else FontFamily.Monospace
                )
            }
            SignalBars(rssiLevel(d.rssi), color = tint)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaCard(m: VehicleBus.MediaInfo?) {
    val has = m != null && m.title.isNotBlank()
    val playing = m?.playing == true
    // Live position between player updates (same extrapolation the bike gets).
    val receivedAt = remember(m) { SystemClock.elapsedRealtime() }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(playing, m) { while (playing) { now = SystemClock.elapsedRealtime(); delay(500) } }
    val dur = m?.durationMs ?: 0L
    val pos = if (m == null) 0L else (m.positionMs + if (playing) (now - receivedAt).coerceAtLeast(0) else 0L)
        .coerceAtMost(if (dur > 0) dur else Long.MAX_VALUE)

    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(Vl.Volt.copy(alpha = 0.35f), Vl.Cyan.copy(alpha = 0.20f)))),
                contentAlignment = Alignment.Center
            ) {
                if (has) Equalizer(playing, Vl.Text, Modifier.height(26.dp).width(25.dp))
                else Icon(Icons.Rounded.MusicNote, null, tint = Vl.Text.copy(alpha = 0.8f))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Eyebrow(if (playing) "Now playing" else if (has) "Paused" else "Music")
                Gap(2.dp)
                Text(
                    if (has) m!!.title else "Nothing playing",
                    color = Vl.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
                Text(
                    if (has) m!!.artist.ifBlank { m.app } else "Play something — it shows on your bike",
                    color = Vl.TextDim, fontSize = 13.sp, maxLines = 1
                )
            }
        }
        AnimatedVisibility(has && dur > 0, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Column {
                Gap(14.dp)
                ProgressTrack(pos.toFloat() / dur.coerceAtLeast(1), Vl.Volt)
                Row {
                    Text(mmss(pos), color = Vl.TextFaint, fontSize = 11.sp)
                    Spacer(Modifier.weight(1f))
                    Text(mmss(dur), color = Vl.TextFaint, fontSize = 11.sp)
                }
            }
        }
        Gap(14.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            RoundButton(Icons.Rounded.SkipPrevious, "Previous", 52.dp, filled = false, onClick = { MediaHub.apply("PREV") })
            RoundButton(Icons.Rounded.PlayArrow, "Play/pause", 68.dp, filled = true, onClick = { MediaHub.apply("PLAYPAUSE") }) {
                AnimatedContent(playing, transitionSpec = {
                    (fadeIn(tween(180)) + androidx.compose.animation.scaleIn(initialScale = 0.6f)) togetherWith
                        (fadeOut(tween(120)) + androidx.compose.animation.scaleOut(targetScale = 0.6f))
                }, label = "pp") { p ->
                    Icon(if (p) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Vl.OnVolt, modifier = Modifier.size(32.dp))
                }
            }
            RoundButton(Icons.Rounded.SkipNext, "Next", 52.dp, filled = false, onClick = { MediaHub.apply("NEXT") })
        }
    }
}

@Composable
private fun NavCard(n: VehicleBus.NavInstruction?) {
    GlassCard(accent = if (n != null) Vl.Cyan else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(if (n != null) maneuverIcon(n.maneuver) else Icons.Rounded.North, if (n != null) Vl.Cyan else Vl.TextDim, size = 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Eyebrow("Next turn", color = if (n != null) Vl.Cyan else Vl.TextDim)
                Gap(2.dp)
                if (n == null) {
                    Text("No route", color = Vl.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Start directions in Google Maps", color = Vl.TextDim, fontSize = 13.sp)
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        if (n.distance.isNotEmpty()) {
                            Text(n.distance, color = Vl.Cyan, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(n.maneuver, color = Vl.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                            modifier = Modifier.padding(bottom = 2.dp))
                    }
                    if (n.road.isNotEmpty()) Text(n.road, color = Vl.TextDim, fontSize = 13.sp, maxLines = 1)
                }
            }
        }
    }
}

// ================================================================ small helpers

private data class PhoneStatus(val battery: Int, val signal: Int)

/** Battery % and signal level 0..4, polled every few seconds (the same values sent to the bike). */
@Composable
private fun rememberPhoneStatus(ctx: Context) = produceState(PhoneStatus(0, 0), ctx) {
    val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    while (true) {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val sig = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) tm?.signalStrength?.level ?: 0 else 0
        } catch (_: Exception) { 0 }
        value = PhoneStatus(if (level >= 0 && scale > 0) level * 100 / scale else 0, sig)
        delay(3_000)
    }
}

private fun signalWord(level: Int) = when (level) { 4 -> "Excellent"; 3 -> "Good"; 2 -> "Fair"; 1 -> "Weak"; else -> "None" }

private fun rssiLevel(rssi: Int) = when { rssi >= -60 -> 4; rssi >= -70 -> 3; rssi >= -80 -> 2; rssi >= -90 -> 1; else -> 0 }

private fun mmss(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

private fun maneuverIcon(maneuver: String): ImageVector {
    val t = maneuver.lowercase()
    return when {
        "u-turn" in t || "u turn" in t -> Icons.Rounded.UTurnLeft
        "roundabout" in t || "rotary" in t -> Icons.Rounded.Autorenew
        "arrive" in t || "destination" in t -> Icons.Rounded.Flag
        "slight left" in t || "keep left" in t -> Icons.Rounded.NorthWest
        "slight right" in t || "keep right" in t -> Icons.Rounded.NorthEast
        "left" in t -> Icons.Rounded.TurnLeft
        "right" in t -> Icons.Rounded.TurnRight
        else -> Icons.Rounded.North
    }
}

// Debug screen keeps its own simple layout; these give it the new glass look.
@Composable
private fun SurfaceCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) =
    GlassCard(content = content)

@Composable
private fun SectionLabel(text: String) = Eyebrow(text)

// ---------------- Debug screen ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebugScreen(onBack: () -> Unit) {
    val log by VehicleBus.log.collectAsStateWithLifecycle()
    val streamOn by VehicleBus.debugStream.collectAsStateWithLifecycle()
    var song by remember { mutableStateOf("Test Song") }
    var artist by remember { mutableStateOf("Test Artist") }
    var playing by remember { mutableStateOf(true) }
    var caller by remember { mutableStateOf("Mom") }
    var ringing by remember { mutableStateOf(true) }
    var navDist by remember { mutableStateOf("450 m") }
    var navRoad by remember { mutableStateOf("MG Road") }
    var navDir by remember { mutableStateOf("I") }
    var streamingNav by remember { mutableStateOf(false) }
    var raw by remember { mutableStateOf("9Hello~~~~~~~~~~~~~~~World~~~~~~~~~~~~~~~00000dd") }

    fun musicBytes() = ClusterProtocol.music(VehicleBus.MediaInfo(playing, song, artist, 0L, 0L), 50)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                title = { Text("Debug / Test", fontWeight = FontWeight.Bold) },
            )
        }
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(
                        if (streamOn != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
                    Spacer(Modifier.size(8.dp))
                    Text(if (streamOn != null) "Streaming a test packet"
                         else "Not streaming (auto music/nav/call active)", fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { streamingNav = false; VehicleBus.setDebugStream(null) }) { Text("Stop") }
                }
            }

            item {
                SurfaceCard {
                    SectionLabel("Music")
                    OutlinedTextField(song, { song = it }, label = { Text("Song") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(artist, { artist = it }, label = { Text("Artist") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = playing, onCheckedChange = { playing = it })
                        Text("Playing", fontSize = 13.sp)
                        Spacer(Modifier.weight(1f))
                        Button(onClick = { streamingNav = false; VehicleBus.setDebugStream(musicBytes()) }) { Text("Stream music") }
                    }
                }
            }

            item {
                SurfaceCard {
                    SectionLabel("Call (no real call)")
                    OutlinedTextField(caller, { caller = it }, label = { Text("Caller") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = ringing, onCheckedChange = { ringing = it })
                        Text(if (ringing) "Ringing" else "Active", fontSize = 13.sp)
                        Spacer(Modifier.weight(1f))
                        Button(onClick = { streamingNav = false; VehicleBus.setDebugStream(ClusterProtocol.call(caller, ringing, 50, 12)) }) { Text("Stream call") }
                    }
                }
            }

            item {
                SurfaceCard {
                    SectionLabel("Nav — stream, then step letters to map arrows")
                    OutlinedTextField(navDist, { navDist = it }, label = { Text("Distance e.g. 450 m") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(navRoad, { navRoad = it }, label = { Text("Road") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = {
                            navDir = stepLetter(navDir, -1)
                            if (streamingNav) VehicleBus.setDebugStream(ClusterProtocol.navRaw(navDir, navDist, navRoad))
                        }) { Text("◀") }
                        Text("dir = $navDir", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold,
                            fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                        OutlinedButton(onClick = {
                            navDir = stepLetter(navDir, +1)
                            if (streamingNav) VehicleBus.setDebugStream(ClusterProtocol.navRaw(navDir, navDist, navRoad))
                        }) { Text("▶") }
                        Spacer(Modifier.size(6.dp))
                        Button(onClick = {
                            streamingNav = true
                            VehicleBus.setDebugStream(ClusterProtocol.navRaw(navDir, navDist, navRoad))
                        }) { Text("Stream nav") }
                    }
                }
            }

            item {
                SurfaceCard {
                    SectionLabel("Raw packet (type char first)")
                    OutlinedTextField(raw, { raw = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row {
                        Button(onClick = { VehicleBus.emitDebug(ClusterProtocol.rawPacket(raw)) }) { Text("Send once") }
                        Spacer(Modifier.size(6.dp))
                        OutlinedButton(onClick = { streamingNav = false; VehicleBus.setDebugStream(ClusterProtocol.rawPacket(raw)) }) { Text("Stream") }
                    }
                }
            }

            item {
                SurfaceCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Log (TX / RX)")
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(onClick = { VehicleBus.clearLog() }) { Text("Clear") }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    if (log.isEmpty()) Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    log.take(16).forEach { Text(it, fontSize = 10.sp, fontFamily = FontFamily.Monospace) }
                }
            }
        }
    }
}

private fun stepLetter(cur: String, delta: Int): String {
    val c = cur.firstOrNull()?.uppercaseChar() ?: 'A'
    var n = c.code + delta
    if (n < 'A'.code) n = 'Z'.code
    if (n > 'Z'.code) n = 'A'.code
    return n.toChar().toString()
}

// ---------------- helpers ----------------

@Composable
private fun LocalCtx(): Context = androidx.compose.ui.platform.LocalContext.current

@Composable
private fun Image(
    painter: androidx.compose.ui.graphics.painter.Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    colorFilter: ColorFilter? = null,
) = androidx.compose.foundation.Image(painter = painter, contentDescription = contentDescription,
    modifier = modifier, colorFilter = colorFilter)

private fun vehiclePhotoFile(ctx: Context): File = File(ctx.filesDir, "vehicle.png")

private fun loadVehicleBitmap(ctx: Context): ImageBitmap? {
    val f = vehiclePhotoFile(ctx)
    if (!f.exists()) return null
    return try { BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() } catch (_: Exception) { null }
}

private fun deleteVehiclePhoto(ctx: Context) { runCatching { vehiclePhotoFile(ctx).delete() } }

private fun saveVehiclePhoto(ctx: Context, uri: Uri): Boolean = try {
    val src = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    if (src == null) false else {
        val maxDim = 1200
        val scaled = if (src.width > maxDim || src.height > maxDim) {
            val ratio = minOf(maxDim.toFloat() / src.width, maxDim.toFloat() / src.height)
            Bitmap.createScaledBitmap(src, (src.width * ratio).toInt(), (src.height * ratio).toInt(), true)
        } else src
        FileOutputStream(vehiclePhotoFile(ctx)).use { out -> scaled.compress(Bitmap.CompressFormat.PNG, 100, out) }
        true
    }
} catch (_: Exception) { false }

private fun isNotificationAccessGranted(ctx: Context): Boolean {
    val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
    return flat?.contains(ctx.packageName) == true
}

private fun hasLocation(ctx: Context): Boolean =
    ctx.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun mapsApiKey(ctx: Context): String = try {
    val ai = ctx.packageManager.getApplicationInfo(ctx.packageName, PackageManager.GET_META_DATA)
    val k = ai.metaData?.getString("com.google.android.geo.API_KEY").orEmpty()
    if (k.contains("MAPS_API_KEY") || k.equals("YOUR_ANDROID_MAPS_SDK_KEY", true)) "" else k
} catch (_: Exception) { "" }
