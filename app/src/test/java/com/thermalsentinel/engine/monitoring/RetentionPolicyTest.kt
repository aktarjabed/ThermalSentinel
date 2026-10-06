package com.thermalsentinel.engine.monitoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetentionPolicyTest {

    @Test
    fun bucketStartsAreFloorAligned() {
        val hour = RetentionPolicy.HOUR_MILLIS
        assertEquals(hour, RetentionPolicy.bucketStart(hour + 1, hour))
        assertEquals(0L, RetentionPolicy.bucketStart(hour - 1, hour))
        assertEquals(hour * 3, RetentionPolicy.bucketStart(hour * 3 + 59_999, hour))

        val day = RetentionPolicy.DAY_MILLIS
        assertEquals(day * 2, RetentionPolicy.bucketStart(day * 2 + 12 * hour, day))
    }

    @Test
    fun cutoffsSitExactlyOneRetentionWindowBehindNow() {
        val now = 1_700_000_000_000L
        assertEquals(
            now - RetentionPolicy.RAW_RETENTION_DAYS * RetentionPolicy.DAY_MILLIS,
            RetentionPolicy.rawCutoffMillis(now)
        )
        assertEquals(
            now - RetentionPolicy.HOURLY_RETENTION_DAYS * RetentionPolicy.DAY_MILLIS,
            RetentionPolicy.hourlyCutoffMillis(now)
        )
        assertEquals(
            now - RetentionPolicy.DAILY_RETENTION_DAYS * RetentionPolicy.DAY_MILLIS,
            RetentionPolicy.dailyCutoffMillis(now)
        )
    }

    @Test
    fun theLadderNarrowsFromRawToHourlyToDaily() {
        val now = 1_700_000_000_000L
        assertTrue(RetentionPolicy.dailyCutoffMillis(now) < RetentionPolicy.hourlyCutoffMillis(now))
        assertTrue(RetentionPolicy.hourlyCutoffMillis(now) < RetentionPolicy.rawCutoffMillis(now))
        assertEquals(7, RetentionPolicy.RAW_RETENTION_DAYS)
        assertEquals(90, RetentionPolicy.HOURLY_RETENTION_DAYS)
        assertEquals(400, RetentionPolicy.DAILY_RETENTION_DAYS)
    }

    @Test
    fun expectedSampleCountGuardsAgainstDivisionByZero() {
        assertEquals(2L, RetentionPolicy.expectedSamples(60_000L, 30_000L))
        assertEquals(0L, RetentionPolicy.expectedSamples(60_000L, 0L))
        assertEquals(0L, RetentionPolicy.expectedSamples(0L, 30_000L))
    }
}
