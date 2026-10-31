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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.BatteryAlert
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
import com.vehiclelink.app.ui.theme.Radius
import com.vehiclelink.app.ui.theme.Space
import com.vehiclelink.app.ui.theme.ChakraPetch
import com.vehiclelink.app.ui.theme.Vl
import com.vehiclelink.app.ui.theme.VlType
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
    val vehicles by com.vehiclelink.app.core.VehicleStore.vehicles.collectAsStateWithLifecycle()
    val activeAddress by VehicleBus.activeAddress.collectAsStateWithLifecycle()
    val reconnecting by VehicleBus.reconnecting.collectAsStateWithLifecycle()
    val nav by VehicleBus.nav.collectAsStateWithLifecycle()
    val media by VehicleBus.media.collectAsStateWithLifecycle()
    val call by VehicleBus.call.collectAsStateWithLifecycle()
    val listenerOn = isNotificationAccessGranted(ctx)
    val mapsKey = mapsApiKey(ctx)
    val phone by rememberPhoneStatus(ctx)

    var showAddSheet by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf("home") }   // home | settings | debug
    var resumes by remember { mutableStateOf(0) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { resumes++ }
    val batteryExempt = remember(resumes) { isBatteryExempt(ctx) }
    var photoVersion by remember { mutableStateOf(0) }
    val vehicleBitmap = remember(photoVersion, screen) { loadVehicleBitmap(ctx) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && saveVehiclePhoto(ctx, uri)) photoVersion++
    }

    when (screen) {
        "debug" -> { DebugScreen(onBack = { screen = "settings" }); return }
        "settings" -> {
            SettingsScreen(
                onBack = { screen = "home" },
                onOpenNotificationAccess = onOpenNotificationAccess,
                onOpenTestBench = { screen = "debug" },
                hasCustomPhoto = vehicleBitmap != null,
                onResetPhoto = { deleteVehiclePhoto(ctx); photoVersion++ },
            )
            return
        }
    }

    val connected = conn == VehicleBus.Conn.CONNECTED
    // The vehicle on the hero: the one we're linked/linking to, else the most recently used one.
    val heroVehicle = com.vehiclelink.app.core.VehicleStore.find(activeAddress) ?: vehicles.firstOrNull()
    if (showAddSheet) AddVehicleSheet(onClose = { showAddSheet = false })
    val accent = if (call != null) Vl.Coral else connColor(conn)
    // safeDrawing = status + navigation bars + display cutout; content scrolls clear of all of them.
    val bars = WindowInsets.safeDrawing.asPaddingValues()

    Box(Modifier.fillMaxSize()) {
        AuroraBackground(accent)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Space.Screen, end = Space.Screen,
                top = bars.calculateTopPadding() + Space.s, bottom = bars.calculateBottomPadding() + Space.xl
            ),
            verticalArrangement = Arrangement.spacedBy(Space.m)
        ) {
            item(key = "header") {
                Header(
                    conn = conn,
                    onPickPhoto = { pickPhoto.launch("image/*") },
                    onDisconnect = { BleVehicleService.disconnect(ctx) },
                    onSettings = { screen = "settings" },
                )
            }

            item(key = "hero") {
                Hero(
                    conn = conn,
                    status = status,
                    name = heroVehicle?.nickname ?: BIKE_NAME,
                    waiting = reconnecting && conn == VehicleBus.Conn.DISCONNECTED,
                    photo = vehicleBitmap,
                    onPickPhoto = { pickPhoto.launch("image/*") },
                    connectLabel = heroVehicle?.let { "Connect to ${it.nickname}" } ?: "Add your vehicle",
                    onConnect = {
                        val v = heroVehicle
                        if (v != null) BleVehicleService.connect(ctx, v.address) else showAddSheet = true
                    },
                    onSearching = { showAddSheet = true },
                    onDisconnect = { BleVehicleService.disconnect(ctx) },
                )
            }

            item(key = "telemetry") {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                    Metric(if (phone.charging) "Charging" else "Phone battery", "${phone.battery}%", Modifier.weight(1f)) {
                        BatteryGlyph(phone.battery, phone.charging)
                    }
                    Metric("Signal", signalWord(phone.signal), Modifier.weight(1f)) { SignalBars(phone.signal) }
                    Metric("Bike link", if (connected) "Live" else "Off", Modifier.weight(1f)) {
                        Icon(
                            if (connected) Icons.Rounded.BluetoothConnected else Icons.Rounded.Bluetooth, null,
                            tint = if (connected) Vl.Volt else Vl.TextFaint, modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            item(key = "vehicles") {
                VehiclesSection(
                    vehicles = vehicles,
                    conn = conn,
                    activeAddress = activeAddress,
                    onConnect = { v -> BleVehicleService.connect(ctx, v.address) },
                    onAdd = { showAddSheet = true },
                    modifier = Modifier.animateItem(),
                )
            }

            val c = call
            if (c != null) item(key = "call") { CallCard(c, Modifier.animateItem()) }

            if (!batteryExempt) item(key = "battery") {
                GlassCard(Modifier.animateItem(), accent = Vl.Amber, onClick = { requestBatteryExemption(ctx) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.BatteryAlert, Vl.Amber)
                        Spacer(Modifier.width(Space.m))
                        Column(Modifier.weight(1f)) {
                            Text("Keep the bike link alive", style = VlType.Body, color = Vl.Text)
                            Text("So the bike doesn't drop mid-ride",
                                style = VlType.Caption, color = Vl.TextDim)
                        }
                        Text("Allow", style = VlType.Label, color = Vl.Amber)
                    }
                }
            }

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
        SystemBarScrims()
    }
}

/**
 * Solid-to-clear fades behind the status and navigation bars, so cards scrolling past never sit
 * directly under the clock or the nav buttons.
 */
@Composable
fun BoxScope.SystemBarScrims() {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        Modifier.align(Alignment.TopCenter).fillMaxWidth().height(top + 16.dp)
            .background(Brush.verticalGradient(listOf(Vl.Bg, Vl.Bg.copy(alpha = 0.85f), Color.Transparent)))
    )
    Box(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(bottom + 24.dp)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Vl.Bg.copy(alpha = 0.9f), Vl.Bg)))
    )
}

// ================================================================ header

@Composable
private fun Header(
    conn: VehicleBus.Conn,
    onPickPhoto: () -> Unit,
    onDisconnect: () -> Unit,
    onSettings: () -> Unit,
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
            RoundButton(Icons.Rounded.MoreHoriz, "Menu", Space.Touch, filled = false, onClick = { menuOpen = true })
            DropdownMenu(
                expanded = menuOpen, onDismissRequest = { menuOpen = false },
                shape = RoundedCornerShape(18.dp), containerColor = Color(0xFF151920),
            ) {
                DropdownMenuItem(text = { Text("Settings") }, onClick = { onSettings(); menuOpen = false })
                DropdownMenuItem(text = { Text("Set bike photo") }, onClick = { onPickPhoto(); menuOpen = false })
                if (conn == VehicleBus.Conn.CONNECTED || conn == VehicleBus.Conn.CONNECTING)
                    DropdownMenuItem(text = { Text("Disconnect", color = Vl.Coral) }, onClick = { onDisconnect(); menuOpen = false })
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
    name: String,
    waiting: Boolean,
    photo: ImageBitmap?,
    onPickPhoto: () -> Unit,
    connectLabel: String,
    onConnect: () -> Unit,
    onSearching: () -> Unit,
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
                androidx.compose.foundation.Image(photo, name, bikeMod, contentScale = ContentScale.Fit)
            } else {
                Image(painterResource(R.drawable.bike_hero), name, bikeMod)
                Row(
                    Modifier.align(Alignment.TopEnd).clip(CircleShape).background(Vl.Glass).padding(8.dp),
                ) { Icon(Icons.Rounded.AddAPhoto, "Set photo", tint = Vl.TextDim, modifier = Modifier.size(16.dp)) }
            }
        }
        Text(name, style = MaterialTheme.typography.titleLarge, color = Vl.Text, maxLines = 1)
        Gap(10.dp)
        StatusPill(conn, when (conn) {
            VehicleBus.Conn.CONNECTED -> "Connected"
            VehicleBus.Conn.CONNECTING -> "Connecting…"
            VehicleBus.Conn.SCANNING -> "Searching nearby…"
            // Keep actionable service messages (Bluetooth off / permission); the add sheet explains scan results.
            VehicleBus.Conn.DISCONNECTED -> when {
                waiting -> "Waiting for your bike"
                status.contains("Bluetooth", ignoreCase = true) -> status
                else -> "Not connected"
            }
        })
        Gap(20.dp)
        when (conn) {
            VehicleBus.Conn.CONNECTED ->
                PrimaryAction("Disconnect", PrimaryKind.Quiet, Icons.Rounded.LinkOff, onDisconnect, Modifier.fillMaxWidth())
            VehicleBus.Conn.CONNECTING ->
                PrimaryAction("Connecting…", PrimaryKind.Busy, null, onDisconnect, Modifier.fillMaxWidth())
            VehicleBus.Conn.SCANNING ->
                PrimaryAction("Searching…", PrimaryKind.Busy, null, onSearching, Modifier.fillMaxWidth())
            VehicleBus.Conn.DISCONNECTED ->
                if (waiting) PrimaryAction("Waiting for your bike…", PrimaryKind.Busy, null, onDisconnect, Modifier.fillMaxWidth())
                else PrimaryAction(connectLabel, PrimaryKind.Go, Icons.Rounded.BluetoothSearching, onConnect, Modifier.fillMaxWidth())
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
    // Confirm-reject window (first bike press): count it down here too.
    val pendingUntil by VehicleBus.rejectPendingUntil.collectAsStateWithLifecycle()
    var tick by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(pendingUntil) { while (pendingUntil > SystemClock.elapsedRealtime()) { tick = SystemClock.elapsedRealtime(); delay(200) } }
    val confirmLeft = ((pendingUntil - tick + 999) / 1000).coerceAtLeast(0)
    GlassCard(modifier, accent = Vl.Coral) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(c.name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?", Vl.Coral, ringing = c.ringing)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Eyebrow(
                    when { c.ringing -> "Incoming call"; c.outgoing -> "Outgoing call"; else -> "On call" },
                    color = Vl.Coral
                )
                Text(c.name, color = Vl.Text, fontFamily = ChakraPetch, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    if (c.ringing) "Showing on your bike" else "%d:%02d · on your bike".format(secs / 60, secs % 60),
                    color = Vl.TextDim, fontSize = 13.sp
                )
            }
            Icon(if (c.outgoing) Icons.Rounded.CallMade else Icons.Rounded.Call, null, tint = Vl.Coral)
        }
        AnimatedVisibility(confirmLeft > 0, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Text("Press reject on the bike again to confirm · ${confirmLeft}s",
                style = VlType.Label, color = Vl.Amber, modifier = Modifier.padding(top = Space.s))
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
                    color = Vl.Text, fontFamily = ChakraPetch, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1,
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
                    Text("No route", color = Vl.Text, fontFamily = ChakraPetch, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text("Start directions in Google Maps", color = Vl.TextDim, fontSize = 13.sp)
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        if (n.distance.isNotEmpty()) {
                            Text(n.distance, color = Vl.Cyan, fontFamily = ChakraPetch, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(n.maneuver, color = Vl.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                            modifier = Modifier.padding(bottom = 2.dp))
                    }
                    if (n.road.isNotEmpty()) Text(n.road, color = Vl.TextDim, fontSize = 13.sp, maxLines = 1)
                    tripLine(n)?.let { Text(it, color = Vl.TextFaint, fontSize = 12.sp, maxLines = 1) }
                }
            }
        }
    }
}

// ================================================================ small helpers

private data class PhoneStatus(val battery: Int, val signal: Int, val charging: Boolean = false)

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
        // Charging = plugged in and charging, or plugged in and full.
        val st = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        value = PhoneStatus(if (level >= 0 && scale > 0) level * 100 / scale else 0, sig, charging)
        delay(3_000)
    }
}

/** "Arrive 6:26 pm · 11.3 km left" — the same ETA / remainder the bike receives. */
private fun tripLine(n: VehicleBus.NavInstruction): String? {
    val parts = ArrayList<String>()
    if (n.etaHour in 0..23 && n.etaMinute in 0..59) {
        val h12 = (n.etaHour % 12).let { if (it == 0) 12 else it }
        parts += "Arrive %d:%02d %s".format(h12, n.etaMinute, if (n.etaHour >= 12) "pm" else "am")
    }
    if (n.remainingM >= 0) parts += if (n.remainingM < 1000) "${n.remainingM} m left"
        else "%.1f km left".format(n.remainingM / 1000.0)
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
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

fun deleteVehiclePhoto(ctx: Context) { runCatching { vehiclePhotoFile(ctx).delete() } }

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
