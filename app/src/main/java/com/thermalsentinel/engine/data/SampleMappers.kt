package com.thermalsentinel.engine.data

import com.thermalsentinel.engine.domain.*
import com.thermalsentinel.engine.domain.PlatformValues.celsiusToDeciCelsius
import com.thermalsentinel.engine.domain.PlatformValues.deciCelsiusToCelsius
import com.thermalsentinel.engine.domain.PlatformValues.headroomToMilli
import com.thermalsentinel.engine.domain.PlatformValues.milliToHeadroom

/**
 * Domain <-> Room mapping.
 *
 * Two rules are worth stating explicitly because they are easy to get wrong:
 *
 *  1. A null column becomes `Absent(NOT_RECORDED)`, never `Present(0)`.
 *  2. Static device strings (`technology`) are not persisted at all; they are a
 *     property of the battery, not of a moment in time, and the live view reads
 *     them from the platform.
 */
object SampleMappers {

    fun toEntity(sample: DeviceSample): ThermalSampleEntity = ThermalSampleEntity(
        timestampMillis = sample.timestampMillis,
        batteryTemperatureDeciCelsius = sample.battery.temperatureC.valueOrNull?.let(::celsiusToDeciCelsius),
        thermalStatus = sample.thermal.thermalStatus.valueOrNull?.storageValue,
        headroomMilli = sample.thermal.headroom.valueOrNull?.let(::headroomToMilli),
        batteryPercent = sample.battery.batteryPercent.valueOrNull,
        chargingStatus = sample.battery.chargingStatus.valueOrNull?.storageValue,
        powerSource = sample.battery.plugged.valueOrNull?.storageValue,
        currentMicroAmps = sample.battery.currentMicroAmps.valueOrNull,
        averageCurrentMicroAmps = sample.battery.averageCurrentMicroAmps.valueOrNull,
        voltageMilliVolts = sample.battery.voltageMilliVolts.valueOrNull,
        chargeCounterMicroAmpHours = sample.battery.chargeCounterMicroAmpHours.valueOrNull,
        energyCounterNanoWattHours = sample.battery.energyCounterNanoWattHours.valueOrNull,
        health = sample.battery.health.valueOrNull?.storageValue,
        capacityLevel = sample.battery.capacityLevel.valueOrNull?.storageValue,
        cycleCount = sample.battery.cycleCount.valueOrNull,
        sampleReason = sample.sampling.reason.storageValue,
        requestedIntervalMillis = sample.sampling.requestedIntervalMillis,
        actualIntervalMillis = sample.sampling.actualIntervalMillis,
        screenInteractive = sample.sampling.screenInteractive,
        batteryObservedAtMillis = sample.battery.observedAtMillis
    )

    fun toDomain(entity: ThermalSampleEntity): DeviceSample {
        val notRecorded = Reading.Absent(AbsenceReason.NOT_RECORDED)
        return DeviceSample(
            timestampMillis = entity.timestampMillis,
            battery = BatterySnapshot(
                observedAtMillis = entity.batteryObservedAtMillis,
                batteryPercent = entity.batteryPercent?.asReading() ?: notRecorded,
                temperatureC = entity.batteryTemperatureDeciCelsius
                    ?.let { Reading.Present(deciCelsiusToCelsius(it)) } ?: notRecorded,
                voltageMilliVolts = entity.voltageMilliVolts?.asReading() ?: notRecorded,
                currentMicroAmps = entity.currentMicroAmps?.asReading() ?: notRecorded,
                averageCurrentMicroAmps = entity.averageCurrentMicroAmps?.asReading() ?: notRecorded,
                chargeCounterMicroAmpHours = entity.chargeCounterMicroAmpHours?.asReading() ?: notRecorded,
                energyCounterNanoWattHours = entity.energyCounterNanoWattHours?.asReading() ?: notRecorded,
                chargingStatus = entity.chargingStatus
                    ?.let { Reading.Present(ChargingStatus.fromStorage(it)) } ?: notRecorded,
                plugged = entity.powerSource
                    ?.let { Reading.Present(PowerSource.fromStorage(it)) } ?: notRecorded,
                health = entity.health
                    ?.let { Reading.Present(BatteryHealth.fromStorage(it)) } ?: notRecorded,
                capacityLevel = entity.capacityLevel
                    ?.let { Reading.Present(BatteryCapacityLevel.fromStorage(it)) } ?: notRecorded,
                cycleCount = entity.cycleCount?.asReading() ?: notRecorded,
                technology = notRecorded,
                present = notRecorded
            ),
            thermal = ThermalSnapshot(
                thermalStatus = entity.thermalStatus
                    ?.let { Reading.Present(ThermalStatusBand.fromStorage(it)) } ?: notRecorded,
                headroom = entity.headroomMilli
                    ?.let { Reading.Present(milliToHeadroom(it)) } ?: notRecorded
            ),
            sampling = SamplingMetadata(
                reason = SampleReason.fromStorage(entity.sampleReason),
                requestedIntervalMillis = entity.requestedIntervalMillis,
                actualIntervalMillis = entity.actualIntervalMillis,
                screenInteractive = entity.screenInteractive
            )
        )
    }

    /** Chart/statistics projection; null means "not measured at that moment". */
    fun toSeriesPoint(entity: ThermalSampleEntity): Pair<Long, Float?> =
        entity.timestampMillis to entity.batteryTemperatureDeciCelsius?.let(::deciCelsiusToCelsius)

    /**
     * Aggregates back into the same [ThermalSampleEntity] shape is intentionally
     * not provided: a bucket is not a sample, and conflating them would let a
     * chart draw an hourly average as if it were a measurement.
     */
    fun aggregateAverageDeciCelsius(entity: ThermalAggregateEntity): Float? =
        if (entity.sampleCount <= 0) null
        else entity.temperatureSumDeciCelsius.toFloat() / entity.sampleCount
}
