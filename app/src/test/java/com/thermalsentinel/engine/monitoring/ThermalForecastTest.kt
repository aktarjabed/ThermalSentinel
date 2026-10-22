package com.thermalsentinel.engine.monitoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThermalForecastTest {
    private fun points(vararg temperatures: Float?): List<HistoryMath.SeriesPoint> =
        temperatures.mapIndexed { index, temperature ->
            HistoryMath.SeriesPoint(index * 60_000L, temperature)
        }

    @Test
    fun risingSeriesEstimatesMinutesToThreshold() {
        val forecast = HistoryMath.forecastToThreshold(points(35f, 36f, 37f, 38f), thresholdC = 43f)
        requireNotNull(forecast)
        assertEquals(1f, forecast.slopeCPerMinute, 0.001f)
        assertEquals(5f, forecast.minutesToThreshold, 0.01f)
    }

    @Test
    fun alreadyAtOrAboveFittedThresholdReturnsZero() {
        val forecast = HistoryMath.forecastToThreshold(points(40f, 41f, 42f, 43f), thresholdC = 42f)
        requireNotNull(forecast)
        assertEquals(0f, forecast.minutesToThreshold, 0f)
    }

    @Test
    fun flatOrCoolingSeriesDoesNotInventEta() {
        assertNull(HistoryMath.forecastToThreshold(points(40f, 40f, 40f, 40f), 45f))
        assertNull(HistoryMath.forecastToThreshold(points(43f, 42f, 41f, 40f), 45f))
    }

    @Test
    fun missingNonFiniteAndInsufficientReadingsAreNotForecast() {
        assertNull(HistoryMath.forecastToThreshold(points(35f, null, Float.NaN, 38f), 45f))
        assertNull(HistoryMath.forecastToThreshold(points(35f, 36f, 37f), 45f))
        assertNull(HistoryMath.forecastToThreshold(points(35f, 36f, 37f, 38f), Float.NaN))
    }

    @Test
    fun repeatedTimestampCannotProduceSlope() {
        val values = listOf(
            HistoryMath.SeriesPoint(1_000L, 35f),
            HistoryMath.SeriesPoint(1_000L, 36f),
            HistoryMath.SeriesPoint(1_000L, 37f),
            HistoryMath.SeriesPoint(1_000L, 38f)
        )
        assertNull(HistoryMath.forecastToThreshold(values, 45f))
    }
}
