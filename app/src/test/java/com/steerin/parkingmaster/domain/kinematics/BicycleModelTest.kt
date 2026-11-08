package com.steerin.parkingmaster.domain.kinematics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.tan

class BicycleModelTest {

    private val geometry = VehicleGeometry(wheelbaseM = 2.75f, steeringRatio = 14.5f, maxSteeringWheelDeg = 540f)

    @Test
    fun `straight reversing does not change heading`() {
        val m = BicycleModel(geometry)
        repeat(100) { m.step(0.05f, -1f, 0f) }
        assertEquals(0f, m.pose.headingDeg, 1e-4f)
        assertEquals(-5f, m.pose.xM, 1e-3f)
    }

    @Test
    fun `full lock reverse rotates by arc length over turning radius`() {
        val m = BicycleModel(geometry)
        val steering = 460f
        val roadWheelRad = Math.toRadians((steering / geometry.steeringRatio).toDouble())
        val radius = geometry.wheelbaseM / tan(roadWheelRad)
        val distance = 7f
        val steps = 1400
        repeat(steps) { m.step(distance / steps / 1f, -1f, steering) } // 1 m/s for `distance` metres
        val expectedDeg = Math.toDegrees(distance / radius).toFloat()
        assertEquals(expectedDeg, abs(m.pose.headingDeg), 0.5f)
    }

    @Test
    fun `right lock while reversing rotates opposite to right lock while driving forward`() {
        val fwd = BicycleModel(geometry).apply { repeat(50) { step(0.1f, 1f, 300f) } }
        val rev = BicycleModel(geometry).apply { repeat(50) { step(0.1f, -1f, 300f) } }
        assertTrue(fwd.pose.headingDeg * rev.pose.headingDeg < 0f)
    }

    @Test
    fun `steering input is clamped to mechanical lock`() {
        val a = BicycleModel(geometry).apply { repeat(20) { step(0.1f, -1f, 540f) } }
        val b = BicycleModel(geometry).apply { repeat(20) { step(0.1f, -1f, 900f) } }
        assertEquals(a.pose.headingDeg, b.pose.headingDeg, 1e-5f)
    }
}
