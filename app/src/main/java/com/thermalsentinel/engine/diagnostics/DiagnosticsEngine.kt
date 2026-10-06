package com.thermalsentinel.engine.diagnostics

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.thermalsentinel.engine.alert.AlertNotificationPublisher
import com.thermalsentinel.engine.data.EngineSettingsRepository
import com.thermalsentinel.engine.data.ThermalHistoryRepository
import com.thermalsentinel.engine.domain.ThermalFormatting
import com.thermalsentinel.engine.monitoring.HistoryMath
import com.thermalsentinel.engine.monitoring.RetentionPolicy
import com.thermalsentinel.engine.monitoring.SamplingPolicy
import com.thermalsentinel.engine.service.MonitoringStatus
import kotlinx.coroutines.flow.first

enum class CheckState(val label: String) {
    OK("Ready"),
    WARNING("Attention"),
    FAILED("Not available"),
    UNKNOWN("Cannot be verified")
}

/**
 * One readiness check.
 *
 * [detail] always states what was actually measured. A check must never report
 * [CheckState.UNKNOWN] as a pass, and must never phrase a guess as a measurement.
 */
data class ReadinessCheck(
    val id: String,
    val title: String,
    val state: CheckState,
    val detail: String,
    val remedy: String? = null
)

/**
 * Runtime readiness checks.
 *
 * Where the app genuinely cannot know something — whether an OEM battery manager
 * will stop the service, for example — the check says so instead of showing a
 * green tick it did not earn.
 */
class DiagnosticsEngine(
    private val context: Context,
    private val history: ThermalHistoryRepository,
    private val settings: EngineSettingsRepository,
    private val notifications: AlertNotificationPublisher
) {

    suspend fun runChecks(nowMillis: Long = System.currentTimeMillis()): List<ReadinessCheck> {
        val checks = mutableListOf<ReadinessCheck>()

        checks += notificationCheck()
        checks += monitoringCheck()
        checks += batteryBroadcastCheck()
        checks += thermalStatusCheck()
        checks += headroomCheck()
        checks += batteryPropertyCheck()
        checks += databaseCheck()
        checks += freshnessCheck(nowMillis)
        checks += retentionCheck()
        checks += backgroundRestrictionCheck(nowMillis)
        checks += exportCacheCheck()

        return checks
    }

    private fun notificationCheck(): ReadinessCheck {
        val visible = notifications.notificationsVisible()
        return ReadinessCheck(
            id = "notifications",
            title = "Notification permission",
            state = if (visible) CheckState.OK else CheckState.WARNING,
            detail = if (visible) {
                "The ongoing monitoring notification and thermal alerts can be shown."
            } else {
                "Notifications are disabled for this app, so the ongoing notification is hidden by Android. " +
                    "Monitoring still runs and the platform still lists the service under active apps."
            },
            remedy = if (visible) null else "Grant the notification permission to see the live temperature without opening the app."
        )
    }

    private suspend fun monitoringCheck(): ReadinessCheck {
        val running = MonitoringStatus.isRunning.value
        val stopReason = settings.lastStopReason.first()
        return ReadinessCheck(
            id = "monitoring",
            title = "Monitoring service",
            state = if (running) CheckState.OK else CheckState.WARNING,
            detail = if (running) {
                "The foreground service is running and sampling."
            } else {
                "Monitoring is not running. Last stop reason: ${stopReason.label.lowercase()}."
            },
            remedy = if (running) null else "Open the Dashboard and turn monitoring on."
        )
    }

    private fun batteryBroadcastCheck(): ReadinessCheck {
        val sticky = stickyBatteryIntent()
        val temperature = sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val hasTemperature = temperature != null && temperature != Int.MIN_VALUE
        return ReadinessCheck(
            id = "battery_broadcast",
            title = "Battery broadcast",
            state = if (sticky != null && hasTemperature) CheckState.OK else CheckState.WARNING,
            detail = when {
                sticky == null -> "Android did not return the sticky battery broadcast, so no battery value can be read."
                !hasTemperature -> "The battery broadcast arrived but this device omits EXTRA_TEMPERATURE, so no temperature can be recorded."
                else -> "The sticky ACTION_BATTERY_CHANGED broadcast is readable and reports a battery temperature."
            },
            remedy = if (sticky != null && hasTemperature) null
            else "This is a device limitation; the app will report temperature as unavailable rather than guessing."
        )
    }

    private fun thermalStatusCheck(): ReadinessCheck {
        val manager = context.getSystemService(PowerManager::class.java)
        val level = manager?.currentThermalStatus
        return ReadinessCheck(
            id = "thermal_status",
            title = "Platform thermal status",
            state = if (level != null) CheckState.OK else CheckState.FAILED,
            detail = if (level != null) {
                "PowerManager reports thermal status level $level right now."
            } else {
                "PowerManager.getCurrentThermalStatus() returned nothing on this device."
            },
            remedy = if (level != null) null else "Thermal-status alerts cannot work on this device."
        )
    }

    private fun headroomCheck(): ReadinessCheck {
        val manager = context.getSystemService(PowerManager::class.java)
        val available = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && manager != null
        if (!available) {
            return ReadinessCheck(
                id = "thermal_headroom",
                title = "Thermal headroom",
                state = CheckState.FAILED,
                detail = "Thermal headroom requires Android 11 or newer, and PowerManager was unavailable."
            )
        }
        val headroom = manager!!.getThermalHeadroom(10)
        val supported = !headroom.isNaN()
        return ReadinessCheck(
            id = "thermal_headroom",
            title = "Thermal headroom",
            state = if (supported) CheckState.OK else CheckState.UNKNOWN,
            detail = if (supported) {
                // Headroom is a ratio, never a temperature; 1.0 is the platform's
                // SEVERE threshold and values above 1.0 are possible.
                "PowerManager reports headroom ${ThermalFormatting.headroom(headroom)} (1.0 is the platform's severe threshold)."
            } else {
                "This device reports NaN for thermal headroom, which means the API is unsupported. " +
                    "It is not a cold reading, so the app shows the value as unavailable."
            }
        )
    }

    private fun batteryPropertyCheck(): ReadinessCheck {
        val manager = context.getSystemService(BatteryManager::class.java)
        if (manager == null) {
            return ReadinessCheck(
                id = "battery_properties",
                title = "Battery properties",
                state = CheckState.FAILED,
                detail = "BatteryManager was unavailable, so current and charge counter cannot be read."
            )
        }
        val current = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val chargeCounter = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val currentSupported = current != Int.MIN_VALUE
        val counterSupported = chargeCounter != Int.MIN_VALUE
        return ReadinessCheck(
            id = "battery_properties",
            title = "Battery properties",
            state = if (currentSupported || counterSupported) CheckState.OK else CheckState.WARNING,
            detail = buildString {
                append(if (currentSupported) "Current is reported. " else "Current is not reported by this fuel gauge. ")
                append(if (counterSupported) "Charge counter is reported." else "Charge counter is not reported by this fuel gauge.")
            },
            remedy = if (currentSupported || counterSupported) null
            else "Current and charge counter will be shown as unavailable; temperature and charge level still work."
        )
    }

    private suspend fun databaseCheck(): ReadinessCheck {
        val counts = history.rowCounts()
        val oldest = counts.oldestSampleMillis
        return ReadinessCheck(
            id = "database",
            title = "Local history database",
            state = if (counts.samples > 0) CheckState.OK else CheckState.WARNING,
            detail = buildString {
                append("${counts.samples} raw samples, ${counts.hourlyBuckets} hourly buckets, ")
                append("${counts.dailyBuckets} daily buckets, ${counts.events} events, ")
                append("${counts.chargingSessions} charging sessions.")
                if (oldest != null) {
                    append(" Oldest raw sample: ${ThermalFormatting.timestamp(oldest)}.")
                }
            },
            remedy = if (counts.samples > 0) null else "Start monitoring; history is stored locally and never uploaded."
        )
    }

    private suspend fun freshnessCheck(nowMillis: Long): ReadinessCheck {
        val latest = history.latestSample.value
        if (latest == null) {
            return ReadinessCheck(
                id = "freshness",
                title = "Data freshness",
                state = CheckState.WARNING,
                detail = "No sample has been stored yet.",
                remedy = "Turn monitoring on from the Dashboard."
            )
        }
        val age = nowMillis - latest.timestampMillis
        val interactive = context.getSystemService(PowerManager::class.java)?.isInteractive ?: true
        val expected = if (interactive) {
            SamplingPolicy.NORMAL_INTERVAL_MS
        } else {
            SamplingPolicy.SCREEN_OFF_INTERVAL_MS
        }
        val state = when {
            age <= expected * 3 -> CheckState.OK
            age <= RetentionPolicy.HOUR_MILLIS -> CheckState.WARNING
            else -> CheckState.FAILED
        }
        val cadenceSuffix = if (interactive) "on." else "off."
        return ReadinessCheck(
            id = "freshness",
            title = "Data freshness",
            state = state,
            detail = "The last stored sample is ${ThermalFormatting.duration(age)} old. Expected cadence is " +
                "about ${ThermalFormatting.duration(expected)} while the screen is $cadenceSuffix",
            remedy = when (state) {
                CheckState.OK -> null
                CheckState.WARNING -> "Sampling can lag under Doze; this is often normal."
                else -> "Monitoring appears to have stopped. Open the Dashboard and resume it."
            }
        )
    }

    private suspend fun retentionCheck(): ReadinessCheck {
        val counts = history.rowCounts()
        val expected = RetentionPolicy.expectedSamples(
            RetentionPolicy.RAW_RETENTION_DAYS * RetentionPolicy.DAY_MILLIS,
            SamplingPolicy.NORMAL_INTERVAL_MS
        )
        return ReadinessCheck(
            id = "retention",
            title = "Retention ladder",
            state = CheckState.OK,
            detail = "Raw samples are kept for ${RetentionPolicy.RAW_RETENTION_DAYS} days, hourly averages for " +
                "${RetentionPolicy.HOURLY_RETENTION_DAYS} days and daily averages for " +
                "${RetentionPolicy.DAILY_RETENTION_DAYS} days. A 7-day window at the normal cadence holds up to " +
                "$expected samples; this device currently stores ${counts.samples}."
        )
    }

    private suspend fun backgroundRestrictionCheck(nowMillis: Long): ReadinessCheck {
        val stopReason = settings.lastStopReason.first()
        val stoppedAt = settings.lastStoppedAtMillis.first()
        val stoppedRecently = stoppedAt != null && nowMillis - stoppedAt < 24 * RetentionPolicy.HOUR_MILLIS
        return ReadinessCheck(
            id = "background_limits",
            title = "Background restrictions",
            state = CheckState.UNKNOWN,
            detail = buildString {
                append("Whether this manufacturer's battery manager will stop a foreground service cannot be ")
                append("verified programmatically. The app records what actually happened instead: ")
                if (stoppedRecently && stopReason.storageValue == "platform_stopped") {
                    append("monitoring was stopped by Android ")
                    append(ThermalFormatting.duration(nowMillis - (stoppedAt ?: nowMillis)))
                    append(" ago.")
                } else {
                    append("no platform-initiated stop has been recorded recently.")
                }
            },
            remedy = "If monitoring stops on its own, exclude the app from battery optimisation in Android Settings."
        )
    }

    private fun exportCacheCheck(): ReadinessCheck {
        val directory = context.cacheDir
        val writable = directory.exists() && directory.canWrite()
        return ReadinessCheck(
            id = "export",
            title = "Export storage",
            state = if (writable) CheckState.OK else CheckState.FAILED,
            detail = if (writable) {
                "CSV exports are written to the app cache and shared through a content URI; nothing is uploaded."
            } else {
                "The app cache directory is not writable, so CSV export will fail."
            }
        )
    }

    private fun stickyBatteryIntent(): Intent? {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        return ContextCompat.registerReceiver(context, null, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }
}
