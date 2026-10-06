package com.thermalsentinel.ui.data

/**
 * Widget appearance variants for the planned home-screen providers.
 *
 * Frozen contract. Persistence and the widget configuration activity are
 * intentionally deferred; only the storage key and the parse fallback are
 * locked here so provider implementations can rely on stable values.
 */
enum class WidgetStyle(val storageValue: String, val label: String, val description: String) {
    SYSTEM(
        storageValue = "system",
        label = "System",
        description = "Follow the current app theme, including light, dark and AMOLED."
    ),
    THEMED(
        storageValue = "themed",
        label = "Themed",
        description = "Use the active Material color scheme. When dynamic color is off, " +
            "this matches the selected accent; when dynamic color is on, it follows " +
            "the wallpaper."
    ),
    TRANSPARENT(
        storageValue = "transparent",
        label = "Transparent",
        description = "Remove the widget background so the wallpaper remains visible."
    );

    companion object {
        fun fromStorage(value: String?): WidgetStyle =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}
