package com.thermalsentinel.ui.data

/**
 * Mirror of the platform thermal-status bands reported by
 * `PowerManager.getCurrentThermalStatus()` (API 29+).
 *
 * [androidLevel] matches the `PowerManager.THERMAL_STATUS_*` integer constants.
 * [selectable] is false for bands that must never appear in the Alert Rules
 * chooser: [NONE] because it represents a normal state, and [UNKNOWN] because
 * the engine has no information about what actually happened.
 *
 * Frozen contract.
 */
enum class ThermalStatusBand(
    val storageValue: String,
    val label: String,
    val androidLevel: Int,
    val selectable: Boolean = true
) {
    UNKNOWN("unknown", "Unknown", -1, selectable = false),
    NONE("none", "None", 0, selectable = false),
    LIGHT("light", "Light", 1),
    MODERATE("moderate", "Moderate", 2),
    SEVERE("severe", "Severe", 3),
    CRITICAL("critical", "Critical", 4),
    EMERGENCY("emergency", "Emergency", 5),
    SHUTDOWN("shutdown", "Shutdown", 6);

    companion object {
        fun fromStorage(value: String?): ThermalStatusBand =
            entries.firstOrNull { it.storageValue == value } ?: UNKNOWN

        /**
         * Maps an integer platform thermal level onto this enum. Any value
         * that does not correspond to a known level — including negative
         * values and any future constant above [SHUTDOWN] — returns
         * [UNKNOWN]. The engine is expected to surface UNKNOWN as
         * "thermal status unavailable" rather than silently treating it as
         * normal.
         */
        fun fromAndroidLevel(level: Int): ThermalStatusBand =
            entries.firstOrNull { it.androidLevel == level } ?: UNKNOWN
    }
}
