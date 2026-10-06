package com.thermalsentinel.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.thermalsentinel.ui.data.AccentPreset
import com.thermalsentinel.ui.data.ThemeMode
import com.thermalsentinel.ui.data.UiPreferences

private fun lightScheme(accent: AccentPreset): ColorScheme {
    val seed = accent.seed()
    return lightColorScheme(
        primary = seed.primary,
        onPrimary = seed.onPrimary,
        primaryContainer = seed.primaryContainer,
        onPrimaryContainer = seed.onPrimaryContainer,
        secondary = seed.secondary,
        tertiary = seed.tertiary,
        surface = Color(0xFFFAF9FF),
        background = Color(0xFFFAF9FF)
    )
}

private fun darkScheme(accent: AccentPreset, amoled: Boolean): ColorScheme {
    val seed = accent.seed()
    val surface = if (amoled) Color.Black else Color(0xFF111318)
    return darkColorScheme(
        primary = seed.primaryDark,
        onPrimary = seed.onPrimaryDark,
        primaryContainer = seed.primaryContainerDark,
        onPrimaryContainer = seed.onPrimaryContainerDark,
        secondary = seed.secondaryDark,
        onSecondary = Color(0xFF293140),
        tertiary = seed.tertiaryDark,
        onTertiary = Color(0xFF3F2840),
        surface = surface,
        background = surface,
        surfaceContainerLowest = if (amoled) Color.Black else Color(0xFF0E1013),
        surfaceContainerLow = if (amoled) Color(0xFF050608) else Color(0xFF191B20),
        surfaceContainer = if (amoled) Color(0xFF08090C) else Color(0xFF1D1F24),
        surfaceContainerHigh = if (amoled) Color(0xFF0E1014) else Color(0xFF272A30),
        surfaceContainerHighest = if (amoled) Color(0xFF12151A) else Color(0xFF32353B)
    )
}

private fun ColorScheme.withAmoledOverride(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF050608),
    surfaceContainer = Color(0xFF08090C),
    surfaceContainerHigh = Color(0xFF0E1014),
    surfaceContainerHighest = Color(0xFF12151A)
)

@Composable
fun ThermalSentinelTheme(
    preferences: UiPreferences,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val useDark = when (preferences.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val baseScheme = when {
        preferences.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useDark -> darkScheme(preferences.accent, preferences.amoled)
        else -> lightScheme(preferences.accent)
    }

    val effectiveScheme = if (useDark && preferences.amoled) {
        baseScheme.withAmoledOverride()
    } else {
        baseScheme
    }

    MaterialTheme(
        colorScheme = effectiveScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
