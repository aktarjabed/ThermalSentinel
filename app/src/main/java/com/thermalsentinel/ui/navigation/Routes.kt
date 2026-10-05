package com.thermalsentinel.ui.navigation

import androidx.annotation.StringRes
import com.thermalsentinel.ui.R

object Routes {
    const val DASHBOARD = "dashboard"
    const val THERMAL = "thermal"
    const val BATTERY = "battery"
    const val ALERT_RULES = "alert_rules"
    const val HISTORY = "history"
    const val NETWORK = "network"
    const val FIREWALL = "firewall"
    const val DNS = "dns"
    const val BLOCKLISTS = "blocklists"
    const val NETWORK_STATS = "network_stats"
    const val PRIVACY = "privacy"
    const val APPS = "apps"
    const val SCANNER = "scanner"
    const val ACTIVITY = "activity"
    const val REPORTS = "reports"
    const val QUICK_ACCESS = "quick_access"
    const val DIAGNOSTICS = "diagnostics"
    const val SETTINGS = "settings"
    const val WIDGETS = "widgets"
    const val ABOUT = "about"
}

/**
 * Drawer groups are a closed set. Modelling them as an enum removes the
 * fragile English-string matching that an earlier revision used.
 */
enum class DestinationGroup(@StringRes val labelRes: Int) {
    OVERVIEW(R.string.group_overview),
    MONITORING(R.string.group_monitoring),
    NETWORK(R.string.group_network),
    SECURITY(R.string.group_security),
    TOOLS(R.string.group_tools)
}

internal data class Destination(
    val route: String,
    @StringRes val labelRes: Int,
    val group: DestinationGroup
)

internal val allDestinations = listOf(
    Destination(Routes.DASHBOARD, R.string.dest_dashboard, DestinationGroup.OVERVIEW),
    Destination(Routes.THERMAL, R.string.dest_thermal, DestinationGroup.MONITORING),
    Destination(Routes.BATTERY, R.string.dest_battery, DestinationGroup.MONITORING),
    Destination(Routes.ALERT_RULES, R.string.dest_alert_rules, DestinationGroup.MONITORING),
    Destination(Routes.HISTORY, R.string.dest_history, DestinationGroup.MONITORING),
    Destination(Routes.NETWORK, R.string.dest_network, DestinationGroup.NETWORK),
    Destination(Routes.FIREWALL, R.string.dest_firewall, DestinationGroup.NETWORK),
    Destination(Routes.DNS, R.string.dest_dns, DestinationGroup.NETWORK),
    Destination(Routes.BLOCKLISTS, R.string.dest_blocklists, DestinationGroup.NETWORK),
    Destination(Routes.NETWORK_STATS, R.string.dest_network_stats, DestinationGroup.NETWORK),
    Destination(Routes.PRIVACY, R.string.dest_privacy, DestinationGroup.SECURITY),
    Destination(Routes.APPS, R.string.dest_apps, DestinationGroup.SECURITY),
    Destination(Routes.SCANNER, R.string.dest_scanner, DestinationGroup.SECURITY),
    Destination(Routes.ACTIVITY, R.string.dest_activity, DestinationGroup.SECURITY),
    Destination(Routes.REPORTS, R.string.dest_reports, DestinationGroup.TOOLS),
    Destination(Routes.QUICK_ACCESS, R.string.dest_quick_access, DestinationGroup.TOOLS),
    Destination(Routes.DIAGNOSTICS, R.string.dest_diagnostics, DestinationGroup.TOOLS),
    Destination(Routes.SETTINGS, R.string.dest_settings, DestinationGroup.TOOLS),
    Destination(Routes.WIDGETS, R.string.dest_widgets, DestinationGroup.TOOLS),
    Destination(Routes.ABOUT, R.string.dest_about, DestinationGroup.TOOLS)
)

internal fun destinationForRoute(route: String): Destination? =
    allDestinations.firstOrNull { it.route == route }
