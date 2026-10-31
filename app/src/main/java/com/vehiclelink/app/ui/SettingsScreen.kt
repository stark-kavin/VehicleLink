package com.vehiclelink.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.GpsOff
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneMissed
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Textsms
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vehiclelink.app.core.AppSettings
import com.vehiclelink.app.ui.theme.Radius
import com.vehiclelink.app.ui.theme.Space
import com.vehiclelink.app.ui.theme.Vl
import com.vehiclelink.app.ui.theme.VlType

/** Settings: what the bike's buttons do, which counters it shows, GPS warning, background reliability. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenTestBench: () -> Unit,
    hasCustomPhoto: Boolean,
    onResetPhoto: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val s by AppSettings.values.collectAsStateWithLifecycle()

    // Re-check system state (battery exemption, permissions) whenever we come back from a system screen.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    val batteryExempt = remember(resumes) { isBatteryExempt(ctx) }
    val listenerOn = remember(resumes) { isListenerOn(ctx) }
    var note by remember { mutableStateOf<String?>(null) }

    val askSendSms = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        AppSettings.update { it.copy(rejectSms = ok) }
        note = if (ok) null else "SMS permission was denied, so VehicleLink can't text callers."
    }
    val askReceiveSms = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        AppSettings.update { it.copy(smsCounter = ok) }
        note = if (ok) null else "SMS permission was denied, so new messages can't be counted."
    }

    val bars = WindowInsets.safeDrawing.asPaddingValues()
    Box(Modifier.fillMaxSize()) {
        AuroraBackground(Vl.Idle)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Space.Screen, end = Space.Screen,
                top = bars.calculateTopPadding() + Space.s, bottom = bars.calculateBottomPadding() + Space.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            item { ScreenHeader("Settings", "How your bike and phone work together", onBack) }

            note?.let { msg -> item { Notice(msg, Vl.Amber) } }

            item {
                Section("Calls on your bike") {
                    ToggleRow(Icons.Rounded.Call, "Answer from bike", "The answer button picks up a ringing call",
                        s.bikeAnswer) { v -> AppSettings.update { it.copy(bikeAnswer = v) } }
                    ToggleRow(Icons.Rounded.CallEnd, "Reject from bike", "The reject button declines a call or hangs up",
                        s.bikeReject) { v -> AppSettings.update { it.copy(bikeReject = v, rejectSms = it.rejectSms && v, confirmReject = it.confirmReject && v) } }
                    ToggleRow(Icons.Rounded.TouchApp, "Confirm before rejecting",
                        if (s.bikeReject) "First press shows “Press again 5” on the bike; press again within 5 s to reject"
                        else "Turn on Reject from bike first",
                        s.confirmReject, enabled = s.bikeReject) { v -> AppSettings.update { it.copy(confirmReject = v) } }
                    ToggleRow(Icons.Rounded.Textsms, "Text the caller when you reject",
                        if (s.bikeReject) "Sends the message below after a reject from the bike" else "Turn on Reject from bike first",
                        s.rejectSms, enabled = s.bikeReject) { v ->
                        if (v && !granted(ctx, android.Manifest.permission.SEND_SMS)) askSendSms.launch(android.Manifest.permission.SEND_SMS)
                        else AppSettings.update { it.copy(rejectSms = v) }
                    }
                    AnimatedVisibility(s.rejectSms, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        VlTextField(
                            value = s.rejectText,
                            onValueChange = { t -> AppSettings.update { it.copy(rejectText = t.take(160)) } },
                            label = "Message",
                            modifier = Modifier.fillMaxWidth().padding(top = Space.s),
                            singleLine = false,
                        )
                    }
                    ToggleRow(Icons.Rounded.PhoneMissed, "Missed-call counter", "Shows how many calls you missed this ride",
                        s.missedCalls) { v -> AppSettings.update { it.copy(missedCalls = v) } }
                }
            }

            item {
                Section("Messages") {
                    ToggleRow(Icons.Rounded.Sms, "New SMS counter", "Shows how many texts arrived this ride. Messages aren't read.",
                        s.smsCounter) { v ->
                        if (v && !granted(ctx, android.Manifest.permission.RECEIVE_SMS)) askReceiveSms.launch(android.Manifest.permission.RECEIVE_SMS)
                        else AppSettings.update { it.copy(smsCounter = v) }
                    }
                }
            }

            item {
                Section("Navigation") {
                    ToggleRow(Icons.Rounded.GpsOff, "GPS-lost warning", "Shows the no-signal mark on the bike when your phone loses GPS",
                        s.gpsWarning) { v -> AppSettings.update { it.copy(gpsWarning = v) } }
                }
            }

            item {
                Section("Keep the link alive") {
                    ActionRow(
                        Icons.Rounded.BatteryAlert,
                        "Battery optimisation",
                        if (batteryExempt) "Allowed to run in the background" else "May be stopped in the background. Tap to allow.",
                        tint = if (batteryExempt) Vl.Volt else Vl.Amber,
                        trailing = if (batteryExempt) "On" else "Allow",
                        onClick = { requestBatteryExemption(ctx) },
                    )
                    ActionRow(
                        Icons.Rounded.NotificationsActive,
                        "Notification access",
                        if (listenerOn) "Music and Google Maps turns are available" else "Needed for music and Google Maps turns",
                        tint = if (listenerOn) Vl.Volt else Vl.Amber,
                        trailing = if (listenerOn) "On" else "Allow",
                        onClick = onOpenNotificationAccess,
                    )
                }
            }

            if (hasCustomPhoto) item {
                Section("Bike photo") {
                    ActionRow(Icons.Rounded.Image, "Use default photo", "Replace your photo with the built-in bike picture",
                        tint = Vl.TextDim, trailing = null, onClick = onResetPhoto)
                }
            }

            item {
                Section("Tools") {
                    ActionRow(Icons.Rounded.Science, "Test bench", "Send test screens to the bike and see the packet log",
                        tint = Vl.Cyan, trailing = null, onClick = onOpenTestBench)
                }
            }
        }
        SystemBarScrims()
    }
}

// ---------------------------------------------------------------- building blocks (shared with the test bench)

@Composable
fun ScreenHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RoundButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", Space.Touch, filled = false, onClick = onBack)
        Spacer(Modifier.width(Space.m))
        Column {
            Text(title, style = VlType.Title, color = Vl.Text)
            Text(subtitle, style = VlType.Caption, color = Vl.TextDim)
        }
    }
}

@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Eyebrow(title, modifier = Modifier.padding(start = Space.xs, bottom = Space.s))
        GlassCard(padding = Space.s) { content() }
    }
}

@Composable
fun Notice(text: String, color: androidx.compose.ui.graphics.Color) {
    GlassCard(accent = color, padding = Space.m) { Text(text, style = VlType.Label, color = Vl.Text) }
}

@Composable
private fun ToggleRow(
    icon: ImageVector, title: String, subtitle: String, checked: Boolean, enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = Space.s, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, if (checked && enabled) Vl.Volt else Vl.TextDim, size = Space.Touch - Space.s)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(title, style = VlType.Body, color = if (enabled) Vl.Text else Vl.TextFaint)
            Text(subtitle, style = VlType.Caption, color = if (enabled) Vl.TextDim else Vl.TextFaint)
        }
        Spacer(Modifier.width(Space.s))
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Vl.OnVolt, checkedTrackColor = Vl.Volt,
                uncheckedThumbColor = Vl.TextDim, uncheckedTrackColor = Vl.GlassStrong, uncheckedBorderColor = Vl.Stroke,
            ),
        )
    }
}

@Composable
fun ActionRow(
    icon: ImageVector, title: String, subtitle: String, tint: androidx.compose.ui.graphics.Color,
    trailing: String?, onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick)
            .padding(horizontal = Space.s, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint, size = Space.Touch - Space.s)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(title, style = VlType.Body, color = Vl.Text)
            Text(subtitle, style = VlType.Caption, color = Vl.TextDim)
        }
        if (trailing != null) Text(trailing, style = VlType.Label, color = tint)
        Icon(Icons.Rounded.ChevronRight, null, tint = Vl.TextFaint)
    }
}

@Composable
fun VlTextField(
    value: String, onValueChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier, singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { Text(label) }, singleLine = singleLine,
        modifier = modifier, shape = RoundedCornerShape(Radius.Tile), textStyle = VlType.Body,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Vl.Volt, unfocusedBorderColor = Vl.Stroke, cursorColor = Vl.Volt,
            focusedLabelColor = Vl.Volt, unfocusedLabelColor = Vl.TextDim,
            focusedTextColor = Vl.Text, unfocusedTextColor = Vl.Text,
        ),
    )
}

// ---------------------------------------------------------------- system helpers

private fun granted(ctx: Context, perm: String) =
    ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

fun isBatteryExempt(ctx: Context): Boolean =
    (ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isIgnoringBatteryOptimizations(ctx.packageName) == true

/** Ask Android to stop battery-optimising VehicleLink (system dialog); falls back to the settings list. */
@SuppressLint("BatteryLife")
fun requestBatteryExemption(ctx: Context) {
    if (isBatteryExempt(ctx)) {
        runCatching { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        return
    }
    val ask = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName))
    runCatching { ctx.startActivity(ask) }
        .onFailure { runCatching { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }
}

private fun isListenerOn(ctx: Context): Boolean =
    Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")?.contains(ctx.packageName) == true
