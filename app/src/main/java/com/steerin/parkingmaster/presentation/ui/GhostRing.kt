package com.steerin.parkingmaster.presentation.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.tooling.preview.Preview
import com.steerin.parkingmaster.domain.reward.WheelSkin
import com.steerin.parkingmaster.presentation.ui.theme.SteerinColors
import androidx.compose.ui.graphics.Path
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.abs
import kotlin.math.min

/**
 * The Snap-In visual: a translucent cyan "ghost" wheel at the target angle and the live wheel
 * on top. When |live - target| <= tolerance the live wheel turns green and the halo lights up.
 *
 * Rotation on screen equals the steering wheel angle 1:1 (540° = 1.5 turns), which is what a
 * driver's hands feel; no scaling, no numbers.
 */
@Composable
fun GhostRing(
    liveDeg: Float,
    targetDeg: Float,
    snapped: Boolean,
    toleranceDeg: Float,
    modifier: Modifier = Modifier,
    showGhost: Boolean = true,
    skin: WheelSkin = WheelSkin.CLASSIC,
    /** Accent colours for the skin. Off during live coaching, where colour carries meaning. */
    decorate: Boolean = !showGhost,
) {
    val live by animateFloatAsState(liveDeg, spring(stiffness = 600f), label = "live")
    val target by animateFloatAsState(targetDeg, tween(250), label = "target")
    val liveColor by animateColorAsState(
        when {
            snapped -> SteerinColors.Green
            abs(liveDeg - targetDeg) <= toleranceDeg * 2.5f -> SteerinColors.Text
            else -> SteerinColors.Coral
        },
        tween(200), label = "color",
    )
    val haloAlpha by animateFloatAsState(if (snapped) 0.35f else 0f, tween(250), label = "halo")

    Canvas(modifier.aspectRatio(1f)) {
        val r = min(size.width, size.height) / 2f
        val c = center
        // Halo
        if (haloAlpha > 0f) {
            drawCircle(SteerinColors.Green.copy(alpha = haloAlpha), radius = r * 0.98f, center = c)
        }
        // Tolerance band around the target top marker
        if (showGhost) {
            val band = toleranceDeg.coerceIn(3f, 40f)
            drawArc(
                color = SteerinColors.Cyan.copy(alpha = 0.18f),
                startAngle = -90f + target - band,
                sweepAngle = band * 2,
                useCenter = false,
                topLeft = Offset(c.x - r * 0.86f, c.y - r * 0.86f),
                size = Size(r * 1.72f, r * 1.72f),
                style = Stroke(width = r * 0.16f, cap = StrokeCap.Butt),
            )
            rotate(target, c) { drawWheel(c, r * 0.86f, SteerinColors.Cyan.copy(alpha = 0.55f), r * 0.045f, ghost = true) }
        }
        // Live wheel
        rotate(live, c) { drawSkinnedWheel(skin, c, r * 0.78f, liveColor, r * 0.06f, decorate) }
        // Direction hint: short arc from live toward target along the rim
        if (showGhost && !snapped) {
            val delta = (target - live).coerceIn(-60f, 60f)
            drawArc(
                color = SteerinColors.Amber,
                startAngle = -90f + live,
                sweepAngle = delta,
                useCenter = false,
                topLeft = Offset(c.x - r * 0.95f, c.y - r * 0.95f),
                size = Size(r * 1.9f, r * 1.9f),
                style = Stroke(width = r * 0.035f, cap = StrokeCap.Round),
            )
        }
    }
}

private fun DrawScope.drawWheel(c: Offset, radius: Float, color: Color, stroke: Float, ghost: Boolean) {
    val w = if (ghost) stroke * 0.8f else stroke
    // Rim
    drawCircle(color, radius, c, style = Stroke(stroke))
    // Hub: horizontal spokes from the emblem to the rim, emblem in the middle (issue #4).
    drawEmblemHub(c, radius, color, w)
    // Top marker: makes rotation unambiguous
    drawCircle(color, stroke * 1.4f, Offset(c.x, c.y - radius))
}

/**
 * The live wheel in the chosen skin. Skins change shape and texture only; the base colour is
 * always [color] (the coaching state). Accent colours appear only when [decorate] is true
 * (standby, skin picker), never while a manoeuvre is being coached.
 */
internal fun DrawScope.drawSkinnedWheel(skin: WheelSkin, c: Offset, radius: Float, color: Color, stroke: Float, decorate: Boolean) {
    when (skin) {
        WheelSkin.CLASSIC -> drawWheel(c, radius, color, stroke, ghost = false)
        WheelSkin.MASTER_RING -> {
            drawWheel(c, radius, color, stroke, ghost = false)
            val accent = if (decorate) SteerinColors.Cyan else color
            drawCircle(accent.copy(alpha = 0.9f), radius * 0.84f, c, style = Stroke(stroke * 0.35f))
            // Diamond marker instead of the dot
            val m = Offset(c.x, c.y - radius); val d = stroke * 1.9f
            drawPath(Path().apply { moveTo(m.x, m.y - d); lineTo(m.x + d, m.y); lineTo(m.x, m.y + d); lineTo(m.x - d, m.y); close() }, accent)
        }
        WheelSkin.CARBON_GRIP -> {
            // Thick rim with grip notches cut into it
            drawCircle(color, radius, c, style = Stroke(stroke * 1.7f))
            val notch = SteerinColors.Background.copy(alpha = 0.55f)
            for (i in 0 until 36) {
                val a = Math.toRadians(i * 10.0)
                val inner = radius - stroke * 0.8f; val outer = radius + stroke * 0.8f
                drawLine(notch, Offset(c.x + inner * sin(a).toFloat(), c.y - inner * cos(a).toFloat()), Offset(c.x + outer * sin(a).toFloat(), c.y - outer * cos(a).toFloat()), strokeWidth = stroke * 0.22f)
            }
            drawWheelHub(c, radius, color, stroke * 1.15f)
            drawCircle(if (decorate) SteerinColors.Coral else color, stroke * 1.4f, Offset(c.x, c.y - radius))
        }
        WheelSkin.NEON_STREAK -> {
            val glow = if (decorate) SteerinColors.Cyan else color
            for ((w, a) in listOf(4.2f to 0.10f, 2.8f to 0.16f, 1.8f to 0.26f)) drawCircle(glow.copy(alpha = a), radius, c, style = Stroke(stroke * w))
            drawWheel(c, radius, color, stroke * 0.8f, ghost = false)
            // Dashed outer track
            for (i in 0 until 24) {
                drawArc(glow.copy(alpha = 0.8f), startAngle = i * 15f, sweepAngle = 7f, useCenter = false,
                    topLeft = Offset(c.x - radius * 1.12f, c.y - radius * 1.12f), size = Size(radius * 2.24f, radius * 2.24f), style = Stroke(stroke * 0.3f))
            }
        }
        WheelSkin.GOLD_KING -> {
            val gold = if (decorate) SteerinColors.Gold else color
            drawWheel(c, radius, color, stroke, ghost = false)
            drawCircle(gold, radius * 1.1f, c, style = Stroke(stroke * 0.35f))
            drawEmblemHub(c, radius, gold, stroke * 1.05f, spokes = false)
            // Crown instead of the dot
            val top = c.y - radius; val w = stroke * 2.6f; val h = stroke * 2.4f
            drawPath(Path().apply {
                moveTo(c.x - w, top + h * 0.5f); lineTo(c.x - w, top - h * 0.6f); lineTo(c.x - w * 0.45f, top - h * 0.05f)
                lineTo(c.x, top - h); lineTo(c.x + w * 0.45f, top - h * 0.05f); lineTo(c.x + w, top - h * 0.6f); lineTo(c.x + w, top + h * 0.5f); close()
            }, gold)
        }
    }
}

private fun DrawScope.drawWheelHub(c: Offset, radius: Float, color: Color, w: Float) = drawEmblemHub(c, radius, color, w)

/** Oval + slanted-H emblem and the two horizontal spokes. See [WheelEmblem]. */
internal fun DrawScope.drawEmblemHub(c: Offset, radius: Float, color: Color, w: Float, spokes: Boolean = true) {
    val rx = radius * WheelEmblem.OVAL_RX
    val ry = radius * WheelEmblem.OVAL_RY
    if (spokes) {
        for (sx in listOf(-1f, 1f)) {
            drawLine(color, Offset(c.x + sx * rx, c.y), Offset(c.x + sx * radius * WheelEmblem.SPOKE_OUTER, c.y), strokeWidth = w * 0.9f, cap = StrokeCap.Round)
        }
    }
    drawOval(color, topLeft = Offset(c.x - rx, c.y - ry), size = Size(rx * 2, ry * 2), style = Stroke(w * 0.75f))
    val l = WheelEmblem.LINES
    for (i in l.indices step 4) {
        drawLine(color, Offset(c.x + l[i] * radius, c.y + l[i + 1] * radius), Offset(c.x + l[i + 2] * radius, c.y + l[i + 3] * radius), strokeWidth = w * 0.8f, cap = StrokeCap.Round)
    }
}


@Preview(widthDp = 320, heightDp = 320, backgroundColor = 0xFF121418, showBackground = true)
@Composable
private fun GhostRingPreview() {
    GhostRing(liveDeg = 380f, targetDeg = 459f, snapped = false, toleranceDeg = 30f)
}
