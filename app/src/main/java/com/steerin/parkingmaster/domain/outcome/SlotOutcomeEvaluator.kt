package com.steerin.parkingmaster.domain.outcome

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Scores the *result* of a parking manoeuvre (where the car ended up), complementing
 * [com.steerin.parkingmaster.domain.scoring.ParkingScoreEvaluator] which scores the *process*.
 *
 * | Category  | Max | Full points                         | Zero points                 |
 * |-----------|-----|-------------------------------------|-----------------------------|
 * | IN_SLOT   | 30  | every gap >= 0 (15 if within 10 cm) | any line crossed by > 10 cm |
 * | CENTERING | 30  | left/right imbalance <= 10 cm       | imbalance >= 60 cm          |
 * | DEPTH     | 20  | nose inside, rear gap 10..50 cm     | nose out, or 40 cm outside  |
 * | ALIGNMENT | 20  | |yaw| <= 1.5 deg                     | |yaw| >= 8 deg              |
 */
class SlotOutcomeEvaluator(private val config: Config = Config()) {

    data class Config(
        val minConfidence: Float = 0.6f,
        val lineTouchToleranceCm: Float = 10f,
        val centeredImbalanceCm: Float = 10f,
        val zeroImbalanceCm: Float = 60f,
        val idealRearMinCm: Float = 10f,
        val idealRearMaxCm: Float = 50f,
        val depthZeroDeviationCm: Float = 40f,
        val alignedYawDeg: Float = 1.5f,
        val zeroYawDeg: Float = 8f,
        /** Weight (percent) of the process score in the combined score when a result score exists. */
        val processWeightPct: Int = 70,
    )

    /** Returns null when the observation is missing or not trustworthy enough to show. */
    fun evaluate(obs: SlotObservation?): OutcomeScore? {
        if (obs == null || obs.confidence < config.minConfidence) return null
        val c = config
        val minGap = minOf(obs.leftGapCm, obs.rightGapCm, obs.frontGapCm, obs.rearGapCm)
        val inSlot = when {
            minGap >= 0f -> OutcomeCategory.IN_SLOT.maxPoints
            minGap >= -c.lineTouchToleranceCm -> OutcomeCategory.IN_SLOT.maxPoints / 2
            else -> 0
        }
        val imbalance = abs(obs.leftGapCm - obs.rightGapCm)
        val centering = linear(imbalance, c.centeredImbalanceCm, c.zeroImbalanceCm, OutcomeCategory.CENTERING.maxPoints)
        val depth = if (obs.frontGapCm < 0f) 0 else {
            val deviation = when {
                obs.rearGapCm < c.idealRearMinCm -> c.idealRearMinCm - obs.rearGapCm
                obs.rearGapCm > c.idealRearMaxCm -> obs.rearGapCm - c.idealRearMaxCm
                else -> 0f
            }
            linear(deviation, 0f, c.depthZeroDeviationCm, OutcomeCategory.DEPTH.maxPoints)
        }
        val alignment = linear(abs(obs.yawDeg), c.alignedYawDeg, c.zeroYawDeg, OutcomeCategory.ALIGNMENT.maxPoints)
        val cats = listOf(
            OutcomeCategoryScore(OutcomeCategory.IN_SLOT, inSlot),
            OutcomeCategoryScore(OutcomeCategory.CENTERING, centering),
            OutcomeCategoryScore(OutcomeCategory.DEPTH, depth),
            OutcomeCategoryScore(OutcomeCategory.ALIGNMENT, alignment),
        )
        return OutcomeScore(cats.sumOf { it.points }, cats, obs)
    }

    /**
     * Process-weighted blend; the process score alone is used when there is no result score.
     * Integer arithmetic so .5 always rounds up (float32 turned 94·0.7 + 19·0.3 = 71.5 into 71.49999 → 71).
     */
    fun combined(processTotal: Int, outcome: OutcomeScore?): Int =
        if (outcome == null) processTotal
        else (processTotal * config.processWeightPct + outcome.total * (100 - config.processWeightPct) + 50) / 100

    /** Full points at or below [full], zero at or above [zero], linear in between. */
    private fun linear(value: Float, full: Float, zero: Float, max: Int): Int = when {
        value <= full -> max
        value >= zero -> 0
        else -> (max * (zero - value) / (zero - full)).roundToInt()
    }
}
