package com.thermalsentinel.engine.alert

import com.thermalsentinel.engine.domain.ThermalStatusBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The alert engine is the part of the app most able to annoy a user or to lie to
 * one, so its behaviour is pinned here: escalation, hysteresis, cooldown and the
 * absent-data hold.
 */
class AlertEngineTest {

    private val rules = ThermalAlertRules.DEFAULT
    private val t0 = 1_700_000_000_000L
    private val minute = 60_000L

    private fun input(
        temperatureC: Float? = null,
        status: ThermalStatusBand? = ThermalStatusBand.NONE,
        onExternalPower: Boolean = false
    ) = AlertInput(temperatureC, status, onExternalPower)

    @Test
    fun normalReadingProducesNoActions() {
        val evaluation = AlertEngine.evaluate(AlertState(), rules, input(temperatureC = 35.5f), t0)

        assertEquals(AlertLevel.NORMAL, evaluation.state.level)
        assertTrue(evaluation.actions.isEmpty())
        assertEquals(1, evaluation.state.evaluations)
    }

    @Test
    fun warningThresholdEscalatesAndNotifiesImmediately() {
        val evaluation = AlertEngine.evaluate(AlertState(), rules, input(temperatureC = 40.5f), t0)

        assertEquals(AlertLevel.WARNING, evaluation.state.level)
        assertEquals(AlertTrigger.BATTERY_TEMPERATURE, evaluation.state.trigger)
        assertEquals(AlertLevel.WARNING, evaluation.state.lastNotifiedLevel)
        assertEquals(t0, evaluation.state.lastNotifiedAtMillis)
        val notify = evaluation.actions.filterIsInstance<AlertAction.Notify>().single()
        assertEquals(AlertLevel.WARNING, notify.level)
        assertFalse(notify.isReminder)
    }

    @Test
    fun aSingleCriticalReadingSkipsTheWarningStep() {
        val evaluation = AlertEngine.evaluate(AlertState(), rules, input(temperatureC = 48.5f), t0)

        assertEquals(AlertLevel.CRITICAL, evaluation.state.level)
        assertEquals(AlertLevel.CRITICAL, evaluation.state.lastNotifiedLevel)
    }

    @Test
    fun hysteresisHoldsTheWarningUntilTheRecoveryLevel() {
        val previous = AlertState(
            level = AlertLevel.WARNING,
            trigger = AlertTrigger.BATTERY_TEMPERATURE,
            sinceMillis = t0,
            lastNotifiedLevel = AlertLevel.WARNING,
            lastNotifiedAtMillis = t0
        )

        // 39.5 °C is below the 40 °C warning threshold but above the 39 °C recovery
        // level, so the state must not come down yet.
        val evaluation = AlertEngine.evaluate(previous, rules, input(temperatureC = 39.5f), t0 + 5 * minute)

        assertEquals(AlertLevel.WARNING, evaluation.state.level)
        assertTrue(evaluation.actions.isEmpty())
        assertTrue(evaluation.explanation.contains("held by hysteresis"))
    }

    @Test
    fun clearingIsAlwaysAnnounced() {
        val previous = AlertState(
            level = AlertLevel.WARNING,
            trigger = AlertTrigger.BATTERY_TEMPERATURE,
            sinceMillis = t0,
            lastNotifiedLevel = AlertLevel.WARNING,
            lastNotifiedAtMillis = t0
        )

        val evaluation = AlertEngine.evaluate(previous, rules, input(temperatureC = 38f), t0 + 5 * minute)

        assertEquals(AlertLevel.NORMAL, evaluation.state.level)
        assertEquals(AlertLevel.NORMAL, evaluation.state.lastNotifiedLevel)
        val cleared = evaluation.actions.filterIsInstance<AlertAction.Cleared>().single()
        assertEquals(AlertLevel.WARNING, cleared.previousLevel)
    }

    @Test
    fun cooldownSuppressesRepeatsButProducesAReminderAfterwards() {
        val previous = AlertState(
            level = AlertLevel.WARNING,
            trigger = AlertTrigger.BATTERY_TEMPERATURE,
            sinceMillis = t0,
            lastNotifiedLevel = AlertLevel.WARNING,
            lastNotifiedAtMillis = t0
        )

        val tooSoon = AlertEngine.evaluate(previous, rules, input(temperatureC = 41f), t0 + 5 * minute)
        assertTrue(tooSoon.actions.isEmpty())

        val afterCooldown = AlertEngine.evaluate(previous, rules, input(temperatureC = 41f), t0 + 16 * minute)
        val notify = afterCooldown.actions.filterIsInstance<AlertAction.Notify>().single()
        assertTrue(notify.isReminder)
        assertEquals(AlertLevel.WARNING, notify.level)
    }

    @Test
    fun cooldownNeverSwallowsAnEscalation() {
        val previous = AlertState(
            level = AlertLevel.WARNING,
            trigger = AlertTrigger.BATTERY_TEMPERATURE,
            sinceMillis = t0,
            lastNotifiedLevel = AlertLevel.WARNING,
            lastNotifiedAtMillis = t0
        )

        val evaluation = AlertEngine.evaluate(previous, rules, input(temperatureC = 49f), t0 + minute)

        assertEquals(AlertLevel.CRITICAL, evaluation.state.level)
        val notify = evaluation.actions.filterIsInstance<AlertAction.Notify>().single()
        assertEquals(AlertLevel.CRITICAL, notify.level)
        assertFalse(notify.isReminder)
    }

    @Test
    fun absentDataHoldsTheStateInsteadOfRecovering() {
        val previous = AlertState(
            level = AlertLevel.CRITICAL,
            trigger = AlertTrigger.BATTERY_TEMPERATURE,
            sinceMillis = t0,
            lastNotifiedLevel = AlertLevel.CRITICAL,
            lastNotifiedAtMillis = t0
        )

        val evaluation = AlertEngine.evaluate(
            previous,
            rules,
            AlertInput(temperatureC = null, thermalStatus = null, onExternalPower = false),
            t0 + minute
        )

        assertEquals(AlertLevel.CRITICAL, evaluation.state.level)
        assertTrue(evaluation.actions.isEmpty())
        assertTrue(evaluation.explanation.contains("state held"))
    }

    @Test
    fun selectedStatusBandRaisesAnAlertWithoutHighTemperature() {
        val evaluation = AlertEngine.evaluate(
            AlertState(),
            rules,
            input(temperatureC = 35f, status = ThermalStatusBand.SEVERE),
            t0
        )

        assertEquals(AlertLevel.WARNING, evaluation.state.level)
        assertEquals(AlertTrigger.THERMAL_STATUS, evaluation.state.trigger)
    }

    @Test
    fun criticalStatusBandRaisesCritical() {
        val evaluation = AlertEngine.evaluate(
            AlertState(),
            rules,
            input(temperatureC = 35f, status = ThermalStatusBand.CRITICAL),
            t0
        )

        assertEquals(AlertLevel.CRITICAL, evaluation.state.level)
    }

    @Test
    fun unselectedStatusBandDoesNotRaiseAnything() {
        val quietRules = rules.copy(statusBandTriggers = setOf(ThermalStatusBand.SHUTDOWN))

        val evaluation = AlertEngine.evaluate(
            AlertState(),
            quietRules,
            input(temperatureC = 35f, status = ThermalStatusBand.LIGHT),
            t0
        )

        assertEquals(AlertLevel.NORMAL, evaluation.state.level)
        assertTrue(evaluation.actions.isEmpty())
    }

    @Test
    fun chargingRuleUsesItsOwnThresholdAndTrigger() {
        val chargingRules = rules.copy(chargingWarningEnabled = true, chargingWarningThresholdC = 42f)

        val charging = AlertEngine.evaluate(
            AlertState(),
            chargingRules,
            input(temperatureC = 42.5f, onExternalPower = true),
            t0
        )
        assertEquals(AlertLevel.WARNING, charging.state.level)
        assertEquals(AlertTrigger.CHARGING_TEMPERATURE, charging.state.trigger)

        // Off external power the ordinary 40 °C warning threshold applies, and the
        // trigger records that it was the battery temperature, not charging heat.
        val battery = AlertEngine.evaluate(
            AlertState(),
            chargingRules,
            input(temperatureC = 41f, onExternalPower = false),
            t0
        )
        assertEquals(AlertTrigger.BATTERY_TEMPERATURE, battery.state.trigger)
    }

    @Test
    fun disabledTemperatureRulesDoNotFireOnTemperatureAlone() {
        val quietRules = rules.copy(
            warningEnabled = false,
            criticalEnabled = false,
            statusBandTriggers = emptySet()
        )

        val hot = AlertEngine.evaluate(AlertState(), quietRules, input(temperatureC = 70f), t0)

        assertEquals(AlertLevel.NORMAL, hot.state.level)
        assertTrue(hot.actions.isEmpty())
    }

    @Test
    fun sinceTimestampTracksTheCurrentLevel() {
        val escalation = AlertEngine.evaluate(AlertState(), rules, input(temperatureC = 40.5f), t0)
        assertEquals(t0, escalation.state.sinceMillis)

        val held = AlertEngine.evaluate(escalation.state, rules, input(temperatureC = 41f), t0 + 5 * minute)
        assertEquals(t0, held.state.sinceMillis)

        val cleared = AlertEngine.evaluate(held.state, rules, input(temperatureC = 30f), t0 + 10 * minute)
        assertEquals(t0 + 10 * minute, cleared.state.sinceMillis)
    }
}
