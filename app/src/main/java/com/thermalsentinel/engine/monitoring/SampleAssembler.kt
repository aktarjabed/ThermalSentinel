package com.thermalsentinel.engine.monitoring

import com.thermalsentinel.engine.domain.BatterySnapshot
import com.thermalsentinel.engine.domain.DeviceSample
import com.thermalsentinel.engine.domain.SampleReason
import com.thermalsentinel.engine.domain.SamplingMetadata
import com.thermalsentinel.engine.domain.ThermalSnapshot

/** Everything the assembler needs to know about the moment of sampling. */
data class SampleRequest(
    val nowMillis: Long,
    val reason: SampleReason,
    val requestedIntervalMillis: Long,
    val previousTimestampMillis: Long?,
    val screenInteractive: Boolean
)

/**
 * Combines the three collector outputs into one [DeviceSample].
 *
 * Pure: the actual elapsed interval (and therefore gap detection) is decided
 * here from timestamps, which is what makes "the phone slept for two hours"
 * visible in history instead of silently compressing into a normal-looking pair
 * of samples.
 */
object SampleAssembler {

    fun assemble(
        request: SampleRequest,
        battery: BatterySnapshot,
        thermal: ThermalSnapshot
    ): DeviceSample = DeviceSample(
        timestampMillis = request.nowMillis,
        battery = battery,
        thermal = thermal,
        sampling = SamplingMetadata(
            reason = request.reason,
            requestedIntervalMillis = request.requestedIntervalMillis,
            actualIntervalMillis = request.previousTimestampMillis
                ?.let { previous -> (request.nowMillis - previous).coerceAtLeast(0L) },
            screenInteractive = request.screenInteractive
        )
    )
}
