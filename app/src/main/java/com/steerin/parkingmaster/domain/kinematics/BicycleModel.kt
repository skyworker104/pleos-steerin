package com.steerin.parkingmaster.domain.kinematics

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Vehicle geometry used by the kinematic model. Defaults approximate a mid-size Hyundai sedan/SUV.
 * CarInfo exterior dimensions can override [wheelbaseM] at runtime when the SDK provides them.
 */
data class VehicleGeometry(
    val wheelbaseM: Float = 2.75f,
    /** Steering wheel degrees per road-wheel degree. */
    val steeringRatio: Float = 14.5f,
    /** Mechanical steering wheel limit (lock) in degrees. */
    val maxSteeringWheelDeg: Float = 540f,
)

data class Pose(val xM: Float = 0f, val yM: Float = 0f, val headingRad: Float = 0f) {
    val headingDeg: Float get() = (headingRad * 180.0 / PI).toFloat()
}

/**
 * Kinematic bicycle model. Integrates steering wheel angle + signed speed into a relative pose.
 *
 * Why this matters: a third-party Pleos app has no parking-slot geometry, no GPS precise enough
 * for centimetres and no camera. What it *does* have is steering angle and speed, which are
 * enough to track how far the car has rotated since the manoeuvre started. The ghost-ring
 * coaching is driven by that relative heading change, not by absolute position.
 */
class BicycleModel(private val geometry: VehicleGeometry = VehicleGeometry()) {

    var pose: Pose = Pose()
        private set

    fun reset() {
        pose = Pose()
    }

    /**
     * @param dtSec time step
     * @param speedMps signed longitudinal speed (negative when reversing)
     * @param steeringWheelDeg steering wheel angle, positive = right
     */
    fun step(dtSec: Float, speedMps: Float, steeringWheelDeg: Float): Pose {
        if (dtSec <= 0f) return pose
        val roadWheelRad = (steeringWheelDeg.coerceIn(-geometry.maxSteeringWheelDeg, geometry.maxSteeringWheelDeg) /
            geometry.steeringRatio) * (PI / 180.0)
        val yawRate = (speedMps / geometry.wheelbaseM) * tan(roadWheelRad)
        val heading = pose.headingRad + (yawRate * dtSec).toFloat()
        val x = pose.xM + speedMps * cos(heading) * dtSec
        val y = pose.yM + speedMps * sin(heading) * dtSec
        pose = Pose(x, y, heading)
        return pose
    }
}
