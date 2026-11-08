package com.steerin.parkingmaster.domain.session

import com.steerin.parkingmaster.data.vehicle.DoorSlot
import com.steerin.parkingmaster.data.vehicle.GearPosition
import com.steerin.parkingmaster.data.vehicle.VehicleSignal
import com.steerin.parkingmaster.domain.model.SessionLog

/**
 * Turns a recorded [SessionLog] back into the signal stream the tracker originally consumed, so a
 * saved session can be replayed through a fresh [ParkingSessionTracker] (same ghost ring, same
 * phases, same score) without touching the vehicle.
 */
object SessionReplayer {

    fun toSignals(log: SessionLog): List<VehicleSignal> {
        val out = ArrayList<VehicleSignal>(log.samples.size * 2)
        var lastGear: GearPosition? = null
        var lastSteer: Float? = null
        var lastBrake: Boolean? = null
        var lastDoor: Boolean? = null
        for (s in log.samples) {
            val ts = s.timestampMs
            if (s.gear != lastGear) { out += VehicleSignal.GearChanged(s.gear, ts); lastGear = s.gear }
            if (s.brakePressed != lastBrake) { out += VehicleSignal.BrakePedal(s.brakePressed, ts); lastBrake = s.brakePressed }
            if (s.anyDoorOpen != lastDoor) { out += VehicleSignal.DoorState(DoorSlot.TRUNK, s.anyDoorOpen, ts); lastDoor = s.anyDoorOpen }
            if (s.steeringDeg != lastSteer) { out += VehicleSignal.SteeringAngle(s.steeringDeg, ts); lastSteer = s.steeringDeg }
            out += VehicleSignal.Speed(s.speedKmh, ts)
        }
        return out
    }
}
