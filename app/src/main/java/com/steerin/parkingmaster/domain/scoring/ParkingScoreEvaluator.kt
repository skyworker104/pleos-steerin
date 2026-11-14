package com.steerin.parkingmaster.domain.scoring

import com.steerin.parkingmaster.data.vehicle.GearPosition
import com.steerin.parkingmaster.domain.model.CategoryScore
import com.steerin.parkingmaster.domain.model.ParkingMetrics
import com.steerin.parkingmaster.domain.model.ParkingScore
import com.steerin.parkingmaster.domain.model.RewardTier
import com.steerin.parkingmaster.domain.model.Sample
import com.steerin.parkingmaster.domain.model.ScoreCategory
import com.steerin.parkingmaster.domain.model.SessionLog
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 100-point "process quality" evaluator for a reverse-parking session.
 *
 * Every metric is derived from signals that are actually available to a Pleos Connect app
 * (steering angle, gear, speed, brake pedal, door state). No GPS, no ultrasonic distance,
 * no camera. See docs/SCORING.md for the rationale behind each threshold.
 */
class ParkingScoreEvaluator(
    private val config: Config = Config(),
) {
    data class Config(
        /** A steering direction change counts only if the wheel moved at least this much between extremes. */
        val reversalHysteresisDeg: Float = 25f,
        /** Reversals up to this number are free (one correction is normal). */
        val freeReversals: Int = 1,
        val reversalPenalty: Int = 4,
        /** Steering rate while the car is moving above which we start deducting.
         *  Hand-over-hand unwinding is ~130-180°/s; only yanking beyond this counts. */
        val steeringRateSoftLimitDegPerSec: Float = 220f,
        val steeringRatePenaltyPer20Deg: Int = 2,
        /** Speed above which a sample is treated as "moving" for rate/door checks. */
        val movingThresholdKmh: Float = 0.5f,
        /** Reverse speed above which we deduct per km/h. */
        val reverseSpeedSoftLimitKmh: Float = 5f,
        val reverseSpeedPenaltyPerKmh: Int = 2,
        /** Deceleration (km/h per second) that counts as a harsh brake. ~2.5 m/s². */
        val harshBrakeKmhPerSec: Float = 9f,
        val harshBrakePenalty: Int = 4,
        val harshBrakeDebounceMs: Long = 1500,
        /** Sessions up to this long get full duration points. */
        val durationFreeSec: Float = 45f,
        val durationPenaltyPer10Sec: Int = 2,
    )

    fun evaluate(log: SessionLog): ParkingScore {
        val metrics = computeMetrics(log)
        val categories = listOf(
            scoreSteering(metrics),
            scoreRetries(metrics),
            scoreSpeedAndBraking(metrics),
            scoreSafety(metrics),
            scoreDuration(metrics),
        )
        val total = categories.sumOf { it.points }.coerceIn(0, 100)
        return ParkingScore(total, RewardTier.fromScore(total), categories, metrics)
    }

    // ---------------------------------------------------------------- metrics

    fun computeMetrics(log: SessionLog): ParkingMetrics {
        val s = log.samples
        return ParkingMetrics(
            steeringReversals = countSteeringReversals(s),
            maxSteeringRateDegPerSec = maxSteeringRateWhileMoving(s),
            directionChanges = countDirectionChanges(s),
            maxReverseSpeedKmh = s.filter { it.gear == GearPosition.REVERSE }.maxOfOrNull { it.speedKmh } ?: 0f,
            harshBrakeCount = countHarshBrakes(s),
            doorOpenWhileMoving = s.any { it.anyDoorOpen && (it.gear == GearPosition.REVERSE || it.speedKmh > config.movingThresholdKmh) },
            durationSec = log.durationSec,
            distanceReversedM = integrateDistance(s, GearPosition.REVERSE),
        )
    }

    /** Counts local extrema of the steering trace, ignoring wiggles smaller than the hysteresis. */
    internal fun countSteeringReversals(samples: List<Sample>): Int {
        if (samples.size < 3) return 0
        var reversals = 0
        var direction = 0 // +1 turning right, -1 turning left, 0 unknown
        var extreme = samples.first().steeringDeg
        for (sample in samples) {
            val d = sample.steeringDeg - extreme
            if (direction >= 0 && d > 0) {
                // still moving right (or first move): push the extreme
                if (d >= config.reversalHysteresisDeg && direction == 0) direction = 1
                if (direction == 1) extreme = sample.steeringDeg
            }
            if (direction <= 0 && d < 0) {
                if (-d >= config.reversalHysteresisDeg && direction == 0) direction = -1
                if (direction == -1) extreme = sample.steeringDeg
            }
            if (direction == 1 && -d >= config.reversalHysteresisDeg) {
                reversals++; direction = -1; extreme = sample.steeringDeg
            } else if (direction == -1 && d >= config.reversalHysteresisDeg) {
                reversals++; direction = 1; extreme = sample.steeringDeg
            }
        }
        return reversals
    }

    /**
     * Rates are measured against an *anchor* sample that only advances once at least
     * [MIN_RATE_WINDOW_MS] has elapsed. Samples closer than that are not dropped: speed, steering and
     * brake updates arrive interleaved, so a steering jump often lands a few ms after a speed sample.
     * Comparing only adjacent samples would silently discard that jump.
     */
    internal fun maxSteeringRateWhileMoving(samples: List<Sample>): Float {
        var maxRate = 0f
        var anchor = samples.firstOrNull() ?: return 0f
        for (i in 1 until samples.size) {
            val b = samples[i]
            val dtMs = b.timestampMs - anchor.timestampMs
            if (dtMs < MIN_RATE_WINDOW_MS) continue
            val a = anchor
            anchor = b
            if (b.speedKmh <= config.movingThresholdKmh && a.speedKmh <= config.movingThresholdKmh) continue
            val rate = abs(b.steeringDeg - a.steeringDeg) / (dtMs / 1000f)
            maxRate = max(maxRate, rate)
        }
        return maxRate
    }

    /** Transitions REVERSE<->DRIVE (through NEUTRAL or not). Each one is a "retry". */
    internal fun countDirectionChanges(samples: List<Sample>): Int {
        var last: GearPosition? = null
        var changes = 0
        for (s in samples) {
            val g = s.gear
            if (g != GearPosition.REVERSE && g != GearPosition.DRIVE) continue
            if (last != null && last != g) changes++
            last = g
        }
        return changes
    }

    internal fun countHarshBrakes(samples: List<Sample>): Int {
        var count = 0
        var lastEventMs: Long? = null
        var anchor = samples.firstOrNull() ?: return 0
        for (i in 1 until samples.size) {
            val b = samples[i]
            val dtMs = b.timestampMs - anchor.timestampMs
            if (dtMs < MIN_RATE_WINDOW_MS) continue // same anchor rule as the steering rate
            val a = anchor
            anchor = b
            val decel = (a.speedKmh - b.speedKmh) / (dtMs / 1000f)
            val debounced = lastEventMs?.let { b.timestampMs - it <= config.harshBrakeDebounceMs } ?: false
            if (decel >= config.harshBrakeKmhPerSec && b.brakePressed && !debounced) {
                count++
                lastEventMs = b.timestampMs
            }
        }
        return count
    }

    internal fun integrateDistance(samples: List<Sample>, gear: GearPosition): Float {
        var meters = 0f
        for (i in 1 until samples.size) {
            val a = samples[i - 1]
            val b = samples[i]
            if (a.gear != gear) continue
            val dt = (b.timestampMs - a.timestampMs) / 1000f
            meters += (a.speedKmh / 3.6f) * dt
        }
        return meters
    }

    // ---------------------------------------------------------------- categories

    private fun scoreSteering(m: ParkingMetrics): CategoryScore {
        val cat = ScoreCategory.STEERING_STABILITY
        val reversalPenalty = min(20, max(0, m.steeringReversals - config.freeReversals) * config.reversalPenalty)
        val over = m.maxSteeringRateDegPerSec - config.steeringRateSoftLimitDegPerSec
        val ratePenalty = if (over > 0) min(15, ((over / 20f).toInt() + 1) * config.steeringRatePenaltyPer20Deg) else 0
        val points = (cat.maxPoints - reversalPenalty - ratePenalty).coerceAtLeast(0)
        return CategoryScore(cat, points, "reversals=${m.steeringReversals}, maxRate=${m.maxSteeringRateDegPerSec.toInt()}°/s")
    }

    private fun scoreRetries(m: ParkingMetrics): CategoryScore {
        val cat = ScoreCategory.RETRY_COUNT
        val penalty = when (m.directionChanges) {
            0 -> 0
            1 -> 5
            2 -> 12
            3 -> 19
            else -> 25
        }
        return CategoryScore(cat, (cat.maxPoints - penalty).coerceAtLeast(0), "directionChanges=${m.directionChanges}")
    }

    private fun scoreSpeedAndBraking(m: ParkingMetrics): CategoryScore {
        val cat = ScoreCategory.SPEED_AND_BRAKING
        val overSpeed = (m.maxReverseSpeedKmh - config.reverseSpeedSoftLimitKmh)
        val speedPenalty = if (overSpeed > 0) min(12, (overSpeed.toInt() + 1) * config.reverseSpeedPenaltyPerKmh) else 0
        val brakePenalty = min(8, m.harshBrakeCount * config.harshBrakePenalty)
        return CategoryScore(cat, (cat.maxPoints - speedPenalty - brakePenalty).coerceAtLeast(0),
            "maxReverse=${"%.1f".format(m.maxReverseSpeedKmh)}km/h, harshBrakes=${m.harshBrakeCount}")
    }

    private fun scoreSafety(m: ParkingMetrics): CategoryScore {
        val cat = ScoreCategory.SAFETY
        val points = if (m.doorOpenWhileMoving) 0 else cat.maxPoints
        return CategoryScore(cat, points, "doorOpenWhileMoving=${m.doorOpenWhileMoving}")
    }

    private fun scoreDuration(m: ParkingMetrics): CategoryScore {
        val cat = ScoreCategory.DURATION
        val over = m.durationSec - config.durationFreeSec
        val penalty = if (over > 0) min(cat.maxPoints, ((over / 10f).toInt() + 1) * config.durationPenaltyPer10Sec) else 0
        return CategoryScore(cat, (cat.maxPoints - penalty).coerceAtLeast(0), "duration=${m.durationSec.toInt()}s")
    }

    private companion object {
        /** Shortest window over which a rate (deg/s, km/h/s) is computed; avoids divide-by-near-zero spikes. */
        const val MIN_RATE_WINDOW_MS = 50L
    }
}
