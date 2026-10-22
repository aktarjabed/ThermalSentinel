package com.thermalsentinel.engine.monitoring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundLoggingPolicyTest {
    @Test
    fun neverLogsAlongsideForegroundMonitoring() {
        assertFalse(BackgroundLoggingPolicy.shouldSample(true, null, 10_000L))
    }

    @Test
    fun skipsRecentSampleButLogsStaleOrMissingHistory() {
        assertFalse(BackgroundLoggingPolicy.shouldSample(false, 800_000L, 1_000_000L))
        assertTrue(BackgroundLoggingPolicy.shouldSample(false, null, 1_000_000L))
        assertTrue(
            BackgroundLoggingPolicy.shouldSample(
                false, 1L, BackgroundLoggingPolicy.RECENT_SAMPLE_WINDOW_MILLIS + 1L
            )
        )
    }

    @Test
    fun futureTimestampFailsClosedAfterClockChange() {
        assertFalse(BackgroundLoggingPolicy.shouldSample(false, 2_000L, 1_000L))
    }
}
