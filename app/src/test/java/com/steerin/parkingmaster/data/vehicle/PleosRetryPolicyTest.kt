package com.steerin.parkingmaster.data.vehicle

import org.junit.Assert.assertEquals
import org.junit.Test

class PleosRetryPolicyTest {
    @Test
    fun `initialize retry backs off exponentially and caps at 30 s`() {
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L),
            (0..6).map { PleosVehicleSignalSource.retryDelayMs(it) })
    }
}
