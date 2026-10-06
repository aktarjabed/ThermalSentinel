package com.thermalsentinel.engine.monitoring

import com.thermalsentinel.engine.monitoring.HistoryMath.SeriesPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryMathTest {

    private fun point(atMillis: Long, value: Float?) = SeriesPoint(atMillis, value)

    @Test
    fun segmentsSplitAtGapsButKeepEverythingElseTogether() {
        val points = listOf(
            point(0, 35f),
            point(1_000, 36f),
            point(10_000, 37f)
        )

        val segments = HistoryMath.segments(points, maxGapMillis = 5_000)

        assertEquals(2, segments.size)
        assertEquals(2, segments[0].points.size)
        assertEquals(1, segments[1].points.size)
    }

    @Test
    fun segmentsAreTimeOrderedAndEmptyInputIsHandled() {
        val unordered = listOf(point(10_000, 37f), point(0, 35f))
        val segments = HistoryMath.segments(unordered, maxGapMillis = 60_000)

        assertEquals(1, segments.size)
        assertEquals(0L, segments.first().points.first().timestampMillis)
        assertEquals(0, HistoryMath.segments(emptyList(), 60_000).size)
    }

    @Test
    fun statisticsIgnoreHolesInsteadOfTreatingThemAsZero() {
        val points = listOf(point(0, 30f), point(60_000, null), point(120_000, 40f))

        assertEquals(30f, HistoryMath.minTemperatureC(points))
        assertEquals(40f, HistoryMath.maxTemperatureC(points))
        assertEquals(35f, HistoryMath.averageTemperatureC(points)!!, 0.001f)
        assertNull(HistoryMath.averageTemperatureC(listOf(point(0, null))))
    }

    @Test
    fun timeAboveThresholdNeedsBothEndpointsMeasured() {
        val points = listOf(
            point(0, 45f),
            point(60_000, 46f),
            point(120_000, null),
            point(180_000, 47f)
        )

        // Only the 0 → 60 000 interval has both endpoints at or above 44 °C.
        val counted = HistoryMath.timeAboveThresholdMillis(points, thresholdC = 44f, maxGapMillis = 120_000)
        assertEquals(60_000L, counted)
    }

    @Test
    fun timeAboveThresholdIgnoresIntervalsThatStraddleAGap() {
        val points = listOf(point(0, 45f), point(3_600_000, 46f))

        val counted = HistoryMath.timeAboveThresholdMillis(points, thresholdC = 44f, maxGapMillis = 300_000)
        assertEquals(0L, counted)
    }

    @Test
    fun timeAboveThresholdCountsNothingWhenEitherSideIsBelow() {
        val points = listOf(point(0, 45f), point(60_000, 40f))

        assertEquals(
            0L,
            HistoryMath.timeAboveThresholdMillis(points, thresholdC = 44f, maxGapMillis = 120_000)
        )
    }

    @Test
    fun riseRateNeedsEnoughPointsAndALongEnoughWindow() {
        val points = listOf(
            point(0, 30f),
            point(60_000, 31f),
            point(120_000, 32f),
            point(180_000, 33f)
        )

        val rate = HistoryMath.riseRateCPerMinute(points, windowMillis = 180_000)
        assertEquals(1.0f, rate!!, 0.001f)
        assertEquals(HistoryMath.RateVerdict.HEATING, HistoryMath.classifyRate(points, 180_000))

        // Two points is not a trend, and neither is a 30-second window.
        assertNull(HistoryMath.riseRateCPerMinute(points.take(2), windowMillis = 180_000))
        assertNull(HistoryMath.riseRateCPerMinute(points, windowMillis = 30_000))
        assertEquals(
            HistoryMath.RateVerdict.INSUFFICIENT_DATA,
            HistoryMath.classifyRate(points.take(2), 180_000)
        )
    }

    @Test
    fun riseRateClassifiesCoolingAndNeutral() {
        val cooling = listOf(
            point(0, 40f),
            point(60_000, 39f),
            point(120_000, 38f),
            point(180_000, 37f)
        )
        assertEquals(HistoryMath.RateVerdict.COOLING, HistoryMath.classifyRate(cooling, 180_000))

        val steady = listOf(
            point(0, 38f),
            point(60_000, 38.1f),
            point(120_000, 38.0f),
            point(180_000, 38.1f)
        )
        assertEquals(HistoryMath.RateVerdict.NEUTRAL, HistoryMath.classifyRate(steady, 180_000))
    }

    @Test
    fun riseRateFitsEveryPointInTheWindowNotJustTheEndpoints() {
        // Same two endpoints (30 °C → 33 °C over three minutes), different
        // interior evidence. An endpoint slope answers 1.0 for both; a fit over
        // the window can tell a mid-window spike from a genuine late climb.
        val spikeInTheMiddle = listOf(
            point(0, 30f), point(60_000, 34f), point(120_000, 30f), point(180_000, 33f)
        )
        val climbAtTheEnd = listOf(
            point(0, 30f), point(60_000, 30f), point(120_000, 34f), point(180_000, 33f)
        )

        assertEquals(
            0.5f,
            HistoryMath.riseRateCPerMinute(spikeInTheMiddle, windowMillis = 180_000)!!,
            0.001f
        )
        assertEquals(
            1.3f,
            HistoryMath.riseRateCPerMinute(climbAtTheEnd, windowMillis = 180_000)!!,
            0.001f
        )
    }

    @Test
    fun riseRateIgnoresSamplesOutsideTheWindow() {
        // One reading from well before the window must not anchor the slope: only
        // the four in-window points describe the last ten minutes. Fitting all five
        // would answer 0.62 °C/min instead of 0.5 — a stale sample would be read
        // as heat the device is not producing right now.
        val points = listOf(
            point(0, 20f),
            point(40 * 60_000L, 45f),
            point(41 * 60_000L, 45.5f),
            point(42 * 60_000L, 46f),
            point(43 * 60_000L, 46.5f)
        )

        val rate = HistoryMath.riseRateCPerMinute(points, windowMillis = 600_000)!!
        assertEquals(0.5f, rate, 0.001f)
    }

    @Test
    fun riseRateRefusesAWindowThatSpansLessThanThreeMinutes() {
        // Enough points, but no elapsed time to call a rate: three readings inside
        // a two-minute stretch. The evidence gate is a span, not a row count,
        // because a fast sampling cadence can supply any number of rows in seconds.
        val points = listOf(
            point(0, 45f),
            point(30_000, 45.5f),
            point(60_000, 46f),
            point(90_000, 46.5f),
            point(120_000, 47f)
        )

        assertNull(HistoryMath.riseRateCPerMinute(points, windowMillis = 600_000))
    }

    @Test
    fun riseRateReturnsNullWhenTheWindowHasNoTimeSpan() {
        // Three readings at the same instant: enough points, no time axis.
        val sameInstant = listOf(
            point(60_000, 30f), point(60_000, 31f), point(60_000, 32f)
        )
        assertNull(HistoryMath.riseRateCPerMinute(sameInstant, windowMillis = 180_000))
    }

    @Test
    fun largestGapAndPercentPointChange() {
        val points = listOf(point(0, 35f), point(60_000, 36f), point(600_000, 37f))
        assertEquals(540_000L, HistoryMath.largestGapMillis(points))
        assertEquals(0L, HistoryMath.largestGapMillis(listOf(point(0, 35f))))

        // Percentage *points*, not a relative percentage: 70 → 85 is +15.
        assertEquals(15, HistoryMath.percentPointChange(70, 85))
        assertEquals(-4, HistoryMath.percentPointChange(85, 81))
        assertNull(HistoryMath.percentPointChange(null, 85))
        assertNull(HistoryMath.percentPointChange(70, null))
    }
}
