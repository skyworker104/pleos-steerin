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
import com.steerin.parkingmaster.presentation.ui.theme.SteerinColors
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

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
        rotate(live, c) { drawWheel(c, r * 0.78f, liveColor, r * 0.06f, ghost = false) }
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
    // Rim
    drawCircle(color, radius, c, style = Stroke(stroke))
    // Hub
    drawCircle(color, radius * 0.16f, c, style = if (ghost) Stroke(stroke * 0.8f) else Stroke(stroke))
    // Three spokes: 180° (bottom), 60°, 300° in screen coordinates (top marker separate)
    for (deg in listOf(90f, 210f, 330f)) {
        val rad = Math.toRadians(deg.toDouble())
        val from = Offset(c.x + cos(rad).toFloat() * radius * 0.18f, c.y + sin(rad).toFloat() * radius * 0.18f)
        val to = Offset(c.x + cos(rad).toFloat() * radius * 0.96f, c.y + sin(rad).toFloat() * radius * 0.96f)
        drawLine(color, from, to, strokeWidth = stroke * 0.9f, cap = StrokeCap.Round)
    }
    // Top marker: makes rotation unambiguous
    drawCircle(color, stroke * 1.4f, Offset(c.x, c.y - radius))
}

@Preview(widthDp = 320, heightDp = 320, backgroundColor = 0xFF121418, showBackground = true)
@Composable
private fun GhostRingPreview() {
    GhostRing(liveDeg = 380f, targetDeg = 459f, snapped = false, toleranceDeg = 30f)
}
