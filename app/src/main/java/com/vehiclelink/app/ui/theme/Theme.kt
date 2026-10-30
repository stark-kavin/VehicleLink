package com.vehiclelink.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

    // Text contrast on the card fill (~#121518): Text 16:1, TextDim 6.1:1, TextFaint 4.8:1 (all ≥ 4.5:1).
    val Text = Color(0xFFF2F4F7)
    val TextDim = Color(0xFF8C96A3)
    val TextFaint = Color(0xFF7A8594)
    val Decor = Color(0xFF3A424D)        // non-text only (inactive bars, tracks)

    val Volt = Color(0xFFD4FF4F)         // connected / primary action
    val OnVolt = Color(0xFF0D1204)
    val Amber = Color(0xFFFFB547)        // searching / connecting / warnings
    val Coral = Color(0xFFFF6B5B)        // calls
    val Cyan = Color(0xFF5CE1FF)         // navigation
    val Idle = Color(0xFF6B7684)
}

/** 8dp spacing grid. Use these instead of ad-hoc dp values. */
object Space {
    val xs = 4.dp    // half-step, only inside dense rows
    val s = 8.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
    val Screen = 16.dp       // horizontal screen margin
    val Touch = 48.dp        // minimum touch target
}

/** Corner radii and borders. Cards 24, inner tiles/badges 16, chips & buttons fully round. */
object Radius {
    val Card = 24.dp
    val Tile = 16.dp
    val Small = 8.dp
}
val BorderWidth = 1.dp

/**
 * Type scale (sp): Display 28 · Title 20 · Headline-row 17 · Body 15 · Label 13 · Caption 12 · Overline 11.
 * Every Text in the home screen uses one of these.
 */
object VlType {
    val Display = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
    val Title = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp)
    val RowTitle = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val Body = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
    val Label = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    val Caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
    val Overline = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp)
    val Metric = TextStyle(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
    val Button = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
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
