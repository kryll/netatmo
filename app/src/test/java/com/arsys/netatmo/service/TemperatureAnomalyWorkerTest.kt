package com.arsys.netatmo.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemperatureAnomalyWorkerTest {

    // Rule confirmed by José (31 oct 2026): alert only when the room is BELOW the
    // setpoint, never above it. Threshold stays the configurable setting (default 3.0).

    @Test
    fun `below the setpoint by more than the threshold alerts`() {
        assertTrue(shouldAlertTemperatureAnomaly(16.9, 20.0, 3.0))
    }

    @Test
    fun `below the setpoint within the threshold does not alert`() {
        assertFalse(shouldAlertTemperatureAnomaly(17.5, 20.0, 3.0))
    }

    @Test
    fun `exactly at threshold distance does not alert`() {
        assertFalse(shouldAlertTemperatureAnomaly(17.0, 20.0, 3.0))
    }

    @Test
    fun `above the setpoint never alerts`() {
        assertFalse(shouldAlertTemperatureAnomaly(21.1, 17.0, 3.0))
        assertFalse(shouldAlertTemperatureAnomaly(25.0, 20.0, 3.0))
        assertFalse(shouldAlertTemperatureAnomaly(23.5, 20.0, 0.5))
    }

    @Test
    fun `at the setpoint does not alert`() {
        assertFalse(shouldAlertTemperatureAnomaly(20.0, 20.0, 3.0))
    }
}
