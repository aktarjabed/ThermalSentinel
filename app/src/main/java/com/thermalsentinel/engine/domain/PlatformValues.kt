package com.thermalsentinel.engine.domain

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure mapping from raw platform values to [Reading]s, plus the unit conversions
 * used by the database and the UI.
 *
 * Every rule here exists because a specific Android behaviour can otherwise turn
 * into a plausible-looking lie on screen:
 *
 *  - `BatteryManager.getIntProperty` returns `Int.MIN_VALUE` when the property is
 *    unsupported, and `getLongProperty` returns `Long.MIN_VALUE`.
 *  - `PowerManager.getThermalHeadroom` returns `NaN` when unsupported; the
 *    collector enforces Android's minimum polling interval to avoid self-induced
 *    `NaN` results.
 *  - Zero is a valid numeric report for battery temperature, current, capacity,
 *    and cycle count. Only documented sentinels and values outside a signal's
 *    physical range are treated as absent.
 *
 * No `android.*` imports: the whole file is exercised by JVM unit tests.
 */
object PlatformValues {

    /** Documented sentinel for "property not supported" on `getIntProperty`. */
    const val INT_UNSUPPORTED_SENTINEL: Int = Int.MIN_VALUE

    /** Documented sentinel for "property not supported" on `getLongProperty`. */
    const val LONG_UNSUPPORTED_SENTINEL: Long = Long.MIN_VALUE

    /** Plausible battery temperature window in tenths of a degree Celsius. */
    private const val MIN_PLAUSIBLE_DECI_CELSIUS = -300
    private const val MAX_PLAUSIBLE_DECI_CELSIUS = 1200

    /** Plausible battery voltage window in millivolts. */
    private const val MIN_PLAUSIBLE_MILLIVOLTS = 1
    private const val MAX_PLAUSIBLE_MILLIVOLTS = 20_000

    fun deciCelsiusToCelsius(deciCelsius: Int): Float = deciCelsius / 10f

    fun celsiusToDeciCelsius(celsius: Float): Int = (celsius * 10f).roundToInt()

    /** Room persists headroom as an Int to avoid float drift in comparisons. */
    fun headroomToMilli(headroom: Float): Int = (headroom * 1000f).roundToInt()

    fun milliToHeadroom(milli: Int): Float = milli / 1000f

    fun millivoltsToVolts(millivolts: Int): Float = millivolts / 1000f

    fun microAmpsToMilliAmps(microAmps: Int): Float = microAmps / 1000f

    /**
     * `BatteryManager.EXTRA_TEMPERATURE` is an Int in tenths of a degree
     * Celsius. A missing extra and an implausible value are reported separately.
     */
    fun temperatureReading(deciCelsius: Int?): Reading<Float> {
        if (deciCelsius == null) return Reading.Absent(AbsenceReason.NOT_REPORTED_BY_PLATFORM)
        if (deciCelsius < MIN_PLAUSIBLE_DECI_CELSIUS || deciCelsius > MAX_PLAUSIBLE_DECI_CELSIUS) {
            return Reading.Absent(AbsenceReason.REPORTED_VALUE_IMPLAUSIBLE)
        }
        // 0.0 °C is physically possible and is not a documented missing-value sentinel.
        return Reading.Present(deciCelsiusToCelsius(deciCelsius))
    }

    fun voltageReading(milliVolts: Int?): Reading<Int> {
        if (milliVolts == null) return Reading.Absent(AbsenceReason.NOT_REPORTED_BY_PLATFORM)
        if (milliVolts < MIN_PLAUSIBLE_MILLIVOLTS || milliVolts > MAX_PLAUSIBLE_MILLIVOLTS) {
            return Reading.Absent(AbsenceReason.REPORTED_VALUE_IMPLAUSIBLE)
        }
        return Reading.Present(milliVolts)
    }

    fun batteryPercentReading(level: Int?, scale: Int?): Reading<Int> {
        if (level == null || scale == null || scale <= 0) {
            return Reading.Absent(AbsenceReason.NOT_REPORTED_BY_PLATFORM)
        }
        if (level < 0 || level > scale) return Reading.Absent(AbsenceReason.REPORTED_VALUE_IMPLAUSIBLE)
        val percent = (level * 100f / scale).roundToInt().coerceIn(0, 100)
        return Reading.Present(percent)
    }

    /**
     * Current is signed: negative discharges, positive charges. Zero is retained
     * as a measured value; `Int.MIN_VALUE` is the documented unsupported sentinel.
     */
    fun currentReading(microAmps: Int?): Reading<Int> {
        if (microAmps == null) return Reading.Absent(AbsenceReason.NOT_REPORTED_BY_PLATFORM)
        if (microAmps == INT_UNSUPPORTED_SENTINEL) {
            return Reading.Absent(AbsenceReason.UNSUPPORTED_ON_THIS_DEVICE)
        }
        return Reading.Present(microAmps)
    }

    fun intPropertyReading(raw: Int?): Reading<Int> {
        if (raw == null) return Reading.Absent(AbsenceReason.NOT_REPORTED_BY_PLATFORM)
        if (raw == INT_UNSUPPORTED_SENTINEL) return Reading.Absent(AbsenceReason.UNSUPPORTED_ON_THIS_DEVICE)
        return Reading.Present(raw)
    }

    fun longPropertyReading(raw: Long?): Reading<Long> {
        if (raw == null) return Reading.Absent(AbsenceReason.NOT_REPORTED_BY_PLATFORM)
        if (raw == LONG_UNSUPPORTED_SENTINEL) return Reading.Absent(AbsenceReason.UNSUPPORTED_ON_THIS_DEVICE)
        if (raw < 0L) return Reading.Absent(AbsenceReason.REPORTED_VALUE_IMPLAUSIBLE)
        return Reading.Present(raw)
    }

    fun cycleCountReading(raw: Int?): Reading<Int> {
        if (raw == null) {
            // The extra itself is API 34+; on older platforms it cannot be read.
            return Reading.Absent(AbsenceReason.UNSUPPORTED_ON_THIS_DEVICE)
        }
        if (raw < 0) return Reading.Absent(AbsenceReason.REPORTED_VALUE_IMPLAUSIBLE)
        return Reading.Present(raw)
    }

    /**
     * Headroom is `>= 0`, with 1.0 at the SEVERE threshold; it may exceed 1.0.
     * A negative value is clamped to 0 by the platform, and `NaN` means the
     * device does not support the API (or we polled it too fast).
     */
    fun headroomReading(raw: Float?): Reading<Float> {
        if (raw == null) return Reading.Absent(AbsenceReason.NOT_REPORTED_BY_PLATFORM)
        if (raw.isNaN()) return Reading.Absent(AbsenceReason.UNSUPPORTED_ON_THIS_DEVICE)
        if (raw.isInfinite()) return Reading.Absent(AbsenceReason.REPORTED_VALUE_IMPLAUSIBLE)
        return Reading.Present(if (raw < 0f) 0f else raw)
    }

    /**
     * Charge counter / energy counter are absolute capacities. Negative values
     * and the unsupported sentinels are rejected; `0` is accepted here because a
     * genuinely empty counter is possible, unlike a zero temperature.
     */
    fun counterReading(raw: Int?): Reading<Int> {
        if (raw == null) return Reading.Absent(AbsenceReason.NOT_REPORTED_BY_PLATFORM)
        if (raw == INT_UNSUPPORTED_SENTINEL) return Reading.Absent(AbsenceReason.UNSUPPORTED_ON_THIS_DEVICE)
        if (raw < 0) return Reading.Absent(AbsenceReason.REPORTED_VALUE_IMPLAUSIBLE)
        return Reading.Present(raw)
    }

    /**
     * Thermal status is always reported on API 29+, so the only interesting case
     * is an integer the engine does not recognise, which maps to
     * [ThermalStatusBand.UNKNOWN] by contract.
     */
    fun thermalStatusReading(rawLevel: Int?): Reading<ThermalStatusBand> {
        if (rawLevel == null) return Reading.Absent(AbsenceReason.NOT_REPORTED_BY_PLATFORM)
        return Reading.Present(ThermalStatusBand.fromAndroidLevel(rawLevel))
    }

    /**
     * Used by the alert engine: how far a temperature sits above a threshold,
     * formatted for diagnostics and never used for a decision on its own.
     */
    fun differenceCelsius(a: Float, b: Float): Float = abs(a - b)
}
