package com.thermalsentinel.engine.export

import com.thermalsentinel.engine.domain.BatterySnapshot
import com.thermalsentinel.engine.domain.DeviceSample
import com.thermalsentinel.engine.domain.SampleReason
import com.thermalsentinel.engine.domain.SamplingMetadata
import com.thermalsentinel.engine.domain.ThermalSnapshot
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ThermalCsvTest {

    private lateinit var originalLocale: Locale

    @Before
    fun rememberLocale() {
        originalLocale = Locale.getDefault()
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    private fun emptySample(nowMillis: Long) = DeviceSample(
        timestampMillis = nowMillis,
        battery = BatterySnapshot.notCollectedYet(nowMillis),
        thermal = ThermalSnapshot.notCollectedYet(),
        sampling = SamplingMetadata(
            reason = SampleReason.SERVICE_START,
            requestedIntervalMillis = 30_000L,
            actualIntervalMillis = null,
            screenInteractive = true
        )
    )

    @Test
    fun headerIsStableAndWideEnoughForEveryMeasurement() {
        val columns = ThermalCsv.HEADER.split(",")
        assertEquals(18, columns.size)
        assertEquals("timestamp_iso", columns.first())
        assertEquals("screen_interactive", columns.last())
        // Column order is part of the export contract: a reader that maps by
        // position must not silently start reading the wrong column.
        assertEquals("battery_temperature_c", columns[2])
        assertEquals("current_now_ua", columns[8])
        assertEquals("sample_reason", columns[14])
    }

    @Test
    fun missingMeasurementsAreEmptyFieldsNeverZeroes() {
        val csv = ThermalCsv.render(listOf(CsvRow.from(emptySample(1_700_000_000_000L))))
        val row = csv.trim().lines()[1]
        val fields = row.split(",")

        assertEquals("", fields[2]) // battery_temperature_c
        assertEquals("", fields[3]) // android_thermal_status
        assertEquals("", fields[4]) // thermal_headroom
        assertEquals("", fields[5]) // battery_percent
        assertEquals("", fields[8]) // current_micro_amps
        assertTrue(row.contains("service_start"))
        assertTrue(row.endsWith(",true"))
    }

    @Test
    fun decimalsIgnoreTheDefaultLocale() {
        Locale.setDefault(Locale.GERMANY)
        assertEquals("38.5", ThermalCsv.decimal(38.5f, 1))
        assertEquals("0.800", ThermalCsv.decimal(0.8f, 3))
        assertEquals("", ThermalCsv.decimal(null, 1))
    }

    @Test
    fun fieldsAreQuotedOnlyWhenRequired() {
        assertEquals("plain", ThermalCsv.field("plain"))
        assertEquals("", ThermalCsv.field(null))
        assertEquals("\"a,b\"", ThermalCsv.field("a,b"))
        assertEquals("\"he said \"\"hi\"\"\"", ThermalCsv.field("he said \"hi\""))
        assertEquals("\"two\nlines\"", ThermalCsv.field("two\nlines"))
    }

    @Test
    fun isoTimeStampCarriesAnOffset() {
        val stamp = ThermalCsv.isoTimestamp(1_700_000_000_000L)
        assertTrue(stamp.contains("T"))
        // Either an explicit Z or a numeric offset is present, so the timestamp is
        // unambiguous wherever the device happens to be.
        assertTrue(stamp.endsWith("Z") || Regex(".*[+-]\\d{2}:\\d{2}$").matches(stamp))
    }

    @Test
    fun renderProducesOneLinePerRowPlusTheHeader() {
        val csv = ThermalCsv.render(
            listOf(
                CsvRow.from(emptySample(1_700_000_000_000L)),
                CsvRow.from(emptySample(1_700_000_060_000L))
            )
        )
        assertEquals(3, csv.trim().lines().size)
    }
}
