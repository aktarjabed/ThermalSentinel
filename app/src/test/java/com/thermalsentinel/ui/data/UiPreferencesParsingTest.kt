package com.thermalsentinel.ui.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UiPreferencesParsingTest {

    @Test
    fun themeModeFallsBackToSystemOnUnknownValue() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage("bogus"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage(null))
    }

    @Test
    fun themeModeRoundTripsEveryEntry() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, ThemeMode.fromStorage(mode.storageValue))
        }
    }

    @Test
    fun accentFallsBackToBlueOnUnknownValue() {
        assertEquals(AccentPreset.BLUE, AccentPreset.fromStorage("bogus"))
        assertEquals(AccentPreset.BLUE, AccentPreset.fromStorage(null))
    }

    @Test
    fun accentRoundTripsEveryEntry() {
        AccentPreset.entries.forEach { preset ->
            assertEquals(preset, AccentPreset.fromStorage(preset.storageValue))
        }
    }

    @Test
    fun storageValuesAreUnique() {
        assertEquals(
            ThemeMode.entries.size,
            ThemeMode.entries.map { it.storageValue }.toSet().size
        )
        assertEquals(
            AccentPreset.entries.size,
            AccentPreset.entries.map { it.storageValue }.toSet().size
        )
    }
}
