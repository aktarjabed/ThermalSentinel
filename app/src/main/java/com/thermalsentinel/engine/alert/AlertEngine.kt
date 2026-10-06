package com.thermalsentinel.engine.alert

import com.thermalsentinel.engine.domain.ThermalStatusBand

/** Alert state vocabulary. [rank] orders severity; NORMAL and RECOVERY are non-alert states. */
enum class AlertLevel(val storageValue: String, val label: String, val rank: Int) {
    NORMAL("normal", "Normal", 0),
    WARNING("warning", "Warning", 1),
    CRITICAL("critical", "Critical", 2),
    /** Below an alert threshold and inside the hysteresis/recovery band. */
    RECOVERY("recovery", "Recovering", 0);

    companion object {
        fun fromStorage(value: String?): AlertLevel =
            entries.firstOrNull { it.storageValue == value } ?: NORMAL
    }
}

/** What pushed the state up. Persisted so history can explain an alert. */
enum class AlertTrigger(val storageValue: String) {
    BATTERY_TEMPERATURE("battery_temperature"),
    CHARGING_TEMPERATURE("charging_temperature"),
    THERMAL_STATUS("thermal_status");

    companion object {
        fun fromStorage(value: String?): AlertTrigger? =
            entries.firstOrNull { it.storageValue == value }
    }
}

/**
 * Everything the engine knows at one evaluation instant.
 *
 * `null` means **not measured**, and is never treated as "normal": a missing
 * reading holds the current state instead of de-escalating it.
 */
data class AlertInput(
    val temperatureC: Float?,
    val thermalStatus: ThermalStatusBand?,
    val onExternalPower: Boolean
)

/**
 * Rolling alert state. Persisted across service restarts so that a reboot or a
 * service restart cannot be used to bypass a cooldown, and so that "since" times
 * survive.
 */
data class AlertState(
    val level: AlertLevel = AlertLevel.NORMAL,
    val trigger: AlertTrigger? = null,
    val sinceMillis: Long = 0L,
    val lastNotifiedLevel: AlertLevel = AlertLevel.NORMAL,
    val lastNotifiedAtMillis: Long = 0L,
    val evaluations: Int = 0
)

/** Side effects the engine asks the caller to perform. */
sealed interface AlertAction {
    data class Notify(
        val level: AlertLevel,
        val trigger: AlertTrigger,
        val temperatureC: Float?,
        val thermalStatus: ThermalStatusBand?,
        /** True when this is a repeat after the cooldown expired, not a new escalation. */
        val isReminder: Boolean
    ) : AlertAction

    /** State returned to normal; the caller may post a short "back in range" note. */
    data class Cleared(
        val previousLevel: AlertLevel,
        val temperatureC: Float?,
        val thermalStatus: ThermalStatusBand?
    ) : AlertAction

    /**
     * The condition stopped being an alert, but the recovery threshold has not
     * been confirmed yet. The caller must take the ended alert's notification out
     * of the shade: it describes a state the engine has already left, and leaving
     * a high-priority warning visible through the whole recovery phase would be
     * reporting an alert that is no longer raised.
     *
     * No replacement notification is posted — the "back in range" message belongs
     * to [Cleared], and a second notification per recovery would train the user to
     * ignore this channel.
     */
    data class AlertEnded(
        val previousLevel: AlertLevel,
        val temperatureC: Float?,
        val thermalStatus: ThermalStatusBand?
    ) : AlertAction
}

data class AlertEvaluation(
    val state: AlertState,
    val actions: List<AlertAction>,
    /** Human-readable reason the state did or did not change. Shown in Diagnostics. */
    val explanation: String
)

/**
 * Pure alert state machine.
 *
 * Design notes that matter:
 *
 *  - **Monotonic escalation**: the state may jump from NORMAL straight to
 *    CRITICAL, because a single 48 °C reading is already critical.
 *  - **Explicit recovery phase**: a warning does not clear until its warning
 *    recovery threshold is reached. A critical condition first steps down to
 *    WARNING when it is still in the warning range, or to RECOVERY when it has
 *    cooled below that range; RECOVERY must reach the warning recovery threshold
 *    before returning to NORMAL. Entering RECOVERY emits [AlertAction.AlertEnded]
 *    so the visible alert notification does not outlive the condition that raised
 *    it; the "back in range" notification still waits for NORMAL.
 *  - **Cooldown applies to notifications, not to state**: while escalated, a
 *    repeat notification is allowed only after the cooldown expires. Escalations
 *    always notify — a cooldown must never swallow a critical transition.
 *  - **Absent data holds**: if the temperature is unavailable and the thermal
 *    status is unavailable, the state is held and no action is taken. The
 *    engine does not "recover" because the sensor stopped reporting.
 */
object AlertEngine {

    fun evaluate(
        previous: AlertState,
        rules: ThermalAlertRules,
        input: AlertInput,
        nowMillis: Long
    ): AlertEvaluation {
        val evaluations = previous.evaluations + 1

        val temperature = input.temperatureC
        val status = input.thermalStatus

        if (temperature == null && status == null) {
            return AlertEvaluation(
                state = previous.copy(evaluations = evaluations),
                actions = emptyList(),
                explanation = "No temperature or thermal-status reading; state held."
            )
        }

        // ---- 1. What the current readings alone would imply. ----------------
        var implied = AlertLevel.NORMAL
        var impliedTrigger: AlertTrigger? = null

        if (temperature != null) {
            val warningThreshold = rules.effectiveWarningThresholdC(input.onExternalPower)
            val warningTrigger = if (input.onExternalPower && rules.chargingWarningEnabled) {
                AlertTrigger.CHARGING_TEMPERATURE
            } else {
                AlertTrigger.BATTERY_TEMPERATURE
            }
            if (rules.criticalEnabled && temperature >= rules.criticalThresholdC) {
                implied = AlertLevel.CRITICAL
                impliedTrigger = AlertTrigger.BATTERY_TEMPERATURE
            } else if (rules.warningEnabled && temperature >= warningThreshold) {
                implied = AlertLevel.WARNING
                impliedTrigger = warningTrigger
            }
        }

        if (status != null && status.isPlatformReported && rules.statusTriggersEnabled) {
            val fires = rules.statusBandTriggers.any { it.selectable && status.androidLevel >= it.androidLevel }
            if (fires) {
                val statusLevel = if (status.androidLevel >= ThermalStatusBand.CRITICAL.androidLevel) {
                    AlertLevel.CRITICAL
                } else {
                    AlertLevel.WARNING
                }
                if (statusLevel.rank > implied.rank) {
                    implied = statusLevel
                    impliedTrigger = AlertTrigger.THERMAL_STATUS
                }
            }
        }

        // ---- 2. Hysteresis and the explicit recovery phase. -----------------
        var level = implied
        var trigger = impliedTrigger
        var heldByHysteresis = false

        when (previous.level) {
            AlertLevel.NORMAL -> Unit

            AlertLevel.WARNING -> when {
                implied == AlertLevel.CRITICAL -> Unit
                temperature == null -> {
                    level = AlertLevel.WARNING // no temperature evidence to clear with
                    heldByHysteresis = true
                }
                implied == AlertLevel.WARNING -> Unit
                temperature > rules.effectiveWarningRecoveryC(input.onExternalPower) -> {
                    level = AlertLevel.WARNING
                    heldByHysteresis = true
                }
                else -> level = AlertLevel.RECOVERY
            }

            AlertLevel.CRITICAL -> when {
                implied == AlertLevel.CRITICAL -> Unit
                temperature == null -> {
                    level = AlertLevel.CRITICAL // absent temperature cannot clear a critical temperature alert
                    heldByHysteresis = true
                }
                temperature > rules.criticalRecoveryC -> {
                    level = AlertLevel.CRITICAL
                    heldByHysteresis = true
                }
                implied == AlertLevel.WARNING -> Unit // recovered below critical, but still in the warning range
                else -> level = AlertLevel.RECOVERY
            }

            AlertLevel.RECOVERY -> when {
                implied == AlertLevel.CRITICAL || implied == AlertLevel.WARNING -> Unit
                temperature == null -> {
                    level = AlertLevel.RECOVERY
                    heldByHysteresis = true
                }
                temperature <= rules.effectiveWarningRecoveryC(input.onExternalPower) -> {
                    level = AlertLevel.NORMAL
                }
                else -> level = AlertLevel.RECOVERY
            }
        }

        // Preserve the cause through the recovery phase for diagnostics/history.
        if (level != AlertLevel.NORMAL && trigger == null) trigger = previous.trigger

        // ---- 3. Notification gating. ----------------------------------------
        val actions = mutableListOf<AlertAction>()
        var lastNotifiedLevel = previous.lastNotifiedLevel
        var lastNotifiedAt = previous.lastNotifiedAtMillis
        val cooldownMillis = rules.cooldownMinutes * 60_000L

        when (level) {
            AlertLevel.NORMAL -> if (previous.level != AlertLevel.NORMAL) {
                // Fully recovered: always announced, never gated by cooldown.
                actions += AlertAction.Cleared(previous.level, temperature, status)
                lastNotifiedLevel = AlertLevel.NORMAL
            }

            AlertLevel.RECOVERY -> {
                // The active alert has ended, but recovery has not yet cleared.
                // Reset severity so a new warning/critical escalation notifies
                // immediately if the temperature rises again.
                lastNotifiedLevel = AlertLevel.NORMAL
                // Only when this evaluation *entered* recovery: an alert was
                // visible and is now over. Staying in recovery is not a new fact.
                val enteredRecoveryFromAlert =
                    previous.level == AlertLevel.WARNING || previous.level == AlertLevel.CRITICAL
                if (enteredRecoveryFromAlert) {
                    actions += AlertAction.AlertEnded(previous.level, temperature, status)
                }
            }

            AlertLevel.WARNING, AlertLevel.CRITICAL -> {
                val escalated = level.rank > lastNotifiedLevel.rank
                val cooldownExpired = nowMillis - lastNotifiedAt >= cooldownMillis
                val repeatAfterRecovery = lastNotifiedLevel == AlertLevel.NORMAL
                if (escalated || (cooldownExpired && (level == lastNotifiedLevel || repeatAfterRecovery))) {
                    actions += AlertAction.Notify(
                        level = level,
                        trigger = trigger ?: AlertTrigger.BATTERY_TEMPERATURE,
                        temperatureC = temperature,
                        thermalStatus = status,
                        isReminder = !escalated
                    )
                    lastNotifiedLevel = level
                    lastNotifiedAt = nowMillis
                }
            }
        }

        val explanation = buildString {
            append("level=").append(level.storageValue)
            append(", previous=").append(previous.level.storageValue)
            if (heldByHysteresis) append(", held by hysteresis")
            if (trigger != null) append(", trigger=").append(trigger.storageValue)
            if (temperature != null) append(", temperature=").append(temperature)
            if (status != null) append(", status=").append(status.storageValue)
        }

        return AlertEvaluation(
            state = AlertState(
                level = level,
                trigger = trigger,
                sinceMillis = if (level == previous.level) previous.sinceMillis else nowMillis,
                lastNotifiedLevel = lastNotifiedLevel,
                lastNotifiedAtMillis = lastNotifiedAt,
                evaluations = evaluations
            ),
            actions = actions,
            explanation = explanation
        )
    }
}
