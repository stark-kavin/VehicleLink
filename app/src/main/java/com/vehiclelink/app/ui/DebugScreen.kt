package com.vehiclelink.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsBoat
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.ForkLeft
import androidx.compose.material.icons.rounded.ForkRight
import androidx.compose.material.icons.rounded.Merge
import androidx.compose.material.icons.rounded.North
import androidx.compose.material.icons.rounded.RampLeft
import androidx.compose.material.icons.rounded.RampRight
import androidx.compose.material.icons.rounded.RoundaboutLeft
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TurnLeft
import androidx.compose.material.icons.rounded.TurnRight
import androidx.compose.material.icons.rounded.TurnSharpLeft
import androidx.compose.material.icons.rounded.TurnSharpRight
import androidx.compose.material.icons.rounded.TurnSlightLeft
import androidx.compose.material.icons.rounded.TurnSlightRight
import androidx.compose.material.icons.rounded.UTurnLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vehiclelink.app.core.ClusterProtocol
import com.vehiclelink.app.core.VehicleBus
import com.vehiclelink.app.ui.theme.BorderWidth
import com.vehiclelink.app.ui.theme.Radius
import com.vehiclelink.app.ui.theme.Space
import com.vehiclelink.app.ui.theme.Vl
import com.vehiclelink.app.ui.theme.VlType

/**
 * Test bench: send test screens to the bike without a real call, song or route.
 * Everything here is active only while this screen is open — leaving it (Back or the arrow) stops any
 * test stream and returns the bike to live phone data. The packet log is also only recorded here.
 */
@Composable
fun DebugScreen(onBack: () -> Unit) {
    DisposableEffect(Unit) {
        VehicleBus.setDebugActive(true)
        onDispose { VehicleBus.setDebugActive(false) }
    }
    BackHandler(onBack = onBack)

    val conn by VehicleBus.conn.collectAsStateWithLifecycle()
    val stream by VehicleBus.debugStream.collectAsStateWithLifecycle()
    val log by VehicleBus.log.collectAsStateWithLifecycle()
    var testing by remember { mutableStateOf("") }   // what the current stream is, in words
    var tab by remember { mutableStateOf(Tab.Arrows) }

    fun startStream(label: String, bytes: ByteArray) { testing = label; VehicleBus.setDebugStream(bytes) }
    fun stopStream() { testing = ""; VehicleBus.setDebugStream(null) }
    fun sendOnce(label: String, bytes: ByteArray) { VehicleBus.emitDebug(bytes); VehicleBus.addLog("sent once · $label") }

    val bars = WindowInsets.safeDrawing.asPaddingValues()
    Box(Modifier.fillMaxSize()) {
        AuroraBackground(if (stream != null) Vl.Cyan else Vl.Idle)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Space.Screen, end = Space.Screen,
                top = bars.calculateTopPadding() + Space.s, bottom = bars.calculateBottomPadding() + Space.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            item { ScreenHeader("Test bench", "Stops automatically when you leave this screen", onBack) }

            item {
                if (conn != VehicleBus.Conn.CONNECTED) {
                    Notice("Your bike isn't connected. Connect first — until then nothing reaches the display.", Vl.Amber)
                }
            }

            // ---- what's on the bike right now
            item {
                GlassCard(accent = if (stream != null) Vl.Cyan else null, padding = Space.m) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Eyebrow(if (stream != null) "Testing now" else "Live", color = if (stream != null) Vl.Cyan else Vl.TextDim)
                            AnimatedContent(
                                if (stream != null) testing.ifBlank { "Test packet" } else "Bike shows your phone's real data",
                                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "testing",
                            ) { Text(it, style = VlType.RowTitle, color = Vl.Text) }
                            if (stream != null) Text("Repeats every 0.8 s", style = VlType.Caption, color = Vl.TextDim)
                        }
                        if (stream != null) Pill("Stop", Icons.Rounded.Stop, Vl.Coral) { stopStream() }
                    }
                }
            }

            // ---- tabs
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Space.s),
                ) { Tab.entries.forEach { t -> Chip(t.label, selected = t == tab) { tab = t } } }
            }

            item {
                AnimatedContent(tab, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "tab") { t ->
                    when (t) {
                        Tab.Arrows -> ArrowsPanel(testing) { label, bytes -> startStream(label, bytes) }
                        Tab.Music -> MusicPanel { label, bytes -> startStream(label, bytes) }
                        Tab.Call -> CallPanel { label, bytes -> startStream(label, bytes) }
                        Tab.Phone -> PhonePanel { label, bytes -> sendOnce(label, bytes) }
                        Tab.Raw -> RawPanel(onOnce = { sendOnce("raw packet", it) }, onStream = { startStream("Raw packet", it) })
                    }
                }
            }

            // ---- log
            item {
                GlassCard(padding = Space.m) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Eyebrow("Packet log", modifier = Modifier.weight(1f))
                        Pill("Clear", null, Vl.TextDim) { VehicleBus.clearLog() }
                    }
                    Gap(Space.s)
                    if (log.isEmpty()) {
                        Text("Packets you send and button presses from the bike show up here.", style = VlType.Caption, color = Vl.TextDim)
                    }
                    log.take(30).forEach { line -> LogLine(line) }
                }
            }
        }
        SystemBarScrims()
    }
}

private enum class Tab(val label: String) { Arrows("Turn arrows"), Music("Music"), Call("Call"), Phone("Phone status"), Raw("Raw packet") }

// ---------------------------------------------------------------- panels

/** Every arrow letter the original app can send, labelled with the maneuver it means. */
private data class Arrow(val letter: String, val label: String, val icon: ImageVector)

private val ARROWS = listOf(
    Arrow("I", "Turn left", Icons.Rounded.TurnLeft), Arrow("J", "Turn right", Icons.Rounded.TurnRight),
    Arrow("G", "Straight", Icons.Rounded.North),
    Arrow("C", "Slight left", Icons.Rounded.TurnSlightLeft), Arrow("D", "Slight right", Icons.Rounded.TurnSlightRight),
    Arrow("U", "Roundabout", Icons.Rounded.RoundaboutLeft),
    Arrow("E", "Sharp left", Icons.Rounded.TurnSharpLeft), Arrow("F", "Sharp right", Icons.Rounded.TurnSharpRight),
    Arrow("P", "U-turn", Icons.Rounded.UTurnLeft),
    Arrow("Z", "Keep left", Icons.Rounded.ForkLeft), Arrow("X", "Keep right", Icons.Rounded.ForkRight),
    Arrow("V", "Merge", Icons.Rounded.Merge),
    Arrow("K", "Ramp left", Icons.Rounded.RampLeft), Arrow("L", "Ramp right", Icons.Rounded.RampRight),
    Arrow("Y", "Ferry", Icons.Rounded.DirectionsBoat),
    Arrow("B", "Heading", Icons.Rounded.Explore), Arrow("H", "Arrived", Icons.Rounded.Flag),
)

@Composable
private fun ArrowsPanel(testing: String, stream: (String, ByteArray) -> Unit) {
    var distance by remember { mutableStateOf("450 m") }
    var road by remember { mutableStateOf("MG Road") }
    var close by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Arrow?>(null) }
    fun send(a: Arrow) {
        selected = a
        val letter = if (close) a.letter.lowercase() else a.letter
        stream("Arrow · ${a.label}${if (close) " (close)" else ""}", ClusterProtocol.navRaw(letter, distance, road))
    }
    GlassCard(padding = Space.m) {
        Text("Tap an arrow to show it on the bike. Check the bike draws the turn named on the tile.",
            style = VlType.Caption, color = Vl.TextDim)
        Gap(Space.m)
        ARROWS.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                row.forEach { a ->
                    ArrowTile(a, selected = testing.isNotEmpty() && selected == a, modifier = Modifier.weight(1f)) { send(a) }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Gap(Space.s)
        }
        Gap(Space.s)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            VlTextField(distance, { distance = it; selected?.let(::send) }, "Distance", Modifier.weight(1f))
            VlTextField(road, { road = it; selected?.let(::send) }, "Road", Modifier.weight(1.4f))
        }
        SwitchLine("Turn is close (under 200 m)", close) { close = it; selected?.let(::send) }
    }
}

@Composable
private fun ArrowTile(a: Arrow, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Vl.Cyan.copy(alpha = 0.18f) else Vl.Glass, tween(200), label = "tile")
    val stroke = if (selected) Vl.Cyan.copy(alpha = 0.6f) else Vl.Stroke
    Column(
        modifier.heightIn(min = 88.dp).clip(RoundedCornerShape(Radius.Tile)).background(bg)
            .border(BorderWidth, stroke, RoundedCornerShape(Radius.Tile)).clickable(onClick = onClick)
            .padding(vertical = Space.s, horizontal = Space.xs),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(a.icon, null, tint = if (selected) Vl.Cyan else Vl.Text, modifier = Modifier.size(28.dp))
        Gap(Space.xs)
        Text(a.label, style = VlType.Caption, color = Vl.Text, textAlign = TextAlign.Center, maxLines = 1)
        Text("'${a.letter}'", style = VlType.Caption, color = Vl.TextFaint, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun MusicPanel(stream: (String, ByteArray) -> Unit) {
    var song by remember { mutableStateOf("Test Song") }
    var artist by remember { mutableStateOf("Test Artist") }
    var playing by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(40f) }
    fun bytes() = ClusterProtocol.music(
        VehicleBus.MediaInfo(playing, song, artist, (progress * 1000).toLong(), 100_000L), 50)
    GlassCard(padding = Space.m) {
        Text("Shows a made-up song on the bike's music screen.", style = VlType.Caption, color = Vl.TextDim)
        Gap(Space.s)
        VlTextField(song, { song = it }, "Song", Modifier.fillMaxWidth())
        Gap(Space.s)
        VlTextField(artist, { artist = it }, "Artist", Modifier.fillMaxWidth())
        SwitchLine("Playing", playing) { playing = it }
        LabeledSlider("Progress", "${progress.toInt()}%", progress, 0f..100f) { progress = it }
        Gap(Space.s)
        PrimaryAction("Show on bike", PrimaryKind.Go, null, { stream("Music · $song", bytes()) }, Modifier.fillMaxWidth())
    }
}

private enum class CallState(val label: String) {
    Ringing("Ringing"), Active("On call"), Outgoing("Outgoing"), Confirm("Confirm reject"), Ended("Call ended")
}

@Composable
private fun CallPanel(stream: (String, ByteArray) -> Unit) {
    var caller by remember { mutableStateOf("Mom") }
    var state by remember { mutableStateOf(CallState.Ringing) }
    val scope = rememberCoroutineScope()
    var countdown by remember { mutableStateOf<Job?>(null) }
    fun bytes() = when (state) {
        CallState.Ringing -> ClusterProtocol.call(caller, ringing = true, voiceVolumePercent = 50, durationSec = 0)
        CallState.Active -> ClusterProtocol.call(caller, ringing = false, voiceVolumePercent = 50, durationSec = 75)
        CallState.Outgoing -> ClusterProtocol.call(caller, ringing = false, voiceVolumePercent = 50, durationSec = 75, outgoing = true)
        CallState.Confirm -> ClusterProtocol.call(caller, ringing = true, voiceVolumePercent = 50, durationSec = 0)
        CallState.Ended -> ClusterProtocol.callNone()
    }
    GlassCard(padding = Space.m) {
        Text("Shows a fake call on the bike. No real call is made.", style = VlType.Caption, color = Vl.TextDim)
        Gap(Space.s)
        VlTextField(caller, { caller = it }, "Caller name", Modifier.fillMaxWidth())
        Gap(Space.m)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            CallState.entries.forEach { c -> Chip(c.label, selected = c == state) { state = c } }
        }
        Gap(Space.m)
        if (state == CallState.Confirm) {
            Text("Plays the confirm-reject countdown on a ringing call, then shows the caller again.",
                style = VlType.Caption, color = Vl.TextDim, modifier = Modifier.padding(bottom = Space.s))
        }
        PrimaryAction("Show on bike", PrimaryKind.Go, null, {
            countdown?.cancel()
            if (state == CallState.Confirm) {
                countdown = scope.launch {
                    for (n in 5 downTo 1) {
                        stream("Call · Press again $n", ClusterProtocol.call("Press again $n", ringing = true, voiceVolumePercent = 50, durationSec = 0))
                        delay(1_000)
                    }
                    stream("Call · Ringing · $caller", bytes())
                }
            } else stream("Call · ${state.label} · $caller", bytes())
        }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun PhonePanel(sendOnce: (String, ByteArray) -> Unit) {
    var battery by remember { mutableFloatStateOf(65f) }
    var signal by remember { mutableIntStateOf(3) }
    var missed by remember { mutableIntStateOf(2) }
    var sms by remember { mutableIntStateOf(1) }
    GlassCard(padding = Space.m) {
        Text("One-off packets. The bike keeps showing them until your phone sends real values again.",
            style = VlType.Caption, color = Vl.TextDim)
        Gap(Space.s)
        LabeledSlider("Phone battery", "${battery.toInt()}%", battery, 0f..100f) { battery = it }
        Text("Mobile signal", style = VlType.Label, color = Vl.Text)
        Gap(Space.xs)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            (0..4).forEach { l -> Chip("$l", selected = l == signal) { signal = l } }
        }
        Gap(Space.m)
        PrimaryAction("Send battery & signal", PrimaryKind.Go, null,
            { sendOnce("battery ${battery.toInt()}% · signal $signal", ClusterProtocol.phoneStatus(battery.toInt(), signal, "Test Rider")) },
            Modifier.fillMaxWidth())
        Gap(Space.m)
        Stepper("Missed calls", missed, { missed = it }) { sendOnce("missed calls $missed", ClusterProtocol.missedCalls(missed)) }
        Gap(Space.s)
        Stepper("New SMS", sms, { sms = it }) { sendOnce("sms count $sms", ClusterProtocol.smsCount(sms)) }
    }
}

@Composable
private fun RawPanel(onOnce: (ByteArray) -> Unit, onStream: (ByteArray) -> Unit) {
    var raw by remember { mutableStateOf("9Hello~~~~~~~~~~~~~~~World~~~~~~~~~~~~~~~00000dd") }
    GlassCard(padding = Space.m) {
        Text("For protocol experiments. Type the packet body; the first character is the packet type.",
            style = VlType.Caption, color = Vl.TextDim)
        Gap(Space.s)
        VlTextField(raw, { raw = it }, "Packet body", Modifier.fillMaxWidth())
        Text("${raw.length} characters", style = VlType.Caption, color = Vl.TextFaint, modifier = Modifier.padding(top = Space.xs))
        Gap(Space.m)
        Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            PrimaryAction("Send once", PrimaryKind.Quiet, null, { onOnce(ClusterProtocol.rawPacket(raw)) }, Modifier.weight(1f))
            PrimaryAction("Repeat", PrimaryKind.Go, null, { onStream(ClusterProtocol.rawPacket(raw)) }, Modifier.weight(1f))
        }
    }
}

// ---------------------------------------------------------------- small pieces

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Vl.Volt else Vl.Glass, tween(200), label = "chip")
    Box(
        Modifier.heightIn(min = Space.Touch).clip(CircleShape).background(bg)
            .border(BorderWidth, if (selected) Color.Transparent else Vl.Stroke, CircleShape)
            .clickable(onClick = onClick).padding(horizontal = Space.m),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = VlType.Label, color = if (selected) Vl.OnVolt else Vl.Text) }
}

@Composable
private fun Pill(text: String, icon: ImageVector?, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.heightIn(min = Space.Touch).clip(CircleShape).background(color.copy(alpha = 0.14f))
            .border(BorderWidth, color.copy(alpha = 0.35f), CircleShape).clickable(onClick = onClick)
            .padding(horizontal = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = color, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(Space.xs)) }
        Text(text, style = VlType.Label, color = color)
    }
}

@Composable
private fun SwitchLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Space.Touch).clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = VlType.Body, color = Vl.Text, modifier = Modifier.weight(1f))
        Switch(checked, onChange, colors = SwitchDefaults.colors(
            checkedThumbColor = Vl.OnVolt, checkedTrackColor = Vl.Volt,
            uncheckedThumbColor = Vl.TextDim, uncheckedTrackColor = Vl.GlassStrong, uncheckedBorderColor = Vl.Stroke))
    }
}

@Composable
private fun LabeledSlider(label: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(Modifier.padding(top = Space.s)) {
        Row {
            Text(label, style = VlType.Label, color = Vl.Text, modifier = Modifier.weight(1f))
            Text(valueText, style = VlType.Label, color = Vl.Volt)
        }
        Slider(value, onChange, valueRange = range, colors = SliderDefaults.colors(
            thumbColor = Vl.Volt, activeTrackColor = Vl.Volt, inactiveTrackColor = Vl.GlassStrong))
    }
}

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit, onSend: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = VlType.Body, color = Vl.Text, modifier = Modifier.weight(1f))
        Pill("−", null, Vl.TextDim) { onChange((value - 1).coerceAtLeast(0)) }
        Text("$value", style = VlType.RowTitle, color = Vl.Text, textAlign = TextAlign.Center, modifier = Modifier.width(40.dp))
        Pill("+", null, Vl.TextDim) { onChange((value + 1).coerceAtMost(99)) }
        Spacer(Modifier.width(Space.s))
        Pill("Send", null, Vl.Volt, onSend)
    }
}

/** "TX▶ …" / "RX cmd=…" lines with a small coloured direction tag. */
@Composable
private fun LogLine(line: String) {
    val (tag, color) = when {
        line.startsWith("RX") -> "BIKE" to Vl.Cyan
        line.startsWith("TX") || line.startsWith("sent") -> "SENT" to Vl.Volt
        else -> "INFO" to Vl.TextDim
    }
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text(tag, color = color, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(40.dp).padding(top = 1.dp))
        Text(line.removePrefix("TX▶ ").removePrefix("TX "), color = Vl.Text, fontSize = 11.sp,
            fontFamily = FontFamily.Monospace, lineHeight = 15.sp)
    }
}
