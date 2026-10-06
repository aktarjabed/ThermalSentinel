package com.thermalsentinel.engine.alert

import com.thermalsentinel.engine.domain.ThermalStatusBand

/**
 * Result of validating a rule set. The Alert Rules screen renders these instead
 * of silently accepting an incoherent configuration.
 */
enum class RuleViolation(val message: String) {
    WARNING_OUT_OF_RANGE("Warning threshold must be between 30 °C and 60 °C."),
    CRITICAL_OUT_OF_RANGE("Critical threshold must be between 30 °C and 70 °C."),
    CRITICAL_NOT_ABOVE_WARNING("Every critical threshold must be at least 1 °C above the warning threshold."),
    HYSTERESIS_OUT_OF_RANGE("The hysteresis band must be between 0 °C and 5 °C."),
    HYSTERESIS_EXCEEDS_BAND(
        "The hysteresis band cannot be wider than the gap between the warning and critical thresholds, " +
            "otherwise the two recovery levels would cross."
    ),
    COOLDOWN_OUT_OF_RANGE("Alert cooldown must be between 5 and 120 minutes."),
    CHARGING_THRESHOLD_OUT_OF_RANGE("Charging warning threshold must be between 30 °C and 60 °C.")
}

/**
 * Persisted alert configuration.
 *
 * Two independent ideas are deliberately kept apart, because conflating them is
 * what produces notification spam:
 *
 *  - **Hysteresis** ([hysteresisC]) decides when the *state* comes back down.
 *    With a 40 °C warning threshold and a 1 °C band, the warning clears at 39 °C.
 *  - **Cooldown** ([cooldownMinutes]) decides how often a *notification* may
 *    repeat while the state stays escalated.
 *
 * A cooldown alone is not hysteresis: without recovery thresholds a device
 * sitting at 40.1 °C would re-alert every cooldown forever.
 */
data class ThermalAlertRules(
    val warningEnabled: Boolean = true,
    val warningThresholdC: Float = 40f,
    val criticalEnabled: Boolean = true,
    val criticalThresholdC: Float = 48f,
    val hysteresisC: Float = 1f,
    val cooldownMinutes: Int = 15,
    val chargingWarningEnabled: Boolean = false,
    val chargingWarningThresholdC: Float = 42f,
    val statusBandTriggers: Set<ThermalStatusBand> = setOf(
        ThermalStatusBand.SEVERE,
        ThermalStatusBand.CRITICAL
    )
) {
    /** Temperature at or below which a warning clears. */
    val warningRecoveryC: Float get() = warningThresholdC - hysteresisC

    /** Temperature at or below which a critical alert de-escalates to warning. */
    val criticalRecoveryC: Float get() = criticalThresholdC - hysteresisC

    /** The warning threshold that applies right now, honouring the charging rule. */
    fun effectiveWarningThresholdC(onExternalPower: Boolean): Float =
        if (onExternalPower && chargingWarningEnabled) chargingWarningThresholdC else warningThresholdC

    /** The warning threshold's recovery level, matching [effectiveWarningThresholdC]. */
    fun effectiveWarningRecoveryC(onExternalPower: Boolean): Float =
        effectiveWarningThresholdC(onExternalPower) - hysteresisC

    val statusTriggersEnabled: Boolean
        get() = statusBandTriggers.any { it.selectable }

    val anyTemperatureTriggerEnabled: Boolean
        get() = warningEnabled || criticalEnabled

    /**
     * The lowest platform level that fires a status alert, or null when the user
     * has not selected any band. Used for the explanatory line under the chips.
     */
    val lowestTriggeredStatusLevel: Int?
        get() = statusBandTriggers.filter { it.selectable }.minOfOrNull { it.androidLevel }

    fun validate(): List<RuleViolation> = buildList {
        if (warningThresholdC < 30f || warningThresholdC > 60f) add(RuleViolation.WARNING_OUT_OF_RANGE)
        if (criticalThresholdC < 30f || criticalThresholdC > 70f) add(RuleViolation.CRITICAL_OUT_OF_RANGE)
        if (criticalThresholdC - warningThresholdC < 1f) add(RuleViolation.CRITICAL_NOT_ABOVE_WARNING)
        if (hysteresisC < 0f || hysteresisC > 5f) add(RuleViolation.HYSTERESIS_OUT_OF_RANGE)
        if (hysteresisC > criticalThresholdC - warningThresholdC) add(RuleViolation.HYSTERESIS_EXCEEDS_BAND)
        if (cooldownMinutes < 5 || cooldownMinutes > 120) add(RuleViolation.COOLDOWN_OUT_OF_RANGE)
        if (chargingWarningThresholdC < 30f || chargingWarningThresholdC > 60f) {
            add(RuleViolation.CHARGING_THRESHOLD_OUT_OF_RANGE)
        }
    }

    val isValid: Boolean get() = validate().isEmpty()

    /**
     * Clamps a candidate change back into a coherent rule set. Used by the UI so
     * a slider drag can never persist an invalid combination.
     */
    fun clamped(): ThermalAlertRules {
        val warning = warningThresholdC.coerceIn(30f, 60f)
        val critical = criticalThresholdC.coerceIn(30f, 70f).coerceAtLeast(warning + 1f)
        val band = hysteresisC.coerceIn(0f, 5f).coerceAtMost(critical - warning)
        return copy(
            warningThresholdC = warning,
            criticalThresholdC = critical,
            hysteresisC = band,
            cooldownMinutes = cooldownMinutes.coerceIn(5, 120),
            chargingWarningThresholdC = chargingWarningThresholdC.coerceIn(30f, 60f),
            statusBandTriggers = statusBandTriggers.filter { it.selectable }.toSet()
        )
    }

    companion object {
        val DEFAULT = ThermalAlertRules()
    }
}
