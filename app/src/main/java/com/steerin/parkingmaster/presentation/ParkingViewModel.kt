package com.steerin.parkingmaster.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.steerin.parkingmaster.R
import com.steerin.parkingmaster.SteerinApplication
import com.steerin.parkingmaster.data.vehicle.GearPosition
import com.steerin.parkingmaster.data.vehicle.SimulatedVehicleSignalSource
import com.steerin.parkingmaster.data.vehicle.SourceStatus
import com.steerin.parkingmaster.domain.model.ManeuverPhase
import com.steerin.parkingmaster.domain.model.ParkingSide
import com.steerin.parkingmaster.domain.session.DrivingState
import com.steerin.parkingmaster.domain.session.ParkingSessionTracker
import com.steerin.parkingmaster.domain.session.TrackerState
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A spoken prompt (string resource + args) or a short snap-in tone. */
sealed interface VoiceEvent {
    data class Say(val resId: Int, val args: List<Any> = emptyList()) : VoiceEvent
    data object SnapTone : VoiceEvent
}

data class UiState(
    val tracker: TrackerState = TrackerState(),
    val source: SourceStatus? = null,
    val simulationAvailable: Boolean = false,
    val playingScenario: String? = null,
    val voiceEnabled: Boolean = true,
    val uxRestricted: Boolean = false,
    val showDemoPanel: Boolean = false,
)

class ParkingViewModel(app: Application) : AndroidViewModel(app) {

    private val container = app as SteerinApplication
    private val source = container.signalSource
    private val simulator: SimulatedVehicleSignalSource? = container.simulator
    private val tracker = ParkingSessionTracker()

    private val _ui = MutableStateFlow(UiState(simulationAvailable = simulator != null, showDemoPanel = simulator != null))
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _voice = MutableSharedFlow<VoiceEvent>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val voice: SharedFlow<VoiceEvent> = _voice.asSharedFlow()

    private var started = false
    private var lastPhase: ManeuverPhase? = null
    private var lastSnapped = false
    private var lastResultAnnounced: Int? = null

    init {
        viewModelScope.launch { source.status.collect { s -> _ui.update { it.copy(source = s) } } }
        simulator?.let { sim -> viewModelScope.launch { sim.playingScenarioId.collect { id -> _ui.update { it.copy(playingScenario = id) } } } }
        viewModelScope.launch {
            source.signals.collect { signal ->
                val state = tracker.onSignal(signal)
                _ui.update { it.copy(tracker = state) }
                emitVoiceFor(state)
            }
        }
    }

    /** Idempotent; the Activity calls this once permissions are settled. */
    fun startSignals() {
        if (started) return
        started = true
        source.start()
    }

    fun setSide(side: ParkingSide) {
        tracker.setSide(side)
        _ui.update { it.copy(tracker = tracker.state) }
    }

    fun acknowledgeResult() {
        tracker.acknowledgeResult()
        _ui.update { it.copy(tracker = tracker.state) }
    }

    fun toggleVoice() = _ui.update { it.copy(voiceEnabled = !it.voiceEnabled) }
    fun toggleDemoPanel() = _ui.update { it.copy(showDemoPanel = !it.showDemoPanel) }
    fun setUxRestricted(restricted: Boolean) = _ui.update { it.copy(uxRestricted = restricted) }

    // --- simulator bindings -------------------------------------------------------------
    fun playScenario(scenario: SimulatedVehicleSignalSource.Scenario) {
        acknowledgeResult()
        simulator?.play(scenario)
    }
    fun simSteering(deg: Float) = simulator?.setSteering(deg)
    fun simSpeed(kmh: Float) = simulator?.setSpeed(kmh)
    fun simGear(g: GearPosition) = simulator?.setGear(g)
    fun simBrake(pressed: Boolean) = simulator?.setBrake(pressed)
    fun simTrunk(open: Boolean) = simulator?.setTrunkOpen(open)

    // --- voice -------------------------------------------------------------------------
    private fun emitVoiceFor(state: TrackerState) {
        when (val d = state.driving) {
            is DrivingState.Coaching -> {
                val phase = d.target.phase
                if (phase != lastPhase) {
                    lastPhase = phase
                    lastSnapped = false
                    android.util.Log.i("ParkingCoach", "phase=$phase target=${d.target.targetDeg.toInt()} rot=${d.target.headingChangeDeg.toInt()}")
                    _voice.tryEmit(VoiceEvent.Say(phasePrompt(phase, state.side)))
                }
                if (d.snapped && !lastSnapped) {
                    android.util.Log.i("ParkingCoach", "snapped phase=$phase steering=${state.snapshot.steeringDeg.toInt()}")
                    _voice.tryEmit(VoiceEvent.SnapTone)
                }
                lastSnapped = d.snapped
                lastResultAnnounced = null
            }
            is DrivingState.Result -> {
                lastPhase = null
                if (lastResultAnnounced != d.score.total) {
                    lastResultAnnounced = d.score.total
                    android.util.Log.i("ParkingCoach", "result total=${d.score.total} tier=${d.score.tier} ${d.score.categories.joinToString { "${it.category}=${it.points}" }}")
                    _voice.tryEmit(VoiceEvent.Say(R.string.voice_result, listOf(d.score.total)))
                }
            }
            DrivingState.Driving -> lastPhase = null
        }
    }

    companion object {
        fun phasePrompt(phase: ManeuverPhase, side: ParkingSide): Int = when (phase) {
            ManeuverPhase.READY -> if (side == ParkingSide.RIGHT) R.string.coach_ready_right else R.string.coach_ready_left
            ManeuverPhase.TURN_IN -> R.string.coach_turn_in
            ManeuverPhase.STRAIGHTEN -> R.string.coach_straighten
            ManeuverPhase.HOLD_STRAIGHT -> R.string.coach_hold_straight
            ManeuverPhase.COMPLETE -> R.string.coach_complete
        }
    }
}
