package com.thermalsentinel.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.thermalsentinel.ui.data.UiPreferences
import com.thermalsentinel.ui.screens.AboutScreen
import com.thermalsentinel.ui.screens.ActivityScreen
import com.thermalsentinel.ui.screens.AlertRulesScreen
import com.thermalsentinel.ui.screens.AppsScreen
import com.thermalsentinel.ui.screens.BatteryScreen
import com.thermalsentinel.ui.screens.BlocklistsScreen
import com.thermalsentinel.ui.screens.DashboardScreen
import com.thermalsentinel.ui.screens.DiagnosticsScreen
import com.thermalsentinel.ui.screens.DnsScreen
import com.thermalsentinel.ui.screens.FirewallScreen
import com.thermalsentinel.ui.screens.HistoryScreen
import com.thermalsentinel.ui.screens.NetworkScreen
import com.thermalsentinel.ui.screens.NetworkStatsScreen
import com.thermalsentinel.ui.screens.PrivacyScreen
import com.thermalsentinel.ui.screens.QuickAccessScreen
import com.thermalsentinel.ui.screens.ReportsScreen
import com.thermalsentinel.ui.screens.ScannerScreen
import com.thermalsentinel.ui.screens.SettingsScreen
import com.thermalsentinel.ui.screens.ThermalScreen
import com.thermalsentinel.ui.screens.WidgetsScreen

/**
 * Nav graph builder.
 *
 * Registration iterates [allDestinations], so the graph cannot fall behind the
 * drawer list as new routes are added there. A route that has a drawer entry
 * but no composable branch below still fails at first navigation, not at
 * compile time; the `else -> error(...)` branch makes that failure explicit
 * rather than showing a blank pane.
 *
 * RouteContractTest guards Routes <-> allDestinations. It does not see this
 * `when` block; adding a route to both the object and the drawer list without
 * a screen here is caught by hand on first navigation, not by CI.
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    preferences: UiPreferences,
    onThemeChanged: (String) -> Unit = {},
    onDynamicColorChanged: (Boolean) -> Unit = {},
    onAmoledChanged: (Boolean) -> Unit = {},
    onAccentChanged: (String) -> Unit = {}
) {
    NavHost(
        navController = navController,
        startDestination = Routes.DASHBOARD,
        modifier = modifier
    ) {
        allDestinations.forEach { destination ->
            composable(destination.route) {
                when (destination.route) {
                    Routes.DASHBOARD -> DashboardScreen(
                        onNavigate = { route ->
                            navController.navigate(route) {
                                launchSingleTop = true
                                restoreState = true
                                popUpTo(Routes.DASHBOARD) { saveState = true }
                            }
                        }
                    )
                    Routes.THERMAL -> ThermalScreen()
                    Routes.BATTERY -> BatteryScreen()
                    Routes.ALERT_RULES -> AlertRulesScreen()
                    Routes.NETWORK -> NetworkScreen()
                    Routes.FIREWALL -> FirewallScreen()
                    Routes.DNS -> DnsScreen()
                    Routes.BLOCKLISTS -> BlocklistsScreen()
                    Routes.NETWORK_STATS -> NetworkStatsScreen()
                    Routes.PRIVACY -> PrivacyScreen()
                    Routes.APPS -> AppsScreen()
                    Routes.SCANNER -> ScannerScreen()
                    Routes.ACTIVITY -> ActivityScreen()
                    Routes.HISTORY -> HistoryScreen()
                    Routes.REPORTS -> ReportsScreen()
                    Routes.QUICK_ACCESS -> QuickAccessScreen()
                    Routes.DIAGNOSTICS -> DiagnosticsScreen()
                    Routes.SETTINGS -> SettingsScreen(
                        preferences = preferences,
                        onThemeChanged = onThemeChanged,
                        onDynamicColorChanged = onDynamicColorChanged,
                        onAmoledChanged = onAmoledChanged,
                        onAccentChanged = onAccentChanged
                    )
                    Routes.WIDGETS -> WidgetsScreen()
                    Routes.ABOUT -> AboutScreen()
                    else -> error("No composable screen registered for route ${destination.route}")
                }
            }
        }
    }
}
