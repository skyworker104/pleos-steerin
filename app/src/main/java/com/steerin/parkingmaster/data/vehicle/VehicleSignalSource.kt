package com.steerin.parkingmaster.data.vehicle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Where the signals are coming from, surfaced in the UI so judges can see the data path. */
enum class SourceKind { PLEOS_VEHICLE_SDK, SIMULATION }

data class SourceStatus(
    val kind: SourceKind,
    val connected: Boolean = false,
    val steeringAvailable: Boolean = false,
    val gearAvailable: Boolean = false,
    val speedAvailable: Boolean = false,
    val brakeAvailable: Boolean = false,
    val doorAvailable: Boolean = false,
    val detail: String = "",
)

/**
 * Abstraction over the vehicle data path. The production implementation wraps the Pleos Vehicle
 * SDK (+ AAOS CarPropertyManager for speed); the simulation implementation replays scripted
 * manoeuvres so the whole app can be demonstrated on any Android device or emulator.
 */
interface VehicleSignalSource {
    val status: StateFlow<SourceStatus>
    val signals: Flow<VehicleSignal>
    fun start()
    fun stop()
}
