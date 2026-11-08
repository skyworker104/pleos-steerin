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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.steerin.parkingmaster.R
import com.steerin.parkingmaster.data.vehicle.GearPosition
import com.steerin.parkingmaster.domain.model.ManeuverPhase
import com.steerin.parkingmaster.domain.model.ParkingSide
import com.steerin.parkingmaster.domain.session.DrivingState
import com.steerin.parkingmaster.domain.session.VehicleSnapshot
import com.steerin.parkingmaster.presentation.ParkingViewModel
import com.steerin.parkingmaster.presentation.ui.theme.SteerinColors
import kotlin.math.roundToInt

/**
 * Glanceable coaching HUD. One instruction line, one ring, four telemetry chips.
 * No touch targets: everything here is read-only while the car is in R.
 */
@Composable
fun CoachingScreen(state: DrivingState.Coaching, snapshot: VehicleSnapshot, side: ParkingSide, uxRestricted: Boolean) {
    val target = state.target
    val instruction = if (state.snapped && target.phase != ManeuverPhase.READY) {
        stringResource(R.string.coach_snapped)
    } else {
        stringResource(ParkingViewModel.phasePrompt(target.phase, side))
    }

    Row(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(0.42f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            GhostRing(
                liveDeg = snapshot.steeringDeg,
                targetDeg = target.targetDeg,
                snapped = state.snapped,
                toleranceDeg = target.toleranceDeg,
                modifier = Modifier.fillMaxHeight(0.92f),
            )
        }
        Column(Modifier.weight(0.58f).fillMaxHeight().padding(start = 24.dp), verticalArrangement = Arrangement.Center) {
            PhaseStepper(target.phase)
            Spacer(Modifier.height(20.dp))
            Text(
                instruction,
                style = MaterialTheme.typography.headlineMedium,
                color = if (state.snapped) SteerinColors.Green else SteerinColors.Text,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ValueChip(stringResource(R.string.chip_steering), "${snapshot.steeringDeg.roundToInt()}°")
                ValueChip(
                    stringResource(R.string.chip_speed), "%.1f km/h".format(snapshot.speedKmh),
                    tint = if (snapshot.speedKmh > 5f) SteerinColors.Amber else SteerinColors.Muted,
                )
                ValueChip(stringResource(R.string.chip_gear), gearLabel(snapshot.gear))
                ValueChip(stringResource(R.string.chip_rotation), "${target.headingChangeDeg.roundToInt()}°")
            }
            Spacer(Modifier.height(16.dp))
            Warnings(snapshot)
            if (!uxRestricted) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.coach_elapsed, state.elapsedSec.roundToInt()),
                    style = MaterialTheme.typography.labelMedium, color = SteerinColors.Muted,
                )
            }
        }
    }
}

@Composable
private fun PhaseStepper(current: ManeuverPhase) {
    val steps = listOf(
        ManeuverPhase.READY to R.string.phase_ready,
        ManeuverPhase.TURN_IN to R.string.phase_turn_in,
        ManeuverPhase.STRAIGHTEN to R.string.phase_straighten,
        ManeuverPhase.HOLD_STRAIGHT to R.string.phase_hold,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEachIndexed { i, (phase, label) ->
            val done = phase.ordinal < current.ordinal
            val active = phase == current
            val bg = when {
                active -> SteerinColors.Cyan.copy(alpha = 0.22f)
                done -> SteerinColors.Green.copy(alpha = 0.14f)
                else -> SteerinColors.Surface
            }
            val fg = when {
                active -> SteerinColors.Cyan
                done -> SteerinColors.Green
                else -> SteerinColors.Muted
            }
            Box(Modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 6.dp)) {
                Text("${i + 1} ${stringResource(label)}", color = fg, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun Warnings(snapshot: VehicleSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (snapshot.anyDoorOpen) WarningLine(stringResource(R.string.warn_door_open), SteerinColors.Coral)
        if (snapshot.speedKmh > 5f) WarningLine(stringResource(R.string.warn_speed), SteerinColors.Amber)
    }
}

@Composable
private fun WarningLine(text: String, color: Color) {
    Box(Modifier.fillMaxWidth().background(color.copy(alpha = 0.14f), RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(text, color = color, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun gearLabel(gear: GearPosition): String = when (gear) {
    GearPosition.PARK -> "P"
    GearPosition.REVERSE -> "R"
    GearPosition.NEUTRAL -> "N"
    GearPosition.DRIVE -> "D"
    GearPosition.UNKNOWN -> "–"
}
