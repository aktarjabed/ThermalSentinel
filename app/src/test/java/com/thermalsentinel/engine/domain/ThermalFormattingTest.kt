package com.thermalsentinel.engine.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class ThermalFormattingTest {
    @Test
    fun temperatureDisplayConvertsWithoutChangingCelsiusValues() {
        assertEquals("40.0°C", ThermalFormatting.temperature(40f, fahrenheit = false, locale = Locale.US))
        assertEquals("104.0°F", ThermalFormatting.temperature(40f, fahrenheit = true, locale = Locale.US))
        assertEquals("32.0°F", ThermalFormatting.temperature(0f, fahrenheit = true, locale = Locale.US))
    }

    @Test
    fun absentTemperaturesRemainAbsentInEitherUnit() {
        assertEquals("—", ThermalFormatting.temperature(null, fahrenheit = false, locale = Locale.US))
        assertEquals("—", ThermalFormatting.temperature(null, fahrenheit = true, locale = Locale.US))
    }

    @Test
    fun rateConversionUsesScaleOnlyAndNeverAddsThirtyTwo() {
        assertEquals("1.00 °C/min", ThermalFormatting.temperatureRate(1f, fahrenheit = false, locale = Locale.US))
        assertEquals("1.80 °F/min", ThermalFormatting.temperatureRate(1f, fahrenheit = true, locale = Locale.US))
    }
}
