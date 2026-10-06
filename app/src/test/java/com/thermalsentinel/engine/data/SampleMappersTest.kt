package com.thermalsentinel.engine.data

import com.thermalsentinel.engine.domain.AbsenceReason
import com.thermalsentinel.engine.domain.BatteryCapacityLevel
import com.thermalsentinel.engine.domain.BatteryHealth
import com.thermalsentinel.engine.domain.BatterySnapshot
import com.thermalsentinel.engine.domain.ChargingStatus
import com.thermalsentinel.engine.domain.DeviceSample
import com.thermalsentinel.engine.domain.PowerSource
import com.thermalsentinel.engine.domain.Reading
import com.thermalsentinel.engine.domain.SampleReason
import com.thermalsentinel.engine.domain.SamplingMetadata
import com.thermalsentinel.engine.domain.ThermalSnapshot
import com.thermalsentinel.engine.domain.ThermalStatusBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SampleMappersTest {

    @Test
    fun aRealZeroCelsiusReadingSurvivesRoomRoundTrip() {
        val sample = sample(temperature = Reading.Present(0f))

        val entity = SampleMappers.toEntity(sample)
        val restored = SampleMappers.toDomain(entity)

        assertEquals(0, entity.batteryTemperatureDeciCelsius)
        assertEquals(0f, restored.battery.temperatureC.valueOrNull!!, 0.001f)
    }

    @Test
    fun absentTemperatureIsNullInRoomAndRestoresAsExplicitAbsence() {
        val sample = sample(
            temperature = Reading.Absent(AbsenceReason.NOT_REPORTED_BY_PLATFORM)
        )

        val entity = SampleMappers.toEntity(sample)
        val restored = SampleMappers.toDomain(entity)

        assertNull(entity.batteryTemperatureDeciCelsius)
        assertEquals(AbsenceReason.NOT_RECORDED, restored.battery.temperatureC.absenceReason)
    }

    @Test
    fun enumStorageAndSamplingMetadataRoundTrip() {
        val original = sample(temperature = Reading.Present(38.5f)).copy(
            sampling = SamplingMetadata(
                reason = SampleReason.SERVICE_START,
                requestedIntervalMillis = 30_000L,
                actualIntervalMillis = 45_000L,
                screenInteractive = false
            )
        )

        val restored = SampleMappers.toDomain(SampleMappers.toEntity(original))

        assertEquals(SampleReason.SERVICE_START, restored.sampling.reason)
        assertEquals(30_000L, restored.sampling.requestedIntervalMillis)
        assertEquals(45_000L, restored.sampling.actualIntervalMillis)
        assertEquals(false, restored.sampling.screenInteractive)
        assertEquals(ThermalStatusBand.MODERATE, restored.thermal.thermalStatus.valueOrNull)
        assertEquals(0.8f, restored.thermal.headroom.valueOrNull!!, 0.001f)
    }

    private fun sample(temperature: Reading<Float>): DeviceSample {
        val absent = Reading.Absent(AbsenceReason.NOT_REPORTED_BY_PLATFORM)
        return DeviceSample(
            timestampMillis = 1_700_000_000_000L,
            battery = BatterySnapshot(
                observedAtMillis = 1_700_000_000_000L,
                batteryPercent = Reading.Present(0),
                temperatureC = temperature,
                voltageMilliVolts = Reading.Present(3_800),
                currentMicroAmps = Reading.Present(0),
                averageCurrentMicroAmps = absent,
                chargeCounterMicroAmpHours = absent,
                energyCounterNanoWattHours = absent,
                chargingStatus = Reading.Present(ChargingStatus.DISCHARGING),
                plugged = Reading.Present(PowerSource.NONE),
                health = Reading.Present(BatteryHealth.GOOD),
                capacityLevel = Reading.Present(BatteryCapacityLevel.NORMAL),
                cycleCount = Reading.Present(0),
                technology = absent,
                present = Reading.Present(true)
            ),
            thermal = ThermalSnapshot(
                thermalStatus = Reading.Present(ThermalStatusBand.MODERATE),
                headroom = Reading.Present(0.8f)
            ),
            sampling = SamplingMetadata(
                reason = SampleReason.SCHEDULED_NORMAL,
                requestedIntervalMillis = 30_000L,
                actualIntervalMillis = 30_000L,
                screenInteractive = true
            )
        )
    }
}
