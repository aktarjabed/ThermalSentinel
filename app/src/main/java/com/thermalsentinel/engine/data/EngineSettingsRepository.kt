package com.thermalsentinel.engine.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.thermalsentinel.engine.alert.AlertLevel
import com.thermalsentinel.engine.alert.AlertState
import com.thermalsentinel.engine.alert.AlertTrigger
import com.thermalsentinel.engine.alert.ThermalAlertRules
import com.thermalsentinel.engine.domain.ThermalStatusBand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private const val TAG = "EngineSettingsRepository"

private val Context.engineDataStore by preferencesDataStore(name = "engine_settings")

/** Why the last monitoring session ended. Shown in Diagnostics, never guessed. */
enum class MonitoringStopReason(val storageValue: String, val label: String) {
    USER_REQUEST("user_request", "Stopped by the user"),
    PERMISSION_REVOKED("permission_revoked", "Notification permission revoked"),
    PLATFORM_STOPPED("platform_stopped", "Stopped by Android"),
    UNKNOWN("unknown", "Unknown");

    companion object {
        fun fromStorage(value: String?): MonitoringStopReason =
            entries.firstOrNull { it.storageValue == value } ?: UNKNOWN
    }
}

/**
 * Persistent engine configuration.
 *
 * Two facts are deliberately persisted rather than kept in memory:
 *
 *  - **Rules** are the user's configuration; losing them on process death would
 *    silently change what the engine alerts about.
 *  - **Monitoring intent** is what makes "resume monitoring when the app is
 *    opened" honest: the app remembers that the user wanted monitoring on rather
 *    than deciding for them.
 *
 * Writes are guarded the same way the UI preference store guards its own: a
 * failed disk write must not crash the monitoring service.
 */
class EngineSettingsRepository(private val context: Context) {

    private object Keys {
        val monitoringEnabled = booleanPreferencesKey("monitoring_enabled")
        val lastStopReason = stringPreferencesKey("monitoring_last_stop_reason")
        val lastStartedAt = longPreferencesKey("monitoring_last_started_at")
        val lastStoppedAt = longPreferencesKey("monitoring_last_stopped_at")
        val sessionCount = intPreferencesKey("monitoring_session_count")

        val warningEnabled = booleanPreferencesKey("rules_warning_enabled")
        val warningThresholdC = floatPreferencesKey("rules_warning_threshold_c")
        val criticalEnabled = booleanPreferencesKey("rules_critical_enabled")
        val criticalThresholdC = floatPreferencesKey("rules_critical_threshold_c")
        val hysteresisC = floatPreferencesKey("rules_hysteresis_c")
        val cooldownMinutes = intPreferencesKey("rules_cooldown_minutes")
        val chargingWarningEnabled = booleanPreferencesKey("rules_charging_enabled")
        val chargingWarningThresholdC = floatPreferencesKey("rules_charging_threshold_c")
        val statusBands = stringSetPreferencesKey("rules_status_bands")

        val alertLevel = stringPreferencesKey("alert_state_level")
        val alertTrigger = stringPreferencesKey("alert_state_trigger")
        val alertSince = longPreferencesKey("alert_state_since")
        val alertLastNotifiedLevel = stringPreferencesKey("alert_state_last_notified_level")
        val alertLastNotifiedAt = longPreferencesKey("alert_state_last_notified_at")
        val alertEvaluations = intPreferencesKey("alert_state_evaluations")
    }

    val monitoringEnabled: Flow<Boolean> = data().map { it[Keys.monitoringEnabled] ?: false }

    val rules: Flow<ThermalAlertRules> = data().map(::readRules)

    val lastStopReason: Flow<MonitoringStopReason> =
        data().map { MonitoringStopReason.fromStorage(it[Keys.lastStopReason]) }

    val lastStartedAtMillis: Flow<Long?> = data().map { it[Keys.lastStartedAt] }
    val lastStoppedAtMillis: Flow<Long?> = data().map { it[Keys.lastStoppedAt] }
    val sessionCount: Flow<Int> = data().map { it[Keys.sessionCount] ?: 0 }

    /**
     * Rolling alert state, persisted so that a service restart or a reboot cannot
     * silently reset a cooldown (or an active escalation) to NORMAL.
     */
    val alertState: Flow<AlertState> = data().map(::readAlertState)

    suspend fun alertStateNow(): AlertState = alertState.first()

    suspend fun saveAlertState(state: AlertState) = editSafely { prefs ->
        prefs[Keys.alertLevel] = state.level.storageValue
        state.trigger?.let { prefs[Keys.alertTrigger] = it.storageValue } ?: prefs.remove(Keys.alertTrigger)
        prefs[Keys.alertSince] = state.sinceMillis
        prefs[Keys.alertLastNotifiedLevel] = state.lastNotifiedLevel.storageValue
        prefs[Keys.alertLastNotifiedAt] = state.lastNotifiedAtMillis
        prefs[Keys.alertEvaluations] = state.evaluations
    }

    /** Non-collecting read used by the service, which has no lifecycle to bind to. */
    suspend fun monitoringEnabledNow(): Boolean = monitoringEnabled.first()

    suspend fun rulesNow(): ThermalAlertRules = rules.first()

    suspend fun setMonitoringEnabled(enabled: Boolean) = editSafely { prefs ->
        prefs[Keys.monitoringEnabled] = enabled
    }

    /**
     * Session bookkeeping, written by the service when it comes up.
     *
     * Note what is *not* here: `monitoring_enabled`. The user's intent is written
     * only by `MonitoringController`, on both the start and the stop path, so a
     * start recorded by the service can never land after a stop and re-arm
     * monitoring the user just switched off. This transaction only ever writes
     * facts about the session that has actually begun.
     */
    suspend fun recordSessionStarted(nowMillis: Long) = editSafely { prefs ->
        prefs[Keys.lastStartedAt] = nowMillis
        prefs[Keys.sessionCount] = (prefs[Keys.sessionCount] ?: 0) + 1
    }

    suspend fun recordMonitoringStopped(nowMillis: Long, reason: MonitoringStopReason) =
        editSafely { prefs ->
            prefs[Keys.monitoringEnabled] = false
            prefs[Keys.lastStoppedAt] = nowMillis
            prefs[Keys.lastStopReason] = reason.storageValue
        }

    /**
     * Single transactional update of the rule set. The UI calls this with a
     * `copy(...)` while dragging sliders, so an interrupted write can never
     * leave a half-updated configuration behind.
     */
    suspend fun updateRules(transform: (ThermalAlertRules) -> ThermalAlertRules) = editSafely { prefs ->
        val updated = transform(readRules(prefs)).clamped()
        prefs[Keys.warningEnabled] = updated.warningEnabled
        prefs[Keys.warningThresholdC] = updated.warningThresholdC
        prefs[Keys.criticalEnabled] = updated.criticalEnabled
        prefs[Keys.criticalThresholdC] = updated.criticalThresholdC
        prefs[Keys.hysteresisC] = updated.hysteresisC
        prefs[Keys.cooldownMinutes] = updated.cooldownMinutes
        prefs[Keys.chargingWarningEnabled] = updated.chargingWarningEnabled
        prefs[Keys.chargingWarningThresholdC] = updated.chargingWarningThresholdC
        prefs[Keys.statusBands] = updated.statusBandTriggers.map { it.storageValue }.toSet()
    }

    suspend fun resetRulesToDefaults() = updateRules { ThermalAlertRules.DEFAULT }

    private fun readAlertState(prefs: androidx.datastore.preferences.core.Preferences): AlertState =
        AlertState(
            level = AlertLevel.fromStorage(prefs[Keys.alertLevel]),
            trigger = AlertTrigger.fromStorage(prefs[Keys.alertTrigger]),
            sinceMillis = prefs[Keys.alertSince] ?: 0L,
            lastNotifiedLevel = AlertLevel.fromStorage(prefs[Keys.alertLastNotifiedLevel]),
            lastNotifiedAtMillis = prefs[Keys.alertLastNotifiedAt] ?: 0L,
            evaluations = prefs[Keys.alertEvaluations] ?: 0
        )

    private fun readRules(prefs: androidx.datastore.preferences.core.Preferences): ThermalAlertRules {
        val defaults = ThermalAlertRules.DEFAULT
        val bands = prefs[Keys.statusBands]
            ?.mapNotNull(ThermalStatusBand::fromStorage)
            ?.filter { it.selectable }
            ?.toSet()
            ?: defaults.statusBandTriggers
        return ThermalAlertRules(
            warningEnabled = prefs[Keys.warningEnabled] ?: defaults.warningEnabled,
            warningThresholdC = prefs[Keys.warningThresholdC] ?: defaults.warningThresholdC,
            criticalEnabled = prefs[Keys.criticalEnabled] ?: defaults.criticalEnabled,
            criticalThresholdC = prefs[Keys.criticalThresholdC] ?: defaults.criticalThresholdC,
            hysteresisC = prefs[Keys.hysteresisC] ?: defaults.hysteresisC,
            cooldownMinutes = prefs[Keys.cooldownMinutes] ?: defaults.cooldownMinutes,
            chargingWarningEnabled = prefs[Keys.chargingWarningEnabled] ?: defaults.chargingWarningEnabled,
            chargingWarningThresholdC = prefs[Keys.chargingWarningThresholdC]
                ?: defaults.chargingWarningThresholdC,
            statusBandTriggers = bands
        ).clamped()
    }

    private fun data(): Flow<androidx.datastore.preferences.core.Preferences> =
        context.engineDataStore.data.catch { throwable ->
            if (throwable is IOException) {
                Log.w(TAG, "Preferences read failed; using defaults for this read", throwable)
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }

    private suspend fun editSafely(block: (MutablePreferences) -> Unit) {
        try {
            context.engineDataStore.edit(block)
        } catch (io: IOException) {
            Log.w(TAG, "Failed to persist engine setting; keeping the previous value", io)
        }
    }
}
