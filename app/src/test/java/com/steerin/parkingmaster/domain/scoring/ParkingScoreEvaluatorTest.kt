package com.steerin.parkingmaster.domain.scoring

import com.steerin.parkingmaster.data.vehicle.GearPosition
import com.steerin.parkingmaster.domain.model.Sample
import com.steerin.parkingmaster.domain.model.ScoreCategory
import com.steerin.parkingmaster.domain.model.SessionLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkingScoreEvaluatorTest {

    private val evaluator = ParkingScoreEvaluator()

    /** Builds a log where each entry is (seconds, steering, speed, gear). */
    private fun log(vararg rows: Row): SessionLog {
        val samples = rows.map { Sample((it.t * 1000).toLong(), it.steer, it.speed, it.gear, it.brake, it.door) }
        return SessionLog(samples.first().timestampMs, samples.last().timestampMs, samples)
    }

    private data class Row(val t: Float, val steer: Float, val speed: Float, val gear: GearPosition = GearPosition.REVERSE, val brake: Boolean = false, val door: Boolean = false)

    @Test
    fun `smooth single-pass park scores full marks`() {
        val l = log(
            Row(0f, 0f, 0f), Row(2f, 450f, 0f), Row(4f, 450f, 3f), Row(10f, 450f, 3f),
            Row(14f, 0f, 2.5f), Row(20f, 0f, 2f), Row(22f, 0f, 0f, brake = true), Row(23f, 0f, 0f, GearPosition.PARK, brake = true),
        )
        val score = evaluator.evaluate(l)
        assertEquals(100, score.total)
        // Turning in and then unwinding is exactly one direction change, which is free.
        assertEquals(1, score.metrics.steeringReversals)
        assertEquals(0, score.metrics.directionChanges)
    }

    @Test
    fun `steering reversals are counted with hysteresis`() {
        val samples = listOf(0f, 100f, 300f, 290f, 310f, 80f, 350f, 0f) // tiny 290/310 wiggle must not count
        val l = log(*samples.mapIndexed { i, s -> Row(i.toFloat(), s, 2f) }.toTypedArray())
        assertEquals(3, evaluator.countSteeringReversals(l.samples))
    }

    @Test
    fun `direction changes count R to D to R as two retries`() {
        val l = log(
            Row(0f, 0f, 2f, GearPosition.REVERSE), Row(3f, 0f, 0f, GearPosition.NEUTRAL),
            Row(4f, 0f, 3f, GearPosition.DRIVE), Row(7f, 0f, 0f, GearPosition.REVERSE), Row(12f, 0f, 2f, GearPosition.REVERSE),
            Row(13f, 0f, 0f, GearPosition.PARK),
        )
        val score = evaluator.evaluate(l)
        assertEquals(2, score.metrics.directionChanges)
        assertEquals(13, score.categories.first { it.category == ScoreCategory.RETRY_COUNT }.points)
    }

    @Test
    fun `harsh brake and over-speed reduce the speed category`() {
        val l = log(
            Row(0f, 0f, 0f), Row(2f, 0f, 9f), Row(4f, 0f, 9f), Row(4.5f, 0f, 0f, brake = true), Row(6f, 0f, 0f, GearPosition.PARK),
        )
        val score = evaluator.evaluate(l)
        assertEquals(1, score.metrics.harshBrakeCount)
        assertEquals(9f, score.metrics.maxReverseSpeedKmh, 0.01f)
        val speedCat = score.categories.first { it.category == ScoreCategory.SPEED_AND_BRAKING }
        assertTrue("expected heavy penalty, got ${speedCat.points}", speedCat.points <= 8)
    }

    @Test
    fun `open trunk while reversing zeroes safety`() {
        val l = log(Row(0f, 0f, 0f), Row(2f, 0f, 2f, door = true), Row(5f, 0f, 0f, GearPosition.PARK))
        val score = evaluator.evaluate(l)
        assertEquals(0, score.categories.first { it.category == ScoreCategory.SAFETY }.points)
    }

    @Test
    fun `long sessions lose duration points gradually`() {
        val l = log(Row(0f, 0f, 0f), Row(30f, 0f, 2f), Row(70f, 0f, 0f, GearPosition.PARK))
        val score = evaluator.evaluate(l)
        // 70 s → 25 s over → (25/10 + 1) * 2 = 6 points off
        assertEquals(4, score.categories.first { it.category == ScoreCategory.DURATION }.points)
    }

    @Test
    fun `total never leaves 0 to 100 and tier matches thresholds`() {
        val bad = log(
            Row(0f, 0f, 12f), Row(1f, 400f, 12f), Row(2f, -400f, 11f), Row(3f, 400f, 10f), Row(4f, -400f, 0f, brake = true, door = true),
            Row(5f, 0f, 5f, GearPosition.DRIVE), Row(6f, 0f, 5f, GearPosition.REVERSE), Row(7f, 0f, 5f, GearPosition.DRIVE),
            Row(8f, 0f, 5f, GearPosition.REVERSE), Row(9f, 0f, 5f, GearPosition.DRIVE), Row(100f, 0f, 0f, GearPosition.PARK),
        )
        val score = evaluator.evaluate(bad)
        assertTrue(score.total in 0..100)
        assertTrue(score.total < 50)
        assertEquals(com.steerin.parkingmaster.domain.model.RewardTier.PRACTICE, score.tier)
    }
}
