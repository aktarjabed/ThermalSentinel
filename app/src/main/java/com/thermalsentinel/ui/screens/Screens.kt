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
import com.thermalsentinel.ui.BuildConfig
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
import com.thermalsentinel.ui.data.ThermalStatusBand
import com.thermalsentinel.ui.data.UiPreferences
import com.thermalsentinel.ui.data.WidgetStyle
import com.thermalsentinel.ui.navigation.Routes
// Required: seed() is an internal extension in the theme package.
import com.thermalsentinel.ui.theme.seed
import java.util.Locale
import kotlin.math.roundToInt

private val thermalPoints = listOf(
    35.8f, 36.2f, 36.7f, 37.1f, 36.5f, 38.4f, 39.2f, 38.6f, 40.1f, 41.3f, 40.2f, 39.5f, 38.4f
)
private val thermalMin = thermalPoints.min()
private val thermalMax = thermalPoints.max()
private val thermalAvg = thermalPoints.average().toFloat()
private val thermalLast = thermalPoints.last()

private val historyPoints = listOf(
    35.1f, 36.4f, 37.0f, 36.8f, 37.5f, 39.1f, 38.4f, 40.2f, 41.8f, 40.7f, 39.9f, 38.5f, 37.8f, 36.9f
)
private val historyMin = historyPoints.min()
private val historyMax = historyPoints.max()
private val historyAvg = historyPoints.average().toFloat()

/**
 * Gauge progress for the current reading, normalised against the chart's
 * 34–44°C plotting window. Used by both the Dashboard and Thermal gauges so
 * they cannot disagree about the same value.
 */
private val thermalGaugeProgress = (thermalLast - 34f) / 10f

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

@Composable
private fun ScreenHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun DashboardScreen(onNavigate: (String) -> Unit = {}) {
    ScreenContent {
        ScreenHeader(
            "Device overview",
            "A single local-first control surface for thermal, battery, network and security"
        )
        PreviewBanner()
        FeatureCard(
            title = "Thermal state",
            subtitle = "Preview sample · battery sensor not connected",
            icon = { Icon(Icons.Default.Thermostat, null) }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Gauge(thermalGaugeProgress, "normal", celsius(thermalLast), size = 110.dp)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    StatusPill("THERMAL: NORMAL", true)
                    KeyValueRow("Android thermal status", "NONE")
                    KeyValueRow("Peak (window)", celsius(thermalMax))
                    KeyValueRow("Sampling", "not active")
                }
            }
        }
        QuickAccessCard(onOpen = { onNavigate(Routes.QUICK_ACCESS) })
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                title = "Battery",
                value = "78%",
                subtitle = "Preview charge state",
                icon = { Icon(Icons.Default.BatteryFull, null) },
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "VPN",
                value = "OFF",
                subtitle = "Protection not connected",
                icon = { Icon(Icons.Default.WifiOff, null) },
                modifier = Modifier.weight(1f)
            )
        }
        FeatureCard(
            title = "Risk snapshot",
            subtitle = "UI model for the future correlation engine",
            icon = { Icon(Icons.Default.Shield, null) }
        ) {
            RiskRow("Thermal load", "Low", 0.22f)
            RiskRow("Charging heat", "Low", 0.18f)
            RiskRow("Network exposure", "Unknown", 0.48f)
            RiskRow("App risk", "Unknown", 0.42f)
        }
        SectionTitle("Upcoming protection modules", "All navigation targets are already wired into the UI.")
        LockedFeatureRow("Local VPN firewall", "Per-app network policy, blocklists and traffic attribution", { Icon(Icons.Default.Lan, null) })
        LockedFeatureRow("DNS protection", "Ad, tracker, phishing and malware domain filtering", { Icon(Icons.Default.Dns, null) })
        LockedFeatureRow("Security scanner", "Evidence-weighted app risk assessment", { Icon(Icons.Default.VerifiedUser, null) })
        LockedFeatureRow("Thermal history", "24h / 7d trends, heating events and charging peaks", { Icon(Icons.Default.History, null) })
    }
}

@Composable
fun ThermalScreen() {
    var range by rememberSaveable { mutableStateOf(0) }
    ScreenContent {
        ScreenHeader("Thermal Monitor", "Battery temperature, Android thermal status, headroom and event history")
        FeatureCard("Current thermal signal", "Preview values only", { Icon(Icons.Default.Thermostat, null) }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Gauge(thermalGaugeProgress, "battery", celsius(thermalLast), size = 110.dp)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusPill("NORMAL", true)
                    KeyValueRow("Thermal status", "NONE")
                    KeyValueRow("Peak (window)", celsius(thermalMax))
                    KeyValueRow("Headroom", "not connected")
                }
            }
        }
        FeatureCard("Temperature trend", "Synthetic UI dataset for chart layout", { Icon(Icons.Default.GraphicEq, null) }) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("24h", "7d", "30d").forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = range == index,
                        onClick = { range = index },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 3)
                    ) {
                        Text(label)
                    }
                }
            }
            LineChart(thermalPoints, 34f, 44f)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                KeyValueRow("Minimum", celsius(thermalMin), modifier = Modifier.weight(1f))
                KeyValueRow("Maximum", celsius(thermalMax), modifier = Modifier.weight(1f))
            }
            KeyValueRow("Average", celsius(thermalAvg))
        }
        FeatureCard("Thermal events", "Rise speed, duration and threshold crossings", { Icon(Icons.Default.Speed, null) }) {
            TimelineRow("14:42", "Fast heating event", "+3.4°C in 7 minutes")
            TimelineRow("12:18", "Charging peak", "39.8°C while charging")
            TimelineRow("09:06", "Cooling event", "−4.1°C in 13 minutes")
        }
        FeatureCard("Alert policy", "Detailed thresholds live in Alert Rules", { Icon(Icons.Default.Notifications, null) }) {
            SettingSwitch("High-temperature warning", "Notify when configured warning condition is reached", false)
            SettingSwitch("Critical thermal warning", "Escalate only for platform-supported critical states", true)
            SettingSwitch("Cooling guidance", "Show user-safe cooling recommendations", true)
        }
    }
}

@Composable
fun BatteryScreen() {
    ScreenContent {
        ScreenHeader("Battery & Charging", "Battery state, current/voltage, sessions, health signals and charging heat")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("Charge", "78%", "Preview", { Icon(Icons.Default.BatteryFull, null) }, Modifier.weight(1f))
            MetricCard("Voltage", "4.12 V", "Preview", { Icon(Icons.Default.Bolt, null) }, Modifier.weight(1f))
        }
        FeatureCard("Battery health", "Only device-supported signals will be shown by the future engine", { Icon(Icons.Default.VerifiedUser, null) }) {
            KeyValueRow("Health state", "Good")
            KeyValueRow("Cycle count", "Not available")
            KeyValueRow("Design capacity", "Not available")
            KeyValueRow("Temperature", celsius(thermalLast))
        }
        FeatureCard("Charging session", "Session-level statistics are represented here", { Icon(Icons.Default.Bolt, null) }) {
            KeyValueRow("Session", "00:38:12")
            KeyValueRow("Average temperature", "38.7°C")
            KeyValueRow("Peak temperature", "40.3°C")
            KeyValueRow("Peak current", "Not connected")
        }
        FeatureCard("Charging behavior", "Future controls", { Icon(Icons.Default.Tune, null) }) {
            SettingSwitch("Charging heat markers", "Place charging markers on historical temperature charts", true)
            SettingSwitch("Peak notifications", "Notify after a sustained charging heat peak", false)
        }
        FeatureCard(
            "Low-Temperature Mode",
            "Guidance only. Automatic brightness reduction is deliberately kept outside the monitoring engine.",
            { Icon(Icons.Default.AcUnit, null) }
        ) {
            SettingSwitch(
                "Show cold-condition guidance",
                "Display practical low-temperature advice without changing any system setting",
                true
            )
            LockedFeatureRow(
                "Automatic brightness reduction",
                "Requires an explicit Settings.System.canWrite() authorization flow with its own setup screen. " +
                    "Not part of the ordinary monitoring engine.",
                { Icon(Icons.Default.BrightnessAuto, null) }
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
    var range by rememberSaveable { mutableStateOf(0) }
    ScreenContent {
        ScreenHeader("Thermal History", "24-hour and multi-day thermal insights with charging markers")
        FeatureCard("History range", "Preview data only", { Icon(Icons.Default.History, null) }) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("24h", "7d", "30d").forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = range == index,
                        onClick = { range = index },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 3)
                    ) {
                        Text(label)
                    }
                }
            }
            LineChart(historyPoints, 34f, 44f)
            KeyValueRow("Minimum", celsius(historyMin))
            KeyValueRow("Maximum", celsius(historyMax))
            KeyValueRow("Average", celsius(historyAvg))
            KeyValueRow("Time above warning", "00:18:24")
        }
        FeatureCard("Charging markers", "Charging sessions will appear over the thermal timeline", { Icon(Icons.Default.Bolt, null) }) {
            TimelineRow("14:05–14:43", "Charging session", "Preview peak 40.3°C")
            TimelineRow("08:12–09:01", "Charging session", "Preview peak 38.6°C")
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
    ScreenContent {
        ScreenHeader("Diagnostics", "Environment readiness, permissions and subsystem health")
        FeatureCard("Environment", "UI shell diagnostics are always available", { Icon(Icons.Default.Memory, null) }) {
            KeyValueRow("Android", "Preview")
            KeyValueRow("API level", "31+")
            KeyValueRow("App build", BuildConfig.VERSION_NAME)
            KeyValueRow("Network protection", "Not connected")
            KeyValueRow("Thermal engine", "Not connected")
        }
        FeatureCard("Readiness checks", "Future runtime checks will replace the static status values", { Icon(Icons.Default.Refresh, null) }) {
            DiagnosticRow("Compose UI", true)
            DiagnosticRow("Theme engine", true)
            DiagnosticRow("Navigation", true)
            DiagnosticRow("Battery monitor", false)
            DiagnosticRow("Thermal monitor", false)
            DiagnosticRow("VPN service", false)
            DiagnosticRow("DNS proxy", false)
            DiagnosticRow("Security scanner", false)
        }
        Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
            Text("Run full diagnostics")
        }
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
private fun DiagnosticRow(label: String, ready: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            if (ready) Icons.Default.CheckCircle else Icons.Default.Info,
            contentDescription = null,
            tint = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.width(10.dp))
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(
            if (ready) "Ready" else "Not connected",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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

/**
 * Preview-only screen. The selected style is rememberSaveable so it survives
 * rotation, but it is deliberately not written to DataStore. The widget
 * provider contract is frozen; provider implementations are not part of this
 * build.
 */
@Composable
fun WidgetsScreen() {
    var style by rememberSaveable { mutableStateOf(WidgetStyle.SYSTEM) }
    ScreenContent {
        ScreenHeader(
            "Widgets",
            "Home-screen surfaces for thermal, battery and network status"
        )
        FeatureCard(
            "Widget style",
            "Frozen contract. Three variants are defined; providers are not implemented in this build.",
            { Icon(Icons.Default.Tune, null) }
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                WidgetStyle.entries.forEachIndexed { index, candidate ->
                    SegmentedButton(
                        selected = style == candidate,
                        onClick = { style = candidate },
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
        }
        FeatureCard("Thermal widget", "Compact status widget", { Icon(Icons.Default.Thermostat, null) }) {
            WidgetPreview(
                title = "Thermal",
                primary = celsius(thermalLast),
                secondary = "Normal · sample",
                icon = Icons.Default.Thermostat,
                style = style
            )
            TextButton(onClick = {}, enabled = false) { Text("Pin widget — future") }
        }
        FeatureCard("Protection widget", "Network and security state at a glance", { Icon(Icons.Default.Shield, null) }) {
            WidgetPreview(
                title = "Protection",
                primary = "OFF",
                secondary = "VPN not connected",
                icon = Icons.Default.Shield,
                style = style
            )
            TextButton(onClick = {}, enabled = false) { Text("Pin widget — future") }
        }
        FeatureCard("Battery widget", "Charge level and charging temperature", { Icon(Icons.Default.BatteryFull, null) }) {
            WidgetPreview(
                title = "Battery",
                primary = "78%",
                secondary = "${celsius(thermalLast)} · sample",
                icon = Icons.Default.BatteryFull,
                style = style
            )
            TextButton(onClick = {}, enabled = false) { Text("Pin widget — future") }
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

// ---------------------------------------------------------------------------
// Alert Rules — frozen UI contract for the future alert engine.
//
// Preview-only. Values use rememberSaveable so they survive rotation, but they
// are deliberately NOT persisted to DataStore and no notification is scheduled
// by this build. When the alert engine is implemented, these values will be
// replaced by repository-backed state without changing the screen signature.
// ---------------------------------------------------------------------------

@Composable
fun AlertRulesScreen() {
    var warningEnabled by rememberSaveable { mutableStateOf(true) }
    var warningTemp by rememberSaveable { mutableStateOf(40f) }
    var criticalEnabled by rememberSaveable { mutableStateOf(true) }
    var criticalTemp by rememberSaveable { mutableStateOf(48f) }
    var cooldownMinutes by rememberSaveable { mutableStateOf(15) }
    var chargingEnabled by rememberSaveable { mutableStateOf(false) }
    var chargingTemp by rememberSaveable { mutableStateOf(45f) }
    // Set<enum> is not Bundle-saveable; store storage keys instead.
    var selectedStatusKeys by rememberSaveable {
        mutableStateOf(
            listOf(
                ThermalStatusBand.SEVERE.storageValue,
                ThermalStatusBand.CRITICAL.storageValue
            )
        )
    }
    val statusSelection: Set<ThermalStatusBand> =
        selectedStatusKeys
            .map(ThermalStatusBand::fromStorage)
            .filter { it.selectable }
            .toSet()

    ScreenContent {
        ScreenHeader(
            "Alert Rules",
            "Threshold, thermal-status and cooldown configuration for the future alert engine"
        )
        PreviewBanner()

        FeatureCard(
            "Warning threshold",
            "Notifies when the battery temperature crosses this value",
            { Icon(Icons.Default.Thermostat, null) }
        ) {
            SettingSwitch(
                "Enable warning alerts",
                "First-level notification when the warning threshold is crossed",
                warningEnabled,
                onCheckedChange = { warningEnabled = it }
            )
            TemperatureEditor(
                label = "Warning temperature",
                value = warningTemp,
                onValueChange = { new ->
                    // Warning must stay strictly below critical.
                    warningTemp = new.coerceAtMost(criticalTemp - 1f)
                },
                enabled = warningEnabled,
                range = 38f..47f
            )
        }

        FeatureCard(
            "Critical threshold",
            "Escalated notification when the platform reports sustained high thermal load",
            { Icon(Icons.Default.Thermostat, null) }
        ) {
            SettingSwitch(
                "Enable critical alerts",
                "Higher-priority notification when the critical threshold is reached",
                criticalEnabled,
                onCheckedChange = { criticalEnabled = it }
            )
            TemperatureEditor(
                label = "Critical temperature",
                value = criticalTemp,
                onValueChange = { new ->
                    // Critical must stay strictly above warning.
                    criticalTemp = new.coerceAtLeast(warningTemp + 1f)
                },
                enabled = criticalEnabled,
                range = 44f..55f
            )
        }

        FeatureCard(
            "Thermal status alerts",
            "Trigger alerts from platform thermal-status transitions instead of raw temperature",
            { Icon(Icons.Default.Speed, null) }
        ) {
            Text(
                "Select the platform thermal-status bands that should trigger an alert. " +
                    "The NONE band is not selectable because it represents a normal state.",
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
                            selected = band in statusSelection,
                            onClick = {
                                selectedStatusKeys = if (band.storageValue in selectedStatusKeys) {
                                    selectedStatusKeys - band.storageValue
                                } else {
                                    selectedStatusKeys + band.storageValue
                                }
                            },
                            label = { Text(band.label) }
                        )
                    }
            }
            Text(
                if (statusSelection.isEmpty()) {
                    "No bands selected. No thermal-status alert will fire."
                } else {
                    "Platform level ${statusSelection.minOf { it.androidLevel }} and above " +
                        "will fire the alert once the engine is implemented."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        FeatureCard(
            "Hysteresis / cooldown",
            "Prevents repeated notifications while the device stays above a threshold",
            { Icon(Icons.Default.Timer, null) }
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Cooldown period", style = MaterialTheme.typography.bodyMedium)
                Text("$cooldownMinutes min", style = MaterialTheme.typography.titleMedium)
            }
            Slider(
                value = cooldownMinutes.toFloat(),
                onValueChange = { cooldownMinutes = it.roundToInt() },
                valueRange = 5f..60f,
                steps = 10
            )
            Text(
                "The engine will suppress repeat alerts until the cooldown expires or the temperature " +
                    "drops below the threshold by the configured hysteresis band.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        FeatureCard(
            "Charging-specific rule",
            "Separate threshold that only applies while the device is charging",
            { Icon(Icons.Default.Bolt, null) }
        ) {
            SettingSwitch(
                "Enable charging-specific threshold",
                "Use a distinct threshold while a charging session is active",
                chargingEnabled,
                onCheckedChange = { chargingEnabled = it }
            )
            TemperatureEditor(
                label = "Charging warning temperature",
                value = chargingTemp,
                onValueChange = { chargingTemp = it },
                enabled = chargingEnabled,
                range = 38f..50f
            )
        }

        FeatureCard(
            "Engine boundaries",
            "What the alert engine will not do",
            { Icon(Icons.Default.Info, null) }
        ) {
            Text(
                "Alert rules act only on signals Android exposes through BatteryManager and PowerManager. " +
                    "The engine will not override the platform thermal engine, will not force-stop apps, " +
                    "and will not change display brightness as part of ordinary monitoring.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
