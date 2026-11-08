package com.steerin.parkingmaster.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.steerin.parkingmaster.R
import com.steerin.parkingmaster.domain.model.ParkingSide
import com.steerin.parkingmaster.domain.model.RewardTier
import com.steerin.parkingmaster.domain.model.SessionSummary
import com.steerin.parkingmaster.domain.outcome.SlotSource
import com.steerin.parkingmaster.presentation.ui.theme.SteerinColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Parked-only screen: list of saved sessions with score, tier, duration and a replay action. */
@Composable
fun HistoryScreen(
    sessions: List<SessionSummary>,
    onBack: () -> Unit,
    onReplay: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.history_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            HistoryStats(sessions)
            Spacer(Modifier.width(12.dp))
            TextButton(onClick = onBack) { Text(stringResource(R.string.history_back), style = MaterialTheme.typography.titleMedium) }
        }
        Spacer(Modifier.height(8.dp))
        if (sessions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyLarge, color = SteerinColors.Muted)
            }
            return
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(sessions, key = { it.id }) { s -> HistoryRow(s, onReplay = { onReplay(s.id) }, onDelete = { onDelete(s.id) }) }
        }
    }
}

@Composable
private fun HistoryStats(sessions: List<SessionSummary>) {
    if (sessions.isEmpty()) return
    val avg = sessions.map { it.combinedTotal }.average().toInt()
    val best = sessions.maxOf { it.combinedTotal }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ValueChip(stringResource(R.string.history_count), "${sessions.size}")
        ValueChip(stringResource(R.string.history_avg), "$avg", tint = SteerinColors.Cyan)
        ValueChip(stringResource(R.string.history_best), "$best", tint = SteerinColors.Green)
    }
}

private val DATE_FORMAT = SimpleDateFormat("M/d HH:mm", Locale.KOREA)

@Composable
private fun HistoryRow(s: SessionSummary, onReplay: () -> Unit, onDelete: () -> Unit) {
    val tier = RewardTier.fromScore(s.combinedTotal)
    val color = tierColor(tier)
    Row(
        Modifier.fillMaxWidth().background(SteerinColors.Surface, RoundedCornerShape(14.dp)).clickable(onClick = onReplay).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${s.combinedTotal}", style = MaterialTheme.typography.headlineLarge, color = color, fontWeight = FontWeight.Bold, modifier = Modifier.width(84.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(tierName(tier)), style = MaterialTheme.typography.titleMedium, color = color)
                s.outcome?.let { o ->
                    val label = stringResource(R.string.outcome_split, s.score.total, o.total) +
                        if (o.observation.source == SlotSource.SIMULATED) " · " + stringResource(R.string.outcome_demo_badge) else ""
                    Text(label, style = MaterialTheme.typography.labelMedium, color = SteerinColors.Amber)
                }
                Text(DATE_FORMAT.format(Date(s.savedAtEpochMs)), style = MaterialTheme.typography.labelLarge, color = SteerinColors.Muted)
                Text(stringResource(if (s.side == ParkingSide.RIGHT) R.string.side_right else R.string.side_left), style = MaterialTheme.typography.labelMedium, color = SteerinColors.Muted)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                s.score.categories.forEach { c ->
                    val frac = c.points.toFloat() / c.category.maxPoints
                    val barColor = when { frac >= 0.85f -> SteerinColors.Green; frac >= 0.5f -> SteerinColors.Amber; else -> SteerinColors.Coral }
                    Box(Modifier.weight(c.category.maxPoints.toFloat()).height(8.dp).background(SteerinColors.SurfaceHigh, RoundedCornerShape(4.dp))) {
                        Box(Modifier.fillMaxWidth(frac.coerceIn(0.03f, 1f)).height(8.dp).background(barColor, RoundedCornerShape(4.dp)))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            val m = s.score.metrics
            Text(
                stringResource(R.string.history_metrics, s.durationSec.toInt(), m.steeringReversals, m.directionChanges, m.maxReverseSpeedKmh),
                style = MaterialTheme.typography.labelMedium, color = SteerinColors.Muted,
            )
        }
        TextButton(onClick = onReplay) { Text(stringResource(R.string.history_replay), color = SteerinColors.Cyan) }
        TextButton(onClick = onDelete) { Text(stringResource(R.string.history_delete), color = SteerinColors.Muted) }
    }
}
