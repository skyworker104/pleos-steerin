package com.steerin.parkingmaster.data.vehicle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Deterministic signal source for demos, tests and any device without a Vehicle Service.
 *
 * Two ways to drive it:
 *  - [play] a scripted [Scenario] (what the PT demo uses, so the demo never depends on live slider skill).
 *  - manual setters ([setSteering], [setGear], ...) bound to the on-screen demo panel.
 *
 * Timestamps come from a virtual clock that advances with wall time while running.
 */
class SimulatedVehicleSignalSource(
    private val tickMs: Long = 50,
) : VehicleSignalSource {

    data class Keyframe(
        val atSec: Float,
        val steeringDeg: Float,
        val speedKmh: Float,
        val gear: GearPosition,
        val brake: Boolean = false,
        val trunkOpen: Boolean = false,
    )

    data class Scenario(val id: String, val keyframes: List<Keyframe>) {
        val durationSec: Float get() = keyframes.last().atSec
    }

    private val _status = MutableStateFlow(
        SourceStatus(
            SourceKind.SIMULATION, connected = true, steeringAvailable = true, gearAvailable = true,
            speedAvailable = true, brakeAvailable = true, doorAvailable = true, detail = "built-in simulator",
        ),
    )
    override val status: StateFlow<SourceStatus> = _status.asStateFlow()

    private val _signals = MutableSharedFlow<VehicleSignal>(extraBufferCapacity = 512, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val signals: Flow<VehicleSignal> = _signals.asSharedFlow()

    private val _playing = MutableStateFlow<String?>(null)
    val playingScenarioId: StateFlow<String?> = _playing.asStateFlow()

    private var scope: CoroutineScope? = null
    private var playJob: Job? = null
    private var startNanos = System.nanoTime()

    /** Wall-clock based virtual time so steering-rate metrics match a real vehicle's timestamps. */
    private val clockMs: Long get() = (System.nanoTime() - startNanos) / 1_000_000L

    // Current manual state
    private var steering = 0f
    private var speed = 0f
    private var gear = GearPosition.PARK
    private var brake = false
    private var trunk = false

    override fun start() {
        if (scope != null) return
        startNanos = System.nanoTime()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { s ->
            s.launch {
                emitAll() // complete initial snapshot for the tracker
                while (isActive) {
                    delay(tickMs)
                    // Speed heartbeat keeps dead-reckoning integrating even when nothing changes,
                    // mirroring PERF_VEHICLE_SPEED at SENSOR_RATE_FAST on a real vehicle.
                    _signals.tryEmit(VehicleSignal.Speed(speed, clockMs))
                }
            }
        }
    }

    override fun stop() {
        playJob?.cancel()
        scope?.cancel()
        scope = null
        _playing.value = null
    }

    fun setSteering(deg: Float) { steering = deg.coerceIn(-540f, 540f); _signals.tryEmit(VehicleSignal.SteeringAngle(steering, clockMs)) }
    fun setSpeed(kmh: Float) { speed = kmh.coerceAtLeast(0f); _signals.tryEmit(VehicleSignal.Speed(speed, clockMs)) }
    fun setGear(g: GearPosition) { gear = g; _signals.tryEmit(VehicleSignal.GearChanged(gear, clockMs)) }
    fun setBrake(pressed: Boolean) { brake = pressed; _signals.tryEmit(VehicleSignal.BrakePedal(brake, clockMs)) }
    fun setTrunkOpen(open: Boolean) { trunk = open; _signals.tryEmit(VehicleSignal.DoorState(DoorSlot.TRUNK, trunk, clockMs)) }

    private fun emitAll() {
        _signals.tryEmit(VehicleSignal.GearChanged(gear, clockMs))
        _signals.tryEmit(VehicleSignal.SteeringAngle(steering, clockMs))
        _signals.tryEmit(VehicleSignal.Speed(speed, clockMs))
        _signals.tryEmit(VehicleSignal.BrakePedal(brake, clockMs))
        _signals.tryEmit(VehicleSignal.DoorState(DoorSlot.TRUNK, trunk, clockMs))
    }

    /** Plays a scenario in (virtual) real time. */
    fun play(scenario: Scenario) {
        val s = scope ?: return
        playJob?.cancel()
        _playing.value = scenario.id
        playJob = s.launch {
            val frames = frames(scenario, tickMs)
            for (f in frames) {
                if (!isActive) break
                if (f.gear != gear) setGear(f.gear)
                if (f.brake != brake) setBrake(f.brake)
                if (f.trunkOpen != trunk) setTrunkOpen(f.trunkOpen)
                setSteering(f.steeringDeg)
                setSpeed(f.speedKmh)
                delay(tickMs)
            }
            _playing.value = null
        }
    }

    companion object {
        /** Linear interpolation of a scenario into per-tick frames. Shared by the player and unit tests. */
        fun frames(scenario: Scenario, tickMs: Long): List<Keyframe> {
            val out = ArrayList<Keyframe>()
            val kf = scenario.keyframes
            // Index-based time to avoid float drift dropping the final keyframe.
            val frameCount = Math.round(scenario.durationSec * 1000f / tickMs) + 1
            for (i in 0 until frameCount) {
                val t = i * tickMs / 1000f
                var a = kf.first()
                var b = kf.first()
                for (k in kf) {
                    if (k.atSec <= t) a = k else { b = k; break }
                    b = k
                }
                val f = if (b.atSec > a.atSec) ((t - a.atSec) / (b.atSec - a.atSec)).coerceIn(0f, 1f) else 1f
                out += Keyframe(
                    atSec = t,
                    steeringDeg = a.steeringDeg + (b.steeringDeg - a.steeringDeg) * f,
                    speedKmh = a.speedKmh + (b.speedKmh - a.speedKmh) * f,
                    gear = a.gear,
                    brake = a.brake,
                    trunkOpen = a.trunkOpen,
                )
            }
            return out
        }

        /** Expands frames into the signal stream the tracker would see (for tests). */
        fun signals(scenario: Scenario, tickMs: Long, startMs: Long = 0): List<VehicleSignal> {
            val out = ArrayList<VehicleSignal>()
            var lastGear: GearPosition? = null
            var lastBrake: Boolean? = null
            var lastTrunk: Boolean? = null
            frames(scenario, tickMs).forEachIndexed { i, f ->
                val ts = startMs + i * tickMs
                if (f.gear != lastGear) { out += VehicleSignal.GearChanged(f.gear, ts); lastGear = f.gear }
                if (f.brake != lastBrake) { out += VehicleSignal.BrakePedal(f.brake, ts); lastBrake = f.brake }
                if (f.trunkOpen != lastTrunk) { out += VehicleSignal.DoorState(DoorSlot.TRUNK, f.trunkOpen, ts); lastTrunk = f.trunkOpen }
                out += VehicleSignal.SteeringAngle(f.steeringDeg, ts)
                out += VehicleSignal.Speed(f.speedKmh, ts)
            }
            return out
        }

        /**
         * A textbook perpendicular reverse park into a slot on the RIGHT:
         * stop, lock right, creep back while rotating ~90°, unwind, straighten, stop, P.
         * Tuned so the bicycle model (wheelbase 2.75 m, ratio 14.5) rotates ~90° overall.
         */
        val MODEL_PARKING = Scenario(
            "model",
            listOf(
                Keyframe(0f, 0f, 0f, GearPosition.DRIVE, brake = true),
                Keyframe(1f, 0f, 0f, GearPosition.REVERSE, brake = true),
                Keyframe(3f, 460f, 0f, GearPosition.REVERSE, brake = true),   // pre-turn to lock while stopped
                Keyframe(5f, 460f, 3f, GearPosition.REVERSE),                 // creep back at 3 km/h
                Keyframe(10.1f, 460f, 3f, GearPosition.REVERSE),              // hold lock (~65° rotated)
                Keyframe(13.6f, 0f, 2.5f, GearPosition.REVERSE),              // unwind hand-over-hand (~130°/s), ~80° total
                Keyframe(21f, 0f, 2f, GearPosition.REVERSE),                  // creep straight back
                Keyframe(23f, 0f, 0f, GearPosition.REVERSE, brake = true),    // gentle stop
                Keyframe(24f, 0f, 0f, GearPosition.PARK, brake = true),
            ),
        )

        /** A hesitant first-timer: too fast, several corrections, one pull-forward retry, harsh stop. */
        val NOVICE_PARKING = Scenario(
            "novice",
            listOf(
                Keyframe(0f, 0f, 0f, GearPosition.DRIVE, brake = true),
                Keyframe(1f, 0f, 0f, GearPosition.REVERSE, brake = true),
                Keyframe(2f, 200f, 6f, GearPosition.REVERSE),                 // moves before turning
                Keyframe(4f, 450f, 8f, GearPosition.REVERSE),                 // too fast
                Keyframe(6f, 150f, 7f, GearPosition.REVERSE),                 // reversal 1
                Keyframe(8f, 420f, 6f, GearPosition.REVERSE),                 // reversal 2
                Keyframe(8.5f, 420f, 0f, GearPosition.REVERSE, brake = true), // harsh stop
                Keyframe(10f, -300f, 0f, GearPosition.DRIVE, brake = true),   // pull forward to retry
                Keyframe(13f, -300f, 4f, GearPosition.DRIVE),
                Keyframe(14f, -300f, 0f, GearPosition.DRIVE, brake = true),
                Keyframe(15f, 380f, 0f, GearPosition.REVERSE, brake = true),
                Keyframe(22f, 380f, 3f, GearPosition.REVERSE),
                Keyframe(26f, -80f, 3f, GearPosition.REVERSE),                // over-correct
                Keyframe(30f, 40f, 2f, GearPosition.REVERSE),
                Keyframe(40f, 0f, 2f, GearPosition.REVERSE),
                Keyframe(41f, 0f, 0f, GearPosition.REVERSE, brake = true),
                Keyframe(42f, 0f, 0f, GearPosition.PARK, brake = true),
            ),
        )

        val ALL = listOf(MODEL_PARKING, NOVICE_PARKING)
    }
}
