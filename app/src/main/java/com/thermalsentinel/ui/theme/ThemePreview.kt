package com.thermalsentinel.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.thermalsentinel.ui.data.AccentPreset
import com.thermalsentinel.ui.data.ThemeMode
import com.thermalsentinel.ui.data.UiPreferences

@Preview(name = "Light", showBackground = true)
@Composable
private fun LightPreview() {
    ThermalSentinelTheme(preferences = UiPreferences(), content = {})
}

@Preview(name = "Dark AMOLED Violet", showBackground = true)
@Composable
private fun DarkAmoledPreview() {
    ThermalSentinelTheme(
        preferences = UiPreferences(
            themeMode = ThemeMode.DARK,
            dynamicColor = false,
            amoled = true,
            accent = AccentPreset.VIOLET
        ),
        content = {}
    )
}
