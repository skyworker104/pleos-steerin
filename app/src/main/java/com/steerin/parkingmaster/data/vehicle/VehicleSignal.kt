package com.steerin.parkingmaster.data.vehicle

/** Gear position normalised from Pleos `VehicleGear` / AAOS `GEAR_SELECTION`. */
enum class GearPosition { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

/** Door/trunk slots we care about during a parking manoeuvre. */
enum class DoorSlot { FRONT_LEFT, FRONT_RIGHT, REAR_LEFT, REAR_RIGHT, TRUNK, HOOD }

/**
 * A single vehicle signal update.
 *
 * All timestamps are monotonic milliseconds from the source (SystemClock.elapsedRealtime on
 * device, a virtual clock in simulation/tests).
 */
sealed interface VehicleSignal {
    val timestampMs: Long

    /** Steering wheel angle in degrees. Positive = clockwise (right), negative = left. */
    data class SteeringAngle(val degrees: Float, override val timestampMs: Long) : VehicleSignal

    data class GearChanged(val gear: GearPosition, override val timestampMs: Long) : VehicleSignal

    /** Vehicle speed in km/h (always >= 0; direction comes from gear). */
    data class Speed(val kmh: Float, override val timestampMs: Long) : VehicleSignal

    data class BrakePedal(val pressed: Boolean, override val timestampMs: Long) : VehicleSignal

    data class DoorState(val door: DoorSlot, val open: Boolean, override val timestampMs: Long) : VehicleSignal
}
