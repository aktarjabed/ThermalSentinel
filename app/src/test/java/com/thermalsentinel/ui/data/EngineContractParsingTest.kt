package com.thermalsentinel.ui.data

// The band enum moved into the engine package when the engine phase started; the
// duplicate UI copy is gone, so the contract test now covers the engine enum.
import com.thermalsentinel.engine.domain.ThermalStatusBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineContractParsingTest {

    @Test
    fun widgetStyleFallsBackToSystemOnUnknownValue() {
        assertEquals(WidgetStyle.SYSTEM, WidgetStyle.fromStorage("bogus"))
        assertEquals(WidgetStyle.SYSTEM, WidgetStyle.fromStorage(null))
    }

    @Test
    fun widgetStyleRoundTripsEveryEntry() {
        WidgetStyle.entries.forEach { style ->
            assertEquals(style, WidgetStyle.fromStorage(style.storageValue))
        }
    }

    @Test
    fun widgetStyleStorageValuesAreUnique() {
        assertEquals(
            WidgetStyle.entries.size,
            WidgetStyle.entries.map { it.storageValue }.toSet().size
        )
    }

    @Test
    fun thermalStatusBandFallsBackToUnknownOnUnknownString() {
        assertEquals(ThermalStatusBand.UNKNOWN, ThermalStatusBand.fromStorage("bogus"))
        assertEquals(ThermalStatusBand.UNKNOWN, ThermalStatusBand.fromStorage(null))
    }

    @Test
    fun thermalStatusBandRoundTripsEveryEntry() {
        ThermalStatusBand.entries.forEach { band ->
            assertEquals(band, ThermalStatusBand.fromStorage(band.storageValue))
        }
    }

    @Test
    fun thermalStatusBandMapsKnownAndroidLevelsDirectly() {
        ThermalStatusBand.entries
            .filter { it.androidLevel >= 0 }
            .forEach { band ->
                assertEquals(band, ThermalStatusBand.fromAndroidLevel(band.androidLevel))
            }
    }

    @Test
    fun thermalStatusBandClampsOutOfRangeAndroidLevelToUnknown() {
        // Negative values and any future platform constant above SHUTDOWN are
        // both unknown; the engine surfaces UNKNOWN as "thermal status
        // unavailable" rather than as a normal or shutdown state.
        assertEquals(ThermalStatusBand.UNKNOWN, ThermalStatusBand.fromAndroidLevel(-1))
        assertEquals(ThermalStatusBand.UNKNOWN, ThermalStatusBand.fromAndroidLevel(7))
        assertEquals(ThermalStatusBand.UNKNOWN, ThermalStatusBand.fromAndroidLevel(99))
    }

    @Test
    fun onlyRealBandsAreSelectableForAlerts() {
        // NONE and UNKNOWN must not be selectable in the Alert Rules chooser.
        assertFalse(ThermalStatusBand.NONE.selectable)
        assertFalse(ThermalStatusBand.UNKNOWN.selectable)
        assertTrue(ThermalStatusBand.LIGHT.selectable)
        assertTrue(ThermalStatusBand.SHUTDOWN.selectable)
    }
}
