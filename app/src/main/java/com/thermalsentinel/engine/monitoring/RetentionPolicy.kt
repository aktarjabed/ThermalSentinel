package com.thermalsentinel.engine.monitoring

/**
 * Retention ladder for local history.
 *
 * The database keeps three resolutions so that a 30-day chart does not require
 * 30 days of 15-second rows, and so that the app can state exactly what it keeps
 * and for how long:
 *
 *  - **Raw samples** for [RAW_RETENTION_DAYS] (7 days). This is what the 24 h
 *    chart and the "heating event" detection use.
 *  - **Hourly buckets** for [HOURLY_RETENTION_DAYS] (90 days), produced by
 *    compacting raw samples as they age out.
 *  - **Daily buckets** for [DAILY_RETENTION_DAYS] (400 days), produced by
 *    compacting hourly buckets.
 *
 * Nothing is uploaded; the whole ladder lives in the app's private database.
 */
object RetentionPolicy {

    const val RAW_RETENTION_DAYS = 7
    const val HOURLY_RETENTION_DAYS = 90
    const val DAILY_RETENTION_DAYS = 400

    const val MINUTE_MILLIS = 60_000L
    const val HOUR_MILLIS = 60 * MINUTE_MILLIS
    const val DAY_MILLIS = 24 * HOUR_MILLIS

    /** Bucket widths stored in `thermal_aggregates.bucketMillis`. */
    const val HOURLY_BUCKET_MILLIS = HOUR_MILLIS
    const val DAILY_BUCKET_MILLIS = DAY_MILLIS

    /** Raw samples older than this are compacted, then deleted. */
    fun rawCutoffMillis(nowMillis: Long): Long = nowMillis - RAW_RETENTION_DAYS * DAY_MILLIS

    /** Hourly buckets older than this are compacted into daily buckets. */
    fun hourlyCutoffMillis(nowMillis: Long): Long = nowMillis - HOURLY_RETENTION_DAYS * DAY_MILLIS

    /** Daily buckets older than this are deleted. */
    fun dailyCutoffMillis(nowMillis: Long): Long = nowMillis - DAILY_RETENTION_DAYS * DAY_MILLIS

    /** Start of the bucket that contains [timestampMillis]. */
    fun bucketStart(timestampMillis: Long, bucketMillis: Long): Long =
        timestampMillis - (timestampMillis % bucketMillis)

    /**
     * Expected rows for a window at a given cadence. Used by Diagnostics to tell
     * the user whether a sparse history is explained by cadence or by gaps in
     * monitoring — never to fabricate missing rows.
     */
    fun expectedSamples(windowMillis: Long, intervalMillis: Long): Long =
        if (intervalMillis <= 0L) 0L else windowMillis / intervalMillis
}
