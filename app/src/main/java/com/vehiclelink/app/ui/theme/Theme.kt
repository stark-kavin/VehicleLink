package com.vehiclelink.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.vehiclelink.app.R
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
 * Chakra Petch (SIL OFL 1.1, bundled in res/font; licence in docs/licenses): angular, instrument-cluster
 * letterforms for headings, numbers, buttons and labels. Body and caption text stay on the system font so
 * small text remains easy to read.
 */
val ChakraPetch = FontFamily(
    Font(R.font.chakra_petch_regular, FontWeight.Normal),
    Font(R.font.chakra_petch_medium, FontWeight.Medium),
    Font(R.font.chakra_petch_semibold, FontWeight.SemiBold),
    Font(R.font.chakra_petch_bold, FontWeight.Bold),
)

/**
 * Type scale (sp): Display 28 · Title 20 · Headline-row 17 · Body 15 · Label 13 · Caption 12 · Overline 11.
 * Every Text in the home screen uses one of these.
 */
object VlType {
    val Display = TextStyle(fontFamily = ChakraPetch, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
    val Title = TextStyle(fontFamily = ChakraPetch, fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)
    val RowTitle = TextStyle(fontFamily = ChakraPetch, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val Body = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
    val Label = TextStyle(fontFamily = ChakraPetch, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
    val Caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
    val Overline = TextStyle(fontFamily = ChakraPetch, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.8.sp)
    val Metric = TextStyle(fontFamily = ChakraPetch, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)
    val Button = TextStyle(fontFamily = ChakraPetch, fontSize = 17.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp)
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
        displaySmall = displaySmall.copy(fontFamily = ChakraPetch, fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontFamily = ChakraPetch, fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontFamily = ChakraPetch, fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontFamily = ChakraPetch, fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontFamily = ChakraPetch, fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontFamily = ChakraPetch, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp),
    )
}

/** The app is dark-first on purpose: it is glanced at on a bike, often outdoors or at night. */
@Composable
fun VehicleLinkTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, typography = Type, content = content)
}
