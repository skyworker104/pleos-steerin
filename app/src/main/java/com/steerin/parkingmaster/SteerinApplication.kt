package com.steerin.parkingmaster

import android.app.Application
import android.util.Log
import com.steerin.parkingmaster.data.vehicle.PleosVehicleSignalSource
import com.steerin.parkingmaster.data.vehicle.SimulatedVehicleSignalSource
import com.steerin.parkingmaster.data.vehicle.VehicleSignalSource

/**
 * Minimal service locator. Picks the Pleos Vehicle SDK when a Vehicle Service is installed
 * (Pleos Connect AVD / vehicle) and the built-in simulator everywhere else.
 */
class SteerinApplication : Application() {

    lateinit var signalSource: VehicleSignalSource
        private set

    /** Non-null only when running in simulation; the demo panel binds to it. */
    var simulator: SimulatedVehicleSignalSource? = null
        private set

    override fun onCreate() {
        super.onCreate()
        signalSource = if (PleosVehicleSignalSource.isAvailable(this)) {
            Log.i(TAG, "Pleos Vehicle Service detected → using Vehicle SDK")
            PleosVehicleSignalSource(this)
        } else {
            Log.i(TAG, "No Vehicle Service → using simulator")
            SimulatedVehicleSignalSource().also { simulator = it }
        }
    }

    companion object {
        private const val TAG = "SteerinApp"
    }
}
