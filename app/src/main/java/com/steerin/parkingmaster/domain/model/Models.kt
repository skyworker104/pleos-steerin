package com.steerin.parkingmaster.domain.model

import com.steerin.parkingmaster.data.vehicle.GearPosition

/** One fused snapshot of the vehicle state, recorded whenever any signal changes. */
data class Sample(
    val timestampMs: Long,
    val steeringDeg: Float,
    val speedKmh: Float,
    val gear: GearPosition,
    val brakePressed: Boolean,
    val anyDoorOpen: Boolean,
)

/** Everything recorded between the first REVERSE and the final PARK. */
data class SessionLog(
    val startedAtMs: Long,
    val endedAtMs: Long,
    val samples: List<Sample>,
) {
    val durationSec: Float get() = (endedAtMs - startedAtMs) / 1000f
}

/** Which side the parking slot is on, relative to the driver. Drives the ghost target sign. */
enum class ParkingSide { LEFT, RIGHT }

/** Phases of a perpendicular reverse-parking manoeuvre. */
enum class ManeuverPhase { READY, TURN_IN, STRAIGHTEN, HOLD_STRAIGHT, COMPLETE }

/** What the ghost ring should show right now. */
data class GhostTarget(
    val phase: ManeuverPhase,
    /** Target steering wheel angle in degrees (same sign convention as the live signal). */
    val targetDeg: Float,
    /** Snap-in tolerance for this phase, in degrees. */
    val toleranceDeg: Float,
    /** Heading change accumulated since the manoeuvre started, in degrees (absolute). */
    val headingChangeDeg: Float,
)

enum class ScoreCategory(val maxPoints: Int) {
    STEERING_STABILITY(35),
    RETRY_COUNT(25),
    SPEED_AND_BRAKING(20),
    SAFETY(10),
    DURATION(10),
}

data class CategoryScore(
    val category: ScoreCategory,
    val points: Int,
    /** Human-readable metric behind the points, e.g. "조향 반전 3회". */
    val evidence: String,
)

data class ParkingMetrics(
    val steeringReversals: Int,
    val maxSteeringRateDegPerSec: Float,
    val directionChanges: Int,
    val maxReverseSpeedKmh: Float,
    val harshBrakeCount: Int,
    val doorOpenWhileMoving: Boolean,
    val durationSec: Float,
    val distanceReversedM: Float,
)

enum class RewardTier(val minScore: Int, val badgeKey: String) {
    PRACTICE(0, "practice"),
    ENTRY(50, "entry"),
    MASTER(70, "master"),
    TOP_KING(90, "top_king");

    companion object {
        fun fromScore(score: Int): RewardTier =
            entries.sortedByDescending { it.minScore }.first { score >= it.minScore }
    }
}

data class ParkingScore(
    val total: Int,
    val tier: RewardTier,
    val categories: List<CategoryScore>,
    val metrics: ParkingMetrics,
) {
    /** The category that lost the most points relative to its maximum, used for the coaching tip. */
    val weakestCategory: ScoreCategory?
        get() = categories.minByOrNull { it.points.toFloat() / it.category.maxPoints }
            ?.takeIf { it.points < it.category.maxPoints }?.category
}
