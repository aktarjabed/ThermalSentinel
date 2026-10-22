package com.thermalsentinel.engine.export

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalJsonTest {
    @Test
    fun absentReadingsAreNullRatherThanZero() {
        val row = CsvRow(1_800_000_000_000, null, null, null, null, null, null, null, null, null, null, null, null, "test", 30_000, null, false)
        val json = ThermalJson.render(listOf(row), generatedAtMillis = 1_800_000_000_000)
        assertTrue(json.contains("\"battery_temperature_c\": null"))
        assertTrue(json.contains("\"battery_percent\": null"))
        assertFalse(json.contains("\"battery_temperature_c\": 0"))
    }

    @Test
    fun stringsAreEscapedAndNumbersUseDotDecimal() {
        val row = CsvRow(1_800_000_000_000, 37.5f, "warning\"\nnext", 0.625f, 85, "charging", "usb", -450_000, -400_000, 4_100, 3_000_000, "good", 10, "screen_on", 30_000, 29_900, true)
        val json = ThermalJson.render(listOf(row), generatedAtMillis = row.timestampMillis)
        assertTrue(json.contains("\"android_thermal_status\": \"warning\\\"\\nnext\""))
        assertTrue(json.contains("\"battery_temperature_c\": 37.5"))
        assertTrue(json.contains("\"thermal_headroom\": 0.625"))
        assertTrue(json.contains("\"screen_interactive\": true"))
    }

    @Test
    fun nonFiniteNumbersAreNull() {
        val row = CsvRow(1, Float.NaN, null, Float.POSITIVE_INFINITY, null, null, null, null, null, null, null, null, null, "test", 5_000, null, false)
        val json = ThermalJson.render(listOf(row), generatedAtMillis = 1)
        assertTrue(json.contains("\"battery_temperature_c\": null"))
        assertTrue(json.contains("\"thermal_headroom\": null"))
        assertFalse(json.contains("NaN"))
        assertFalse(json.contains("Infinity"))
    }
}
