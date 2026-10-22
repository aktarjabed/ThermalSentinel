package com.thermalsentinel.ui.data

import androidx.compose.runtime.compositionLocalOf

/** Current display unit. Stored temperatures and all rules remain Celsius internally. */
val LocalUseFahrenheit = compositionLocalOf { false }
