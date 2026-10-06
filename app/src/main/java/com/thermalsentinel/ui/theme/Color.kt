package com.thermalsentinel.ui.theme

import androidx.compose.ui.graphics.Color
import com.thermalsentinel.ui.data.AccentPreset

internal data class AccentSeed(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val tertiary: Color,
    val primaryDark: Color,
    val onPrimaryDark: Color,
    val primaryContainerDark: Color,
    val onPrimaryContainerDark: Color,
    val secondaryDark: Color,
    val tertiaryDark: Color
)

internal fun AccentPreset.seed(): AccentSeed = when (this) {
    AccentPreset.BLUE -> AccentSeed(
        primary = Color(0xFF315FAF),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD9E2FF),
        onPrimaryContainer = Color(0xFF001A41),
        secondary = Color(0xFF555F71),
        tertiary = Color(0xFF715573),
        primaryDark = Color(0xFFB0C6FF),
        onPrimaryDark = Color(0xFF0A2F6E),
        primaryContainerDark = Color(0xFF1B3E85),
        onPrimaryContainerDark = Color(0xFFD9E2FF),
        secondaryDark = Color(0xFFBEC7DC),
        tertiaryDark = Color(0xFFDEB9D6)
    )
    AccentPreset.VIOLET -> AccentSeed(
        primary = Color(0xFF6650A4),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE9DDFF),
        onPrimaryContainer = Color(0xFF22005E),
        secondary = Color(0xFF655A6F),
        tertiary = Color(0xFF80516C),
        primaryDark = Color(0xFFCFBCFF),
        onPrimaryDark = Color(0xFF381E72),
        primaryContainerDark = Color(0xFF4F378B),
        onPrimaryContainerDark = Color(0xFFE9DDFF),
        secondaryDark = Color(0xFFCBC2DB),
        tertiaryDark = Color(0xFFEFB8C8)
    )
    AccentPreset.GREEN -> AccentSeed(
        primary = Color(0xFF3D6945),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFC0EBC5),
        onPrimaryContainer = Color(0xFF002108),
        secondary = Color(0xFF536558),
        tertiary = Color(0xFF3E665F),
        primaryDark = Color(0xFFA5D2AA),
        onPrimaryDark = Color(0xFF0A3814),
        primaryContainerDark = Color(0xFF25502F),
        onPrimaryContainerDark = Color(0xFFC0EBC5),
        secondaryDark = Color(0xFFB7CCBB),
        tertiaryDark = Color(0xFFA6CFC7)
    )
    AccentPreset.AMBER -> AccentSeed(
        primary = Color(0xFF745A00),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFE287),
        onPrimaryContainer = Color(0xFF241A00),
        secondary = Color(0xFF665E45),
        tertiary = Color(0xFF665A4C),
        primaryDark = Color(0xFFE6C34C),
        onPrimaryDark = Color(0xFF3D2E00),
        primaryContainerDark = Color(0xFF574400),
        onPrimaryContainerDark = Color(0xFFFFE287),
        secondaryDark = Color(0xFFD2C6A8),
        tertiaryDark = Color(0xFFD4C3B0)
    )
    AccentPreset.ROSE -> AccentSeed(
        primary = Color(0xFF9B4057),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFD9DF),
        onPrimaryContainer = Color(0xFF3F0012),
        secondary = Color(0xFF76565E),
        tertiary = Color(0xFF805634),
        primaryDark = Color(0xFFFFB1C0),
        onPrimaryDark = Color(0xFF5E1028),
        primaryContainerDark = Color(0xFF7D2A40),
        onPrimaryContainerDark = Color(0xFFFFD9DF),
        secondaryDark = Color(0xFFE5BDC5),
        tertiaryDark = Color(0xFFF3B88C)
    )
}
