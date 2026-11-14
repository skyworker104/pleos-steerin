package com.steerin.parkingmaster.domain.reward

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class RewardEngineTest {
    private val zone = ZoneOffset.UTC
    private val engine = RewardEngine(zone)
    private val today = LocalDate.of(2026, 11, 14)
    private val now = at(today, 12)

    private fun at(day: LocalDate, hour: Int) = day.atTime(hour, 0).toInstant(zone).toEpochMilli()
    private fun results(vararg scores: Int, day: LocalDate = today) = scores.mapIndexed { i, s -> SessionResult(at(day, 0) + i * 60_000L, s) }

    @Test fun `no history keeps only the classic wheel`() {
        val p = engine.progress(emptyList(), now)
        assertEquals(setOf(WheelSkin.CLASSIC), p.unlocked)
        assertEquals(0, p.dayStreak)
        assertEquals(WheelSkin.MASTER_RING, engine.nextGoal(p)?.skin)
    }

    @Test fun `first 70 unlocks master ring, 69 does not`() {
        assertFalse(WheelSkin.MASTER_RING in engine.progress(results(69), now).unlocked)
        assertTrue(WheelSkin.MASTER_RING in engine.progress(results(69, 70), now).unlocked)
    }

    @Test fun `gold king needs three sessions of 90 or more`() {
        val two = engine.progress(results(95, 40, 90), now)
        assertFalse(WheelSkin.GOLD_KING in two.unlocked)
        assertEquals(2 to 3, RewardRules.requirement(WheelSkin.GOLD_KING, two))
        assertTrue(WheelSkin.GOLD_KING in engine.progress(results(95, 40, 90, 91), now).unlocked)
    }

    @Test fun `neon needs five 70+ in a row and a miss resets the run`() {
        val broken = engine.progress(results(80, 80, 80, 80, 60, 80), now)
        assertEquals(4, broken.bestGoodStreak)
        assertEquals(1, broken.goodStreak)
        assertFalse(WheelSkin.NEON_STREAK in broken.unlocked)
        assertEquals("progress shows the run in progress", 1 to 5, RewardRules.requirement(WheelSkin.NEON_STREAK, broken))
        val run = engine.progress(results(60, 71, 72, 73, 74, 75), now)
        assertEquals(5, run.goodStreak)
        assertTrue(WheelSkin.NEON_STREAK in run.unlocked)
        // A later miss resets the visible run but never re-locks the skin
        val after = engine.progress(results(60, 71, 72, 73, 74, 75, 30), now)
        assertTrue(WheelSkin.NEON_STREAK in after.unlocked)
        assertEquals(0 to 5, RewardRules.requirement(WheelSkin.NEON_STREAK, after))
    }

    @Test fun `streak follows time order, not list order`() {
        val shuffled = listOf(SessionResult(at(today, 3), 80), SessionResult(at(today, 1), 80), SessionResult(at(today, 2), 30))
        assertEquals(1, engine.progress(shuffled, now).goodStreak)
    }

    @Test fun `carbon grip after ten sessions whatever the score`() {
        assertTrue(WheelSkin.CARBON_GRIP in engine.progress(results(*IntArray(10) { 20 }), now).unlocked)
        assertFalse(WheelSkin.CARBON_GRIP in engine.progress(results(*IntArray(9) { 100 }), now).unlocked)
    }

    @Test fun `persisted unlocks survive a trimmed history`() {
        val p = engine.progress(results(40), now, alreadyUnlocked = setOf(WheelSkin.GOLD_KING))
        assertTrue(WheelSkin.GOLD_KING in p.unlocked)
        assertTrue(WheelSkin.CLASSIC in p.unlocked)
    }

    @Test fun `day streak counts back from today or yesterday`() {
        val days = listOf(today, today.minusDays(1), today.minusDays(2), today.minusDays(4)).map { SessionResult(at(it, 9), 50) }
        assertEquals(3, engine.progress(days, now).dayStreak)
        val fromYesterday = listOf(today.minusDays(1), today.minusDays(2)).map { SessionResult(at(it, 9), 50) }
        assertEquals(2, engine.progress(fromYesterday, now).dayStreak)
        val stale = listOf(today.minusDays(2)).map { SessionResult(at(it, 9), 50) }
        assertEquals(0, engine.progress(stale, now).dayStreak)
    }

    @Test fun `next goal walks the ladder and ends when everything is unlocked`() {
        val p = engine.progress(results(70), now)
        val goal = engine.nextGoal(p)!!
        assertEquals(WheelSkin.CARBON_GRIP, goal.skin)
        assertEquals(1, goal.current)
        assertEquals(10, goal.target)
        assertNull(engine.nextGoal(engine.progress(emptyList(), now, WheelSkin.entries.toSet())))
    }

    @Test fun `mascot mood follows the score bands`() {
        assertEquals(MascotMood.PROUD, MascotMood.forScore(90))
        assertEquals(MascotMood.CHEER, MascotMood.forScore(70))
        assertEquals(MascotMood.ENCOURAGE, MascotMood.forScore(50))
        assertEquals(MascotMood.COMFORT, MascotMood.forScore(49))
    }
}
