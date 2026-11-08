package com.steerin.parkingmaster.presentation.ux

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.util.Log

/**
 * Bridges AAOS `CarUxRestrictionsManager` to a simple boolean: "is the driver currently
 * restricted (vehicle moving)?". The app hides the demo panel and any secondary text while true,
 * as the Pleos app review guide requires even for distraction-optimized apps.
 *
 * Safe to construct on a phone: [start] checks for the optional library first.
 */
class UxRestrictionsMonitor(
    private val context: Context,
    private val onChanged: (restricted: Boolean) -> Unit,
) {
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null

    private val listener by lazy {
        CarUxRestrictionsManager.OnUxRestrictionsChangedListener { r: CarUxRestrictions ->
            onChanged(r.isRequiresDistractionOptimization)
        }
    }

    fun start() {
        if (runCatching { Class.forName("android.car.Car") }.isFailure) return
        try {
            val c = Car.createCar(context) ?: return
            car = c
            val m = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager ?: return
            manager = m
            m.registerListener(listener)
            m.currentCarUxRestrictions?.let { onChanged(it.isRequiresDistractionOptimization) }
        } catch (t: Throwable) {
            Log.w(TAG, "UX restrictions unavailable: ${t.message}")
        }
    }

    fun stop() {
        runCatching { manager?.unregisterListener() }
        runCatching { car?.disconnect() }
        manager = null
        car = null
    }

    companion object {
        private const val TAG = "UxRestrictions"
    }
}
