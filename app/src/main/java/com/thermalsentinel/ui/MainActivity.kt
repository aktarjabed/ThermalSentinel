package com.thermalsentinel.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.thermalsentinel.ui.data.AccentPreset
import com.thermalsentinel.ui.data.ThemeMode
import com.thermalsentinel.ui.data.UiPreferences
import com.thermalsentinel.ui.data.UiPreferencesViewModel
import com.thermalsentinel.ui.navigation.AppNavHost
import com.thermalsentinel.ui.navigation.Destination
import com.thermalsentinel.ui.navigation.DestinationGroup
import com.thermalsentinel.ui.navigation.Routes
import com.thermalsentinel.ui.navigation.allDestinations
import com.thermalsentinel.ui.navigation.destinationForRoute
import com.thermalsentinel.ui.theme.ThermalSentinelTheme
import kotlinx.coroutines.launch

private data class BottomDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
)

private val bottomDestinations = listOf(
    BottomDestination(Routes.DASHBOARD, R.string.nav_home, Icons.Default.GridView),
    BottomDestination(Routes.THERMAL, R.string.nav_thermal, Icons.Default.Thermostat),
    BottomDestination(Routes.NETWORK, R.string.nav_network, Icons.Default.Wifi),
    BottomDestination(Routes.SCANNER, R.string.nav_security, Icons.Default.Security),
    BottomDestination(Routes.SETTINGS, R.string.nav_settings, Icons.Default.MoreHoriz)
)

/**
 * Maps every destination route onto its owning bottom-navigation tab so that
 * drawer-launched screens still show a highlighted parent tab.
 */
private fun bottomTabFor(route: String): String = when (route) {
    Routes.DASHBOARD -> Routes.DASHBOARD
    Routes.THERMAL, Routes.BATTERY, Routes.ALERT_RULES, Routes.HISTORY -> Routes.THERMAL
    Routes.NETWORK, Routes.FIREWALL, Routes.DNS, Routes.BLOCKLISTS,
    Routes.NETWORK_STATS -> Routes.NETWORK
    Routes.SCANNER, Routes.PRIVACY, Routes.APPS, Routes.ACTIVITY -> Routes.SCANNER
    else -> Routes.SETTINGS
}

class MainActivity : ComponentActivity() {
    private val uiPreferencesViewModel: UiPreferencesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate per core-splashscreen docs.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Hold the splash screen until DataStore has emitted its first value.
        // This avoids the light-then-dark flash on cold start when a saved
        // Dark or AMOLED theme differs from the system default.
        splash.setKeepOnScreenCondition {
            uiPreferencesViewModel.preferences.value == null
        }

        setContent {
            val preferences by uiPreferencesViewModel.preferences.collectAsStateWithLifecycle()
            val prefs = preferences
            if (prefs == null) {
                // Splash is still on screen; nothing to draw yet.
                return@setContent
            }

            val useDark = when (prefs.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // Reapply system-bar styling only when the light/dark choice
            // actually changes, rather than on every recomposition. This keeps
            // status-bar icon contrast tied to the in-app theme, not the
            // system theme.
            DisposableEffect(useDark) {
                enableEdgeToEdge(
                    statusBarStyle = if (useDark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    },
                    navigationBarStyle = if (useDark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    }
                )
                onDispose { }
            }

            ThermalSentinelTheme(preferences = prefs) {
                ThermalSentinelApp(
                    preferences = prefs,
                    onThemeChanged = { uiPreferencesViewModel.setThemeMode(ThemeMode.fromStorage(it)) },
                    onDynamicColorChanged = { uiPreferencesViewModel.setDynamicColor(it) },
                    onAmoledChanged = { uiPreferencesViewModel.setAmoled(it) },
                    onAccentChanged = { uiPreferencesViewModel.setAccent(AccentPreset.fromStorage(it)) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThermalSentinelApp(
    preferences: UiPreferences,
    onThemeChanged: (String) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onAmoledChanged: (Boolean) -> Unit,
    onAccentChanged: (String) -> Unit
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val entry by navController.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route ?: Routes.DASHBOARD
    val destination = destinationForRoute(currentRoute)
    val title = destination?.let { stringResource(it.labelRes) }
        ?: stringResource(R.string.dest_unknown)
    val activeTab = bottomTabFor(currentRoute)

    // "0.1.0-ui" and "0.1.0-ui-debug" both collapse to "0.1.0".
    val displayVersion = BuildConfig.VERSION_NAME.substringBefore('-')
    val footer = stringResource(R.string.build_version_label, displayVersion)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                DrawerContent(
                    currentRoute = currentRoute,
                    footer = footer,
                    onNavigate = { route ->
                        scope.launch { drawerState.close() }
                        navController.navigate(route) {
                            launchSingleTop = true
                            restoreState = true
                            popUpTo(Routes.DASHBOARD) { saveState = true }
                        }
                    }
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = stringResource(R.string.open_navigation)
                            )
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    bottomDestinations.forEach { bottom ->
                        NavigationBarItem(
                            selected = activeTab == bottom.route,
                            onClick = {
                                navController.navigate(bottom.route) {
                                    launchSingleTop = true
                                    restoreState = true
                                    popUpTo(Routes.DASHBOARD) { saveState = true }
                                }
                            },
                            icon = { Icon(bottom.icon, contentDescription = null) },
                            label = { Text(stringResource(bottom.labelRes)) }
                        )
                    }
                }
            }
        ) { paddingValues ->
            AppNavHost(
                navController = navController,
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                preferences = preferences,
                onThemeChanged = onThemeChanged,
                onDynamicColorChanged = onDynamicColorChanged,
                onAmoledChanged = onAmoledChanged,
                onAccentChanged = onAccentChanged
            )
        }
    }
}

@Composable
private fun DrawerContent(
    currentRoute: String,
    footer: String,
    onNavigate: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(PaddingValues(horizontal = 20.dp, vertical = 24.dp))) {
            Text(
                stringResource(R.string.app_short_name),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            val grouped: Map<DestinationGroup, List<Destination>> =
                allDestinations.groupBy { it.group }
            grouped.forEach { (group, destinations) ->
                item {
                    Text(
                        stringResource(group.labelRes),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                destinations.forEach { destination ->
                    item {
                        NavigationDrawerItem(
                            label = { Text(stringResource(destination.labelRes)) },
                            selected = currentRoute == destination.route,
                            onClick = { onNavigate(destination.route) },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        Text(
            footer,
            modifier = Modifier.padding(20.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
