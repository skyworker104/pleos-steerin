package com.steerin.parkingmaster.domain.coaching

import com.steerin.parkingmaster.domain.model.ManeuverPhase
import com.steerin.parkingmaster.domain.model.ParkingSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GhostTargetPlannerTest {

    @Test
    fun `phases progress READY to TURN_IN to STRAIGHTEN to HOLD_STRAIGHT and never regress`() {
        val p = GhostTargetPlanner()
        assertEquals(ManeuverPhase.READY, p.plan(ParkingSide.RIGHT, 0f, moving = false).phase)
        assertEquals(ManeuverPhase.TURN_IN, p.plan(ParkingSide.RIGHT, 0f, moving = true).phase)
        assertEquals(ManeuverPhase.TURN_IN, p.plan(ParkingSide.RIGHT, 40f, moving = true).phase)
        assertEquals(ManeuverPhase.STRAIGHTEN, p.plan(ParkingSide.RIGHT, 70f, moving = true, liveSteeringDeg = 300f).phase)
        assertEquals(ManeuverPhase.HOLD_STRAIGHT, p.plan(ParkingSide.RIGHT, 90f, moving = true, liveSteeringDeg = 100f).phase)
        // Overshoot / noise must not bounce back
        assertEquals(ManeuverPhase.HOLD_STRAIGHT, p.plan(ParkingSide.RIGHT, 60f, moving = true).phase)
    }

    @Test
    fun `target sign follows parking side`() {
        val right = GhostTargetPlanner().plan(ParkingSide.RIGHT, 0f, moving = false)
        val left = GhostTargetPlanner().plan(ParkingSide.LEFT, 0f, moving = false)
        assertTrue(right.targetDeg > 0f)
        assertTrue(left.targetDeg < 0f)
        assertEquals(right.targetDeg, -left.targetDeg, 1e-3f)
    }

    @Test
    fun `straighten phase unwinds linearly to zero`() {
        val p = GhostTargetPlanner(turnInUntilDeg = 70f, straightAtDeg = 88f)
        p.plan(ParkingSide.RIGHT, 0f, true, 459f)
        val atStart = p.plan(ParkingSide.RIGHT, 70f, true, 459f)
        val mid = p.plan(ParkingSide.RIGHT, 79f, true, 300f)
        val end = p.plan(ParkingSide.RIGHT, 88f, true, 0f)
        assertEquals(ManeuverPhase.STRAIGHTEN, atStart.phase)
        assertTrue(mid.targetDeg in 1f..atStart.targetDeg)
        assertEquals(0f, end.targetDeg, 1e-3f)
    }

    @Test
    fun `snap-in uses the phase tolerance`() {
        val p = GhostTargetPlanner()
        val ready = p.plan(ParkingSide.RIGHT, 0f, false)
        assertTrue(GhostTargetPlanner.isSnapped(ready.targetDeg - 25f, ready))
        assertFalse(GhostTargetPlanner.isSnapped(ready.targetDeg - 45f, ready))
    }

    @Test
    fun `centred wheel after the turn-in completes STRAIGHTEN even if rotation is a little short`() {
        val p = GhostTargetPlanner(turnInUntilDeg = 65f, straightAtDeg = 80f, centredExitAfterDeg = 75f)
        p.plan(ParkingSide.RIGHT, 0f, true, 459f)
        assertEquals(ManeuverPhase.STRAIGHTEN, p.plan(ParkingSide.RIGHT, 70f, true, 200f).phase)
        assertEquals(ManeuverPhase.STRAIGHTEN, p.plan(ParkingSide.RIGHT, 72f, true, 0f).phase)   // too early to call it straight
        assertEquals(ManeuverPhase.HOLD_STRAIGHT, p.plan(ParkingSide.RIGHT, 76f, true, 5f).phase)
    }
}
