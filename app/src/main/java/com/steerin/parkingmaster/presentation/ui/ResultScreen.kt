package com.steerin.parkingmaster.presentation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.steerin.parkingmaster.R
import com.steerin.parkingmaster.domain.model.CategoryScore
import com.steerin.parkingmaster.domain.model.ParkingScore
import com.steerin.parkingmaster.domain.model.RewardTier
import com.steerin.parkingmaster.domain.model.ScoreCategory
import com.steerin.parkingmaster.domain.outcome.OutcomeCategory
import com.steerin.parkingmaster.domain.outcome.OutcomeScore
import com.steerin.parkingmaster.domain.outcome.SlotObservation
import com.steerin.parkingmaster.domain.outcome.SlotOutcomeEvaluator
import com.steerin.parkingmaster.domain.outcome.SlotSource
import com.steerin.parkingmaster.presentation.ui.theme.SteerinColors

/**
 * Shown only in P. Touch is allowed here.
 *
 * Without slot data (today's platform) this is the process score alone. With an [outcome]
 * (SVM, or the labelled demo source) the headline becomes the process/result blend and a top-view
 * slot diagram with the four gaps is added on the right.
 */
@Composable
fun ResultScreen(score: ParkingScore, outcome: OutcomeScore? = null, onAcknowledge: () -> Unit) {
    val combined = SlotOutcomeEvaluator().combined(score.total, outcome)
    val tier = RewardTier.fromScore(combined)
    Row(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp)) {
        // Left: headline score
        Column(
            Modifier.weight(if (outcome == null) 0.38f else 0.26f).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(if (outcome == null) R.string.result_title else R.string.outcome_combined), style = MaterialTheme.typography.titleLarge, color = SteerinColors.Muted)
            Text("$combined", style = MaterialTheme.typography.displayLarge, color = tierColor(tier))
            if (outcome == null) {
                Text(stringResource(R.string.result_points_suffix), style = MaterialTheme.typography.titleMedium, color = SteerinColors.Muted)
            } else {
                Text(stringResource(R.string.outcome_split, score.total, outcome.total), style = MaterialTheme.typography.labelLarge, color = SteerinColors.Muted)
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.background(tierColor(tier).copy(alpha = 0.16f), RoundedCornerShape(50)).padding(horizontal = 18.dp, vertical = 8.dp)) {
                Text(stringResource(tierName(tier)), color = tierColor(tier), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAcknowledge, colors = ButtonDefaults.buttonColors(containerColor = SteerinColors.Cyan, contentColor = SteerinColors.Background)) {
                Text(stringResource(R.string.result_confirm), style = MaterialTheme.typography.titleMedium)
            }
        }
        // Middle: process breakdown + tip
        Column(
            Modifier.weight(if (outcome == null) 0.62f else 0.44f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 16.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            score.categories.forEach { CategoryBar(it) }
            Spacer(Modifier.height(6.dp))
            val m = score.metrics
            Text(
                stringResource(
                    R.string.result_metrics,
                    m.steeringReversals, m.directionChanges, m.maxReverseSpeedKmh, m.durationSec.toInt(), m.distanceReversedM,
                ),
                style = MaterialTheme.typography.labelMedium, color = SteerinColors.Muted,
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().background(SteerinColors.Surface, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 10.dp)) {
                Column {
                    Text(stringResource(R.string.result_tip_title), style = MaterialTheme.typography.labelLarge, color = SteerinColors.Cyan)
                    Spacer(Modifier.height(2.dp))
                    Text(stringResource(tipFor(score, outcome)), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (outcome == null) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.outcome_unavailable), style = MaterialTheme.typography.labelMedium, color = SteerinColors.Muted)
            }
        }
        // Right: slot diagram + result breakdown
        if (outcome != null) {
            Column(Modifier.weight(0.30f).fillMaxHeight().padding(start = 20.dp), verticalArrangement = Arrangement.Center) {
                OutcomePanel(outcome)
            }
        }
    }
}

/** Picks the tip for whichever of the process or result categories lost the most, proportionally. */
private fun tipFor(score: ParkingScore, outcome: OutcomeScore?): Int {
    val p = score.categories.minByOrNull { it.points.toFloat() / it.category.maxPoints }?.takeIf { it.points < it.category.maxPoints }
    val o = outcome?.categories?.minByOrNull { it.points.toFloat() / it.category.maxPoints }?.takeIf { it.points < it.category.maxPoints }
    val pFrac = p?.let { it.points.toFloat() / it.category.maxPoints } ?: 1f
    val oFrac = o?.let { it.points.toFloat() / it.category.maxPoints } ?: 1f
    return when {
        o != null && oFrac < pFrac -> outcomeTip(o.category)
        p != null -> tipFor(p.category)
        else -> R.string.tip_perfect
    }
}

@Composable
private fun OutcomePanel(outcome: OutcomeScore) {
    val obs = outcome.observation
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.outcome_title), style = MaterialTheme.typography.titleMedium)
        val demo = obs.source == SlotSource.SIMULATED
        val badge = if (demo) SteerinColors.Amber else SteerinColors.Green
        Box(Modifier.background(badge.copy(alpha = 0.18f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
            Text(stringResource(if (demo) R.string.outcome_demo_badge else R.string.outcome_svm_badge), color = badge, style = MaterialTheme.typography.labelMedium)
        }
    }
    Spacer(Modifier.height(4.dp))
    SlotDiagram(obs, Modifier.fillMaxWidth().height(150.dp))
    Spacer(Modifier.height(4.dp))
    outcome.categories.forEach { c ->
        val frac = c.points.toFloat() / c.category.maxPoints
        Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(outcomeName(c.category)), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(76.dp))
            Box(Modifier.weight(1f).height(8.dp).background(SteerinColors.SurfaceHigh, RoundedCornerShape(4.dp))) {
                Box(Modifier.fillMaxWidth(frac.coerceIn(0.02f, 1f)).fillMaxHeight().background(fracColor(frac), RoundedCornerShape(4.dp)))
            }
            Text("${c.points}/${c.category.maxPoints}", style = MaterialTheme.typography.labelLarge, color = fracColor(frac), modifier = Modifier.padding(start = 6.dp).width(44.dp))
        }
    }
}

/**
 * Top view of the slot (white lines; dashed entry line on top) and the car reversed into it.
 * Geometry comes straight from the four gaps and yaw, so it shows exactly what was scored.
 */
@Composable
private fun SlotDiagram(obs: SlotObservation, modifier: Modifier) {
    val carColor = if (obs.insideSlot) SteerinColors.Cyan else SteerinColors.Coral
    val lineColor = SteerinColors.Muted
    val labelStyle = MaterialTheme.typography.labelMedium
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.outcome_gap_front, gapText(obs.frontGapCm)), style = labelStyle, color = gapColor(obs.frontGapCm))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.outcome_gap_side, gapText(obs.leftGapCm)), style = labelStyle, color = gapColor(obs.leftGapCm), modifier = Modifier.width(48.dp))
            Canvas(Modifier.fillMaxHeight().width(84.dp)) {
                val carW = CAR_WIDTH_CM; val carL = CAR_LENGTH_CM
                // Slot = car + both gaps (a negative gap simply means the body is over that line).
                val slotW = carW + obs.leftGapCm + obs.rightGapCm
                val slotL = carL + obs.frontGapCm + obs.rearGapCm
                // Fit the union of slot and car into the canvas, centred.
                val minX = minOf(0f, obs.leftGapCm); val maxX = maxOf(slotW, obs.leftGapCm + carW)
                val minY = minOf(0f, obs.frontGapCm); val maxY = maxOf(slotL, obs.frontGapCm + carL)
                val scale = minOf(size.width / (maxX - minX), size.height / (maxY - minY)) * 0.92f
                val ox = (size.width - (maxX - minX) * scale) / 2f - minX * scale
                val oy = (size.height - (maxY - minY) * scale) / 2f - minY * scale
                val stroke = 2.dp.toPx()
                // Slot lines: left, right, rear (solid) and entry (dashed)
                drawLine(lineColor, Offset(ox, oy), Offset(ox, oy + slotL * scale), stroke)
                drawLine(lineColor, Offset(ox + slotW * scale, oy), Offset(ox + slotW * scale, oy + slotL * scale), stroke)
                drawLine(lineColor, Offset(ox, oy + slotL * scale), Offset(ox + slotW * scale, oy + slotL * scale), stroke)
                drawLine(lineColor, Offset(ox, oy), Offset(ox + slotW * scale, oy), stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                // Car body, offset by the gaps and rotated by yaw around its centre
                val carX = ox + obs.leftGapCm * scale
                val carY = oy + obs.frontGapCm * scale
                val centre = Offset(carX + carW * scale / 2f, carY + carL * scale / 2f)
                rotate(obs.yawDeg, centre) {
                    drawRoundRect(carColor.copy(alpha = 0.28f), Offset(carX, carY), Size(carW * scale, carL * scale), androidx.compose.ui.geometry.CornerRadius(6f, 6f))
                    drawRoundRect(carColor, Offset(carX, carY), Size(carW * scale, carL * scale), androidx.compose.ui.geometry.CornerRadius(6f, 6f), style = Stroke(stroke))
                    // Windscreen marks the front of the car
                    drawLine(carColor, Offset(carX + carW * scale * 0.18f, carY + carL * scale * 0.28f), Offset(carX + carW * scale * 0.82f, carY + carL * scale * 0.28f), stroke)
                }
            }
            Text(stringResource(R.string.outcome_gap_side, gapText(obs.rightGapCm)), style = labelStyle, color = gapColor(obs.rightGapCm), modifier = Modifier.width(48.dp).padding(start = 6.dp))
        }
        Text(
            stringResource(R.string.outcome_gap_rear, gapText(obs.rearGapCm)) + " · " + stringResource(R.string.outcome_yaw, obs.yawDeg),
            style = labelStyle, color = SteerinColors.Muted,
        )
    }
}

private const val CAR_WIDTH_CM = 185f
private const val CAR_LENGTH_CM = 470f

private fun gapText(cm: Float) = "${cm.toInt()}cm"
private fun gapColor(cm: Float) = if (cm < 0f) SteerinColors.Coral else SteerinColors.Muted
private fun fracColor(frac: Float) = when {
    frac >= 0.85f -> SteerinColors.Green
    frac >= 0.5f -> SteerinColors.Amber
    else -> SteerinColors.Coral
}

fun outcomeName(c: OutcomeCategory): Int = when (c) {
    OutcomeCategory.IN_SLOT -> R.string.ocat_in_slot
    OutcomeCategory.CENTERING -> R.string.ocat_centering
    OutcomeCategory.DEPTH -> R.string.ocat_depth
    OutcomeCategory.ALIGNMENT -> R.string.ocat_alignment
}

fun outcomeTip(c: OutcomeCategory): Int = when (c) {
    OutcomeCategory.IN_SLOT -> R.string.otip_in_slot
    OutcomeCategory.CENTERING -> R.string.otip_centering
    OutcomeCategory.DEPTH -> R.string.otip_depth
    OutcomeCategory.ALIGNMENT -> R.string.otip_alignment
}

@Composable
private fun CategoryBar(c: CategoryScore) {
    val frac = c.points.toFloat() / c.category.maxPoints
    val color = when {
        frac >= 0.85f -> SteerinColors.Green
        frac >= 0.5f -> SteerinColors.Amber
        else -> SteerinColors.Coral
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(categoryName(c.category)), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(140.dp))
            Box(Modifier.weight(1f).height(12.dp).background(SteerinColors.SurfaceHigh, RoundedCornerShape(6.dp))) {
                Box(Modifier.fillMaxWidth(frac.coerceIn(0.02f, 1f)).fillMaxHeight().background(color, RoundedCornerShape(6.dp)))
            }
            Spacer(Modifier.width(12.dp))
            Text("${c.points}/${c.category.maxPoints}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.width(64.dp))
        }
    }
}

fun categoryName(c: ScoreCategory): Int = when (c) {
    ScoreCategory.STEERING_STABILITY -> R.string.cat_steering
    ScoreCategory.RETRY_COUNT -> R.string.cat_retry
    ScoreCategory.SPEED_AND_BRAKING -> R.string.cat_speed
    ScoreCategory.SAFETY -> R.string.cat_safety
    ScoreCategory.DURATION -> R.string.cat_duration
}

fun tipFor(c: ScoreCategory): Int = when (c) {
    ScoreCategory.STEERING_STABILITY -> R.string.tip_steering
    ScoreCategory.RETRY_COUNT -> R.string.tip_retry
    ScoreCategory.SPEED_AND_BRAKING -> R.string.tip_speed
    ScoreCategory.SAFETY -> R.string.tip_safety
    ScoreCategory.DURATION -> R.string.tip_duration
}

fun tierName(t: RewardTier): Int = when (t) {
    RewardTier.TOP_KING -> R.string.tier_top_king
    RewardTier.MASTER -> R.string.tier_master
    RewardTier.ENTRY -> R.string.tier_entry
    RewardTier.PRACTICE -> R.string.tier_practice
}

fun tierColor(t: RewardTier) = when (t) {
    RewardTier.TOP_KING -> SteerinColors.Green
    RewardTier.MASTER -> SteerinColors.Cyan
    RewardTier.ENTRY -> SteerinColors.Amber
    RewardTier.PRACTICE -> SteerinColors.Coral
}
