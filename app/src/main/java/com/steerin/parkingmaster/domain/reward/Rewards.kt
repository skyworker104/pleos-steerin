package com.steerin.parkingmaster.domain.reward

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Cosmetic steering-wheel skins unlocked by parking well. Purely visual: every skin keeps the
 * coaching colour language (cyan target, green snapped, coral off-target), so a skin can never
 * change what the driver is being told.
 */
enum class WheelSkin(val id: String) {
    CLASSIC("classic"),
    MASTER_RING("master_ring"),
    CARBON_GRIP("carbon_grip"),
    NEON_STREAK("neon_streak"),
    GOLD_KING("gold_king");

    companion object {
        fun fromId(id: String?): WheelSkin? = entries.firstOrNull { it.id == id }
    }
}

/** One finished parking, as far as rewards care: when, and the headline (combined) score. */
data class SessionResult(val epochMs: Long, val score: Int)

data class RewardProgress(
    val totalSessions: Int = 0,
    val bestScore: Int = 0,
    /** Sessions scoring at least [RewardRules.MASTER_SCORE]. */
    val masterCount: Int = 0,
    /** Sessions scoring at least [RewardRules.KING_SCORE]. */
    val kingCount: Int = 0,
    /** Consecutive most-recent sessions scoring at least [RewardRules.MASTER_SCORE]. */
    val goodStreak: Int = 0,
    /** Longest such run ever seen in the stored history. */
    val bestGoodStreak: Int = 0,
    /** Consecutive calendar days with at least one parking, ending today or yesterday. */
    val dayStreak: Int = 0,
    val unlocked: Set<WheelSkin> = setOf(WheelSkin.CLASSIC),
)

/** The next skin still locked, with how far along the driver is. */
data class NextGoal(val skin: WheelSkin, val current: Int, val target: Int)

object RewardRules {
    const val MASTER_SCORE = 70
    const val KING_SCORE = 90
    const val KING_TIMES = 3
    const val STREAK_LENGTH = 5
    const val SESSION_COUNT = 10

    /** Order in which skins are offered as the "next goal". */
    val ladder = listOf(WheelSkin.MASTER_RING, WheelSkin.CARBON_GRIP, WheelSkin.NEON_STREAK, WheelSkin.GOLD_KING)

    /**
     * Current / target for one skin, as shown to the driver. For the streak skin "current" is the
     * run in progress (a miss resets it to 0), while unlocking uses the best run ever ([earned]).
     */
    fun requirement(skin: WheelSkin, p: RewardProgress): Pair<Int, Int> = when (skin) {
        WheelSkin.CLASSIC -> 0 to 0
        WheelSkin.MASTER_RING -> minOf(p.masterCount, 1) to 1
        WheelSkin.CARBON_GRIP -> minOf(p.totalSessions, SESSION_COUNT) to SESSION_COUNT
        WheelSkin.NEON_STREAK -> minOf(p.goodStreak, STREAK_LENGTH) to STREAK_LENGTH
        WheelSkin.GOLD_KING -> minOf(p.kingCount, KING_TIMES) to KING_TIMES
    }

    fun earned(skin: WheelSkin, p: RewardProgress): Boolean = when (skin) {
        WheelSkin.NEON_STREAK -> p.bestGoodStreak >= STREAK_LENGTH
        else -> requirement(skin, p).let { (cur, target) -> cur >= target }
    }
}

/** Pure, deterministic reward evaluation over the stored session history. */
class RewardEngine(private val zone: ZoneId = ZoneId.systemDefault()) {

    /**
     * [results] in any order. [alreadyUnlocked] is what was unlocked before (persisted separately),
     * so trimming or deleting old sessions never takes a skin away.
     */
    fun progress(results: List<SessionResult>, nowMs: Long, alreadyUnlocked: Set<WheelSkin> = emptySet()): RewardProgress {
        val chrono = results.sortedBy { it.epochMs }
        var run = 0
        var bestRun = 0
        for (r in chrono) {
            run = if (r.score >= RewardRules.MASTER_SCORE) run + 1 else 0
            bestRun = maxOf(bestRun, run)
        }
        val base = RewardProgress(
            totalSessions = chrono.size,
            bestScore = chrono.maxOfOrNull { it.score } ?: 0,
            masterCount = chrono.count { it.score >= RewardRules.MASTER_SCORE },
            kingCount = chrono.count { it.score >= RewardRules.KING_SCORE },
            goodStreak = run,
            bestGoodStreak = bestRun,
            dayStreak = dayStreak(chrono, nowMs),
        )
        val earned = WheelSkin.entries.filter { RewardRules.earned(it, base) }.toSet()
        return base.copy(unlocked = earned + alreadyUnlocked + WheelSkin.CLASSIC)
    }

    fun nextGoal(p: RewardProgress): NextGoal? =
        RewardRules.ladder.firstOrNull { it !in p.unlocked }?.let { skin ->
            val (cur, target) = RewardRules.requirement(skin, p)
            NextGoal(skin, cur, target)
        }

    private fun dayStreak(chrono: List<SessionResult>, nowMs: Long): Int {
        if (chrono.isEmpty()) return 0
        val days = chrono.map { Instant.ofEpochMilli(it.epochMs).atZone(zone).toLocalDate() }.toSet()
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        var day: LocalDate = when {
            today in days -> today
            today.minusDays(1) in days -> today.minusDays(1)
            else -> return 0
        }
        var count = 0
        while (day in days) {
            count++
            day = day.minusDays(1)
        }
        return count
    }
}

/** Expression of the mascot (휠리) on the result card. */
enum class MascotMood { HELLO, PROUD, CHEER, ENCOURAGE, COMFORT;

    companion object {
        fun forScore(score: Int): MascotMood = when {
            score >= RewardRules.KING_SCORE -> PROUD
            score >= RewardRules.MASTER_SCORE -> CHEER
            score >= 50 -> ENCOURAGE
            else -> COMFORT
        }
    }
}
