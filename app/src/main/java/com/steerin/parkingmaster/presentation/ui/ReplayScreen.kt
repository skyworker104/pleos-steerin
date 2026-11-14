package com.steerin.parkingmaster.presentation.ui

import com.steerin.parkingmaster.domain.reward.WheelSkin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.steerin.parkingmaster.R
import com.steerin.parkingmaster.domain.outcome.SlotOutcomeEvaluator
import com.steerin.parkingmaster.domain.session.DrivingState
import com.steerin.parkingmaster.presentation.ReplayController
import com.steerin.parkingmaster.presentation.ui.theme.SteerinColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DATE_FORMAT = SimpleDateFormat("yyyy.M.d HH:mm", Locale.KOREA)

/**
 * Replays a saved session through the same coaching HUD and result card, with a transport bar.
 * Parked-only; touch is allowed. Voice is intentionally silent during replay.
 */
@Composable
fun ReplayScreen(controller: ReplayController, onClose: () -> Unit, skin: WheelSkin = WheelSkin.CLASSIC) {
    val st by controller.state.collectAsStateWithLifecycle()
    val record = st.record
    val replayOutcome = remember(record) { SlotOutcomeEvaluator().evaluate(record?.slot) }

    Column(Modifier.fillMaxSize()) {
        // Transport bar
        Row(
            Modifier.fillMaxWidth().background(SteerinColors.Surface).padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.replay_title), style = MaterialTheme.typography.titleMedium, color = SteerinColors.Cyan)
            if (record != null) Text(DATE_FORMAT.format(Date(record.savedAtEpochMs)), style = MaterialTheme.typography.labelLarge, color = SteerinColors.Muted)
            Spacer(Modifier.width(4.dp))
            Box(Modifier.weight(1f).height(8.dp).background(SteerinColors.SurfaceHigh, RoundedCornerShape(4.dp))) {
                Box(Modifier.fillMaxWidth(st.progress.coerceIn(0.01f, 1f)).height(8.dp).background(SteerinColors.Cyan, RoundedCornerShape(4.dp)))
            }
            Text(stringResource(R.string.replay_elapsed, st.elapsedSec.toInt(), (record?.log?.durationSec ?: 0f).toInt()), style = MaterialTheme.typography.labelLarge, color = SteerinColors.Muted)
            TextButton(onClick = { if (st.playing) controller.pause() else controller.play() }) {
                Text(stringResource(if (st.playing) R.string.replay_pause else if (st.finished) R.string.replay_restart else R.string.replay_play))
            }
            listOf(1f, 2f, 4f).forEach { sp ->
                TextButton(onClick = { controller.setSpeed(sp) }) {
                    Text("${sp.toInt()}x", color = if (st.speed == sp) SteerinColors.Green else SteerinColors.Muted)
                }
            }
            TextButton(onClick = onClose) { Text(stringResource(R.string.history_back)) }
        }
        // Body: same HUD as live coaching
        Box(Modifier.fillMaxSize()) {
            when (val d = st.tracker.driving) {
                is DrivingState.Coaching -> CoachingScreen(d, st.tracker.snapshot, st.tracker.side, uxRestricted = false, skin = skin)
                is DrivingState.Result -> ResultScreen(d.score, outcome = replayOutcome, onAcknowledge = onClose)
                DrivingState.Driving -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(if (st.finished) R.string.replay_done else R.string.replay_loading), color = SteerinColors.Muted, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
