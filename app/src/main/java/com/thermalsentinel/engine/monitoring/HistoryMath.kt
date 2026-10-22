package com.thermalsentinel.engine.monitoring

import kotlin.math.abs

/**
 * Pure history mathematics.
 *
 * Everything here is deliberately free of Android types, so the rules that
 * decide "was that a heating event?", "how long was the device above the
 * threshold?" and "where are the gaps?" are unit-tested rather than eyeballed
 * on a phone.
 *
 * The one invariant that runs through the whole file: **missing data is never
 * filled in.** A sample with no temperature is a hole; a series broken by a gap
 * stays broken.
 */
object HistoryMath {

    /** A point in a temperature series; a null temperature is a real hole. */
    data class SeriesPoint(val timestampMillis: Long, val temperatureC: Float?)

    /** A run of points with no gap larger than the requested maximum. */
    data class SeriesSegment(val points: List<SeriesPoint>) {
        val valued: List<SeriesPoint> get() = points.filter { it.temperatureC != null }
    }

    /** A conservative short-term linear projection toward a configured threshold. */
    data class ThresholdForecast(
        val slopeCPerMinute: Float,
        /** Zero means the fitted current temperature is already at/above the threshold. */
        val minutesToThreshold: Float
    )

    const val HEATING_RATE_C_PER_MINUTE = 0.4f
    const val COOLING_RATE_C_PER_MINUTE = -0.4f

    /** Minimum evidence before a rate is claimed at all. */
    const val MIN_POINTS_FOR_RATE = 3
    const val MIN_RATE_WINDOW_MILLIS = 3 * 60_000L

    fun seriesPoints(
        pairs: List<Pair<Long, Float?>>
    ): List<SeriesPoint> = pairs.map { SeriesPoint(it.first, it.second) }

    /**
     * Splits a series wherever consecutive points are further apart than
     * [maxGapMillis]. Charts draw each segment separately so a three-hour gap is
     * visible as a gap.
     */
    fun segments(points: List<SeriesPoint>, maxGapMillis: Long): List<SeriesSegment> {
        if (points.isEmpty()) return emptyList()
        val sorted = points.sortedBy { it.timestampMillis }
        val result = mutableListOf<SeriesSegment>()
        var current = mutableListOf(sorted.first())
        for (index in 1 until sorted.size) {
            val previous = sorted[index - 1]
            val next = sorted[index]
            if (next.timestampMillis - previous.timestampMillis > maxGapMillis) {
                result += SeriesSegment(current)
                current = mutableListOf(next)
            } else {
                current += next
            }
        }
        result += SeriesSegment(current)
        return result
    }

    fun minTemperatureC(points: List<SeriesPoint>): Float? =
        points.mapNotNull { it.temperatureC }.minOrNull()

    fun maxTemperatureC(points: List<SeriesPoint>): Float? =
        points.mapNotNull { it.temperatureC }.maxOrNull()

    fun averageTemperatureC(points: List<SeriesPoint>): Float? {
        val values = points.mapNotNull { it.temperatureC }
        if (values.isEmpty()) return null
        return values.sum() / values.size
    }

    /**
     * Time spent at or above [thresholdC], counted only across intervals where
     * **both** endpoints were measured and the pair does not straddle a gap.
     * Anything else would inflate the number with data the engine never had.
     */
    fun timeAboveThresholdMillis(
        points: List<SeriesPoint>,
        thresholdC: Float,
        maxGapMillis: Long
    ): Long {
        if (points.size < 2) return 0L
        val sorted = points.sortedBy { it.timestampMillis }
        var total = 0L
        for (index in 1 until sorted.size) {
            val previous = sorted[index - 1]
            val next = sorted[index]
            val previousValue = previous.temperatureC ?: continue
            val nextValue = next.temperatureC ?: continue
            val delta = next.timestampMillis - previous.timestampMillis
            if (delta <= 0L || delta > maxGapMillis) continue
            if (previousValue >= thresholdC && nextValue >= thresholdC) total += delta
        }
        return total
    }

    /**
     * Temperature change per minute across the requested window, as a least-squares fit
     * over the measured points in that time window. A pair of readings is not enough to
     * call a trend; require multiple readings and a minimum elapsed span.
     */
    fun riseRateCPerMinute(points: List<SeriesPoint>, windowMillis: Long): Float? {
        if (windowMillis < MIN_RATE_WINDOW_MILLIS) return null
        val valued = measuredPointsInWindow(points, windowMillis)
        if (valued.size < MIN_POINTS_FOR_RATE) return null
        if (valued.last().timestampMillis - valued.first().timestampMillis < MIN_RATE_WINDOW_MILLIS) return null
        return fitLine(valued)?.slopeCPerMinute?.toFloat()
    }

    /**
     * Short-term estimate of minutes until the fitted temperature reaches [thresholdC].
     * Missing, non-finite, duplicate-time or insufficient readings never produce an ETA.
     * A flat/falling trend is not extrapolated into a future alert.
     */
    fun forecastToThreshold(
        points: List<SeriesPoint>,
        thresholdC: Float,
        windowMillis: Long = 10 * 60_000L,
        minPoints: Int = 4,
        minSpanMillis: Long = 60_000L,
        risingSlopeCPerMinute: Float = 0.02f
    ): ThresholdForecast? {
        if (!thresholdC.isFinite() || !risingSlopeCPerMinute.isFinite()) return null
        if (windowMillis <= 0L || minPoints < 2 || minSpanMillis < 0L || risingSlopeCPerMinute < 0f) return null
        val valued = measuredPointsInWindow(points, windowMillis)
        if (valued.size < minPoints) return null
        if (valued.last().timestampMillis - valued.first().timestampMillis < minSpanMillis) return null

        val fit = fitLine(valued) ?: return null
        val minutes = when {
            fit.fittedAtLatestC >= thresholdC.toDouble() -> 0.0
            fit.slopeCPerMinute < risingSlopeCPerMinute.toDouble() -> return null
            else -> (thresholdC.toDouble() - fit.fittedAtLatestC) / fit.slopeCPerMinute
        }
        if (!minutes.isFinite() || minutes < 0.0 || minutes > Float.MAX_VALUE) return null
        return ThresholdForecast(fit.slopeCPerMinute.toFloat(), minutes.toFloat())
    }

    private data class LinearFit(
        val slopeCPerMinute: Double,
        val fittedAtLatestC: Double
    )

    private fun measuredPointsInWindow(
        points: List<SeriesPoint>,
        windowMillis: Long
    ): List<SeriesPoint> {
        if (windowMillis <= 0L) return emptyList()
        val valid = points.filter { point ->
            point.timestampMillis >= 0L && point.temperatureC?.isFinite() == true
        }.sortedBy { it.timestampMillis }
        val newest = valid.lastOrNull()?.timestampMillis ?: return emptyList()
        val cutoff = newest - windowMillis
        return valid.filter { it.timestampMillis >= cutoff }
    }

    /** Shared regression prevents the event trend and user-facing forecast from diverging. */
    private fun fitLine(points: List<SeriesPoint>): LinearFit? {
        val samples = points.mapNotNull { point ->
            val temperature = point.temperatureC ?: return@mapNotNull null
            if (!temperature.isFinite()) return@mapNotNull null
            point.timestampMillis to temperature.toDouble()
        }.sortedBy { it.first }
        if (samples.size < 2) return null

        val startTimeMillis = samples.first().first
        val xs = samples.map { (timestamp, _) -> (timestamp - startTimeMillis) / 60_000.0 }
        val ys = samples.map { (_, temperature) -> temperature }
        var sumX = 0.0
        var sumY = 0.0
        var sumXY = 0.0
        var sumXX = 0.0
        for (index in xs.indices) {
            val x = xs[index]
            val y = ys[index]
            sumX += x
            sumY += y
            sumXY += x * y
            sumXX += x * x
        }
        val n = xs.size.toDouble()
        val denominator = n * sumXX - sumX * sumX
        if (!denominator.isFinite() || denominator <= 0.0) return null
        val slope = (n * sumXY - sumX * sumY) / denominator
        val fittedLatest = (sumY / n) + slope * ((xs.last()) - (sumX / n))
        if (!slope.isFinite() || !fittedLatest.isFinite()) return null
        return LinearFit(slopeCPerMinute = slope, fittedAtLatestC = fittedLatest)
    }

    /** Rate classification used to write heating/cooling events. */
    enum class RateVerdict { HEATING, COOLING, NEUTRAL, INSUFFICIENT_DATA }

    fun classifyRate(points: List<SeriesPoint>, windowMillis: Long): RateVerdict {
        val rate = riseRateCPerMinute(points, windowMillis) ?: return RateVerdict.INSUFFICIENT_DATA
        return when {
            rate >= HEATING_RATE_C_PER_MINUTE -> RateVerdict.HEATING
            rate <= COOLING_RATE_C_PER_MINUTE -> RateVerdict.COOLING
            else -> RateVerdict.NEUTRAL
        }
    }

    /** Largest gap between consecutive points, used by Diagnostics. */
    fun largestGapMillis(points: List<SeriesPoint>): Long {
        if (points.size < 2) return 0L
        val sorted = points.sortedBy { it.timestampMillis }
        var largest = 0L
        for (index in 1 until sorted.size) {
            val delta = sorted[index].timestampMillis - sorted[index - 1].timestampMillis
            if (delta > largest) largest = delta
        }
        return largest
    }

    /**
     * Change in **percentage points** between two battery levels: 70 % → 85 % is
     * `15`, not `21.4`. This is the number the charging summary wants (a charge
     * rate over level), and it is deliberately not a relative percentage — no
     * rounding is applied here because the caller formats the value for display.
     */
    fun percentPointChange(from: Int?, to: Int?): Int? {
        if (from == null || to == null) return null
        return to - from
    }

    /** Absolute difference, exposed so UI text and tests agree. */
    fun absoluteDelta(a: Float, b: Float): Float = abs(a - b)
}
