package com.steerin.parkingmaster.presentation.ui

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.steerin.parkingmaster.R
import com.steerin.parkingmaster.domain.model.CategoryScore
import com.steerin.parkingmaster.domain.model.ParkingScore
import com.steerin.parkingmaster.domain.model.RewardTier
import com.steerin.parkingmaster.domain.model.ScoreCategory
import com.steerin.parkingmaster.presentation.ui.theme.SteerinColors

/** Shown only in P. Touch is allowed here. */
@Composable
fun ResultScreen(score: ParkingScore, onAcknowledge: () -> Unit) {
    Row(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp)) {
        // Left: headline score
        Column(
            Modifier.weight(0.38f).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.result_title), style = MaterialTheme.typography.titleLarge, color = SteerinColors.Muted)
            Text("${score.total}", style = MaterialTheme.typography.displayLarge, color = tierColor(score.tier))
            Text(stringResource(R.string.result_points_suffix), style = MaterialTheme.typography.titleMedium, color = SteerinColors.Muted)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.background(tierColor(score.tier).copy(alpha = 0.16f), RoundedCornerShape(50)).padding(horizontal = 18.dp, vertical = 8.dp)) {
                Text(stringResource(tierName(score.tier)), color = tierColor(score.tier), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAcknowledge, colors = ButtonDefaults.buttonColors(containerColor = SteerinColors.Cyan, contentColor = SteerinColors.Background)) {
                Text(stringResource(R.string.result_confirm), style = MaterialTheme.typography.titleMedium)
            }
        }
        // Right: breakdown + tip
        Column(Modifier.weight(0.62f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 16.dp), verticalArrangement = Arrangement.Center) {
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
            val tip = score.weakestCategory?.let { tipFor(it) } ?: R.string.tip_perfect
            Box(Modifier.fillMaxWidth().background(SteerinColors.Surface, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 10.dp)) {
                Column {
                    Text(stringResource(R.string.result_tip_title), style = MaterialTheme.typography.labelLarge, color = SteerinColors.Cyan)
                    Spacer(Modifier.height(2.dp))
                    Text(stringResource(tip), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
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
