package com.steerin.parkingmaster.presentation

import com.steerin.parkingmaster.data.vehicle.SimulatedVehicleSignalSource
import com.steerin.parkingmaster.domain.model.ParkingSide
import com.steerin.parkingmaster.domain.model.SessionRecord
import com.steerin.parkingmaster.domain.session.DrivingState
import com.steerin.parkingmaster.domain.session.ParkingSessionTracker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReplayControllerTest {

    private fun record(): Pair<SessionRecord, Int> {
        val tracker = ParkingSessionTracker().also { it.setSide(ParkingSide.RIGHT) }
        var result: DrivingState.Result? = null
        SimulatedVehicleSignalSource.signals(SimulatedVehicleSignalSource.MODEL_PARKING, 50).forEach { s ->
            (tracker.onSignal(s).driving as? DrivingState.Result)?.let { result = it }
        }
        val r = checkNotNull(result)
        return SessionRecord("r", 0L, ParkingSide.RIGHT, r.log) to r.score.total
    }

    @Test
    fun `pause then play resumes where it stopped and ends with the original score`() = runTest {
        val (rec, total) = record()
        val replay = ReplayController(backgroundScope)
        replay.open(rec)
        advanceTimeBy(3_000); runCurrent()
        replay.pause()
        val paused = replay.state.value.progress
        assertTrue("progress was $paused", paused > 0f && paused < 1f)

        replay.play(); runCurrent()
        assertTrue("resumed at ${replay.state.value.progress}, paused at $paused", replay.state.value.progress >= paused)

        advanceTimeBy(10 * 60_000); runCurrent() // backgroundScope work is not drained by advanceUntilIdle
        val end = replay.state.value
        assertTrue(end.finished)
        assertEquals(total, (end.tracker.driving as DrivingState.Result).score.total)
    }
}
