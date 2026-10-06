package com.thermalsentinel.engine.alert

import com.thermalsentinel.engine.domain.ThermalStatusBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalAlertRulesTest {

    @Test
    fun defaultsAreValid() {
        assertTrue(ThermalAlertRules.DEFAULT.isValid)
        assertTrue(ThermalAlertRules.DEFAULT.validate().isEmpty())
    }

    @Test
    fun recoveryLevelsSitOneHysteresisBandBelowTheThresholds() {
        val rules = ThermalAlertRules.DEFAULT
        assertEquals(39f, rules.warningRecoveryC, 0.001f)
        assertEquals(47f, rules.criticalRecoveryC, 0.001f)
    }

    @Test
    fun outOfRangeWarningIsReported() {
        val rules = ThermalAlertRules.DEFAULT.copy(warningThresholdC = 25f)
        assertTrue(rules.validate().contains(RuleViolation.WARNING_OUT_OF_RANGE))
    }

    @Test
    fun criticalMustSitAboveWarning() {
        val rules = ThermalAlertRules.DEFAULT.copy(warningThresholdC = 48f, criticalThresholdC = 48f)
        assertTrue(rules.validate().contains(RuleViolation.CRITICAL_NOT_ABOVE_WARNING))
    }

    @Test
    fun hysteresisCannotBeWiderThanTheThresholdGap() {
        val rules = ThermalAlertRules.DEFAULT.copy(
            warningThresholdC = 40f,
            criticalThresholdC = 44f,
            hysteresisC = 5f
        )
        assertTrue(rules.validate().contains(RuleViolation.HYSTERESIS_EXCEEDS_BAND))
    }

    @Test
    fun cooldownHasABoundedRange() {
        assertTrue(
            ThermalAlertRules.DEFAULT.copy(cooldownMinutes = 2)
                .validate()
                .contains(RuleViolation.COOLDOWN_OUT_OF_RANGE)
        )
        assertTrue(
            ThermalAlertRules.DEFAULT.copy(cooldownMinutes = 500)
                .validate()
                .contains(RuleViolation.COOLDOWN_OUT_OF_RANGE)
        )
    }

    @Test
    fun clampingAlwaysProducesAValidRuleSet() {
        val hostile = ThermalAlertRules(
            warningThresholdC = 90f,
            criticalThresholdC = 10f,
            hysteresisC = 99f,
            cooldownMinutes = 0,
            chargingWarningThresholdC = -20f,
            statusBandTriggers = setOf(ThermalStatusBand.NONE, ThermalStatusBand.UNKNOWN)
        ).clamped()

        assertTrue(hostile.validate().isEmpty())
        assertEquals(60f, hostile.warningThresholdC, 0.001f)
        // Clamping raises the critical threshold to warning + 1 rather than to the
        // top of its own range, so the pair stays as close as the rules allow.
        assertEquals(61f, hostile.criticalThresholdC, 0.001f)
        assertEquals(1f, hostile.hysteresisC, 0.001f)
        assertEquals(5, hostile.cooldownMinutes)
        assertEquals(30f, hostile.chargingWarningThresholdC, 0.001f)
        assertTrue(hostile.statusBandTriggers.isEmpty())
    }

    @Test
    fun chargingThresholdReplacesTheWarningThresholdOnlyWhileCharging() {
        val rules = ThermalAlertRules.DEFAULT.copy(
            chargingWarningEnabled = true,
            chargingWarningThresholdC = 42f
        )

        assertEquals(40f, rules.effectiveWarningThresholdC(onExternalPower = false), 0.001f)
        assertEquals(42f, rules.effectiveWarningThresholdC(onExternalPower = true), 0.001f)
        assertEquals(41f, rules.effectiveWarningRecoveryC(onExternalPower = true), 0.001f)
    }

    @Test
    fun lowestTriggeredStatusLevelIsTheMinimumSelectedPlatformLevel() {
        assertEquals(3, ThermalAlertRules.DEFAULT.lowestTriggeredStatusLevel)

        val onlyCritical = ThermalAlertRules.DEFAULT.copy(
            statusBandTriggers = setOf(ThermalStatusBand.CRITICAL)
        )
        assertEquals(4, onlyCritical.lowestTriggeredStatusLevel)

        val none = ThermalAlertRules.DEFAULT.copy(statusBandTriggers = emptySet())
        assertEquals(null, none.lowestTriggeredStatusLevel)
    }
}
