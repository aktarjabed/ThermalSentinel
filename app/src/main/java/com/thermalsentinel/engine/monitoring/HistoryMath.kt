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
     * Temperature change per minute across the requested window.
     *
     * Returns null when there is not enough evidence (fewer than
     * [MIN_POINTS_FOR_RATE] measured points, or a window shorter than
     * [MIN_RATE_WINDOW_MILLIS]). A single pair of samples is not a trend.
     */
    fun riseRateCPerMinute(points: List<SeriesPoint>, windowMillis: Long): Float? {
        if (windowMillis < MIN_RATE_WINDOW_MILLIS) return null
        val valued = points.filter { it.temperatureC != null }.sortedBy { it.timestampMillis }
        if (valued.size < MIN_POINTS_FOR_RATE) return null
        val newest = valued.last()
        val cutoff = newest.timestampMillis - windowMillis
        val oldest = valued.firstOrNull { it.timestampMillis >= cutoff } ?: return null
        val elapsed = newest.timestampMillis - oldest.timestampMillis
        if (elapsed < MIN_RATE_WINDOW_MILLIS) return null
        val firstValue = oldest.temperatureC ?: return null
        val lastValue = newest.temperatureC ?: return null
        return (lastValue - firstValue) / (elapsed / 60_000f)
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

    /** Rounded percentage change helper for the charging summary. */
    fun percentChange(from: Int?, to: Int?): Int? {
        if (from == null || to == null) return null
        return to - from
    }

    /** Absolute difference, exposed so UI text and tests agree. */
    fun absoluteDelta(a: Float, b: Float): Float = abs(a - b)
}
