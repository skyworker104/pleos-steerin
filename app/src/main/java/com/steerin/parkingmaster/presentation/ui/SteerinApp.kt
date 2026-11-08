package com.steerin.parkingmaster.presentation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.steerin.parkingmaster.R
import com.steerin.parkingmaster.data.vehicle.SourceKind
import com.steerin.parkingmaster.domain.session.DrivingState
import com.steerin.parkingmaster.presentation.AppScreen
import com.steerin.parkingmaster.presentation.ParkingViewModel
import com.steerin.parkingmaster.presentation.UiState
import com.steerin.parkingmaster.presentation.ui.theme.SteerinColors

/**
 * Pleos Connect renders apps on a 2560x1440 @160dpi canvas (≈1860x660 dp for the app pane), where
 * phone-sized dp values look tiny from the driver's seat. We scale the whole composition so that a
 * 420 dp-tall layout fills whatever height the window actually has (clamped to 1x..2.2x).
 */
@Composable
fun SteerinApp(viewModel: ParkingViewModel) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val base = LocalDensity.current
    val scale = (configuration.screenHeightDp / 420f).coerceIn(1f, 2.2f)
    val scaled = Density(base.density * scale, base.fontScale)

    CompositionLocalProvider(LocalDensity provides scaled) {
        SteerinContent(ui, viewModel)
    }
}

@Composable
private fun SteerinContent(ui: UiState, viewModel: ParkingViewModel) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Row(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                TopStatusBar(ui, onToggleVoice = viewModel::toggleVoice, onToggleDemo = viewModel::toggleDemoPanel)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (ui.screen) {
                        AppScreen.HISTORY -> HistoryScreen(
                            sessions = ui.history, onBack = viewModel::showMain,
                            onReplay = viewModel::openReplay, onDelete = viewModel::deleteSession,
                        )
                        AppScreen.REPLAY -> ReplayScreen(viewModel.replay, onClose = viewModel::showHistory)
                        AppScreen.MAIN -> Crossfade(targetState = ui.tracker.driving::class, label = "screen") { kind ->
                            when (kind) {
                                DrivingState.Coaching::class -> {
                                    val coaching = ui.tracker.driving as? DrivingState.Coaching
                                    if (coaching != null) CoachingScreen(coaching, ui.tracker.snapshot, ui.tracker.side, ui.uxRestricted)
                                }
                                DrivingState.Result::class -> {
                                    val result = ui.tracker.driving as? DrivingState.Result
                                    if (result != null) ResultScreen(
                                        result.score,
                                        outcome = ui.outcome?.takeIf { it.log === result.log }?.score,
                                        onAcknowledge = viewModel::acknowledgeResult,
                                    )
                                }
                                else -> StandbyScreen(ui, onSideChange = viewModel::setSide, onOpenHistory = viewModel::showHistory, onToggleSvmDemo = viewModel::toggleSvmDemo)
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(visible = ui.simulationAvailable && ui.showDemoPanel && !ui.uxRestricted) {
                DemoPanel(ui, viewModel, Modifier.width(300.dp).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun TopStatusBar(ui: UiState, onToggleVoice: () -> Unit, onToggleDemo: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
        SourceChip(ui)
        Spacer(Modifier.weight(1f))
        if (!ui.uxRestricted) {
            TextButton(onClick = onToggleVoice) {
                Text(stringResource(if (ui.voiceEnabled) R.string.voice_on else R.string.voice_off))
            }
            if (ui.simulationAvailable) {
                TextButton(onClick = onToggleDemo) {
                    Text(stringResource(if (ui.showDemoPanel) R.string.demo_hide else R.string.demo_show))
                }
            }
        }
    }
}

@Composable
private fun SourceChip(ui: UiState) {
    val s = ui.source
    val (label, color) = when {
        s == null -> stringResource(R.string.source_connecting) to SteerinColors.Muted
        s.kind == SourceKind.PLEOS_VEHICLE_SDK && s.connected -> stringResource(R.string.source_pleos) to SteerinColors.Green
        s.kind == SourceKind.PLEOS_VEHICLE_SDK -> stringResource(R.string.source_pleos_disconnected) to SteerinColors.Coral
        else -> stringResource(R.string.source_simulation) to SteerinColors.Amber
    }
    Box(
        Modifier.background(color.copy(alpha = 0.15f), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Text(label, color = color, style = MaterialTheme.typography.labelMedium)
    }
}

/** Small rounded chip used for telemetry values. */
@Composable
fun ValueChip(label: String, value: String, tint: Color = SteerinColors.Muted, modifier: Modifier = Modifier) {
    Column(
        modifier
            .widthIn(min = 92.dp)
            .background(SteerinColors.Surface, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = SteerinColors.Muted, maxLines = 1, softWrap = false)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = tint, maxLines = 1, softWrap = false)
    }
}
