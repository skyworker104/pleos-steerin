package com.steerin.parkingmaster.domain.session

import com.steerin.parkingmaster.data.vehicle.DoorSlot
import com.steerin.parkingmaster.data.vehicle.GearPosition
import com.steerin.parkingmaster.data.vehicle.VehicleSignal
import com.steerin.parkingmaster.domain.coaching.GhostTargetPlanner
import com.steerin.parkingmaster.domain.kinematics.BicycleModel
import com.steerin.parkingmaster.domain.kinematics.VehicleGeometry
import com.steerin.parkingmaster.domain.model.GhostTarget
import com.steerin.parkingmaster.domain.model.ManeuverPhase
import com.steerin.parkingmaster.domain.model.ParkingScore
import com.steerin.parkingmaster.domain.model.ParkingSide
import com.steerin.parkingmaster.domain.model.Sample
import com.steerin.parkingmaster.domain.model.SessionLog
import com.steerin.parkingmaster.domain.scoring.ParkingScoreEvaluator
import java.util.EnumSet

/** Live vehicle snapshot shown in the coaching HUD. */
data class VehicleSnapshot(
    val steeringDeg: Float = 0f,
    val speedKmh: Float = 0f,
    val gear: GearPosition = GearPosition.UNKNOWN,
    val brakePressed: Boolean = false,
    val openDoors: Set<DoorSlot> = emptySet(),
) {
    val anyDoorOpen: Boolean get() = openDoors.isNotEmpty()
}

/**
 * Driving-state machine aligned with Google's Design for Driving guidance:
 *  - DRIVING: minimal UI, no touch targets.
 *  - COACHING: glanceable ghost ring + one-line instruction while the manoeuvre is in progress.
 *  - RESULT: score card, only shown once the car is in PARK.
 */
sealed interface DrivingState {
    data object Driving : DrivingState
    data class Coaching(val target: GhostTarget, val snapped: Boolean, val elapsedSec: Float) : DrivingState
    data class Result(val score: ParkingScore, val log: SessionLog) : DrivingState
}

data class TrackerState(
    val driving: DrivingState = DrivingState.Driving,
    val snapshot: VehicleSnapshot = VehicleSnapshot(),
    val side: ParkingSide = ParkingSide.RIGHT,
)

/**
 * Pure-Kotlin core: consumes [VehicleSignal]s, records a [SessionLog] between the first REVERSE
 * and the final PARK, drives the ghost planner through the bicycle model, and scores the session.
 * No Android dependencies so it is fully unit-testable.
 */
class ParkingSessionTracker(
    geometry: VehicleGeometry = VehicleGeometry(),
    private val evaluator: ParkingScoreEvaluator = ParkingScoreEvaluator(),
    private val planner: GhostTargetPlanner = GhostTargetPlanner(geometry),
    /** If the car drives forward faster than this for longer than [abandonAfterMs], the session is dropped. */
    private val abandonSpeedKmh: Float = 15f,
    private val abandonAfterMs: Long = 4000,
) {
    private val model = BicycleModel(geometry)

    var state: TrackerState = TrackerState()
        private set

    private var snapshot = VehicleSnapshot()
    private val openDoors = EnumSet.noneOf(DoorSlot::class.java)
    private var sessionStartMs: Long? = null
    private var lastSampleMs: Long? = null
    private val samples = ArrayList<Sample>()
    private var fastForwardSinceMs: Long? = null
    private var lastSteeringTs: Long? = null

    val isSessionActive: Boolean get() = sessionStartMs != null

    fun setSide(side: ParkingSide) {
        state = state.copy(side = side)
    }

    /** Dismiss the result card and go back to DRIVING. */
    fun acknowledgeResult() {
        pendingResult = null
        if (state.driving is DrivingState.Result) state = state.copy(driving = DrivingState.Driving)
    }

    fun onSignal(signal: VehicleSignal): TrackerState {
        val ts = signal.timestampMs
        integrateMotion(ts)
        applyToSnapshot(signal)

        when (signal) {
            is VehicleSignal.GearChanged -> onGear(signal.gear, ts)
            else -> Unit
        }

        if (sessionStartMs != null) {
            recordSample(ts)
            checkAbandon(ts)
        }

        state = state.copy(snapshot = snapshot, driving = computeDrivingState(ts))
        return state
    }

    // ------------------------------------------------------------------ internals

    private fun applyToSnapshot(signal: VehicleSignal) {
        snapshot = when (signal) {
            is VehicleSignal.SteeringAngle -> snapshot.copy(steeringDeg = signal.degrees)
            is VehicleSignal.Speed -> snapshot.copy(speedKmh = signal.kmh)
            is VehicleSignal.GearChanged -> snapshot.copy(gear = signal.gear)
            is VehicleSignal.BrakePedal -> snapshot.copy(brakePressed = signal.pressed)
            is VehicleSignal.DoorState -> {
                if (signal.open) openDoors.add(signal.door) else openDoors.remove(signal.door)
                snapshot.copy(openDoors = openDoors.toSet())
            }
        }
    }

    /** Dead-reckon with the *previous* snapshot over the elapsed interval. */
    private fun integrateMotion(nowMs: Long) {
        val last = lastSteeringTs
        if (sessionStartMs != null && last != null && nowMs > last) {
            val dt = (nowMs - last) / 1000f
            val signedMps = when (snapshot.gear) {
                GearPosition.REVERSE -> -snapshot.speedKmh / 3.6f
                GearPosition.DRIVE -> snapshot.speedKmh / 3.6f
                else -> 0f
            }
            model.step(dt, signedMps, snapshot.steeringDeg)
        }
        lastSteeringTs = nowMs
    }

    private fun onGear(gear: GearPosition, ts: Long) {
        when {
            gear == GearPosition.REVERSE && sessionStartMs == null -> startSession(ts)
            gear == GearPosition.PARK && sessionStartMs != null -> finishSession(ts)
        }
    }

    private fun startSession(ts: Long) {
        sessionStartMs = ts
        samples.clear()
        model.reset()
        planner.reset()
        fastForwardSinceMs = null
        lastSteeringTs = ts
    }

    private fun recordSample(ts: Long) {
        if (lastSampleMs == ts && samples.isNotEmpty()) {
            samples[samples.lastIndex] = toSample(ts)
        } else {
            samples.add(toSample(ts))
        }
        lastSampleMs = ts
    }

    private fun toSample(ts: Long) = Sample(
        timestampMs = ts,
        steeringDeg = snapshot.steeringDeg,
        speedKmh = snapshot.speedKmh,
        gear = snapshot.gear,
        brakePressed = snapshot.brakePressed,
        anyDoorOpen = snapshot.anyDoorOpen,
    )

    private fun checkAbandon(ts: Long) {
        val forwardFast = snapshot.gear == GearPosition.DRIVE && snapshot.speedKmh > abandonSpeedKmh
        if (forwardFast) {
            val since = fastForwardSinceMs ?: ts.also { fastForwardSinceMs = it }
            if (ts - since >= abandonAfterMs) abandonSession()
        } else {
            fastForwardSinceMs = null
        }
    }

    private fun abandonSession() {
        sessionStartMs = null
        samples.clear()
        planner.reset()
        pendingResult = null
    }

    private var pendingResult: DrivingState.Result? = null

    private fun finishSession(ts: Long) {
        val start = sessionStartMs ?: return
        recordSample(ts)
        val log = SessionLog(start, ts, samples.toList())
        val hasMotion = log.samples.any { it.speedKmh > 0.5f }
        sessionStartMs = null
        planner.complete()
        pendingResult = if (hasMotion && log.durationSec >= 2f) {
            DrivingState.Result(evaluator.evaluate(log), log)
        } else {
            null // Shifting R -> P without moving is not a parking attempt.
        }
    }

    private fun computeDrivingState(ts: Long): DrivingState {
        val start = sessionStartMs
        if (start != null) {
            val moving = snapshot.speedKmh > 0.5f
            val target = planner.plan(state.side, model.pose.headingDeg, moving, snapshot.steeringDeg)
            val snapped = GhostTargetPlanner.isSnapped(snapshot.steeringDeg, target)
            return DrivingState.Coaching(target, snapped, (ts - start) / 1000f)
        }
        pendingResult?.let { result ->
            // Keep showing the card until acknowledged, but only while parked. UNKNOWN is treated as
            // parked on purpose: a dropped/unknown gear signal right after P must not wipe the result.
            return if (snapshot.gear == GearPosition.PARK || snapshot.gear == GearPosition.UNKNOWN) result
            else { pendingResult = null; DrivingState.Driving }
        }
        return if (state.driving is DrivingState.Result) state.driving else DrivingState.Driving
    }

    /** Exposed for the HUD: current manoeuvre phase even before the first signal of a session. */
    val phase: ManeuverPhase get() = planner.currentPhase
}
