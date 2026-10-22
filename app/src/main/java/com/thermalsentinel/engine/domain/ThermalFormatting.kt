package com.thermalsentinel.engine.domain

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Shared formatting so a value cannot be rendered one way in a notification and
 * another way on a screen. Every function takes a nullable value and renders an
 * explicit "not measured" marker instead of a zero.
 */
object ThermalFormatting {

    const val UNAVAILABLE_MARKER = "—"

    fun temperatureCelsius(
        value: Float?,
        unavailable: String = UNAVAILABLE_MARKER,
        locale: Locale = Locale.getDefault()
    ): String = if (value == null) unavailable else String.format(locale, "%.1f°C", value)

    /** Display conversion only: Room, rules and calculations remain Celsius-based. */
    fun temperature(
        valueCelsius: Float?,
        fahrenheit: Boolean,
        unavailable: String = UNAVAILABLE_MARKER,
        locale: Locale = Locale.getDefault()
    ): String {
        if (valueCelsius == null) return unavailable
        val value = if (fahrenheit) celsiusToFahrenheit(valueCelsius) else valueCelsius
        val unit = if (fahrenheit) "°F" else "°C"
        return String.format(locale, "%.1f%s", value, unit)
    }

    fun celsiusToFahrenheit(valueCelsius: Float): Float = valueCelsius * 9f / 5f + 32f

    fun temperatureRate(
        degreesCelsiusPerMinute: Float,
        fahrenheit: Boolean,
        locale: Locale = Locale.getDefault()
    ): String {
        val rate = if (fahrenheit) degreesCelsiusPerMinute * 9f / 5f else degreesCelsiusPerMinute
        val unit = if (fahrenheit) "°F/min" else "°C/min"
        return String.format(locale, "%.2f %s", rate, unit)
    }

    fun temperatureDeltaCelsius(value: Float?, locale: Locale = Locale.getDefault()): String =
        if (value == null) UNAVAILABLE_MARKER else String.format(locale, "%+.1f°C", value)

    fun percent(value: Int?, unavailable: String = UNAVAILABLE_MARKER): String =
        if (value == null) unavailable else "$value%"

    fun milliAmps(microAmps: Int?, locale: Locale = Locale.getDefault()): String = when {
        microAmps == null -> UNAVAILABLE_MARKER
        abs(microAmps) < 1000 -> String.format(locale, "%d µA", microAmps)
        else -> String.format(locale, "%.0f mA", microAmps / 1000f)
    }

    fun volts(milliVolts: Int?, locale: Locale = Locale.getDefault()): String =
        if (milliVolts == null) UNAVAILABLE_MARKER else String.format(locale, "%.2f V", milliVolts / 1000f)

    /** Headroom is a ratio, not a temperature. Never append a degree sign. */
    fun headroom(value: Float?, locale: Locale = Locale.getDefault()): String =
        if (value == null) UNAVAILABLE_MARKER else String.format(locale, "%.2f", value)

    fun microAmpHours(value: Int?): String = if (value == null) UNAVAILABLE_MARKER else "$value µAh"

    fun cycleCount(value: Int?, unavailable: String = UNAVAILABLE_MARKER): String =
        if (value == null) unavailable else value.toString()

    /**
     * Duration for a session or a statistic. Rounds to the minute above an hour,
     * because "1 h 04 min" is more honest than a fake seconds precision on a
     * value that was sampled every 30 seconds.
     */
    fun duration(millis: Long?, unavailable: String = UNAVAILABLE_MARKER): String {
        if (millis == null || millis < 0) return unavailable
        val totalMinutes = (millis / 60_000.0).roundToInt()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 -> String.format(Locale.ROOT, "%d h %02d min", hours, minutes)
            else -> "$minutes min"
        }
    }

    /** Short clock label used by charts and timelines. */
    fun clock(timestampMillis: Long, pattern: String = "HH:mm"): String =
        java.time.Instant.ofEpochMilli(timestampMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))

    /** Date and time, used by history headers and the CSV export. */
    fun timestamp(timestampMillis: Long): String =
        java.time.Instant.ofEpochMilli(timestampMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT))

    /** Renders a reading's absence with the shared vocabulary. */
    fun readingOrUnavailable(reading: Reading<Int>, unavailableLabel: String = UNAVAILABLE_MARKER): String {
        val value = reading.valueOrNull
        if (value != null) return value.toString()
        val reason = reading.absenceReason
        return if (reason == null) unavailableLabel else reason.displayLabel
    }
}
