package com.steerin.parkingmaster.domain.outcome

import com.steerin.parkingmaster.data.outcome.SimulatedOutcomeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlotOutcomeEvaluatorTest {

    private val evaluator = SlotOutcomeEvaluator()

    private fun obs(
        left: Float = 40f, right: Float = 40f, front: Float = 30f, rear: Float = 25f,
        yaw: Float = 0f, confidence: Float = 0.95f,
    ) = SlotObservation(0L, left, right, front, rear, yaw, confidence, SlotSource.SVM)

    private fun OutcomeScore.points(c: OutcomeCategory) = categories.first { it.category == c }.points

    @Test
    fun `centred straight car inside the lines scores 100`() {
        val s = evaluator.evaluate(obs())!!
        assertEquals(100, s.total)
        assertNull(s.weakestCategory)
    }

    @Test
    fun `missing or low-confidence observation yields no result score`() {
        assertNull(evaluator.evaluate(null))
        assertNull(evaluator.evaluate(obs(confidence = 0.4f)))
    }

    @Test
    fun `touching a line costs half of IN_SLOT, crossing it costs all`() {
        assertEquals(15, evaluator.evaluate(obs(right = -5f))!!.points(OutcomeCategory.IN_SLOT))
        assertEquals(0, evaluator.evaluate(obs(right = -25f))!!.points(OutcomeCategory.IN_SLOT))
    }

    @Test
    fun `left-right imbalance reduces centering linearly`() {
        assertEquals(30, evaluator.evaluate(obs(left = 45f, right = 35f))!!.points(OutcomeCategory.CENTERING))
        assertEquals(15, evaluator.evaluate(obs(left = 57.5f, right = 22.5f))!!.points(OutcomeCategory.CENTERING))
        assertEquals(0, evaluator.evaluate(obs(left = 70f, right = 10f))!!.points(OutcomeCategory.CENTERING))
    }

    @Test
    fun `nose sticking out of the entry line zeroes depth`() {
        val s = evaluator.evaluate(obs(front = -8f))!!
        assertEquals(0, s.points(OutcomeCategory.DEPTH))
        assertEquals(OutcomeCategory.DEPTH, s.weakestCategory)
    }

    @Test
    fun `yaw beyond 8 degrees zeroes alignment`() {
        assertEquals(20, evaluator.evaluate(obs(yaw = -1.2f))!!.points(OutcomeCategory.ALIGNMENT))
        assertEquals(0, evaluator.evaluate(obs(yaw = 9f))!!.points(OutcomeCategory.ALIGNMENT))
    }

    @Test
    fun `combined score is process weighted 70 to 30 and falls back to process alone`() {
        val perfect = evaluator.evaluate(obs())!!
        assertEquals(93, evaluator.combined(90, perfect))
        assertEquals(90, evaluator.combined(90, null))
    }

    @Test
    fun `demo fixtures are ordered so a better manoeuvre shows a better position`() {
        val good = evaluator.evaluate(SimulatedOutcomeSource.fixtureFor(95, 0L))!!.total
        val mid = evaluator.evaluate(SimulatedOutcomeSource.fixtureFor(75, 0L))!!.total
        val poor = evaluator.evaluate(SimulatedOutcomeSource.fixtureFor(40, 0L))!!.total
        assertTrue("good=$good mid=$mid poor=$poor", good > mid && mid > poor)
        assertEquals(SlotSource.SIMULATED, SimulatedOutcomeSource.fixtureFor(95, 0L).source)
    }

    @Test
    fun `crossing a line by exactly 10 cm still earns half of IN_SLOT`() {
        assertEquals(15, evaluator.evaluate(obs(right = -10f))!!.points(OutcomeCategory.IN_SLOT))
        assertEquals(0, evaluator.evaluate(obs(right = -10.1f))!!.points(OutcomeCategory.IN_SLOT))
    }

    @Test
    fun `combined score rounds 71_5 up to 72`() {
        val outcome = OutcomeScore(19, emptyList(), obs())
        assertEquals(72, evaluator.combined(94, outcome)) // 94·0.7 + 19·0.3 = 71.5
        assertEquals(94, evaluator.combined(94, null))
        assertEquals(100, evaluator.combined(100, OutcomeScore(100, emptyList(), obs())))
    }
}
