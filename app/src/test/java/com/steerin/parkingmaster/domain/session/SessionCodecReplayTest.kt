package com.steerin.parkingmaster.domain.session

import com.steerin.parkingmaster.data.vehicle.SimulatedVehicleSignalSource
import com.steerin.parkingmaster.domain.model.ParkingSide
import com.steerin.parkingmaster.domain.model.SessionRecord
import com.steerin.parkingmaster.domain.outcome.SlotObservation
import com.steerin.parkingmaster.domain.outcome.SlotSource
import com.steerin.parkingmaster.domain.scoring.ParkingScoreEvaluator
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCodecReplayTest {

    private fun recordedModelSession(): Pair<DrivingState.Result, ParkingSide> {
        val tracker = ParkingSessionTracker().also { it.setSide(ParkingSide.LEFT) }
        var result: DrivingState.Result? = null
        SimulatedVehicleSignalSource.signals(SimulatedVehicleSignalSource.MODEL_PARKING, 50).forEach { s ->
            (tracker.onSignal(s).driving as? DrivingState.Result)?.let { result = it }
        }
        return checkNotNull(result) to ParkingSide.LEFT
    }

    @Test
    fun `encode then decode preserves the log and header`() {
        val (res, side) = recordedModelSession()
        val rec = SessionRecord("20261108-120000-0001", 1_762_000_000_000L, side, res.log)
        val text = SessionCodec.encode(rec)
        val back = SessionCodec.decode(text)
        assertEquals(rec.id, back.id)
        assertEquals(rec.savedAtEpochMs, back.savedAtEpochMs)
        assertEquals(rec.side, back.side)
        assertEquals(rec.log.startedAtMs, back.log.startedAtMs)
        assertEquals(rec.log.endedAtMs, back.log.endedAtMs)
        assertEquals(rec.log.samples.size, back.log.samples.size)
        assertEquals(rec.log.samples.first(), back.log.samples.first())
        assertEquals(rec.log.samples.last(), back.log.samples.last())
        assertTrue(text.startsWith("steerin-session 1"))
    }

    @Test
    fun `replaying a decoded session reproduces the same score and phases`() {
        val (res, side) = recordedModelSession()
        val rec = SessionCodec.decode(SessionCodec.encode(SessionRecord("x", 0L, side, res.log)))
        val tracker = ParkingSessionTracker().also { it.setSide(rec.side) }
        val states = SessionReplayer.toSignals(rec.log).map { tracker.onSignal(it) }
        val replayed = states.last().driving as DrivingState.Result
        assertEquals(res.score.total, replayed.score.total)
        assertEquals(res.score.categories.map { it.points }, replayed.score.categories.map { it.points })
        val phases = states.mapNotNull { (it.driving as? DrivingState.Coaching)?.target?.phase }.distinct()
        assertTrue("phases were $phases", phases.size >= 3)
        // Ghost target sign must follow the stored side (LEFT → negative lock).
        val firstTarget = states.firstNotNullOf { (it.driving as? DrivingState.Coaching)?.target }
        assertTrue(firstTarget.targetDeg < 0f)
    }

    @Test
    fun `evaluator on a decoded log equals the live score`() {
        val (res, side) = recordedModelSession()
        val rec = SessionCodec.decode(SessionCodec.encode(SessionRecord("y", 0L, side, res.log)))
        assertEquals(res.score.total, ParkingScoreEvaluator().evaluate(rec.log).total)
    }

    @Test
    fun `slot observation round-trips and older files without it still decode`() {
        val (res, side) = recordedModelSession()
        val slot = SlotObservation(res.log.endedAtMs, 41f, 37f, 32f, 24f, 0.8f, 0.93f, SlotSource.SIMULATED)
        val back = SessionCodec.decode(SessionCodec.encode(SessionRecord("z", 0L, side, res.log, slot)))
        assertEquals(slot, back.slot)

        val legacy = SessionCodec.encode(SessionRecord("old", 0L, side, res.log))
        assertNull(SessionCodec.decode(legacy).slot)

        val malformed = legacy.replace("samples\n", "slot=broken\nsamples\n")
        val tolerant = SessionCodec.decode(malformed)
        assertNull(tolerant.slot)
        assertEquals(res.log.samples.size, tolerant.log.samples.size)
    }

    @Test
    fun `values changing in the same ms as P survive save and replay`() {
        val (res, side) = recordedModelSession()
        val samples = res.log.samples.toMutableList()
        val last = samples.last()
        // Door opens and brake releases at the very moment P is engaged.
        samples[samples.lastIndex] = last.copy(anyDoorOpen = true, brakePressed = !last.brakePressed, steeringDeg = last.steeringDeg + 30f)
        val log = res.log.copy(samples = samples)
        val tracker = ParkingSessionTracker().also { it.setSide(side) }
        val replayed = SessionReplayer.toSignals(log).map { tracker.onSignal(it) }.last().driving as DrivingState.Result
        assertEquals(samples.last(), replayed.log.samples.last())
        assertEquals(ParkingScoreEvaluator().evaluate(log).total, replayed.score.total)
    }

    @Test
    fun `a corrupt sample row is skipped instead of dropping the session`() {
        val (res, side) = recordedModelSession()
        val text = SessionCodec.encode(SessionRecord("x", 1L, side, res.log))
        val lines = text.lines().toMutableList()
        val firstRow = lines.indexOf("ts,steer,speed,gear,brake,door") + 2
        lines[firstRow] = "garbage,1.0,,R,0,0"
        val back = SessionCodec.decode(lines.joinToString("\n"))
        assertEquals(res.log.samples.size - 1, back.log.samples.size)
    }
}
