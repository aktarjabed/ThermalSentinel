package com.thermalsentinel.engine.export

import java.util.Locale

/** Versioned JSON representation of raw thermal samples. Missing readings are null, never fabricated zeroes. */
object ThermalJson {
    fun render(rows: List<CsvRow>, generatedAtMillis: Long = System.currentTimeMillis()): String =
        buildString {
            append("{\n")
            append("  \"schema_version\": 1,\n")
            append("  \"generated_at\": ").append(quote(ThermalCsv.isoTimestamp(generatedAtMillis))).append(",\n")
            append("  \"sample_count\": ").append(rows.size).append(",\n")
            append("  \"samples\": [")
            if (rows.isNotEmpty()) append('\n')
            rows.forEachIndexed { index, row ->
                append("    {\n")
                append("      \"timestamp_iso\": ").append(quote(ThermalCsv.isoTimestamp(row.timestampMillis))).append(",\n")
                append("      \"timestamp_epoch_millis\": ").append(row.timestampMillis).append(",\n")
                append("      \"battery_temperature_c\": ").append(number(row.batteryTemperatureC, 1)).append(",\n")
                append("      \"android_thermal_status\": ").append(nullableString(row.androidThermalStatus)).append(",\n")
                append("      \"thermal_headroom\": ").append(number(row.thermalHeadroom, 3)).append(",\n")
                append("      \"battery_percent\": ").append(nullableNumber(row.batteryPercent)).append(",\n")
                append("      \"charging_status\": ").append(nullableString(row.chargingStatus)).append(",\n")
                append("      \"power_source\": ").append(nullableString(row.powerSource)).append(",\n")
                append("      \"current_now_ua\": ").append(nullableNumber(row.currentMicroAmps)).append(",\n")
                append("      \"current_average_ua\": ").append(nullableNumber(row.averageCurrentMicroAmps)).append(",\n")
                append("      \"voltage_mv\": ").append(nullableNumber(row.voltageMilliVolts)).append(",\n")
                append("      \"charge_counter_uah\": ").append(nullableNumber(row.chargeCounterMicroAmpHours)).append(",\n")
                append("      \"battery_health\": ").append(nullableString(row.batteryHealth)).append(",\n")
                append("      \"cycle_count\": ").append(nullableNumber(row.cycleCount)).append(",\n")
                append("      \"sample_reason\": ").append(quote(row.sampleReason)).append(",\n")
                append("      \"requested_interval_ms\": ").append(row.requestedIntervalMillis).append(",\n")
                append("      \"actual_interval_ms\": ").append(nullableNumber(row.actualIntervalMillis)).append(",\n")
                append("      \"screen_interactive\": ").append(row.screenInteractive).append('\n')
                append("    }")
                if (index != rows.lastIndex) append(',')
                append('\n')
            }
            append("  ]\n}\n")
        }

    private fun nullableString(value: String?): String = value?.let(::quote) ?: "null"
    private fun nullableNumber(value: Number?): String = value?.toString() ?: "null"
    private fun number(value: Float?, decimals: Int): String =
        if (value == null || !value.isFinite()) "null"
        else String.format(Locale.ROOT, "%.${decimals}f", value)

    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char.code < 0x20) {
                    append("\\u")
                    append(char.code.toString(16).padStart(4, '0'))
                } else append(char)
            }
        }
        append('"')
    }
}
