package com.thermalsentinel.engine.monitoring

/** Pure decision rule for sparse, opt-in background samples. */
object BackgroundLoggingPolicy {
    const val PERIODIC_INTERVAL_MILLIS = 15 * 60_000L
    const val RECENT_SAMPLE_WINDOW_MILLIS = 5 * 60_000L

    /** Future timestamps fail closed, so wall-clock adjustments never create a rapid loop. */
    fun shouldSample(
        monitoringRunning: Boolean,
        latestSampleMillis: Long?,
        nowMillis: Long
    ): Boolean {
        if (monitoringRunning) return false
        if (latestSampleMillis == null) return true
        if (latestSampleMillis > nowMillis) return false
        return nowMillis - latestSampleMillis >= RECENT_SAMPLE_WINDOW_MILLIS
    }
}
