package com.thermalsentinel.engine.domain

/**
 * Types of rows written to the local event timeline.
 *
 * The timeline is the honest record of what the engine observed and decided; it
 * is also how the app explains missing data (see [GAP_DETECTED]) instead of
 * drawing a straight line across it.
 */
enum class ThermalEventType(val storageValue: String, val label: String) {
    MONITORING_STARTED("monitoring_started", "Monitoring started"),
    MONITORING_STOPPED("monitoring_stopped", "Monitoring stopped"),
    ALERT_RAISED("alert_raised", "Alert raised"),
    ALERT_REMINDER("alert_reminder", "Alert repeated after cooldown"),
    ALERT_CLEARED("alert_cleared", "Alert cleared"),
    HEATING_EVENT("heating_event", "Heating event"),
    COOLING_EVENT("cooling_event", "Cooling event"),
    CHARGING_STARTED("charging_started", "Charging started"),
    CHARGING_STOPPED("charging_stopped", "Charging stopped"),
    GAP_DETECTED("gap_detected", "Sampling gap"),
    RETENTION_COMPACTED("retention_compacted", "History compacted");

    companion object {
        fun fromStorage(value: String?): ThermalEventType? =
            entries.firstOrNull { it.storageValue == value }
    }
}
