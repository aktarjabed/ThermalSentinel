package com.thermalsentinel.engine.monitoring

import com.thermalsentinel.engine.alert.AlertLevel
import com.thermalsentinel.engine.domain.SampleReason
import com.thermalsentinel.engine.domain.ThermalStatusBand

/** Inputs that decide the next sampling delay. All of them are cheap to obtain. */
data class SamplingContext(
    val alertLevel: AlertLevel,
    val onExternalPower: Boolean,
    /** `PowerManager.isInteractive()` — the screen state, not the device state. */
    val screenInteractive: Boolean,
    val thermalStatus: ThermalStatusBand?,
    val batteryPercent: Int?
)

/**
 * The cadence decision plus the sentence that explains it. Diagnostics shows the
 * explanation verbatim, so the sampling policy can never look arbitrary.
 */
data class SamplingDecision(
    val intervalMillis: Long,
    val reason: SampleReason,
    val explanation: String
)

/**
 * Adaptive sampling policy.
 *
 * The intervals below are a *starting calibration*, not a measured optimum. The
 * spec records the benchmark procedure that must confirm them on real hardware
 * (thermal-engine cost, wakeups per hour, and history fidelity). The 5-second
 * floor is for battery/status sampling in critical conditions. Thermal headroom
 * has a separate process-wide limiter in [ThermalHeadroomReadLimiter] and is
 * never polled more often than once every 10 seconds.
 */
object SamplingPolicy {

    /** Screen on, nothing unusual happening. */
    const val NORMAL_INTERVAL_MS = 30_000L

    /** Screen off, unplugged: sampling still happens, just less often. */
    const val SCREEN_OFF_INTERVAL_MS = 60_000L

    /** On external power: charge heat moves faster than idle heat. */
    const val CHARGING_INTERVAL_MS = 15_000L

    /** Alert state warning, or a moderate platform thermal band. */
    const val ELEVATED_INTERVAL_MS = 10_000L

    /** Alert state critical, or a severe-or-worse platform thermal band. */
    const val EVENT_INTERVAL_MS = 5_000L

    /** Low battery while unplugged: monitoring continues, more cheaply. */
    const val BATTERY_SAVER_INTERVAL_MS = 90_000L

    /** Absolute floor. See the class comment. */
    const val MIN_INTERVAL_MS = 5_000L

    /** Battery percentage at or below which the battery-saver cadence applies. */
    const val LOW_BATTERY_PERCENT = 15

    fun decide(context: SamplingContext): SamplingDecision {
        if (context.alertLevel == AlertLevel.CRITICAL) {
            return SamplingDecision(
                EVENT_INTERVAL_MS,
                SampleReason.SCHEDULED_EVENT,
                "Alert state is critical, so sampling is at its fastest cadence."
            )
        }

        val statusLevel = context.thermalStatus?.takeIf { it.isPlatformReported }?.androidLevel

        // SEVERE means the platform itself has started throttling, which is the
        // point at which five-second sampling stops being over-sampling. The
        // threshold is not CRITICAL: waiting until CRITICAL would miss the
        // throttling ramp that the history is most useful for.
        if (statusLevel != null && statusLevel >= ThermalStatusBand.SEVERE.androidLevel) {
            return SamplingDecision(
                EVENT_INTERVAL_MS,
                SampleReason.SCHEDULED_EVENT,
                "Android reports thermal status ${context.thermalStatus.label} or worse."
            )
        }

        if (context.alertLevel == AlertLevel.WARNING || context.alertLevel == AlertLevel.RECOVERY) {
            val explanation = if (context.alertLevel == AlertLevel.RECOVERY) {
                "Alert state is recovering, so sampling remains accelerated until the recovery threshold is confirmed."
            } else {
                "Alert state is warning, so sampling is accelerated."
            }
            return SamplingDecision(
                ELEVATED_INTERVAL_MS,
                SampleReason.SCHEDULED_EVENT,
                explanation
            )
        }

        if (statusLevel != null && statusLevel >= ThermalStatusBand.MODERATE.androidLevel) {
            return SamplingDecision(
                ELEVATED_INTERVAL_MS,
                SampleReason.SCHEDULED_EVENT,
                "Android reports thermal status ${context.thermalStatus.label}."
            )
        }

        if (context.onExternalPower) {
            return SamplingDecision(
                CHARGING_INTERVAL_MS,
                SampleReason.SCHEDULED_CHARGING,
                "Charging, so sampling follows the charging cadence."
            )
        }

        if (!context.screenInteractive) {
            return SamplingDecision(
                SCREEN_OFF_INTERVAL_MS,
                SampleReason.SCHEDULED_NORMAL,
                "Screen is off and the device is on battery, so sampling is relaxed."
            )
        }

        val percent = context.batteryPercent
        if (percent != null && percent <= LOW_BATTERY_PERCENT) {
            return SamplingDecision(
                BATTERY_SAVER_INTERVAL_MS,
                SampleReason.SCHEDULED_NORMAL,
                "Battery is at $percent%, so sampling is relaxed to reduce its own impact."
            )
        }

        return SamplingDecision(
            NORMAL_INTERVAL_MS,
            SampleReason.SCHEDULED_NORMAL,
            "Normal cadence."
        )
    }

    /** Convenience for the service loop. */
    fun intervalMillis(context: SamplingContext): Long = decide(context).intervalMillis.coerceAtLeast(MIN_INTERVAL_MS)
}
