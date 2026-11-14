package com.steerin.parkingmaster.presentation

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.steerin.parkingmaster.R
import com.steerin.parkingmaster.SteerinApplication
import com.steerin.parkingmaster.data.outcome.ParkEvent
import com.steerin.parkingmaster.data.outcome.ParkingOutcomeSource
import com.steerin.parkingmaster.data.outcome.SimulatedOutcomeSource
import com.steerin.parkingmaster.data.session.SessionRepository
import com.steerin.parkingmaster.data.vehicle.GearPosition
import com.steerin.parkingmaster.data.vehicle.SimulatedVehicleSignalSource
import com.steerin.parkingmaster.data.vehicle.SourceStatus
import com.steerin.parkingmaster.domain.model.ManeuverPhase
import com.steerin.parkingmaster.domain.model.ParkingSide
import com.steerin.parkingmaster.domain.model.SessionLog
import com.steerin.parkingmaster.domain.model.SessionSummary
import com.steerin.parkingmaster.domain.outcome.OutcomeScore
import com.steerin.parkingmaster.domain.outcome.SlotOutcomeEvaluator
import com.steerin.parkingmaster.domain.reward.RewardEngine
import com.steerin.parkingmaster.domain.reward.RewardProgress
import com.steerin.parkingmaster.domain.reward.SessionResult
import com.steerin.parkingmaster.domain.reward.WheelSkin
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
    /** [urgent] = safety warning: never cut off by a later prompt (see SpeechScheduler). */
    data class Say(val resId: Int, val args: List<Any> = emptyList(), val urgent: Boolean = false) : VoiceEvent
    data object SnapTone : VoiceEvent
}

/** Top-level navigation. Live coaching always wins: a session starting in R pulls the UI back to MAIN. */
enum class AppScreen { MAIN, HISTORY, REPLAY, SKINS }

data class UiState(
    val tracker: TrackerState = TrackerState(),
    val source: SourceStatus? = null,
    val simulationAvailable: Boolean = false,
    val playingScenario: String? = null,
    val voiceEnabled: Boolean = true,
    val uxRestricted: Boolean = false,
    val showDemoPanel: Boolean = false,
    val screen: AppScreen = AppScreen.MAIN,
    val history: List<SessionSummary> = emptyList(),
    val lastSavedSessionId: String? = null,
    /** Demo-only SVM result score (labelled "가상 데이터"). Off by default; never on in a real car by itself. */
    val svmDemoEnabled: Boolean = false,
    /** Result score for the result currently on screen, tagged with its log so a stale one is never shown. */
    val outcome: ResultOutcome? = null,
    /** Skins / streaks derived from the history plus persisted unlocks. */
    val reward: RewardProgress = RewardProgress(),
    val selectedSkin: WheelSkin = WheelSkin.CLASSIC,
    /** Skins unlocked by the result currently on screen (shown on the result card, cleared on acknowledge). */
    val newlyUnlocked: List<WheelSkin> = emptyList(),
    /** False until the session store has been read once; rewards and 휠리's lines wait for it. */
    val historyLoaded: Boolean = false,
)

data class ResultOutcome(val log: SessionLog, val score: OutcomeScore)

class ParkingViewModel(app: Application) : AndroidViewModel(app) {

    private val container = app as SteerinApplication
    private val source = container.signalSource
    private val simulator: SimulatedVehicleSignalSource? = container.simulator
    private val repository: SessionRepository = container.sessionRepository
    private val rewardStore = container.rewardStore
    private val rewardEngine = RewardEngine()
    /** Set just before saving a result, so only unlocks caused by that result are announced. */
    private var announceUnlocks = false
    private val tracker = ParkingSessionTracker()
    private val outcomeEvaluator = SlotOutcomeEvaluator()
    private val simulatedOutcome = SimulatedOutcomeSource()
    private val outcomeSource: ParkingOutcomeSource
        get() = if (_ui.value.svmDemoEnabled) simulatedOutcome else container.outcomeSource

    val replay = ReplayController(viewModelScope)

    private val _ui = MutableStateFlow(
        UiState(
            simulationAvailable = simulator != null, showDemoPanel = simulator != null,
            selectedSkin = rewardStore.selected(), reward = RewardProgress(unlocked = rewardStore.unlocked()),
        ),
    )
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _voice = MutableSharedFlow<VoiceEvent>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val voice: SharedFlow<VoiceEvent> = _voice.asSharedFlow()

    private var started = false
    private var lastPhase: ManeuverPhase? = null
    private var lastSnapped = false
    private var doorWarned = false
    private var speedWarned = false
    private var lastSavedLog: SessionLog? = null

    init {
        viewModelScope.launch { source.status.collect { s -> _ui.update { it.copy(source = s) } } }
        viewModelScope.launch {
            repository.summaries.collect { list ->
                _ui.update { it.copy(history = list) }
                if (_ui.value.historyLoaded) updateRewards(list)
            }
        }
        viewModelScope.launch {
            repository.refresh()
            _ui.update { it.copy(historyLoaded = true) }
            updateRewards(repository.summaries.value)
        }
        simulator?.let { sim -> viewModelScope.launch { sim.playingScenarioId.collect { id -> _ui.update { it.copy(playingScenario = id) } } } }
        viewModelScope.launch {
            source.signals.collect { signal ->
                val state = tracker.onSignal(signal)
                _ui.update { it.copy(tracker = state) }
                emitVoiceFor(state)
                persistIfNewResult(state)
                // A real manoeuvre starting while the driver browses history: bring coaching to front.
                if (state.driving is DrivingState.Coaching && _ui.value.screen != AppScreen.MAIN) showMain()
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
        _ui.update { it.copy(tracker = tracker.state, newlyUnlocked = emptyList()) }
    }

    // --- rewards (wheel skins, mascot) -------------------------------------------------------
    private fun updateRewards(history: List<com.steerin.parkingmaster.domain.model.SessionSummary>) {
        val before = rewardStore.unlocked()
        val progress = rewardEngine.progress(history.map { SessionResult(it.savedAtEpochMs, it.combinedTotal) }, System.currentTimeMillis(), before)
        val gained = (progress.unlocked - before).sortedBy { it.ordinal }
        if (gained.isNotEmpty()) {
            rewardStore.saveUnlocked(progress.unlocked)
            Log.i("ParkingCoach", "unlocked skins=$gained announce=$announceUnlocks")
        }
        val show = if (announceUnlocks && gained.isNotEmpty()) gained else null
        if (announceUnlocks && history.isNotEmpty()) announceUnlocks = false
        _ui.update { it.copy(reward = progress, newlyUnlocked = show ?: it.newlyUnlocked) }
    }

    fun nextGoal() = rewardEngine.nextGoal(_ui.value.reward)

    fun showSkins() {
        if (_ui.value.uxRestricted || _ui.value.tracker.driving is DrivingState.Coaching) return
        _ui.update { it.copy(screen = AppScreen.SKINS) }
    }

    fun selectSkin(skin: WheelSkin) {
        if (skin !in _ui.value.reward.unlocked) return
        rewardStore.saveSelected(skin)
        _ui.update { it.copy(selectedSkin = skin) }
    }

    fun toggleSvmDemo() = _ui.update { it.copy(svmDemoEnabled = !it.svmDemoEnabled) }
    fun toggleVoice() = _ui.update { it.copy(voiceEnabled = !it.voiceEnabled) }
    fun toggleDemoPanel() = _ui.update { it.copy(showDemoPanel = !it.showDemoPanel) }
    fun setUxRestricted(restricted: Boolean) {
        _ui.update { it.copy(uxRestricted = restricted) }
        if (restricted && _ui.value.screen != AppScreen.MAIN) showMain()
    }

    // --- navigation / history ------------------------------------------------------------
    fun showMain() {
        replay.stop()
        _ui.update { it.copy(screen = AppScreen.MAIN) }
    }

    fun showHistory() {
        replay.stop()
        viewModelScope.launch { repository.refresh() }
        _ui.update { it.copy(screen = AppScreen.HISTORY) }
    }

    fun openReplay(sessionId: String) {
        viewModelScope.launch {
            val record = repository.load(sessionId) ?: return@launch
            // Driving restriction or a live manoeuvre may have started while the file was loading.
            val ui = _ui.value
            if (ui.uxRestricted || ui.tracker.driving is DrivingState.Coaching || ui.screen != AppScreen.HISTORY) return@launch
            replay.open(record)
            _ui.update { it.copy(screen = AppScreen.REPLAY) }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch { repository.delete(sessionId) }
    }

    private fun persistIfNewResult(state: TrackerState) {
        val result = state.driving as? DrivingState.Result ?: return
        if (result.log === lastSavedLog) return
        lastSavedLog = result.log
        viewModelScope.launch {
            val slot = runCatching { outcomeSource.observeAtPark(ParkEvent(result.log, result.score)) }
                .onFailure { Log.w("ParkingCoach", "slot observation failed: ${it.message}") }.getOrNull()
            val outcome = outcomeEvaluator.evaluate(slot)
            val score = result.score
            Log.i("ParkingCoach", "result total=${score.total} tier=${score.tier} ${score.categories.joinToString { "${it.category}=${it.points}" }}")
            if (outcome != null) {
                val combined = outcomeEvaluator.combined(score.total, outcome)
                Log.i("ParkingCoach", "outcome total=${outcome.total} combined=$combined source=${slot?.source} ${outcome.categories.joinToString { "${it.category}=${it.points}" }}")
                _ui.update { it.copy(outcome = ResultOutcome(result.log, outcome)) }
                _voice.tryEmit(VoiceEvent.Say(R.string.voice_outcome, listOf(outcome.total, combined)))
            } else {
                _voice.tryEmit(VoiceEvent.Say(R.string.voice_result, listOf(score.total)))
            }
            announceUnlocks = true
            runCatching { repository.save(result.log, state.side, slot) }
                .onSuccess { rec -> _ui.update { it.copy(lastSavedSessionId = rec.id) } }
                .onFailure { announceUnlocks = false; Log.w("ParkingCoach", "session save failed: ${it.message}") }
        }
    }

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
                    Log.i("ParkingCoach", "phase=$phase target=${d.target.targetDeg.toInt()} rot=${d.target.headingChangeDeg.toInt()}")
                    _voice.tryEmit(VoiceEvent.Say(phasePrompt(phase, state.side)))
                }
                if (d.snapped && !lastSnapped) {
                    Log.i("ParkingCoach", "snapped phase=$phase steering=${state.snapshot.steeringDeg.toInt()}")
                    _voice.tryEmit(VoiceEvent.SnapTone)
                }
                lastSnapped = d.snapped
                emitWarningsFor(state)
            }
            is DrivingState.Result -> {
                // Announced once per result from persistIfNewResult, after the slot lookup,
                // so the process and result scores are spoken as one sentence.
                lastPhase = null
            }
            DrivingState.Driving -> lastPhase = null
        }
        if (state.driving !is DrivingState.Coaching) { doorWarned = false; speedWarned = false }
    }

    /**
     * Door / over-speed warnings are spoken, not only drawn: in R the system rear-view camera owns
     * the screen, so an on-screen warning never reaches the driver. Each warning is spoken once per
     * rising edge; the speed warning re-arms only after slowing clearly below the limit.
     */
    private fun emitWarningsFor(state: TrackerState) {
        val snap = state.snapshot
        if (snap.anyDoorOpen && !doorWarned) _voice.tryEmit(VoiceEvent.Say(R.string.warn_door_open, urgent = true))
        doorWarned = snap.anyDoorOpen
        when {
            isOverSpeed(snap.speedKmh) && !speedWarned -> { speedWarned = true; _voice.tryEmit(VoiceEvent.Say(R.string.warn_speed, urgent = true)) }
            snap.speedKmh < OVER_SPEED_KMH - OVER_SPEED_REARM_KMH -> speedWarned = false
        }
    }

    override fun onCleared() {
        replay.stop()
        super.onCleared()
    }

    companion object {
        /** Same limit as the scoring soft limit and the on-screen warning. */
        const val OVER_SPEED_KMH = 5f
        private const val OVER_SPEED_REARM_KMH = 0.5f

        fun isOverSpeed(kmh: Float) = kmh > OVER_SPEED_KMH

        /** Instruction for the heads-up banner: a safety warning beats the coaching prompt. */
        fun headsUpText(d: DrivingState.Coaching, state: TrackerState): Int = when {
            state.snapshot.anyDoorOpen -> R.string.warn_door_open
            isOverSpeed(state.snapshot.speedKmh) -> R.string.warn_speed
            d.snapped && d.target.phase != ManeuverPhase.READY -> R.string.coach_snapped
            else -> phasePrompt(d.target.phase, state.side)
        }

        fun phasePrompt(phase: ManeuverPhase, side: ParkingSide): Int = when (phase) {
            ManeuverPhase.READY -> if (side == ParkingSide.RIGHT) R.string.coach_ready_right else R.string.coach_ready_left
            ManeuverPhase.TURN_IN -> R.string.coach_turn_in
            ManeuverPhase.STRAIGHTEN -> R.string.coach_straighten
            ManeuverPhase.HOLD_STRAIGHT -> R.string.coach_hold_straight
            ManeuverPhase.COMPLETE -> R.string.coach_complete
        }
    }
}
