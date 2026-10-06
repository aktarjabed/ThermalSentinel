@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.thermalsentinel.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.thermalsentinel.BuildConfig
import com.thermalsentinel.ui.components.FeatureCard
import com.thermalsentinel.ui.components.Gauge
import com.thermalsentinel.ui.components.KeyValueRow
import com.thermalsentinel.ui.components.LineChart
import com.thermalsentinel.ui.components.LockedFeatureRow
import com.thermalsentinel.ui.components.MetricCard
import com.thermalsentinel.ui.components.PreviewBanner
import com.thermalsentinel.ui.components.SectionTitle
import com.thermalsentinel.ui.components.StatusPill
import com.thermalsentinel.ui.components.TimelineRow
import com.thermalsentinel.ui.data.AccentPreset
import com.thermalsentinel.ui.data.ThemeMode
import com.thermalsentinel.ui.data.UiPreferences
import com.thermalsentinel.ui.data.WidgetStyle
import com.thermalsentinel.ui.navigation.Routes
// Required: seed() is an internal extension in the theme package.
import com.thermalsentinel.ui.theme.seed
import java.util.Locale
import kotlin.math.roundToInt
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thermalsentinel.engine.alert.AlertLevel
import com.thermalsentinel.engine.diagnostics.CheckState
import com.thermalsentinel.engine.diagnostics.ReadinessCheck
import com.thermalsentinel.engine.domain.BatteryHealth
import com.thermalsentinel.engine.domain.Reading
import com.thermalsentinel.engine.domain.ThermalFormatting
import com.thermalsentinel.engine.domain.ThermalStatusBand
import com.thermalsentinel.engine.domain.displayLabel
import com.thermalsentinel.engine.export.ExportOutcome
import com.thermalsentinel.engine.monitoring.HistoryMath
import com.thermalsentinel.engine.monitoring.RetentionPolicy
import com.thermalsentinel.ui.components.DividerRow
import com.thermalsentinel.ui.components.ThermalHistoryChart
import com.thermalsentinel.ui.data.UiPreferencesViewModel
import com.thermalsentinel.ui.live.AlertRulesViewModel
import com.thermalsentinel.ui.live.BatteryViewModel
import com.thermalsentinel.ui.live.DiagnosticsViewModel
import com.thermalsentinel.ui.live.HistoryRange
import com.thermalsentinel.ui.live.HistoryViewModel
import com.thermalsentinel.ui.live.MonitoringViewModel
import com.thermalsentinel.ui.live.ThermalViewModel

/**
 * Formats a temperature for display. Locale-aware so decimal separators follow
 * the user's locale; `Locale.ROOT` would force "38.4°C" everywhere, which is
 * not what Android convention expects.
 */
private fun celsius(value: Float): String =
    String.format(Locale.getDefault(), "%.1f°C", value)

/**
 * Honest scroll container. fillMaxSize guarantees the container occupies the
 * full Scaffold content area regardless of how tall the inner Column grows.
 */
@Composable
private fun ScreenContent(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

// ---------------------------------------------------------------------------
// Availability-aware value rendering shared by the live screens.
//
// Every helper below takes a `Reading` and renders the *reason* a value is
// missing instead of a zero, because "0 mA" and "this device has no current
// sensor" are different facts.
// ---------------------------------------------------------------------------

private fun Reading<*>.absenceLabel(): String =
    absenceReason?.displayLabel ?: ThermalFormatting.UNAVAILABLE_MARKER

/** Renders the absence reason when there is no reading at all. */
private fun Reading<*>?.absenceLabelOrMarker(): String =
    this?.absenceLabel() ?: ThermalFormatting.UNAVAILABLE_MARKER

private fun <T> Reading<T>?.presentOrNull(): T? = this?.valueOrNull

private fun temperatureText(reading: Reading<Float>?): String =
    reading.presentOrNull()?.let { ThermalFormatting.temperatureCelsius(it) }
        ?: reading.absenceLabelOrMarker()

private fun temperatureValueText(value: Float?): String = ThermalFormatting.temperatureCelsius(value)

private fun headroomText(reading: Reading<Float>?): String =
    // Headroom is a ratio between readings, so it is never rendered with a degree sign.
    reading.presentOrNull()?.let { ThermalFormatting.headroom(it) }
        ?: reading.absenceLabelOrMarker()

private fun percentText(reading: Reading<Int>?): String =
    reading.presentOrNull()?.let { ThermalFormatting.percent(it) }
        ?: reading.absenceLabelOrMarker()

private fun voltsText(reading: Reading<Int>?): String =
    reading.presentOrNull()?.let { ThermalFormatting.volts(it) }
        ?: reading.absenceLabelOrMarker()

private fun milliAmpsText(reading: Reading<Int>?): String =
    reading.presentOrNull()?.let { ThermalFormatting.milliAmps(it) }
        ?: reading.absenceLabelOrMarker()

private fun microAmpHoursText(reading: Reading<Int>?): String =
    reading.presentOrNull()?.let { ThermalFormatting.microAmpHours(it) }
        ?: reading.absenceLabelOrMarker()

private fun intText(reading: Reading<Int>?): String =
    reading.presentOrNull()?.toString() ?: reading.absenceLabelOrMarker()

private fun textText(reading: Reading<String>?): String =
    reading.presentOrNull() ?: reading.absenceLabelOrMarker()

private fun booleanText(reading: Reading<Boolean>?): String =
    reading.presentOrNull()?.let { if (it) "Yes" else "No" } ?: reading.absenceLabelOrMarker()

private fun labelText(reading: Reading<ThermalStatusBand>?): String =
    reading.presentOrNull()?.label ?: reading.absenceLabelOrMarker()

private fun <T> enumText(reading: Reading<T>?, label: (T) -> String): String =
    reading.presentOrNull()?.let(label) ?: reading.absenceLabelOrMarker()

/** Age of a measurement as a positive phrase, never as an implied freshness. */
private fun ageText(timestampMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val age = (nowMillis - timestampMillis).coerceAtLeast(0L)
    return when {
        age < 90_000L -> "just now"
        age < 3_600_000L -> "${age / 60_000L} min ago"
        age < 86_400_000L -> "${age / 3_600_000L} h ago"
        else -> "${age / 86_400_000L} d ago"
    }
}

/** Plotting window with one degree of padding, so the line never touches the edge. */
private fun paddedRange(points: List<HistoryMath.SeriesPoint>): ClosedFloatingPointRange<Float> {
    val values = points.mapNotNull { it.temperatureC }
    if (values.isEmpty()) return 30f..45f
    val low = (values.min() - 1f).roundToInt().toFloat()
    val high = (values.max() + 1f).roundToInt().toFloat()
    return low..(if (high > low) high else low + 1f)
}

private fun alertPillLabel(level: AlertLevel): String = when (level) {
    AlertLevel.NORMAL -> "ALERT: NORMAL"
    AlertLevel.WARNING -> "ALERT: WARNING"
    AlertLevel.CRITICAL -> "ALERT: CRITICAL"
}

@Composable
private fun NoticeCard(text: String, isError: Boolean = false, modifier: Modifier = Modifier) {
    val container = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val content = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content)
    ) {
        Text(
            text,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ReadinessRow(check: ReadinessCheck) {
    val tint = when (check.state) {
        CheckState.OK -> MaterialTheme.colorScheme.primary
        CheckState.WARNING -> MaterialTheme.colorScheme.tertiary
        CheckState.FAILED -> MaterialTheme.colorScheme.error
        CheckState.UNKNOWN -> MaterialTheme.colorScheme.outline
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(
                if (check.state == CheckState.OK) Icons.Default.CheckCircle else Icons.Default.Info,
                contentDescription = null,
                tint = tint
            )
            Spacer(Modifier.width(10.dp))
            Text(check.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(check.state.label, style = MaterialTheme.typography.bodySmall, color = tint)
        }
        Text(
            check.detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 34.dp)
        )
        check.remedy?.let { remedy ->
            Text(
                remedy,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 34.dp)
            )
        }
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable
fun DashboardScreen(onNavigate: (String) -> Unit = {}) {
    val monitoring: MonitoringViewModel = viewModel()
    val enabled by monitoring.monitoringEnabled.collectAsStateWithLifecycle()
    val running by monitoring.serviceRunning.collectAsStateWithLifecycle()
    val sample by monitoring.latestSample.collectAsStateWithLifecycle()
    val alertState by monitoring.alertState.collectAsStateWithLifecycle()
    val stopReason by monitoring.lastStopReason.collectAsStateWithLifecycle()
    val message by monitoring.message.collectAsStateWithLifecycle()

    // Monitoring is resumed only from a screen the user is actually looking at.
    // Nothing here runs from Application.onCreate, because a foreground-service
    // start from a background context is rejected by Android 12 and above.
    LaunchedEffect(Unit) { monitoring.resumeIfRequested() }

    val current = sample
    val battery = current?.battery
    val thermal = current?.thermal
    val temperature = battery?.temperatureC
    val gaugeProgress = (((temperature?.valueOrNull ?: 34f) - 34f) / 10f).coerceIn(0f, 1f)
    val currentMessage = message

    ScreenContent {
        ScreenHeader(
            "Device overview",
            "Live state from the local monitoring engine — no account, no upload"
        )

        when {
            current == null -> NoticeCard(
                "Nothing has been measured on this device yet. Turn monitoring on to start recording " +
                    "battery temperature, platform thermal status and charge state."
            )

            enabled && !running -> NoticeCard(
                "Monitoring is paused (${stopReason.label.lowercase()}). It resumes when you turn it back " +
                    "on; recorded history is kept."
            )

            running -> NoticeCard("Monitoring is running. Every sample stays on this device.")

            else -> Unit
        }

        if (currentMessage != null) {
            NoticeCard(currentMessage, isError = true)
            TextButton(onClick = monitoring::dismissMessage) { Text("Dismiss") }
        }

        FeatureCard(
            title = "Thermal state",
            subtitle = when {
                current == null -> "No measurement yet"
                running -> "Latest sample ${ageText(current.timestampMillis)}"
                else -> "Last stored sample ${ageText(current.timestampMillis)} · monitoring is off"
            },
            icon = { Icon(Icons.Default.Thermostat, null) }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Gauge(
                    progress = gaugeProgress,
                    label = if (temperature?.valueOrNull == null) "unavailable" else "battery",
                    value = temperatureText(temperature),
                    size = 110.dp
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    // "positive" is reserved for the normal state only: a warning
                    // must never be rendered with the affirmative colour.
                    StatusPill(alertPillLabel(alertState.level), alertState.level == AlertLevel.NORMAL)
                    KeyValueRow("Battery temperature", temperatureText(temperature))
                    KeyValueRow("Android thermal status", labelText(thermal?.thermalStatus))
                    KeyValueRow("Headroom (1.0 = severe)", headroomText(thermal?.headroom))
                    KeyValueRow("Charge level", percentText(battery?.batteryPercent))
                }
            }
        }

        FeatureCard(
            title = "Monitoring control",
            subtitle = "Foreground service with an ongoing notification",
            icon = { Icon(Icons.Default.Shield, null) }
        ) {
            SettingSwitch(
                title = "Thermal & battery monitoring",
                subtitle = if (running) "Running · adaptive sampling (5–60 s by state)"
                else "Stopped · no new samples are recorded",
                checked = enabled,
                onCheckedChange = { wanted ->
                    if (wanted) monitoring.startMonitoring() else monitoring.stopMonitoring()
                }
            )
            if (enabled && !running) {
                TextButton(onClick = monitoring::resumeIfRequested, modifier = Modifier.fillMaxWidth()) {
                    Text("Resume monitoring now")
                }
            }
            Text(
                "Monitoring reads battery temperature, Android thermal status, headroom, charge level, " +
                    "voltage, current, health and charging state. It never changes system settings, never " +
                    "force-stops apps and never transmits anything.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        QuickAccessCard(onOpen = { onNavigate(Routes.QUICK_ACCESS) })

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                title = "Battery",
                value = percentText(battery?.batteryPercent),
                subtitle = if (current == null) "No measurement yet" else "Measured ${ageText(current.timestampMillis)}",
                icon = { Icon(Icons.Default.BatteryFull, null) },
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Network protection",
                value = "OFF",
                subtitle = "Not implemented in this build",
                icon = { Icon(Icons.Default.WifiOff, null) },
                modifier = Modifier.weight(1f)
            )
        }

        FeatureCard(
            title = "Risk snapshot",
            subtitle = "The correlation engine is not part of the monitoring core",
            icon = { Icon(Icons.Default.Shield, null) }
        ) {
            KeyValueRow("Thermal load", "Not scored yet")
            KeyValueRow("Charging heat", "Not scored yet")
            KeyValueRow("Network exposure", "No VPN engine in this build")
            KeyValueRow("App risk", "No scanner engine in this build")
            Text(
                "Each row reports the state of its engine instead of a fabricated score.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionTitle(
            "Not implemented in this build",
            "Listed so the navigation can never imply that a module is active."
        )
        LockedFeatureRow(
            "Local VPN firewall",
            "Per-app network policy, blocklists and traffic attribution",
            { Icon(Icons.Default.Lan, null) }
        )
        LockedFeatureRow(
            "DNS protection",
            "Ad, tracker, phishing and malware domain filtering",
            { Icon(Icons.Default.Dns, null) }
        )
        LockedFeatureRow(
            "Security scanner",
            "Evidence-weighted app risk assessment",
            { Icon(Icons.Default.VerifiedUser, null) }
        )
        LockedFeatureRow(
            "Usage correlation",
            "Needs the Usage Access grant from Android Settings; not requested in this build",
            { Icon(Icons.Default.Visibility, null) }
        )
    }
}
@Composable
fun ThermalScreen() {
    val viewModel: ThermalViewModel = viewModel()
    val sample by viewModel.latestSample.collectAsStateWithLifecycle()
    val series by viewModel.series.collectAsStateWithLifecycle()
    val statistics by viewModel.statistics.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()

    val current = sample
    val battery = current?.battery
    val thermal = current?.thermal
    val temperature = battery?.temperatureC
    val gaugeProgress = (((temperature?.valueOrNull ?: 34f) - 34f) / 10f).coerceIn(0f, 1f)
    val chartRange = paddedRange(series)
    val windowEnd = System.currentTimeMillis()
    val windowStart = windowEnd - RetentionPolicy.DAY_MILLIS
    // The threshold plotted is the one the engine actually alerts on right now,
    // including the separate charging threshold when it applies.
    val activeThreshold = rules.effectiveWarningThresholdC(battery?.onExternalPower == true)

    ScreenContent {
        ScreenHeader(
            "Thermal Monitor",
            "Battery temperature, Android thermal status, headroom and recorded events"
        )

        FeatureCard(
            title = "Current reading",
            subtitle = when {
                current == null -> "No measurement yet"
                else -> "Measured ${ageText(current.timestampMillis)}"
            },
            icon = { Icon(Icons.Default.Thermostat, null) }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Gauge(
                    progress = gaugeProgress,
                    label = if (temperature?.valueOrNull == null) "unavailable" else "battery",
                    value = temperatureText(temperature),
                    size = 110.dp
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyValueRow("Battery temperature", temperatureText(temperature))
                    KeyValueRow("Android thermal status", labelText(thermal?.thermalStatus))
                    KeyValueRow("Headroom (1.0 = severe)", headroomText(thermal?.headroom))
                    KeyValueRow("Power source", enumText(battery?.plugged) { it.label })
                }
            }
            Text(
                "Battery temperature comes from the battery broadcast and describes the battery, not the " +
                    "whole device. Headroom is a platform ratio between 0 and 1 or more; it is not a temperature.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        FeatureCard(
            title = "Temperature trend",
            subtitle = "Last 24 hours · raw samples; a break in the line is a period with no measurement",
            icon = { Icon(Icons.Default.GraphicEq, null) }
        ) {
            ThermalHistoryChart(
                points = series,
                minValue = chartRange.start,
                maxValue = chartRange.endInclusive,
                windowStartMillis = windowStart,
                windowEndMillis = windowEnd,
                thresholdC = activeThreshold,
                maxGapMillis = viewModel.maxGapForChart()
            )
            KeyValueRow("Minimum", temperatureValueText(statistics?.minTemperatureC))
            KeyValueRow("Maximum", temperatureValueText(statistics?.maxTemperatureC))
            KeyValueRow("Average", temperatureValueText(statistics?.averageTemperatureC))
            KeyValueRow("Warning threshold", celsius(activeThreshold))
            val above = statistics?.timeAboveThresholdMillis
            KeyValueRow(
                "Time above threshold",
                when {
                    statistics == null -> "No data"
                    above == null -> "Not derivable"
                    else -> ThermalFormatting.duration(above)
                }
            )
            Text(
                "${statistics?.sampleCount ?: 0} samples · ${statistics?.gapCount ?: 0} gaps longer than " +
                    "2.5× the requested interval",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        FeatureCard(
            title = "Recorded events",
            subtitle = "Written by the engine when something changed",
            icon = { Icon(Icons.Default.Speed, null) }
        ) {
            if (events.isEmpty()) {
                Text(
                    "No events recorded yet. Heating, cooling, threshold and charging transitions appear " +
                        "here after monitoring has been running for a while.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                events.take(8).forEach { event ->
                    TimelineRow(
                        time = ThermalFormatting.clock(event.timestampMillis),
                        title = event.type.label,
                        detail = event.detail
                            ?: event.temperatureC?.let { celsius(it) }
                            ?: "Recorded"
                    )
                }
            }
        }

        FeatureCard(
            title = "Active alert policy",
            subtitle = "Persisted rules the engine is evaluating right now",
            icon = { Icon(Icons.Default.Notifications, null) }
        ) {
            KeyValueRow(
                "Warning",
                if (rules.warningEnabled) {
                    "${celsius(rules.warningThresholdC)} · clears at ${celsius(rules.warningRecoveryC)}"
                } else {
                    "Disabled"
                }
            )
            KeyValueRow(
                "Critical",
                if (rules.criticalEnabled) {
                    "${celsius(rules.criticalThresholdC)} · de-escalates at ${celsius(rules.criticalRecoveryC)}"
                } else {
                    "Disabled"
                }
            )
            KeyValueRow("Repeat cooldown", "${rules.cooldownMinutes} min")
            KeyValueRow(
                "Platform bands",
                rules.statusBandTriggers.joinToString { it.label }.ifEmpty { "None selected" }
            )
            if (battery?.onExternalPower == true) {
                Text(
                    if (rules.chargingWarningEnabled) {
                        "Charging rule active: the warning threshold is ${celsius(rules.chargingWarningThresholdC)} " +
                            "while on external power."
                    } else {
                        "Charging rule is off, so the normal warning threshold applies while charging."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
@Composable
fun BatteryScreen() {
    val viewModel: BatteryViewModel = viewModel()
    val sample by viewModel.latestSample.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val openSession by viewModel.openSession.collectAsStateWithLifecycle()
    val statistics by viewModel.statistics.collectAsStateWithLifecycle()

    val current = sample
    val battery = current?.battery
    val temperature = battery?.temperatureC

    ScreenContent {
        ScreenHeader(
            "Battery & Charging",
            "Battery state, current and voltage, charging sessions and the health signals this device exposes"
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                title = "Charge",
                value = percentText(battery?.batteryPercent),
                subtitle = when {
                    current == null -> "No measurement yet"
                    battery?.onExternalPower == true -> "On external power"
                    else -> "On battery"
                },
                icon = { Icon(Icons.Default.BatteryFull, null) },
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Voltage",
                value = voltsText(battery?.voltageMilliVolts),
                subtitle = "BatteryManager voltage",
                icon = { Icon(Icons.Default.Bolt, null) },
                modifier = Modifier.weight(1f)
            )
        }

        FeatureCard(
            title = "Temperature",
            subtitle = "The same reading the alert engine evaluates",
            icon = { Icon(Icons.Default.Thermostat, null) }
        ) {
            KeyValueRow("Battery temperature", temperatureText(temperature))
            KeyValueRow("Charging status", enumText(battery?.chargingStatus) { it.label })
            KeyValueRow("Power source", enumText(battery?.plugged) { it.label })
            KeyValueRow("Measured", current?.let { ageText(it.timestampMillis) } ?: "Not yet")
        }

        FeatureCard(
            title = "Current draw",
            subtitle = "Unsigned values are never invented: an unsupported sensor reports its absence reason",
            icon = { Icon(Icons.Default.Bolt, null) }
        ) {
            KeyValueRow("Current now", milliAmpsText(battery?.currentMicroAmps))
            KeyValueRow("Current average", milliAmpsText(battery?.averageCurrentMicroAmps))
            KeyValueRow("Charge counter", microAmpHoursText(battery?.chargeCounterMicroAmpHours))
            KeyValueRow(
                "Energy counter",
                battery?.energyCounterNanoWattHours?.valueOrNull?.let { "$it nWh" }
                    ?: battery?.energyCounterNanoWattHours?.absenceLabel()
                    ?: "—"
            )
        }

        FeatureCard(
            title = "Battery health",
            subtitle = "Reported by the battery broadcast and BatteryManager properties",
            icon = { Icon(Icons.Default.VerifiedUser, null) }
        ) {
            KeyValueRow("Health state", enumText(battery?.health) { it.label })
            KeyValueRow("Cycle count", intText(battery?.cycleCount))
            KeyValueRow("Capacity level", enumText(battery?.capacityLevel) { it.label })
            KeyValueRow("Technology", textText(battery?.technology))
            KeyValueRow("Battery present", booleanText(battery?.present))
            Text(
                "Cycle count exists on Android 14 and above, and capacity level on Android 16 and above, " +
                    "and some devices still report neither. Where the platform is silent the value above " +
                    "says so instead of showing a zero.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        FeatureCard(
            title = "Charging session",
            subtitle = "Sessions are derived from the platform charging state, not from a user timer",
            icon = { Icon(Icons.Default.Timer, null) }
        ) {
            val open = openSession
            if (open == null) {
                Text(
                    "No charging session is in progress. The last session, its duration and its peak " +
                        "temperature appear here when the device is plugged in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                KeyValueRow("Started", ThermalFormatting.clock(open.startMillis))
                KeyValueRow("Duration", ThermalFormatting.duration(open.durationMillis))
                KeyValueRow("Charge", "${open.startPercent?.let { "$it%" } ?: "—"} → ${open.endPercent?.let { "$it%" } ?: "—"}")
                KeyValueRow("Peak temperature", temperatureValueText(open.peakTemperatureC))
                KeyValueRow("Average temperature", temperatureValueText(open.averageTemperatureC))
                KeyValueRow("Samples", open.sampleCount.toString())
            }
            if (sessions.size > 1) {
                DividerRow()
                Text("Earlier sessions", style = MaterialTheme.typography.titleSmall)
                sessions.drop(1).take(4).forEach { session ->
                    TimelineRow(
                        time = ThermalFormatting.clock(session.startMillis),
                        title = "Peak ${temperatureValueText(session.peakTemperatureC)}",
                        detail = "${ThermalFormatting.duration(session.durationMillis)} · ${session.sampleCount} samples"
                    )
                }
            }
        }

        FeatureCard(
            title = "Charging behaviour recorded so far",
            subtitle = "Measured, not estimated",
            icon = { Icon(Icons.Default.Tune, null) }
        ) {
            KeyValueRow("Samples taken while charging", (statistics?.chargingSampleCount ?: 0).toString())
            KeyValueRow("Maximum temperature today", temperatureValueText(statistics?.maxTemperatureC))
            KeyValueRow("Records over 40 °C today", "Counted in Thermal History")
        }

        FeatureCard(
            title = "Low-Temperature Mode",
            subtitle = "Guidance only. The monitoring engine never changes a system setting.",
            icon = { Icon(Icons.Default.AcUnit, null) }
        ) {
            SettingSwitch(
                title = "Cold-condition guidance",
                subtitle = "Platform health state reports COLD when the battery is too cold to charge normally",
                checked = battery?.health?.valueOrNull == BatteryHealth.COLD,
                onCheckedChange = null
            )
            Text(
                "The row above mirrors the platform health flag; it is not a switch this app can turn on. " +
                    "Automatic brightness or charging changes would require separate system authorisations " +
                    "and are deliberately outside this engine.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun NetworkScreen() {
    ScreenContent {
        ScreenHeader("Network Protection", "Local VPN pipeline, per-app control and traffic attribution")
        FeatureCard("VPN protection", "No VPN service is running in this UI-only build", { Icon(Icons.Default.Wifi, null) }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(Icons.Default.WifiOff, null, modifier = Modifier.size(46.dp), tint = MaterialTheme.colorScheme.error)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Protection OFF", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Future implementation will expose explicit VPN lifecycle states and fail-safe degraded states.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            StatusPill("NOT CONNECTED", false)
            KeyValueRow("Mode", "Local device VPN")
            KeyValueRow("DNS interception", "Planned")
            KeyValueRow("IPv4 / IPv6", "Planned")
        }
        FeatureCard("Traffic visibility", "Attribution chain for future packet engine", { Icon(Icons.Default.Visibility, null) }) {
            KeyValueRow("Known domain", "Preferred")
            KeyValueRow("DNS association", "Preferred")
            KeyValueRow("SNI observed", "Secondary")
            KeyValueRow("IP-only", "Fallback")
            KeyValueRow("QUIC", "Special handling")
            KeyValueRow("Unattributable", "Explicit state")
        }
        FeatureCard("Per-app policy", "The engine will support allow, block and route decisions", { Icon(Icons.Default.Apps, null) }) {
            SettingSwitch("Unknown apps", "Default policy for applications without a custom rule", false)
            SettingSwitch("Block known trackers", "Use locally managed tracker blocklists", true)
            SettingSwitch("Block known ads", "Use locally managed advertisement blocklists", true)
            SettingSwitch("Block phishing", "Safety rule may outrank normal user allow rules", true)
        }
    }
}

@Composable
fun FirewallScreen() {
    ScreenContent {
        ScreenHeader("Firewall Rules", "Rule priority, categories, per-app exceptions and audit-friendly decisions")
        FeatureCard("Decision precedence", "Displayed in the intended order for the future rule engine", { Icon(Icons.Default.Rule, null) }) {
            listOf(
                "SYSTEM_SAFETY",
                "USER_EXPLICIT_BLOCK",
                "MALWARE",
                "PHISHING",
                "USER_ALLOW",
                "TRACKER",
                "ADVERTISEMENT",
                "APP_POLICY",
                "DEFAULT_ALLOW"
            ).forEachIndexed { index, label ->
                KeyValueRow("${index + 1}. $label", if (index < 4) "high priority" else "normal")
            }
        }
        FeatureCard("Protection categories", "No rule changes are persisted in this UI build", { Icon(Icons.Default.Shield, null) }) {
            SettingSwitch("Advertisements", "Block known ad domains", true)
            SettingSwitch("Trackers", "Block known tracker domains", true)
            SettingSwitch("Malware", "Block known malicious infrastructure", true)
            SettingSwitch("Phishing", "Block known phishing infrastructure", true)
        }
        FeatureCard("Advanced override", "Visible because precedence must be explicit", { Icon(Icons.Default.SettingsApplications, null) }) {
            SettingSwitch(
                "Allow user allow-rules to override malware/phishing",
                "Future expert mode; should require explicit confirmation",
                false
            )
        }
    }
}

@Composable
fun DnsScreen() {
    ScreenContent {
        ScreenHeader("DNS Protection", "Resolver mode, blocklists, cache correlation and attribution")
        FeatureCard("DNS engine", "UI state model", { Icon(Icons.Default.Dns, null) }) {
            KeyValueRow("Resolver", "System resolver")
            KeyValueRow("Interception", "Not active")
            KeyValueRow("Cache correlation", "Planned")
            KeyValueRow("Encrypted DNS", "Attribution may be incomplete")
        }
        FeatureCard("Blocklists", "Local lists that can later be refreshed by the app", { Icon(Icons.Default.Storage, null) }) {
            listOf("Advertisements", "Trackers", "Malware", "Phishing").forEach { label ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = true, onCheckedChange = null, enabled = false)
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        FeatureCard("Resolver privacy", "No remote service is used by this UI shell", { Icon(Icons.Default.PrivacyTip, null) }) {
            SettingSwitch("Prefer encrypted DNS", "Future implementation will use explicitly supported resolver modes", false)
            SettingSwitch("Cache DNS answers", "Respect TTL and preserve attribution correlation", true)
        }
    }
}

@Composable
fun BlocklistsScreen() {
    ScreenContent {
        ScreenHeader("Blocklists", "Local list categories, update state and rule provenance")
        FeatureCard("List catalog", "The future implementation can keep list content local and auditable", { Icon(Icons.Default.Storage, null) }) {
            ListRow("Advertisements", "Preview list · 1.2M domains", true)
            ListRow("Trackers", "Preview list · 430K domains", true)
            ListRow("Malware", "Preview list · safety critical", true)
            ListRow("Phishing", "Preview list · safety critical", true)
        }
        FeatureCard("Update policy", "No network operation is executed by this UI build", { Icon(Icons.Default.Refresh, null) }) {
            KeyValueRow("Source mode", "Local / user-managed")
            KeyValueRow("Last refresh", "Never")
            KeyValueRow("Update schedule", "Not configured")
            SettingSwitch("Check for list updates", "Future background maintenance task", false)
        }
        FeatureCard(
            "Safety",
            "Critical lists should not be silently downgraded by ordinary allow rules",
            { Icon(Icons.Default.Shield, null) }
        ) {
            SettingSwitch("Safety precedence", "Keep malware/phishing rules above normal category rules", true)
        }
    }
}

@Composable
fun NetworkStatsScreen() {
    ScreenContent {
        ScreenHeader("Network Statistics", "Local traffic volume, flows, categories and app attribution")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("Flows", "—", "Not connected", { Icon(Icons.Default.Lan, null) }, Modifier.weight(1f))
            MetricCard("Blocked", "—", "Not connected", { Icon(Icons.Default.Shield, null) }, Modifier.weight(1f))
        }
        FeatureCard("Traffic profile", "The visual language for future hourly/daily statistics", { Icon(Icons.Default.GraphicEq, null) }) {
            LineChart(
                listOf(0.2f, 0.3f, 0.4f, 0.28f, 0.55f, 0.63f, 0.45f, 0.72f, 0.58f, 0.36f, 0.42f, 0.31f),
                0f,
                1f
            )
            KeyValueRow("Download", "Not connected")
            KeyValueRow("Upload", "Not connected")
            KeyValueRow("DNS queries", "Not connected")
        }
        FeatureCard("Attribution quality", "Prefer DNS association before weaker metadata such as SNI or IP-only", { Icon(Icons.Default.Visibility, null) }) {
            KeyValueRow("Domain known", "Preferred")
            KeyValueRow("SNI observed", "Secondary")
            KeyValueRow("IP only", "Fallback")
            KeyValueRow("QUIC", "Special handling")
            KeyValueRow("Unattributable", "Explicit state")
        }
    }
}

@Composable
fun PrivacyScreen() {
    ScreenContent {
        ScreenHeader("Privacy Monitor", "Network tracking, app access signals and local privacy reports")
        FeatureCard("Privacy posture", "Mock summary for final dashboard layout", { Icon(Icons.Default.PrivacyTip, null) }) {
            Gauge(0.78f, "posture", "78")
            KeyValueRow("Tracker activity", "Unknown")
            KeyValueRow("Sensitive permission signals", "5")
            KeyValueRow("Network visibility", "VPN inactive")
        }
        FeatureCard("Signals", "The future engine should expose evidence rather than opaque claims", { Icon(Icons.Default.Visibility, null) }) {
            LockedFeatureRow("Foreground app", "Which application is currently active", { Icon(Icons.Default.Apps, null) })
            LockedFeatureRow("Usage correlation", "Optional UsageStats-based activity correlation", { Icon(Icons.Default.Speed, null) })
            LockedFeatureRow("Tracker domains", "Domain-level attribution when observable", { Icon(Icons.Default.Dns, null) })
        }
        FeatureCard("Privacy notifications", "Configurable future alerts", { Icon(Icons.Default.Notifications, null) }) {
            SettingSwitch("New tracker burst", "Notify when a new tracker activity pattern is detected", false)
            SettingSwitch("Sensitive permission change", "Surface unusual permission changes", true)
        }
    }
}

private data class AppItem(val name: String, val risk: String, val detail: String)

@Composable
fun AppsScreen() {
    val apps = listOf(
        AppItem("Browser", "Low", "Common network activity"),
        AppItem("Unknown Utility", "Medium", "Broad permissions in preview dataset"),
        AppItem("Media Player", "Low", "Local media permissions"),
        AppItem("System UI", "System", "System package")
    )
    ScreenContent {
        ScreenHeader("App Activity", "Installed-app inventory, usage correlation and network policy targets")
        FeatureCard("Inventory model", "The final scanner will report evidence, confidence and risk", { Icon(Icons.Default.Apps, null) }) {
            KeyValueRow("Visible apps", "148")
            KeyValueRow("User apps", "93")
            KeyValueRow("System apps", "55")
            KeyValueRow("Network policy rules", "0")
        }
        apps.forEach { app ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Apps, null, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                app.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill(app.risk.uppercase(), app.risk == "Low" || app.risk == "System")
                    }
                }
            }
        }
        TextButton(onClick = {}, enabled = false) { Text("Open full package details (future)") }
    }
}

@Composable
fun ScannerScreen() {
    ScreenContent {
        ScreenHeader(
            "Security Scanner",
            "Evidence-weighted app assessment without claiming definitive malware detection"
        )
        FeatureCard("Scanner status", "No PackageManager scan is running in this UI build", { Icon(Icons.Default.Security, null) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Gauge(0.34f, "confidence", "—", size = 110.dp)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusPill("NOT RUN", false)
                    KeyValueRow("Packages checked", "—")
                    KeyValueRow("High-risk findings", "—")
                    KeyValueRow("Last scan", "Never")
                }
            }
        }
        FeatureCard("Assessment model", "Risk score + confidence + evidence", { Icon(Icons.Default.BugReport, null) }) {
            RiskRow("Suspicious permissions", "Signal", 0.35f)
            RiskRow("Exported components", "Signal", 0.28f)
            RiskRow("Install source", "Signal", 0.18f)
            RiskRow("Network correlation", "Signal", 0.52f)
        }
        FeatureCard(
            "Safety wording",
            "The product should not label an app malicious from heuristics alone",
            { Icon(Icons.Default.Info, null) }
        ) {
            Text(
                "Future findings will identify the evidence used, the confidence level, and what the user can inspect " +
                    "in Android Settings. Network blocking and Device Owner controls remain separate from the " +
                    "personal-app scanner.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun ActivityScreen() {
    ScreenContent {
        ScreenHeader("Security Timeline", "Local event stream for thermal, network and app-security correlation")
        FeatureCard("Event stream", "Mock timeline for visual hierarchy", { Icon(Icons.Default.History, null) }) {
            TimelineRow("14:48", "Thermal rise", "Battery temperature moved above recent baseline")
            TimelineRow("14:32", "DNS event", "Preview tracker category observed")
            TimelineRow("13:54", "App permission change", "Preview signal detected")
            TimelineRow("12:18", "Charging peak", "Charging session reached preview peak")
            TimelineRow("10:07", "VPN state", "Protection currently off")
        }
    }
}
@Composable
fun HistoryScreen() {
    val viewModel: HistoryViewModel = viewModel()
    val context = LocalContext.current
    val selectedRange by viewModel.range.collectAsStateWithLifecycle()
    val series by viewModel.series.collectAsStateWithLifecycle()
    val statistics by viewModel.statistics.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val outcome by viewModel.exportState.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()

    val windowEnd = System.currentTimeMillis()
    val windowStart = windowEnd - selectedRange.windowMillis
    val chartRange = paddedRange(series)
    val currentOutcome = outcome
    val above = statistics?.timeAboveThresholdMillis

    ScreenContent {
        ScreenHeader(
            "Thermal History",
            "Raw samples for 24 hours, hourly averages for 90 days, daily averages for 400 days"
        )

        FeatureCard(
            title = "Range",
            subtitle = "Longer ranges read compacted buckets, so they describe averages rather than moments",
            icon = { Icon(Icons.Default.History, null) }
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                HistoryRange.entries.forEachIndexed { index, candidate ->
                    SegmentedButton(
                        selected = candidate == selectedRange,
                        onClick = { viewModel.selectRange(candidate) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = HistoryRange.entries.size
                        )
                    ) {
                        Text(candidate.label)
                    }
                }
            }
            ThermalHistoryChart(
                points = series,
                minValue = chartRange.start,
                maxValue = chartRange.endInclusive,
                windowStartMillis = windowStart,
                windowEndMillis = windowEnd,
                thresholdC = rules.warningThresholdC,
                maxGapMillis = viewModel.maxGapForRange()
            )
            Text(
                if (selectedRange.usesRawSamples) {
                    "Each point is one stored sample. A break in the line is a period when nothing was measured."
                } else {
                    "Each point is the average of one " +
                        (if (selectedRange.bucketMillis == RetentionPolicy.DAILY_BUCKET_MILLIS) "day" else "hour") +
                        ". The dashed line is the warning threshold."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            KeyValueRow("Minimum", temperatureValueText(statistics?.minTemperatureC))
            KeyValueRow("Maximum", temperatureValueText(statistics?.maxTemperatureC))
            KeyValueRow("Average", temperatureValueText(statistics?.averageTemperatureC))
            KeyValueRow("Samples counted", (statistics?.sampleCount ?: 0).toString())
            KeyValueRow(
                "Time above ${celsius(rules.warningThresholdC)}",
                when {
                    statistics == null -> "No data"
                    above == null -> "Not derivable from averages"
                    else -> ThermalFormatting.duration(above)
                }
            )
        }

        FeatureCard(
            title = "Export",
            subtitle = "CSV written to the app cache, then shared by you — nothing is uploaded",
            icon = { Icon(Icons.Default.Download, null) }
        ) {
            Button(onClick = viewModel::exportCsv, modifier = Modifier.fillMaxWidth()) {
                Text("Export CSV")
            }
            when (currentOutcome) {
                null -> Text(
                    "Only the sample table is exported, because hourly and daily rows are averages. " +
                        "An empty field in the file means the platform did not report that value.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                is ExportOutcome.Success -> {
                    NoticeCard(
                        "Exported ${currentOutcome.result.rowCount} samples covering " +
                            "${ThermalFormatting.duration(currentOutcome.result.windowMillis)}." +
                            if (currentOutcome.result.truncatedToRetention) {
                                " The window was limited to the seven days of raw samples; older history " +
                                    "exists only as hourly and daily averages."
                            } else {
                                ""
                            }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                context.startActivity(
                                    Intent.createChooser(viewModel.shareIntent(currentOutcome.result), null)
                                )
                            }
                        ) {
                            Text("Share")
                        }
                        TextButton(onClick = viewModel::clearExportState) { Text("Dismiss") }
                    }
                }

                is ExportOutcome.Failure -> NoticeCard(currentOutcome.message, isError = true)

                is ExportOutcome.Empty -> NoticeCard(currentOutcome.message)
            }
        }

        FeatureCard(
            title = "Charging sessions",
            subtitle = "Recorded from the platform charging state; peaks are measured, not estimated",
            icon = { Icon(Icons.Default.Bolt, null) }
        ) {
            if (sessions.isEmpty()) {
                Text(
                    "No charging session has been recorded in this window yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                sessions.take(5).forEach { session ->
                    val end = session.endMillis?.let { ThermalFormatting.clock(it) } ?: "now"
                    TimelineRow(
                        time = "${ThermalFormatting.clock(session.startMillis)}–$end",
                        title = if (session.isOpen) "Charging · in progress" else "Charging session",
                        detail = buildString {
                            append(ThermalFormatting.duration(session.durationMillis))
                            session.peakTemperatureC?.let { append(" · peak ${celsius(it)}") }
                            session.averageTemperatureC?.let { append(" · average ${celsius(it)}") }
                            append(" · ${session.sampleCount} samples")
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ReportsScreen() {
    ScreenContent {
        ScreenHeader("Reports & Export", "Local reports for diagnostics, thermal history and privacy/security review")
        FeatureCard("Report center", "Export actions are visible but inactive until the data layer exists", { Icon(Icons.Default.Download, null) }) {
            ReportButton("Thermal report", "24h / 7d charts, peaks and events")
            ReportButton("Security report", "Risk findings, evidence and network policy summary")
            ReportButton("Privacy report", "Tracker activity and privacy signals")
            ReportButton("Full diagnostic bundle", "App/build/device diagnostics")
        }
        FeatureCard("Formats", "Future output formats", { Icon(Icons.Default.Storage, null) }) {
            KeyValueRow("JSON", "Structured export")
            KeyValueRow("CSV", "Tabular history")
            KeyValueRow("PDF", "Human-readable report")
        }
    }
}
@Composable
fun DiagnosticsScreen() {
    val viewModel: DiagnosticsViewModel = viewModel()
    val checks by viewModel.checks.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val monitoringEnabled by viewModel.monitoringEnabled.collectAsStateWithLifecycle()

    ScreenContent {
        ScreenHeader(
            "Diagnostics",
            "Runtime readiness reported by the engine, including what it cannot verify"
        )

        FeatureCard(
            title = "Environment",
            subtitle = "Read from this device at runtime",
            icon = { Icon(Icons.Default.Memory, null) }
        ) {
            KeyValueRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            KeyValueRow("Device", "${Build.MANUFACTURER} ${Build.MODEL}")
            KeyValueRow("App build", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            KeyValueRow("Monitoring intent", if (monitoringEnabled) "Enabled by the user" else "Not enabled")
            KeyValueRow("Network protection", "Not implemented in this build")
            KeyValueRow("Security scanner", "Not implemented in this build")
        }

        FeatureCard(
            title = "Readiness checks",
            subtitle = "\"Cannot be verified\" is a real answer, not a failure state",
            icon = { Icon(Icons.Default.CheckCircle, null) }
        ) {
            if (checks.isEmpty()) {
                Text(
                    "No checks have reported yet. They run automatically when this screen opens.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                checks.forEach { check -> ReadinessRow(check) }
            }
            Button(
                onClick = viewModel::runChecks,
                enabled = !running,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (running) "Running checks…" else "Run diagnostics again")
            }
        }

        NoticeCard(
            "The monitoring service uses a foreground service with an ongoing notification. Some device " +
                "manufacturers stop background services anyway; if monitoring stops without you turning it " +
                "off, the Dashboard says so and offers to resume it.",
            isError = false
        )
    }
}

@Composable
fun SettingsScreen(
    preferences: UiPreferences,
    onThemeChanged: (String) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onAmoledChanged: (Boolean) -> Unit,
    onAccentChanged: (String) -> Unit
) {
    ScreenContent {
        ScreenHeader(
            "Settings",
            "The UI configuration is active now; engine-specific controls are presented as design targets"
        )
        FeatureCard("Appearance", "Theme changes are persisted locally and work in this UI build", { Icon(Icons.Default.DarkMode, null) }) {
            ThemeMode.entries.forEach { mode ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    RadioButton(selected = preferences.themeMode == mode, onClick = { onThemeChanged(mode.storageValue) })
                    Text(mode.storageValue.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Text("Color source", style = MaterialTheme.typography.titleMedium)
            SettingSwitch(
                "Dynamic system colors",
                "Use Android's dynamic color scheme when available",
                preferences.dynamicColor,
                onCheckedChange = onDynamicColorChanged
            )
            SettingSwitch(
                "AMOLED black surfaces",
                "Use near-black surfaces when dark theme is active",
                preferences.amoled,
                onCheckedChange = onAmoledChanged
            )
            Text("Accent", style = MaterialTheme.typography.titleMedium)
            AccentGrid(
                selected = preferences.accent,
                onAccentChanged = onAccentChanged,
                enabled = !preferences.dynamicColor
            )
            if (preferences.dynamicColor) {
                Text(
                    "Dynamic system colors are enabled, so the selected accent is not applied. " +
                        "Turn off “Dynamic system colors” to use a custom accent.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        FeatureCard("Monitoring", "Future thermal/battery controls", { Icon(Icons.Default.Thermostat, null) }) {
            SettingSwitch("Background monitoring", "Persistent monitoring service", false)
            SettingSwitch("Adaptive sampling", "Change sampling rate based on thermal state", true)
            SettingSwitch("24h history", "Persist thermal measurements locally", true)
        }
        FeatureCard("Network protection", "Future VPN controls", { Icon(Icons.Default.Wifi, null) }) {
            SettingSwitch("Network protection", "Local VPN firewall", false)
            SettingSwitch("Always-on VPN guidance", "Expose setup state without silently changing system settings", false)
            SettingSwitch("Fail-safe VPN behavior", "Show protection OFF when forwarding is unavailable", true)
        }
        FeatureCard("Security", "Future scanner and policy controls", { Icon(Icons.Default.Security, null) }) {
            SettingSwitch("App risk scanning", "Evidence-weighted PackageManager scanner", false)
            SettingSwitch("Usage correlation", "Optional UsageStats correlation", false)
            SettingSwitch("Device Owner mode", "Separate managed-device capability surface", false)
        }
        FeatureCard("Notifications", "Notification design targets", { Icon(Icons.Default.Notifications, null) }) {
            SettingSwitch("Thermal alerts", "Temperature/thermal status alerts", true)
            SettingSwitch("Security alerts", "High-confidence security findings", true)
            SettingSwitch("Network alerts", "Phishing/malware protection events", true)
        }
        FeatureCard("Data & privacy", "Local-first product behavior", { Icon(Icons.Default.Lock, null) }) {
            KeyValueRow("Cloud account", "None")
            KeyValueRow("Remote analytics", "None")
            KeyValueRow("Advertising SDKs", "None")
            KeyValueRow("Purchases", "Not implemented")
            KeyValueRow("Default storage", "On-device")
            KeyValueRow("HTTPS inspection", "Never")
        }
    }
}

/**
 * Selectable accent swatch row. Each swatch is 48dp (Android minimum touch
 * target) and uses FlowRow with selectableGroup() so five items wrap at large
 * font scales and expose correct radio-group semantics. The selected swatch
 * shows a 3dp ring rather than an overlay glyph.
 */
@Composable
private fun AccentGrid(
    selected: AccentPreset,
    onAccentChanged: (String) -> Unit,
    enabled: Boolean = true
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().selectableGroup()
    ) {
        AccentPreset.entries.forEach { preset ->
            val accentSeed = preset.seed()
            val isSelected = preset == selected && enabled
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .selectable(
                            selected = preset == selected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { onAccentChanged(preset.storageValue) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val swatchBase = Modifier
                        .size(44.dp)
                        .background(
                            color = if (enabled) accentSeed.primary
                            else accentSeed.primary.copy(alpha = 0.38f),
                            shape = CircleShape
                        )
                    val swatch = if (isSelected) {
                        swatchBase.border(
                            width = 3.dp,
                            color = MaterialTheme.colorScheme.onSurface,
                            shape = CircleShape
                        )
                    } else {
                        swatchBase
                    }
                    Box(modifier = swatch)
                }
                Text(
                    preset.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = onCheckedChange != null)
    }
}

@Composable
private fun RiskRow(label: String, value: String, progress: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.labelLarge)
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ReportButton(title: String, subtitle: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Download, null)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("UI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}
@Composable
fun WidgetsScreen() {
    val preferencesViewModel: UiPreferencesViewModel = viewModel()
    val monitoring: MonitoringViewModel = viewModel()
    val preferences by preferencesViewModel.preferences.collectAsStateWithLifecycle()
    val sample by monitoring.latestSample.collectAsStateWithLifecycle()

    val style = preferences?.widgetStyle ?: WidgetStyle.SYSTEM
    val battery = sample?.battery
    val temperature = battery?.temperatureC
    val current = sample

    ScreenContent {
        ScreenHeader(
            "Widgets",
            "The home-screen widget is implemented and reads the same local history as the screens"
        )

        FeatureCard(
            title = "Widget style",
            subtitle = "Persisted immediately and applied by the widget provider",
            icon = { Icon(Icons.Default.Tune, null) }
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                WidgetStyle.entries.forEachIndexed { index, candidate ->
                    SegmentedButton(
                        selected = style == candidate,
                        onClick = { preferencesViewModel.setWidgetStyle(candidate) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = WidgetStyle.entries.size
                        )
                    ) {
                        Text(candidate.label)
                    }
                }
            }
            Text(
                style.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Adding the widget is done from the launcher's widget picker; the app cannot pin it for " +
                    "you. The provider refreshes when monitoring stores a new sample (at most once a " +
                    "minute) and every hour as a fallback.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        FeatureCard(
            title = "Thermal widget",
            subtitle = "Preview uses the last stored reading from this device",
            icon = { Icon(Icons.Default.Thermostat, null) }
        ) {
            WidgetPreview(
                title = "Thermal",
                primary = temperatureText(temperature),
                secondary = if (current == null) {
                    "No measurement yet"
                } else {
                    "${labelText(sample?.thermal?.thermalStatus)} · ${ageText(current.timestampMillis)}"
                },
                icon = Icons.Default.Thermostat,
                style = style
            )
        }

        FeatureCard(
            title = "Battery widget",
            subtitle = "Charge level and the battery temperature from the same sample",
            icon = { Icon(Icons.Default.BatteryFull, null) }
        ) {
            WidgetPreview(
                title = "Battery",
                primary = percentText(battery?.batteryPercent),
                secondary = if (current == null) "No measurement yet" else temperatureText(temperature),
                icon = Icons.Default.BatteryFull,
                style = style
            )
        }

        FeatureCard(
            title = "Protection widget",
            subtitle = "Shown as off until a network engine exists",
            icon = { Icon(Icons.Default.Shield, null) }
        ) {
            WidgetPreview(
                title = "Protection",
                primary = "OFF",
                secondary = "No VPN engine in this build",
                icon = Icons.Default.Shield,
                style = style
            )
        }
    }
}

@Composable
private fun ListRow(title: String, subtitle: String, enabled: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked = enabled, onCheckedChange = null, enabled = false)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WidgetPreview(
    title: String,
    primary: String,
    secondary: String,
    icon: ImageVector,
    style: WidgetStyle = WidgetStyle.SYSTEM
) {
    val container = when (style) {
        WidgetStyle.SYSTEM -> MaterialTheme.colorScheme.surfaceContainerHighest
        WidgetStyle.THEMED -> MaterialTheme.colorScheme.primaryContainer
        WidgetStyle.TRANSPARENT -> Color.Transparent
    }
    val content = when (style) {
        WidgetStyle.THEMED -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        border = if (style == WidgetStyle.TRANSPARENT)
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        else null
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge)
                Text(primary, style = MaterialTheme.typography.headlineSmall)
                Text(
                    secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun AboutScreen() {
    ScreenContent {
        ScreenHeader("About", "Thermal Sentinel UI foundation")
        FeatureCard("Thermal Sentinel", "Private, local-first Android device protection architecture", { Icon(Icons.Default.Shield, null) }) {
            KeyValueRow("UI version", BuildConfig.VERSION_NAME)
            KeyValueRow("Minimum Android", "31")
            KeyValueRow("Target Android", "36")
            KeyValueRow("License", "Private / personal project")
        }
        FeatureCard("Design principles", "Rules that the implementation will preserve", { Icon(Icons.Default.Tune, null) }) {
            LockedFeatureRow("No root", "No private Android APIs or rooting requirement", { Icon(Icons.Default.Lock, null) })
            LockedFeatureRow("No cloud by default", "Local storage and on-device processing first", { Icon(Icons.Default.CloudOff, null) })
            LockedFeatureRow("No HTTPS interception", "Network protection does not install a CA or decrypt payloads", { Icon(Icons.Default.Lock, null) })
            LockedFeatureRow("Fail safe", "Protection state is explicit when a subsystem is unavailable", { Icon(Icons.Default.Shield, null) })
        }
        FeatureCard(
            "Monetisation position",
            "Frozen product decision for any future release",
            { Icon(Icons.Default.Tune, null) }
        ) {
            Text(
                "If monetisation is introduced, it will be a one-time Pro unlock. No advertising SDKs, " +
                    "no rewarded ads, no remote analytics. Play Billing is not implemented in this UI " +
                    "build and will be added as its own phase behind the same local-first data model.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LockedFeatureRow(
                "Pro unlock",
                "Future one-time purchase for insights and custom thresholds. No ads.",
                { Icon(Icons.Default.Lock, null) }
            )
        }
        Text(
            "This build is intentionally UI-first. The screens, navigation, theme system and persisted appearance " +
                "settings are real; feature engines are not.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
@Composable
fun AlertRulesScreen() {
    val viewModel: AlertRulesViewModel = viewModel()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val violations by viewModel.violations.collectAsStateWithLifecycle()

    ScreenContent {
        ScreenHeader(
            "Alert Rules",
            "Persisted thresholds — the running engine evaluates exactly these values"
        )

        if (violations.isNotEmpty()) {
            NoticeCard(
                "These settings would not be usable, so the stored values were clamped: " +
                    violations.joinToString(" "),
                isError = true
            )
        }

        FeatureCard(
            title = "Warning threshold",
            subtitle = "First-level notification when the battery temperature crosses this value",
            icon = { Icon(Icons.Default.Thermostat, null) }
        ) {
            SettingSwitch(
                title = "Enable warning alerts",
                subtitle = "Notifies once, then no more often than the cooldown allows",
                checked = rules.warningEnabled,
                onCheckedChange = viewModel::setWarningEnabled
            )
            TemperatureEditor(
                label = "Warning temperature",
                value = rules.warningThresholdC,
                onValueChange = viewModel::setWarningThreshold,
                enabled = rules.warningEnabled,
                range = 38f..47f
            )
            Text(
                "Clears when the temperature falls to ${celsius(rules.warningRecoveryC)}, " +
                    "which is the ${celsius(rules.hysteresisC)} hysteresis band below the threshold.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        FeatureCard(
            title = "Critical threshold",
            subtitle = "Escalates and uses a higher-priority notification channel",
            icon = { Icon(Icons.Default.Thermostat, null) }
        ) {
            SettingSwitch(
                title = "Enable critical alerts",
                subtitle = "Kept at least 1 °C above the warning threshold",
                checked = rules.criticalEnabled,
                onCheckedChange = viewModel::setCriticalEnabled
            )
            TemperatureEditor(
                label = "Critical temperature",
                value = rules.criticalThresholdC,
                onValueChange = viewModel::setCriticalThreshold,
                enabled = rules.criticalEnabled,
                range = 44f..55f
            )
            Text(
                "De-escalates to a warning at ${celsius(rules.criticalRecoveryC)}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        FeatureCard(
            title = "Thermal status alerts",
            subtitle = "Trigger from platform thermal-status changes as well as temperature",
            icon = { Icon(Icons.Default.Speed, null) }
        ) {
            Text(
                "Select the platform bands that should raise an alert. NONE is not selectable because it " +
                    "is the normal state.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ThermalStatusBand.entries
                    .filter { it.selectable }
                    .forEach { band ->
                        FilterChip(
                            selected = band in rules.statusBandTriggers,
                            onClick = { viewModel.toggleStatusBand(band) },
                            label = { Text(band.label) }
                        )
                    }
            }
            Text(
                if (rules.statusBandTriggers.isEmpty()) {
                    "No bands selected. Thermal-status alerts will not fire; temperature alerts still will."
                } else {
                    "Platform level ${rules.lowestTriggeredStatusLevel ?: 0} and above will raise an alert."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        FeatureCard(
            title = "Hysteresis and cooldown",
            subtitle = "Hysteresis decides when the state clears; the cooldown only limits repeats",
            icon = { Icon(Icons.Default.Timer, null) }
        ) {
            TemperatureEditor(
                label = "Hysteresis band",
                value = rules.hysteresisC,
                onValueChange = viewModel::setHysteresis,
                enabled = true,
                range = 0f..3f
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Repeat cooldown", style = MaterialTheme.typography.bodyMedium)
                Text("${rules.cooldownMinutes} min", style = MaterialTheme.typography.titleMedium)
            }
            Slider(
                value = rules.cooldownMinutes.toFloat(),
                onValueChange = { viewModel.setCooldownMinutes(it.roundToInt()) },
                valueRange = 5f..60f,
                steps = 10
            )
            Text(
                "While the state stays escalated, a repeat notification is allowed only after the cooldown. " +
                    "A new escalation always notifies immediately, and the \"back in range\" note is never " +
                    "suppressed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        FeatureCard(
            title = "Charging-specific rule",
            subtitle = "A separate threshold that only applies while on external power",
            icon = { Icon(Icons.Default.Bolt, null) }
        ) {
            SettingSwitch(
                title = "Enable charging-specific threshold",
                subtitle = "Useful because charging adds heat that has nothing to do with load",
                checked = rules.chargingWarningEnabled,
                onCheckedChange = viewModel::setChargingWarningEnabled
            )
            TemperatureEditor(
                label = "Charging warning temperature",
                value = rules.chargingWarningThresholdC,
                onValueChange = viewModel::setChargingWarningThreshold,
                enabled = rules.chargingWarningEnabled,
                range = 38f..50f
            )
        }

        FeatureCard(
            title = "Engine boundaries",
            subtitle = "What the alert engine does not do",
            icon = { Icon(Icons.Default.Info, null) }
        ) {
            Text(
                "Alerts act only on signals Android exposes through BatteryManager and PowerManager. " +
                    "The engine does not override the platform thermal engine, does not force-stop apps, " +
                    "does not change display brightness and does not upload anything. Notifications are " +
                    "posted locally even when the notification permission is denied — Android simply hides them.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = viewModel::resetToDefaults, modifier = Modifier.fillMaxWidth()) {
                Text("Reset to defaults (${celsius(40f)} / ${celsius(48f)}, 15 min)")
            }
        }
    }
}

@Composable
private fun TemperatureEditor(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    enabled: Boolean,
    range: ClosedFloatingPointRange<Float>
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.outline
            )
            Text(
                "${value.roundToInt()}°C",
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.outline
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            enabled = enabled,
            steps = (range.endInclusive - range.start).roundToInt() - 1
        )
    }
}

// ---------------------------------------------------------------------------
// Quick Access — launcher surface for Android Settings intents.
// Every row is disabled until the intent integration phase begins. The target
// intent action is printed on each row so the future wiring is unambiguous.
// ---------------------------------------------------------------------------

@Composable
fun QuickAccessScreen() {
    ScreenContent {
        ScreenHeader(
            "Quick Access",
            "Shortcuts into the Android Settings screens relevant to thermal and network diagnosis"
        )
        PreviewBanner()
        QuickAccessRow(
            title = "Battery settings",
            subtitle = "Battery saver, adaptive battery and per-app background policy",
            intentAction = "Settings.ACTION_BATTERY_SAVER_SETTINGS",
            icon = Icons.Default.BatteryFull
        )
        QuickAccessRow(
            title = "Display settings",
            subtitle = "Brightness, adaptive brightness and screen timeout",
            intentAction = "Settings.ACTION_DISPLAY_SETTINGS",
            icon = Icons.Default.DarkMode
        )
        QuickAccessRow(
            title = "Network settings",
            subtitle = "Wi-Fi, mobile data, VPN and private DNS",
            intentAction = "Settings.ACTION_WIRELESS_SETTINGS",
            icon = Icons.Default.Wifi
        )
        QuickAccessRow(
            title = "App settings",
            subtitle = "Application list, permissions and per-app battery usage",
            intentAction = "Settings.ACTION_APPLICATION_SETTINGS",
            icon = Icons.Default.Apps
        )
        FeatureCard(
            "Engine boundary",
            "Quick Access never changes a system setting directly",
            { Icon(Icons.Default.Info, null) }
        ) {
            Text(
                "Each row opens the corresponding Android Settings screen and lets the user make the change. " +
                    "The app will not write to Settings.System, Settings.Secure or Settings.Global outside of " +
                    "an explicit, separately authorized flow such as the brightness reduction setup.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuickAccessRow(
    title: String,
    subtitle: String,
    intentAction: String,
    icon: ImageVector
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, null, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    intentAction,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun QuickAccessCard(onOpen: () -> Unit) {
    FeatureCard(
        title = "Quick access",
        subtitle = "Jump to the Android Settings screens used during thermal and network diagnosis",
        icon = { Icon(Icons.Default.SettingsApplications, null) }
    ) {
        QuickAccessRow(
            title = "Battery settings",
            subtitle = "Battery saver and per-app background policy",
            intentAction = "Settings.ACTION_BATTERY_SAVER_SETTINGS",
            icon = Icons.Default.BatteryFull
        )
        QuickAccessRow(
            title = "Network settings",
            subtitle = "Wi-Fi, mobile data, VPN and private DNS",
            intentAction = "Settings.ACTION_WIRELESS_SETTINGS",
            icon = Icons.Default.Wifi
        )
        TextButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
            Text("Open Quick Access")
        }
    }
}
