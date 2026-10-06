package com.thermalsentinel.engine.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These rules exist so the app can never show a reassuring number it did not
 * measure. Each expectation below is a claim about hardware honesty, so it is
 * pinned by a test rather than left to reviewer memory.
 */
class PlatformValuesTest {

    private fun reason(reading: Reading<*>): AbsenceReason? = reading.absenceReason

    @Test
    fun deciCelsiusConvertsBothWays() {
        assertEquals(38.5f, PlatformValues.deciCelsiusToCelsius(385), 0.001f)
        assertEquals(385, PlatformValues.celsiusToDeciCelsius(38.5f))
        assertEquals(0.0f, PlatformValues.deciCelsiusToCelsius(0), 0.001f)
    }

    @Test
    fun headroomConvertsBothWaysAsAThousandth() {
        assertEquals(800, PlatformValues.headroomToMilli(0.8f))
        assertEquals(0.8f, PlatformValues.milliToHeadroom(800), 0.001f)
    }

    @Test
    fun missingTemperatureIsReportedAsNotReported() {
        assertEquals(AbsenceReason.NOT_REPORTED_BY_PLATFORM, reason(PlatformValues.temperatureReading(null)))
    }

    @Test
    fun zeroAndOutOfRangeTemperaturesAreImplausible() {
        assertEquals(AbsenceReason.REPORTED_VALUE_IMPLAUSIBLE, reason(PlatformValues.temperatureReading(0)))
        assertEquals(AbsenceReason.REPORTED_VALUE_IMPLAUSIBLE, reason(PlatformValues.temperatureReading(-400)))
        assertEquals(AbsenceReason.REPORTED_VALUE_IMPLAUSIBLE, reason(PlatformValues.temperatureReading(1300)))
    }

    @Test
    fun aPlausibleTemperatureIsPresent() {
        assertEquals(38.5f, PlatformValues.temperatureReading(385).valueOrNull!!, 0.001f)
        assertEquals(38.5f, PlatformValues.temperatureReading(-300).valueOrNull!!, 0.001f)
    }

    @Test
    fun voltageRejectsZeroAndAbsurdValues() {
        assertEquals(AbsenceReason.REPORTED_VALUE_IMPLAUSIBLE, reason(PlatformValues.voltageReading(0)))
        assertEquals(AbsenceReason.REPORTED_VALUE_IMPLAUSIBLE, reason(PlatformValues.voltageReading(50_000)))
        assertEquals(4120, PlatformValues.voltageReading(4120).valueOrNull)
    }

    @Test
    fun batteryPercentIsScaledAndBounded() {
        assertEquals(78, PlatformValues.batteryPercentReading(78, 100).valueOrNull)
        assertEquals(50, PlatformValues.batteryPercentReading(3900, 7800).valueOrNull)
        assertEquals(AbsenceReason.NOT_REPORTED_BY_PLATFORM, reason(PlatformValues.batteryPercentReading(78, null)))
        assertEquals(AbsenceReason.NOT_REPORTED_BY_PLATFORM, reason(PlatformValues.batteryPercentReading(78, 0)))
        assertEquals(AbsenceReason.REPORTED_VALUE_IMPLAUSIBLE, reason(PlatformValues.batteryPercentReading(120, 100)))
    }

    @Test
    fun unsupportedSentinelsBecomeTheirOwnReason() {
        assertEquals(
            AbsenceReason.UNSUPPORTED_ON_THIS_DEVICE,
            reason(PlatformValues.currentReading(PlatformValues.INT_UNSUPPORTED_SENTINEL))
        )
        assertEquals(
            AbsenceReason.UNSUPPORTED_ON_THIS_DEVICE,
            reason(PlatformValues.intPropertyReading(PlatformValues.INT_UNSUPPORTED_SENTINEL))
        )
        assertEquals(
            AbsenceReason.UNSUPPORTED_ON_THIS_DEVICE,
            reason(PlatformValues.longPropertyReading(PlatformValues.LONG_UNSUPPORTED_SENTINEL))
        )
    }

    @Test
    fun zeroCurrentIsImplausibleRatherThanReassuring() {
        // "0 mA" and "this fuel gauge does not report current" are different facts.
        assertEquals(AbsenceReason.REPORTED_VALUE_IMPLAUSIBLE, reason(PlatformValues.currentReading(0)))
        assertEquals(-450_000, PlatformValues.currentReading(-450_000).valueOrNull)
    }

    @Test
    fun negativeIntegerPropertiesAreImplausibleButZeroIsAcceptedForCounters() {
        assertEquals(AbsenceReason.REPORTED_VALUE_IMPLAUSIBLE, reason(PlatformValues.counterReading(-5)))
        assertEquals(0, PlatformValues.counterReading(0).valueOrNull)
        assertEquals(AbsenceReason.REPORTED_VALUE_IMPLAUSIBLE, reason(PlatformValues.longPropertyReading(-1L)))
    }

    @Test
    fun cycleCountAbsenceIsUnsupportedRatherThanAnError() {
        // EXTRA_CYCLE_COUNT is API 34+, so absence on older platforms is expected.
        assertEquals(AbsenceReason.UNSUPPORTED_ON_THIS_DEVICE, reason(PlatformValues.cycleCountReading(null)))
        assertEquals(AbsenceReason.REPORTED_VALUE_IMPLAUSIBLE, reason(PlatformValues.cycleCountReading(0)))
        assertEquals(412, PlatformValues.cycleCountReading(412).valueOrNull)
    }

    @Test
    fun headroomNaNMeansUnsupportedAndNegativeIsClamped() {
        assertEquals(AbsenceReason.UNSUPPORTED_ON_THIS_DEVICE, reason(PlatformValues.headroomReading(Float.NaN)))
        assertEquals(
            AbsenceReason.REPORTED_VALUE_IMPLAUSIBLE,
            reason(PlatformValues.headroomReading(Float.POSITIVE_INFINITY))
        )
        assertEquals(0f, PlatformValues.headroomReading(-0.4f).valueOrNull!!, 0.001f)
        assertEquals(1.35f, PlatformValues.headroomReading(1.35f).valueOrNull!!, 0.001f)
    }

    @Test
    fun unknownThermalStatusLevelsMapToUnknownAndAreNeverSafe() {
        assertEquals(ThermalStatusBand.SEVERE, PlatformValues.thermalStatusReading(3).valueOrNull)
        assertEquals(ThermalStatusBand.UNKNOWN, PlatformValues.thermalStatusReading(99).valueOrNull)
        assertEquals(AbsenceReason.NOT_REPORTED_BY_PLATFORM, reason(PlatformValues.thermalStatusReading(null)))
        assertTrue(!ThermalStatusBand.UNKNOWN.isPlatformReported)
    }

    @Test
    fun differenceIsAlwaysPositive() {
        assertEquals(2.0f, PlatformValues.differenceCelsius(38f, 40f), 0.001f)
        assertEquals(2.0f, PlatformValues.differenceCelsius(40f, 38f), 0.001f)
    }
}
