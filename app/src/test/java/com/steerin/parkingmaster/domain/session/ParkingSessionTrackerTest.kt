package com.steerin.parkingmaster.domain.session

import com.steerin.parkingmaster.data.vehicle.GearPosition
import com.steerin.parkingmaster.data.vehicle.SimulatedVehicleSignalSource
import com.steerin.parkingmaster.data.vehicle.VehicleSignal
import com.steerin.parkingmaster.domain.model.ManeuverPhase
import com.steerin.parkingmaster.domain.model.RewardTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** End-to-end: scripted scenarios → tracker → score. These are the same scripts the demo plays. */
class ParkingSessionTrackerTest {

    private fun run(scenario: SimulatedVehicleSignalSource.Scenario): Pair<ParkingSessionTracker, List<TrackerState>> {
        val tracker = ParkingSessionTracker()
        val states = SimulatedVehicleSignalSource.signals(scenario, tickMs = 50).map { tracker.onSignal(it) }
        return tracker to states
    }

    @Test
    fun `model parking reaches HOLD_STRAIGHT and scores TOP_KING`() {
        val (_, states) = run(SimulatedVehicleSignalSource.MODEL_PARKING)
        val phases = states.mapNotNull { (it.driving as? DrivingState.Coaching)?.target?.phase }.distinct()
        assertEquals(listOf(ManeuverPhase.READY, ManeuverPhase.TURN_IN, ManeuverPhase.STRAIGHTEN, ManeuverPhase.HOLD_STRAIGHT), phases)
        val result = states.last().driving as? DrivingState.Result
        assertNotNull("expected a result card in P", result)
        assertTrue("score was ${result!!.score.total}", result.score.total >= 90)
        assertEquals(RewardTier.TOP_KING, result.score.tier)
        // The driver followed the ghost ring: snapped most of the time once moving.
        val coaching = states.mapNotNull { it.driving as? DrivingState.Coaching }.filter { it.target.phase != ManeuverPhase.READY }
        val snappedRatio = coaching.count { it.snapped }.toFloat() / coaching.size
        assertTrue("snapped ratio $snappedRatio", snappedRatio > 0.6f)
    }

    @Test
    fun `novice parking is penalised on several categories`() {
        val (_, states) = run(SimulatedVehicleSignalSource.NOVICE_PARKING)
        val result = states.last().driving as DrivingState.Result
        val m = result.score.metrics
        assertTrue(m.steeringReversals >= 2)
        assertEquals(2, m.directionChanges)
        assertTrue(m.maxReverseSpeedKmh > 5f)
        assertTrue("score was ${result.score.total}", result.score.total < 70)
    }

    @Test
    fun `shifting R to P without moving is not a parking attempt`() {
        val t = ParkingSessionTracker()
        t.onSignal(VehicleSignal.GearChanged(GearPosition.REVERSE, 0))
        t.onSignal(VehicleSignal.Speed(0f, 1000))
        val s = t.onSignal(VehicleSignal.GearChanged(GearPosition.PARK, 3000))
        assertTrue(s.driving is DrivingState.Driving)
    }

    @Test
    fun `driving away forward abandons the session`() {
        val t = ParkingSessionTracker()
        t.onSignal(VehicleSignal.GearChanged(GearPosition.REVERSE, 0))
        t.onSignal(VehicleSignal.Speed(2f, 1000))
        t.onSignal(VehicleSignal.GearChanged(GearPosition.DRIVE, 2000))
        t.onSignal(VehicleSignal.Speed(30f, 3000))
        assertTrue(t.isSessionActive)
        t.onSignal(VehicleSignal.Speed(30f, 8000))
        assertFalse(t.isSessionActive)
        assertTrue(t.state.driving is DrivingState.Driving)
    }

    @Test
    fun `result card stays until acknowledged and then clears`() {
        val (tracker, states) = run(SimulatedVehicleSignalSource.MODEL_PARKING)
        assertTrue(states.last().driving is DrivingState.Result)
        val still = tracker.onSignal(VehicleSignal.Speed(0f, states.last().snapshot.let { 999_999L }))
        assertTrue(still.driving is DrivingState.Result)
        tracker.acknowledgeResult()
        val after = tracker.onSignal(VehicleSignal.Speed(0f, 1_000_000L))
        assertTrue(after.driving is DrivingState.Driving)
    }

    @Test
    fun `out-of-order signals keep recorded samples monotonic`() {
        val t = ParkingSessionTracker()
        t.onSignal(VehicleSignal.GearChanged(GearPosition.REVERSE, 1_000))
        t.onSignal(VehicleSignal.Speed(3f, 1_200))
        t.onSignal(VehicleSignal.SteeringAngle(90f, 1_190)) // merged stream: arrives 10 ms late
        t.onSignal(VehicleSignal.Speed(3f, 3_400))
        val result = t.onSignal(VehicleSignal.GearChanged(GearPosition.PARK, 3_500)).driving as DrivingState.Result
        val ts = result.log.samples.map { it.timestampMs }
        assertEquals(ts.sorted(), ts)
    }
}
