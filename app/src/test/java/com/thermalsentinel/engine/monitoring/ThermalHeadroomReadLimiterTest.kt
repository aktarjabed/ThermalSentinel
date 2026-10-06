package com.thermalsentinel.engine.monitoring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalHeadroomReadLimiterTest {

    @Test
    fun firstReadIsAllowedImmediately() {
        val limiter = ThermalHeadroomReadLimiter()

        assertTrue(limiter.tryAcquire(0L))
    }

    @Test
    fun callsBeforeTenSecondsAreDenied() {
        val limiter = ThermalHeadroomReadLimiter()

        assertTrue(limiter.tryAcquire(10_000L))
        assertFalse(limiter.tryAcquire(10_001L))
        assertFalse(limiter.tryAcquire(19_999L))
    }

    @Test
    fun nextReadIsAllowedAtTheTenSecondBoundary() {
        val limiter = ThermalHeadroomReadLimiter()

        assertTrue(limiter.tryAcquire(10_000L))
        assertTrue(limiter.tryAcquire(20_000L))
    }

    @Test
    fun backwardsClockMovementFailsClosed() {
        val limiter = ThermalHeadroomReadLimiter()

        assertTrue(limiter.tryAcquire(50_000L))
        assertFalse(limiter.tryAcquire(49_000L))
        assertFalse(limiter.tryAcquire(59_999L))
        assertTrue(limiter.tryAcquire(60_000L))
    }
}
