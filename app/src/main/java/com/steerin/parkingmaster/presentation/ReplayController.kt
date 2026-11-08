package com.steerin.parkingmaster.presentation

import com.steerin.parkingmaster.domain.model.SessionRecord
import com.steerin.parkingmaster.domain.session.ParkingSessionTracker
import com.steerin.parkingmaster.domain.session.SessionReplayer
import com.steerin.parkingmaster.domain.session.TrackerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ReplayUiState(
    val record: SessionRecord? = null,
    val tracker: TrackerState = TrackerState(),
    /** 0..1 position within the recording. */
    val progress: Float = 0f,
    val elapsedSec: Float = 0f,
    val playing: Boolean = false,
    val speed: Float = 1f,
    val finished: Boolean = false,
)

/**
 * Plays a saved session through a private [ParkingSessionTracker] in (scaled) real time.
 * The replay tracker is separate from the live one, so a replay never touches the current drive.
 */
class ReplayController(private val scope: CoroutineScope) {

    private val _state = MutableStateFlow(ReplayUiState())
    val state: StateFlow<ReplayUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun open(record: SessionRecord) {
        stop()
        _state.value = ReplayUiState(record = record)
        play()
    }

    fun play() {
        val record = _state.value.record ?: return
        if (_state.value.playing) return
        if (_state.value.finished) _state.update { it.copy(finished = false, progress = 0f, elapsedSec = 0f) }
        _state.update { it.copy(playing = true) }
        job = scope.launch {
            val tracker = ParkingSessionTracker().also { it.setSide(record.side) }
            val signals = SessionReplayer.toSignals(record.log)
            val t0 = record.log.startedAtMs
            val span = (record.log.endedAtMs - t0).coerceAtLeast(1)
            var lastTs = signals.firstOrNull()?.timestampMs ?: t0
            for (sig in signals) {
                if (!isActive) return@launch
                val gap = sig.timestampMs - lastTs
                if (gap > 0) delay((gap / _state.value.speed).toLong().coerceAtMost(2000))
                lastTs = sig.timestampMs
                val ts = tracker.onSignal(sig)
                _state.update {
                    it.copy(
                        tracker = ts,
                        progress = ((sig.timestampMs - t0).toFloat() / span).coerceIn(0f, 1f),
                        elapsedSec = (sig.timestampMs - t0) / 1000f,
                    )
                }
            }
            _state.update { it.copy(playing = false, finished = true, progress = 1f) }
        }
    }

    fun pause() {
        job?.cancel(); job = null
        _state.update { it.copy(playing = false) }
    }

    fun setSpeed(speed: Float) = _state.update { it.copy(speed = speed) }

    fun restart() {
        val record = _state.value.record ?: return
        open(record)
    }

    fun stop() {
        job?.cancel(); job = null
        _state.value = ReplayUiState()
    }
}
