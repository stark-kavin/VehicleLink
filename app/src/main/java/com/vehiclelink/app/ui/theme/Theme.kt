package com.vehiclelink.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * "Cockpit" palette — a dark, instrument-cluster look. One bright accent (volt) for "live",
 * amber for searching, coral for calls, cyan for navigation.
 */
object Vl {
    val Bg = Color(0xFF07090C)
    val BgRaised = Color(0xFF0E1116)
    val Glass = Color(0x0FFFFFFF)        // card fill (white 6%)
    val GlassStrong = Color(0x1AFFFFFF)  // pressed / nested fill
    val Stroke = Color(0x17FFFFFF)       // hairline borders

    val Text = Color(0xFFF2F4F7)
    val TextDim = Color(0xFF8C96A3)
    val TextFaint = Color(0xFF55606C)

    val Volt = Color(0xFFD4FF4F)         // connected / primary action
    val OnVolt = Color(0xFF0D1204)
    val Amber = Color(0xFFFFB547)        // searching / connecting / warnings
    val Coral = Color(0xFFFF6B5B)        // calls
    val Cyan = Color(0xFF5CE1FF)         // navigation
    val Idle = Color(0xFF6B7684)
}

private val DarkColors = darkColorScheme(
    primary = Vl.Volt,
    onPrimary = Vl.OnVolt,
    primaryContainer = Color(0xFF2A3310),
    onPrimaryContainer = Vl.Volt,
    secondary = Vl.Cyan,
    background = Vl.Bg,
    onBackground = Vl.Text,
    surface = Vl.BgRaised,
    onSurface = Vl.Text,
    surfaceVariant = Color(0xFF161A21),
    onSurfaceVariant = Vl.TextDim,
    surfaceContainer = Color(0xFF12161C),
    outline = Color(0xFF2A303A),
    error = Vl.Coral,
)

private val Type = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        labelSmall = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp),
    )
}

/** The app is dark-first on purpose: it is glanced at on a bike, often outdoors or at night. */
@Composable
fun VehicleLinkTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, typography = Type, content = content)
}
