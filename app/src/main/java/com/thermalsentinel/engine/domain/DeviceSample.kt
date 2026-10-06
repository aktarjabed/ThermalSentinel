package com.thermalsentinel.engine.domain

/**
 * Why a sample was taken. Persisted with every row so history can explain its
 * own density instead of looking arbitrarily noisy.
 */
enum class SampleReason(val storageValue: String) {
    /** Scheduled sample while the device was inside its normal envelope. */
    SCHEDULED_NORMAL("scheduled_normal"),

    /** Scheduled sample while a charging session was active. */
    SCHEDULED_CHARGING("scheduled_charging"),

    /** Scheduled sample taken while the alert state was escalated. */
    SCHEDULED_EVENT("scheduled_event"),

    /** The user asked for a refresh; never gated by the sampling policy. */
    USER_REFRESH("user_refresh"),

    /** The battery broadcast delivered a new value and we sampled on it. */
    BROADCAST_TRIGGERED("broadcast_triggered"),

    /** First sample after the monitoring service started. */
    SERVICE_START("service_start");

    companion object {
        fun fromStorage(value: String?): SampleReason =
            entries.firstOrNull { it.storageValue == value } ?: SCHEDULED_NORMAL
    }
}

/**
 * Sampling bookkeeping for one sample.
 *
 * [requestedIntervalMillis] is what the policy asked for;
 * [actualIntervalMillis] is what actually elapsed. They diverge under Doze,
 * under OEM battery managers, and while the CPU is asleep — the engine records
 * the truth rather than assuming the requested cadence was honoured.
 */
data class SamplingMetadata(
    val reason: SampleReason,
    val requestedIntervalMillis: Long,
    val actualIntervalMillis: Long?,
    val screenInteractive: Boolean
) {
    /**
     * A gap is an interval at least [GAP_FACTOR] times longer than requested.
     * Gaps are stored and displayed as gaps: history never interpolates across
     * them, because a straight line between two points three hours apart would
     * be invented data.
     */
    val isGap: Boolean
        get() = actualIntervalMillis != null && actualIntervalMillis > requestedIntervalMillis * GAP_FACTOR

    companion object {
        const val GAP_FACTOR = 2.5
    }
}

/**
 * One coherent measurement: when it was taken, what the battery APIs said, what
 * the thermal APIs said, and why it was taken.
 */
data class DeviceSample(
    val timestampMillis: Long,
    val battery: BatterySnapshot,
    val thermal: ThermalSnapshot,
    val sampling: SamplingMetadata
) {
    /** Convenience accessor; the DB mirrors this as a nullable column. */
    val batteryTemperatureC: Reading<Float>
        get() = battery.temperatureC
}
