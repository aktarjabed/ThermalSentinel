package com.thermalsentinel.engine.monitoring

import com.thermalsentinel.engine.alert.AlertLevel
import com.thermalsentinel.engine.domain.SampleReason
import com.thermalsentinel.engine.domain.ThermalStatusBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SamplingPolicyTest {

    private fun context(
        alertLevel: AlertLevel = AlertLevel.NORMAL,
        onExternalPower: Boolean = false,
        screenInteractive: Boolean = true,
        thermalStatus: ThermalStatusBand? = ThermalStatusBand.NONE,
        batteryPercent: Int? = 80
    ) = SamplingContext(alertLevel, onExternalPower, screenInteractive, thermalStatus, batteryPercent)

    @Test
    fun normalCadenceIsTheDefault() {
        val decision = SamplingPolicy.decide(context())
        assertEquals(SamplingPolicy.NORMAL_INTERVAL_MS, decision.intervalMillis)
        assertEquals(SampleReason.SCHEDULED_NORMAL, decision.reason)
        assertTrue(decision.explanation.isNotBlank())
    }

    @Test
    fun criticalAlertTakesPriorityOverEverything() {
        val decision = SamplingPolicy.decide(
            context(
                alertLevel = AlertLevel.CRITICAL,
                onExternalPower = true,
                screenInteractive = false,
                thermalStatus = ThermalStatusBand.SEVERE,
                batteryPercent = 5
            )
        )
        assertEquals(SamplingPolicy.EVENT_INTERVAL_MS, decision.intervalMillis)
    }

    @Test
    fun severePlatformStatusAcceleratesSampling() {
        val decision = SamplingPolicy.decide(context(thermalStatus = ThermalStatusBand.SEVERE))
        assertEquals(SamplingPolicy.EVENT_INTERVAL_MS, decision.intervalMillis)
    }

    @Test
    fun warningAlertAcceleratesSampling() {
        val decision = SamplingPolicy.decide(context(alertLevel = AlertLevel.WARNING))
        assertEquals(SamplingPolicy.ELEVATED_INTERVAL_MS, decision.intervalMillis)
    }

    @Test
    fun moderatePlatformStatusAcceleratesSampling() {
        val decision = SamplingPolicy.decide(context(thermalStatus = ThermalStatusBand.MODERATE))
        assertEquals(SamplingPolicy.ELEVATED_INTERVAL_MS, decision.intervalMillis)
    }

    @Test
    fun chargingCadenceAppliesOnExternalPower() {
        val decision = SamplingPolicy.decide(context(onExternalPower = true))
        assertEquals(SamplingPolicy.CHARGING_INTERVAL_MS, decision.intervalMillis)
        assertEquals(SampleReason.SCHEDULED_CHARGING, decision.reason)
    }

    @Test
    fun screenOffRelaxesSampling() {
        val decision = SamplingPolicy.decide(context(screenInteractive = false))
        assertEquals(SamplingPolicy.SCREEN_OFF_INTERVAL_MS, decision.intervalMillis)
    }

    @Test
    fun lowBatteryUnpluggedRelaxesSamplingFurther() {
        val decision = SamplingPolicy.decide(context(batteryPercent = 10))
        assertEquals(SamplingPolicy.BATTERY_SAVER_INTERVAL_MS, decision.intervalMillis)
    }

    @Test
    fun aChargingDeviceIsNotTreatedAsLowBattery() {
        val decision = SamplingPolicy.decide(context(onExternalPower = true, batteryPercent = 8))
        assertEquals(SamplingPolicy.CHARGING_INTERVAL_MS, decision.intervalMillis)
    }

    @Test
    fun recoveryStateKeepsTheAcceleratedCadenceUntilConfirmedNormal() {
        val decision = SamplingPolicy.decide(context(alertLevel = AlertLevel.RECOVERY))

        assertEquals(SamplingPolicy.ELEVATED_INTERVAL_MS, decision.intervalMillis)
        assertTrue(decision.explanation.contains("recovering"))
    }

    @Test
    fun unknownThermalStatusDoesNotAccelerateSampling() {
        // UNKNOWN means "no information", so it must not be read as elevated.
        val decision = SamplingPolicy.decide(context(thermalStatus = ThermalStatusBand.UNKNOWN))
        assertEquals(SamplingPolicy.NORMAL_INTERVAL_MS, decision.intervalMillis)
    }

    @Test
    fun everyDecisionRespectsTheFloor() {
        val allStatuses = listOf(null) + ThermalStatusBand.entries.toList()
        val levels = AlertLevel.entries.toList()
        for (status in allStatuses) {
            for (level in levels) {
                for (charging in listOf(true, false)) {
                    for (interactive in listOf(true, false)) {
                        val context = context(
                            alertLevel = level,
                            onExternalPower = charging,
                            screenInteractive = interactive,
                            thermalStatus = status
                        )
                        assertTrue(
                            "interval below the floor for $context",
                            SamplingPolicy.intervalMillis(context) >= SamplingPolicy.MIN_INTERVAL_MS
                        )
                        assertTrue(SamplingPolicy.decide(context).explanation.isNotBlank())
                    }
                }
            }
        }
    }
}
