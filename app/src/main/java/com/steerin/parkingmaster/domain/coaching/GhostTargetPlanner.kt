package com.steerin.parkingmaster.domain.coaching

import com.steerin.parkingmaster.domain.kinematics.VehicleGeometry
import com.steerin.parkingmaster.domain.model.GhostTarget
import com.steerin.parkingmaster.domain.model.ManeuverPhase
import com.steerin.parkingmaster.domain.model.ParkingSide
import kotlin.math.abs

/**
 * Turns the accumulated heading change of a perpendicular reverse-parking manoeuvre into the
 * steering angle the ghost ring should display.
 *
 * Script (slot on the RIGHT; mirror for LEFT):
 *  1. READY          – car stopped in R: pre-turn the wheel to full lock toward the slot.
 *  2. TURN_IN        – hold full lock until the car has rotated ~[turnInUntilDeg].
 *  3. STRAIGHTEN     – unwind linearly so the wheel is centred by [straightAtDeg]. At creeping
 *                      speed the car only gains ~15° while the wheel unwinds, so the threshold
 *                      sits below 90°, and a driver who has centred the wheel after
 *                      [centredExitAfterDeg] exits the phase at their own pace.
 *  4. HOLD_STRAIGHT  – wheel centred, creep straight back until P.
 *
 * Phases only move forward; a driver who overshoots is told to hold straight rather than being
 * bounced back into TURN_IN, which would be confusing mid-manoeuvre.
 */
class GhostTargetPlanner(
    private val geometry: VehicleGeometry = VehicleGeometry(),
    private val turnInUntilDeg: Float = 65f,
    private val straightAtDeg: Float = 80f,
    /** Once past this rotation, a centred wheel also completes STRAIGHTEN (driver-paced exit). */
    private val centredExitAfterDeg: Float = 75f,
    private val centredToleranceDeg: Float = 15f,
    /** Fraction of mechanical lock used as the "full lock" target; leaves margin for the stop. */
    private val lockFraction: Float = 0.85f,
) {
    private var phase: ManeuverPhase = ManeuverPhase.READY

    val currentPhase: ManeuverPhase get() = phase

    fun reset() {
        phase = ManeuverPhase.READY
    }

    fun complete(): GhostTarget {
        phase = ManeuverPhase.COMPLETE
        return GhostTarget(phase, 0f, 5f, 0f)
    }

    /**
     * @param headingChangeDeg signed heading change since manoeuvre start (model convention: + = CCW/left).
     * @param moving whether the car is currently moving.
     */
    fun plan(side: ParkingSide, headingChangeDeg: Float, moving: Boolean, liveSteeringDeg: Float = Float.NaN): GhostTarget {
        val rotated = abs(headingChangeDeg)
        val wheelCentred = !liveSteeringDeg.isNaN() && abs(liveSteeringDeg) <= centredToleranceDeg
        val sign = if (side == ParkingSide.RIGHT) 1f else -1f
        val lock = geometry.maxSteeringWheelDeg * lockFraction

        phase = when (phase) {
            ManeuverPhase.READY -> if (moving || rotated > 2f) ManeuverPhase.TURN_IN else ManeuverPhase.READY
            ManeuverPhase.TURN_IN -> if (rotated >= turnInUntilDeg) ManeuverPhase.STRAIGHTEN else ManeuverPhase.TURN_IN
            ManeuverPhase.STRAIGHTEN ->
                if (rotated >= straightAtDeg || (rotated >= centredExitAfterDeg && wheelCentred)) ManeuverPhase.HOLD_STRAIGHT
                else ManeuverPhase.STRAIGHTEN
            ManeuverPhase.HOLD_STRAIGHT -> ManeuverPhase.HOLD_STRAIGHT
            ManeuverPhase.COMPLETE -> ManeuverPhase.COMPLETE
        }

        return when (phase) {
            ManeuverPhase.READY, ManeuverPhase.TURN_IN ->
                GhostTarget(phase, sign * lock, toleranceDeg = 30f, headingChangeDeg = rotated)
            ManeuverPhase.STRAIGHTEN -> {
                val span = (straightAtDeg - turnInUntilDeg).coerceAtLeast(1f)
                val remaining = ((straightAtDeg - rotated) / span).coerceIn(0f, 1f)
                GhostTarget(phase, sign * lock * remaining, toleranceDeg = 20f, headingChangeDeg = rotated)
            }
            ManeuverPhase.HOLD_STRAIGHT, ManeuverPhase.COMPLETE ->
                GhostTarget(phase, 0f, toleranceDeg = 5f, headingChangeDeg = rotated)
        }
    }

    companion object {
        /** Snap-in test shared by UI and audio feedback. */
        fun isSnapped(liveDeg: Float, target: GhostTarget): Boolean =
            abs(liveDeg - target.targetDeg) <= target.toleranceDeg
    }
}
