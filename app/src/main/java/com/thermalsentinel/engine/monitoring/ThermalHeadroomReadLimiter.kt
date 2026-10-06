package com.thermalsentinel.engine.monitoring

/**
 * Process-local rate limiter for `PowerManager.getThermalHeadroom`.
 *
 * The caller supplies a monotonic elapsed-realtime timestamp. Android advises
 * against polling thermal headroom more often than once every 10 seconds, and
 * also warns against calling it from multiple threads. The synchronized gate
 * gives the shared thermal collector one serialized read path for monitoring and
 * diagnostics alike.
 */
class ThermalHeadroomReadLimiter(
    private val minimumIntervalMillis: Long = DEFAULT_MINIMUM_INTERVAL_MILLIS
) {
    init {
        require(minimumIntervalMillis > 0L) { "minimumIntervalMillis must be positive" }
    }

    private var lastReadAttemptMillis: Long? = null

    /**
     * Returns true when a platform read may start at [nowElapsedRealtimeMillis].
     * A backwards clock movement fails closed until the previous timestamp is
     * reached; this prevents accidental rapid reads if a non-monotonic clock is
     * passed by mistake.
     */
    @Synchronized
    fun tryAcquire(nowElapsedRealtimeMillis: Long): Boolean {
        val previous = lastReadAttemptMillis
        if (previous != null) {
            val elapsed = nowElapsedRealtimeMillis - previous
            if (elapsed < minimumIntervalMillis) return false
        }
        lastReadAttemptMillis = nowElapsedRealtimeMillis
        return true
    }

    companion object {
        const val DEFAULT_MINIMUM_INTERVAL_MILLIS = 10_000L
    }
}
