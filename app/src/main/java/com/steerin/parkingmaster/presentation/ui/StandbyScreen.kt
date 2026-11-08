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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.steerin.parkingmaster.R
import com.steerin.parkingmaster.data.vehicle.GearPosition
import com.steerin.parkingmaster.data.vehicle.SimulatedVehicleSignalSource
import com.steerin.parkingmaster.domain.model.ParkingSide
import com.steerin.parkingmaster.presentation.ParkingViewModel
import com.steerin.parkingmaster.presentation.UiState
import com.steerin.parkingmaster.presentation.ui.theme.SteerinColors
import kotlin.math.roundToInt

/** DRIVING state: minimal, informational. The only control is the parking-side preference. */
@Composable
fun StandbyScreen(ui: UiState, onSideChange: (ParkingSide) -> Unit, onOpenHistory: () -> Unit = {}) {
    val snapshot = ui.tracker.snapshot
    Row(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(0.42f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            GhostRing(
                liveDeg = snapshot.steeringDeg, targetDeg = snapshot.steeringDeg, snapped = false,
                toleranceDeg = 5f, showGhost = false, modifier = Modifier.fillMaxHeight(0.8f),
            )
        }
        Column(Modifier.weight(0.58f).padding(start = 24.dp), verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.standby_title), style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.standby_hint), style = MaterialTheme.typography.bodyLarge, color = SteerinColors.Muted)
            Spacer(Modifier.height(20.dp))
            if (!ui.uxRestricted) {
                Text(stringResource(R.string.setting_side), style = MaterialTheme.typography.labelLarge, color = SteerinColors.Muted)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    SideChip(stringResource(R.string.side_left), ui.tracker.side == ParkingSide.LEFT) { onSideChange(ParkingSide.LEFT) }
                    SideChip(stringResource(R.string.side_right), ui.tracker.side == ParkingSide.RIGHT) { onSideChange(ParkingSide.RIGHT) }
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(onClick = onOpenHistory, colors = ButtonDefaults.outlinedButtonColors(contentColor = SteerinColors.Cyan)) {
                        Text(stringResource(R.string.standby_history) + if (ui.history.isNotEmpty()) " (${ui.history.size})" else "", style = MaterialTheme.typography.titleMedium)
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ValueChip(stringResource(R.string.chip_steering), "${snapshot.steeringDeg.roundToInt()}°")
                ValueChip(stringResource(R.string.chip_speed), "%.1f km/h".format(snapshot.speedKmh))
                ValueChip(stringResource(R.string.chip_gear), gearLabel(snapshot.gear))
            }
            Spacer(Modifier.height(16.dp))
            SourceDetail(ui)
        }
    }
}

@Composable
private fun SideChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(label, style = MaterialTheme.typography.titleMedium) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = SteerinColors.Cyan.copy(alpha = 0.22f), selectedLabelColor = SteerinColors.Cyan,
            containerColor = SteerinColors.Surface, labelColor = SteerinColors.Muted,
        ),
    )
}

@Composable
private fun SourceDetail(ui: UiState) {
    val s = ui.source ?: return
    val items = listOf(
        R.string.sig_steering to s.steeringAvailable,
        R.string.sig_gear to s.gearAvailable,
        R.string.sig_speed to s.speedAvailable,
        R.string.sig_brake to s.brakeAvailable,
        R.string.sig_door to s.doorAvailable,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (res, ok) ->
            val color = if (ok) SteerinColors.Green else SteerinColors.Muted
            Box(Modifier.background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(stringResource(res), color = color, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    if (s.detail.isNotBlank()) {
        Spacer(Modifier.height(4.dp))
        Text(s.detail, style = MaterialTheme.typography.labelMedium, color = SteerinColors.Muted)
    }
}

/**
 * Demo controls for the built-in simulator. Mirrors what the Pleos Virtual Console offers
 * (gear, brake, steering angle, trunk) so the same demo script works on both.
 */
@Composable
fun DemoPanel(ui: UiState, vm: ParkingViewModel, modifier: Modifier = Modifier) {
    var steering by remember { mutableFloatStateOf(0f) }
    var speed by remember { mutableFloatStateOf(0f) }
    var brake by remember { mutableStateOf(false) }
    var trunk by remember { mutableStateOf(false) }
    val snapshotGear = ui.tracker.snapshot.gear

    Column(
        modifier.background(SteerinColors.Surface).padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.demo_title), style = MaterialTheme.typography.titleMedium, color = SteerinColors.Cyan)
        Text(stringResource(R.string.demo_scenarios), style = MaterialTheme.typography.labelMedium, color = SteerinColors.Muted)
        val playing = ui.playingScenario != null
        Button(
            onClick = { vm.playScenario(SimulatedVehicleSignalSource.MODEL_PARKING) }, enabled = !playing,
            colors = ButtonDefaults.buttonColors(containerColor = SteerinColors.Green, contentColor = SteerinColors.Background),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.demo_model)) }
        Button(
            onClick = { vm.playScenario(SimulatedVehicleSignalSource.NOVICE_PARKING) }, enabled = !playing,
            colors = ButtonDefaults.buttonColors(containerColor = SteerinColors.Amber, contentColor = SteerinColors.Background),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.demo_novice)) }
        if (playing) Text(stringResource(R.string.demo_playing), style = MaterialTheme.typography.labelMedium, color = SteerinColors.Amber)

        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.demo_manual), style = MaterialTheme.typography.labelMedium, color = SteerinColors.Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(GearPosition.PARK, GearPosition.REVERSE, GearPosition.NEUTRAL, GearPosition.DRIVE).forEach { g ->
                val selected = snapshotGear == g
                OutlinedButton(
                    onClick = { vm.simGear(g) }, enabled = !playing,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (selected) SteerinColors.Cyan.copy(alpha = 0.22f) else SteerinColors.SurfaceHigh,
                        contentColor = if (selected) SteerinColors.Cyan else SteerinColors.Text,
                    ),
                    modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) { Text(gearLabel(g), style = MaterialTheme.typography.titleMedium) }
            }
        }
        Text("${stringResource(R.string.chip_steering)}  ${steering.roundToInt()}°", style = MaterialTheme.typography.labelLarge)
        Slider(value = steering, onValueChange = { steering = it; vm.simSteering(it) }, valueRange = -540f..540f, enabled = !playing)
        Text("${stringResource(R.string.chip_speed)}  %.1f km/h".format(speed), style = MaterialTheme.typography.labelLarge)
        Slider(value = speed, onValueChange = { speed = it; vm.simSpeed(it) }, valueRange = 0f..20f, enabled = !playing)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.demo_brake), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Switch(checked = brake, onCheckedChange = { brake = it; vm.simBrake(it) }, enabled = !playing)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.demo_trunk), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Switch(checked = trunk, onCheckedChange = { trunk = it; vm.simTrunk(it) }, enabled = !playing)
        }
        Spacer(Modifier.width(1.dp))
        Text(stringResource(R.string.demo_footnote), style = MaterialTheme.typography.labelMedium, color = SteerinColors.Muted)
    }
}
