package com.steerin.parkingmaster.presentation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.steerin.parkingmaster.R
import com.steerin.parkingmaster.domain.reward.MascotMood
import com.steerin.parkingmaster.presentation.ui.theme.SteerinColors
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 휠리 (Wheely): the app's own mascot, a small tyre with a friendly face. Drawn in code so it
 * scales to any display and needs no image assets. Shown only in P (standby, result, skins),
 * never during a manoeuvre.
 */
@Composable
fun Mascot(mood: MascotMood, modifier: Modifier = Modifier, size: Dp = 64.dp) {
    Canvas(modifier.size(size)) { drawMascot(mood) }
}

private val Tyre = Color(0xFF2B313B)
private val Tread = Color(0xFF454D5A)
private val Face = Color(0xFFE6F7FB)
private val Ink = Color(0xFF121418)
private val Cheek = Color(0xFFFF8A80)

private fun DrawScope.drawMascot(mood: MascotMood) {
    val r = min(size.width, size.height) / 2f
    val c = center
    // Tyre with tread blocks
    drawCircle(Tyre, r * 0.98f, c)
    for (i in 0 until 16) {
        val a = Math.toRadians(i * 22.5)
        val p = Offset(c.x + r * 0.9f * sin(a).toFloat(), c.y - r * 0.9f * cos(a).toFloat())
        drawCircle(Tread, r * 0.07f, p)
    }
    // Rim / face
    drawCircle(SteerinColors.Cyan, r * 0.74f, c)
    drawCircle(Face, r * 0.66f, c)
    val eyeY = c.y - r * 0.12f
    val eyeDx = r * 0.24f
    val ink = r * 0.07f
    when (mood) {
        MascotMood.PROUD, MascotMood.CHEER -> {
            // Happy closed eyes ^ ^
            for (sx in listOf(-1f, 1f)) {
                drawArc(Ink, 200f, 140f, false, Offset(c.x + sx * eyeDx - r * 0.1f, eyeY - r * 0.06f), Size(r * 0.2f, r * 0.2f), style = Stroke(ink, cap = StrokeCap.Round))
            }
        }
        else -> for (sx in listOf(-1f, 1f)) {
            drawCircle(Ink, r * 0.075f, Offset(c.x + sx * eyeDx, eyeY))
            drawCircle(Color.White, r * 0.025f, Offset(c.x + sx * eyeDx + r * 0.025f, eyeY - r * 0.03f))
        }
    }
    // Cheeks
    for (sx in listOf(-1f, 1f)) drawCircle(Cheek.copy(alpha = 0.55f), r * 0.08f, Offset(c.x + sx * r * 0.38f, c.y + r * 0.08f))
    // Mouth
    val mouthTop = c.y + r * 0.02f
    when (mood) {
        MascotMood.PROUD -> drawPath(Path().apply {
            moveTo(c.x - r * 0.2f, mouthTop); quadraticBezierTo(c.x, mouthTop + r * 0.36f, c.x + r * 0.2f, mouthTop); close()
        }, Ink)
        MascotMood.CHEER, MascotMood.HELLO -> drawArc(Ink, 20f, 140f, false, Offset(c.x - r * 0.16f, mouthTop - r * 0.12f), Size(r * 0.32f, r * 0.26f), style = Stroke(ink, cap = StrokeCap.Round))
        MascotMood.ENCOURAGE -> drawLine(Ink, Offset(c.x - r * 0.12f, mouthTop + r * 0.08f), Offset(c.x + r * 0.12f, mouthTop + r * 0.05f), ink, StrokeCap.Round)
        MascotMood.COMFORT -> drawArc(Ink, 30f, 120f, false, Offset(c.x - r * 0.1f, mouthTop), Size(r * 0.2f, r * 0.14f), style = Stroke(ink, cap = StrokeCap.Round))
    }
    // Mood extras
    when (mood) {
        MascotMood.PROUD -> sparkle(Offset(c.x + r * 0.82f, c.y - r * 0.78f), r * 0.16f, SteerinColors.Gold)
        MascotMood.CHEER -> sparkle(Offset(c.x + r * 0.84f, c.y - r * 0.74f), r * 0.12f, SteerinColors.Green)
        MascotMood.COMFORT -> drawCircle(SteerinColors.Cyan.copy(alpha = 0.8f), r * 0.07f, Offset(c.x + r * 0.5f, c.y - r * 0.42f))
        else -> Unit
    }
}

private fun DrawScope.sparkle(p: Offset, s: Float, color: Color) {
    drawPath(Path().apply {
        moveTo(p.x, p.y - s); lineTo(p.x + s * 0.28f, p.y - s * 0.28f); lineTo(p.x + s, p.y); lineTo(p.x + s * 0.28f, p.y + s * 0.28f)
        lineTo(p.x, p.y + s); lineTo(p.x - s * 0.28f, p.y + s * 0.28f); lineTo(p.x - s, p.y); lineTo(p.x - s * 0.28f, p.y - s * 0.28f); close()
    }, color)
}

/** Mascot plus a speech bubble with one or two short lines. */
@Composable
fun MascotBubble(mood: MascotMood, lines: List<String>, modifier: Modifier = Modifier, mascotSize: Dp = 56.dp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Mascot(mood, size = mascotSize)
        Box(Modifier.background(SteerinColors.SurfaceHigh, RoundedCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 14.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Column {
                Text(stringResource(R.string.mascot_name), style = MaterialTheme.typography.labelMedium, color = SteerinColors.Cyan)
                lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 90, backgroundColor = 0xFF121418, showBackground = true)
@Composable
private fun MascotPreview() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { MascotMood.entries.forEach { Mascot(it, size = 64.dp) } }
}
