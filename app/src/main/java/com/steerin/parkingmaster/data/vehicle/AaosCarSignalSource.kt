package com.steerin.parkingmaster.data.vehicle

import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Standard Android Automotive Car API path, used for the signals the Pleos Vehicle SDK does not
 * expose (vehicle speed) and as a fallback for gear.
 *
 * `android.car` is an optional platform library: it exists on AAOS/Pleos images and is missing on
 * phones. Everything here is wrapped so a missing class simply disables the source.
 */
class AaosCarSignalSource(private val context: Context) {

    private val _signals = MutableSharedFlow<VehicleSignal>(extraBufferCapacity = 128, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val signals: Flow<VehicleSignal> = _signals.asSharedFlow()

    @Volatile var speedAvailable: Boolean = false
        private set

    /** True once GEAR_SELECTION is subscribed. Gear is read from AAOS first because the Pleos Gear
     *  domain is deprecated in SDK 2.3.2 in favour of Google's GEAR_SELECTION property. */
    @Volatile var gearAvailable: Boolean = false
        private set

    private var car: Car? = null
    private var propertyManager: CarPropertyManager? = null

    // Lazy so that merely constructing this class never loads android.car types on a phone.
    private val speedCallback by lazy { object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            val mps = (value.value as? Float) ?: return
            _signals.tryEmit(VehicleSignal.Speed(kotlin.math.abs(mps) * 3.6f, SystemClock.elapsedRealtime()))
        }
        override fun onErrorEvent(propId: Int, zone: Int) {
            Log.w(TAG, "PERF_VEHICLE_SPEED error prop=$propId zone=$zone")
        }
    } }

    private val gearCallback by lazy { object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            (value.value as? Int)?.let { emitGear(it) }
        }
        override fun onErrorEvent(propId: Int, zone: Int) = Unit
    } }

    private fun emitGear(gear: Int) {
        val mapped = when (gear) {
            VehicleGear.GEAR_PARK -> GearPosition.PARK
            VehicleGear.GEAR_REVERSE -> GearPosition.REVERSE
            VehicleGear.GEAR_NEUTRAL -> GearPosition.NEUTRAL
            VehicleGear.GEAR_UNKNOWN -> GearPosition.UNKNOWN
            else -> GearPosition.DRIVE
        }
        _signals.tryEmit(VehicleSignal.GearChanged(mapped, SystemClock.elapsedRealtime()))
    }

    fun start() {
        if (!isCarLibraryPresent(context)) {
            Log.i(TAG, "android.car not present; speed source disabled")
            return
        }
        try {
            val c = Car.createCar(context) ?: return
            car = c
            val pm = c.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager ?: return
            propertyManager = pm
            if (context.checkSelfPermission(Car.PERMISSION_SPEED) == PackageManager.PERMISSION_GRANTED) {
                speedAvailable = pm.registerCallback(speedCallback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_FAST)
            } else {
                Log.w(TAG, "CAR_SPEED permission not granted; speed unavailable")
            }
            if (context.checkSelfPermission(Car.PERMISSION_POWERTRAIN) == PackageManager.PERMISSION_GRANTED) {
                gearAvailable = pm.registerCallback(gearCallback, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
                if (gearAvailable) {
                    runCatching { pm.getIntProperty(VehiclePropertyIds.GEAR_SELECTION, 0) }
                        .onSuccess { emitGear(it) }
                }
            } else {
                Log.w(TAG, "CAR_POWERTRAIN permission not granted; falling back to Pleos Gear domain")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Car API unavailable: ${t.message}")
        }
    }

    fun stop() {
        runCatching { propertyManager?.unregisterCallback(speedCallback) }
        runCatching { propertyManager?.unregisterCallback(gearCallback) }
        runCatching { car?.disconnect() }
        car = null
        propertyManager = null
        speedAvailable = false
        gearAvailable = false
    }

    companion object {
        private const val TAG = "AaosCarSource"

        fun isCarLibraryPresent(context: Context): Boolean =
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE) &&
                runCatching { Class.forName("android.car.Car") }.isSuccess
    }
}
