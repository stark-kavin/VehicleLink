package com.vehiclelink.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vehiclelink.app.core.VehicleBus
import com.vehiclelink.app.ui.theme.BorderWidth
import com.vehiclelink.app.ui.theme.Radius
import com.vehiclelink.app.ui.theme.Space
import com.vehiclelink.app.ui.theme.Vl
import com.vehiclelink.app.ui.theme.VlType

/** Accent colour for a connection state. */
fun connColor(conn: VehicleBus.Conn): Color = when (conn) {
    VehicleBus.Conn.CONNECTED -> Vl.Volt
    VehicleBus.Conn.CONNECTING, VehicleBus.Conn.SCANNING -> Vl.Amber
    VehicleBus.Conn.DISCONNECTED -> Vl.Idle
}

// ---------------------------------------------------------------- background

/** Two slow-drifting glows tinted by the current state; sits behind everything. */
@Composable
fun AuroraBackground(tint: Color, modifier: Modifier = Modifier) {
    val c by animateColorAsState(tint, tween(900), label = "auroraTint")
    val t = rememberInfiniteTransition(label = "aurora")
    val drift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Reverse), label = "drift")
    Canvas(modifier.fillMaxSize()) {
        drawRect(Vl.Bg)
        val r1 = size.maxDimension * 0.55f
        drawCircle(
            Brush.radialGradient(listOf(c.copy(alpha = 0.16f), Color.Transparent),
                center = Offset(size.width * (0.15f + 0.5f * drift), size.height * 0.12f), radius = r1),
            radius = r1, center = Offset(size.width * (0.15f + 0.5f * drift), size.height * 0.12f)
        )
        val r2 = size.maxDimension * 0.45f
        val c2 = Offset(size.width * (0.9f - 0.4f * drift), size.height * (0.55f + 0.1f * drift))
        drawCircle(Brush.radialGradient(listOf(Vl.Cyan.copy(alpha = 0.05f), Color.Transparent), c2, r2), r2, c2)
    }
}

// ---------------------------------------------------------------- hero effects

/** Expanding radar rings — shown while searching / connecting. */
@Composable
fun RadarRings(active: Boolean, color: Color, modifier: Modifier = Modifier) {
    val alpha by animateFloatAsState(if (active) 1f else 0f, tween(600), label = "radarAlpha")
    if (alpha == 0f) return
    val t = rememberInfiniteTransition(label = "radar")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "radarP")
    Canvas(modifier) {
        val maxR = size.minDimension / 2f
        for (i in 0 until 3) {
            val f = (p + i / 3f) % 1f
            drawCircle(
                color = color.copy(alpha = (1f - f) * 0.45f * alpha),
                radius = maxR * (0.25f + 0.75f * f),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
    }
}

/** Soft elliptical glow under the bike, like a floor reflection. */
@Composable
fun FloorGlow(color: Color, strength: Float, modifier: Modifier = Modifier) {
    val c by animateColorAsState(color, tween(700), label = "floor")
    val s by animateFloatAsState(strength, tween(700), label = "floorS")
    Canvas(modifier) {
        drawOval(
            Brush.radialGradient(
                listOf(c.copy(alpha = 0.35f * s), Color.Transparent),
                center = center, radius = size.width / 2f
            ),
            topLeft = Offset.Zero, size = size
        )
    }
}

// ---------------------------------------------------------------- surfaces

/** Frosted "glass" card with a hairline border. Optional accent tint on the border. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    padding: Dp = Space.m,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Radius.Card)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, spring(stiffness = Spring.StiffnessMediumLow), label = "cardPress")
    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf((accent ?: Color.White).copy(alpha = if (accent != null) 0.10f else 0.07f), Vl.Glass)
                )
            )
            .border(BorderWidth, (accent ?: Color.White).copy(alpha = if (accent != null) 0.28f else 0.09f), shape)
            .then(if (onClick != null) Modifier.clickable(interaction, indication = null, onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun Eyebrow(text: String, color: Color = Vl.TextDim, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = VlType.Overline, color = color, modifier = modifier)
}

/** Small rounded square holding an icon — used as the leading visual in cards. */
@Composable
fun IconBadge(icon: ImageVector, tint: Color, size: Dp = Space.Touch, content: (@Composable BoxScope.() -> Unit)? = null) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(Radius.Tile))
            .background(tint.copy(alpha = 0.14f))
            .border(BorderWidth, tint.copy(alpha = 0.25f), RoundedCornerShape(Radius.Tile)),
        contentAlignment = Alignment.Center
    ) {
        if (content != null) content() else Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

// ---------------------------------------------------------------- status

/** Pill with a breathing dot and the current status line. */
@Composable
fun StatusPill(conn: VehicleBus.Conn, text: String) {
    val color by animateColorAsState(connColor(conn), tween(500), label = "pill")
    val t = rememberInfiniteTransition(label = "dot")
    val pulse by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dotP")
    val live = conn != VehicleBus.Conn.DISCONNECTED
    Row(
        Modifier.clip(CircleShape).background(color.copy(alpha = 0.10f))
            .border(BorderWidth, color.copy(alpha = 0.25f), CircleShape)
            .padding(horizontal = Space.m, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (live) Box(Modifier.size(14.dp).graphicsLayer { alpha = 1f - pulse; scaleX = 0.6f + pulse; scaleY = 0.6f + pulse }
                .background(color.copy(alpha = 0.5f), CircleShape))
            Box(Modifier.size(8.dp).background(color, CircleShape))
        }
        Spacer(Modifier.width(Space.s))
        AnimatedContent(
            text,
            transitionSpec = { (fadeIn(tween(250)) + slideInVertically { it / 2 }) togetherWith (fadeOut(tween(150)) + slideOutVertically { -it / 2 }) },
            label = "statusText"
        ) { Text(it, style = VlType.Label, color = Vl.Text, maxLines = 1) }
    }
}

// ---------------------------------------------------------------- buttons

enum class PrimaryKind { Go, Busy, Quiet }

/** Big pill action with press-squish, colour morph and an animated label. */
@Composable
fun PrimaryAction(label: String, kind: PrimaryKind, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.5f, stiffness = 600f), label = "btn")
    val bg by animateColorAsState(
        when (kind) { PrimaryKind.Go -> Vl.Volt; PrimaryKind.Busy -> Vl.Amber.copy(alpha = 0.14f); PrimaryKind.Quiet -> Vl.GlassStrong },
        tween(400), label = "btnBg"
    )
    val fg by animateColorAsState(
        when (kind) { PrimaryKind.Go -> Vl.OnVolt; PrimaryKind.Busy -> Vl.Amber; PrimaryKind.Quiet -> Vl.Text },
        tween(400), label = "btnFg"
    )
    val border = when (kind) { PrimaryKind.Go -> Color.Transparent; PrimaryKind.Busy -> Vl.Amber.copy(alpha = 0.4f); PrimaryKind.Quiet -> Vl.Stroke }
    Box(
        modifier.height(56.dp).graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(bg)
            .border(BorderWidth, border, CircleShape)
            .clickable(interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            label to kind,
            transitionSpec = { (fadeIn(tween(220)) + scaleIn(initialScale = 0.9f)) togetherWith (fadeOut(tween(120)) + scaleOut(targetScale = 0.9f)) },
            label = "btnLabel"
        ) { (l, k) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (k == PrimaryKind.Busy) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = fg, strokeWidth = 2.dp)
                    Spacer(Modifier.width(Space.s))
                } else if (icon != null) {
                    Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(Space.s))
                }
                Text(l, color = fg, style = VlType.Button)
            }
        }
    }
}

/** Round icon button with press scale; [filled] = volt disc. */
@Composable
fun RoundButton(icon: ImageVector, desc: String, size: Dp, filled: Boolean, onClick: () -> Unit, iconContent: (@Composable () -> Unit)? = null) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.45f, stiffness = 700f), label = "round")
    Box(
        Modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(if (filled) Vl.Volt else Vl.GlassStrong)
            .border(BorderWidth, if (filled) Color.Transparent else Vl.Stroke, CircleShape)
            .clickable(interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (iconContent != null) iconContent()
        else Icon(icon, desc, tint = if (filled) Vl.OnVolt else Vl.Text, modifier = Modifier.size(size * 0.45f))
    }
}

// ---------------------------------------------------------------- gauges

/** Battery glyph with an animated fill; colour shifts to amber / coral when low. */
@Composable
fun BatteryGlyph(percent: Int, modifier: Modifier = Modifier.size(width = 30.dp, height = 16.dp)) {
    val f by animateFloatAsState(percent.coerceIn(0, 100) / 100f, tween(800), label = "batt")
    val col = when { percent <= 15 -> Vl.Coral; percent <= 30 -> Vl.Amber; else -> Vl.Volt }
    Canvas(modifier) {
        val nub = 3.dp.toPx()
        val body = Size(size.width - nub, size.height)
        val r = CornerRadius(4.dp.toPx())
        drawRoundRect(Vl.TextDim.copy(alpha = 0.6f), size = body, cornerRadius = r, style = Stroke(1.5.dp.toPx()))
        drawRoundRect(Vl.TextDim.copy(alpha = 0.6f), topLeft = Offset(body.width + 1f, size.height * 0.3f),
            size = Size(nub - 1f, size.height * 0.4f), cornerRadius = CornerRadius(2f))
        val inset = 3.dp.toPx()
        drawRoundRect(col, topLeft = Offset(inset, inset),
            size = Size((body.width - inset * 2) * f, body.height - inset * 2), cornerRadius = CornerRadius(2.dp.toPx()))
    }
}

/** Four rising bars; [level] 0..4 lit. */
@Composable
fun SignalBars(level: Int, color: Color = Vl.Volt, modifier: Modifier = Modifier.size(width = 26.dp, height = 18.dp)) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        for (i in 0 until 4) {
            val lit = i < level
            val c by animateColorAsState(if (lit) color else Vl.Decor, tween(400, delayMillis = i * 60), label = "bar$i")
            Box(Modifier.weight(1f).fillMaxHeight(0.3f + 0.7f * (i + 1) / 4f).clip(RoundedCornerShape(2.dp)).background(c))
        }
    }
}

/** Animated equaliser — bars bounce while [playing], settle when paused. */
@Composable
fun Equalizer(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "eq")
    val speeds = intArrayOf(520, 380, 610, 450)
    val heights = speeds.mapIndexed { i, ms ->
        t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(ms, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "eq$i")
    }
    val amp by animateFloatAsState(if (playing) 1f else 0f, tween(500), label = "eqAmp")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        heights.forEach { h ->
            Box(
                Modifier.width(4.dp).fillMaxHeight(0.18f + 0.82f * h.value * amp)
                    .clip(RoundedCornerShape(2.dp)).background(color)
            )
        }
    }
}

/** Thin progress track with a glowing head. [fraction] 0..1. */
@Composable
fun ProgressTrack(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600, easing = LinearEasing), label = "prog")
    Box(modifier.fillMaxWidth().height(14.dp).drawBehind {
        val y = size.height / 2
        val h = 4.dp.toPx()
        drawRoundRect(Color.White.copy(alpha = 0.10f), Offset(0f, y - h / 2), Size(size.width, h), CornerRadius(h))
        drawRoundRect(color, Offset(0f, y - h / 2), Size(size.width * f, h), CornerRadius(h))
        val head = Offset(size.width * f, y)
        drawCircle(color.copy(alpha = 0.25f), 9.dp.toPx(), head)
        drawCircle(color, 5.dp.toPx(), head)
    })
}

/** Vertical gap helper to keep layouts terse. */
@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))

/** Small two-line metric used in the telemetry tiles. */
@Composable
fun Metric(label: String, value: String, modifier: Modifier = Modifier, visual: @Composable () -> Unit) {
    GlassCard(modifier, padding = Space.m) {
        Box(Modifier.height(24.dp), contentAlignment = Alignment.CenterStart) { visual() }
        Gap(Space.s)
        AnimatedContent(value, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) }, label = "metric") {
            Text(it, color = Vl.Text, style = VlType.Metric, maxLines = 1)
        }
        Text(label, color = Vl.TextDim, style = VlType.Caption, maxLines = 1)
    }
}

/** Empty-state friendly circle used for avatars. */
@Composable
fun Avatar(initial: String, color: Color, ringing: Boolean) {
    val t = rememberInfiniteTransition(label = "avatar")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "avatarP")
    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        if (ringing) Canvas(Modifier.fillMaxSize()) {
            for (i in 0 until 2) {
                val f = (p + i * 0.5f) % 1f
                drawCircle(color.copy(alpha = (1f - f) * 0.5f), radius = size.minDimension / 2f * (0.7f + 0.3f * f), style = Stroke(2.dp.toPx()))
            }
        }
        Box(
            Modifier.size(46.dp).clip(CircleShape)
                .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.55f)))),
            contentAlignment = Alignment.Center
        ) { Text(initial, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
    }
}
