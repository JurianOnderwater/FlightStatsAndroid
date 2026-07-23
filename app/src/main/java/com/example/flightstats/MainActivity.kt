package com.example.flightstats

import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.flightstats.data.FlightRepository
import com.example.flightstats.ui.AddFlightSheet
import com.example.flightstats.ui.FlightsScreen
import com.example.flightstats.ui.MapScreen
import com.example.flightstats.ui.SettingsScreen
import com.example.flightstats.ui.StatsScreen
import com.example.flightstats.ui.theme.FlightStatsTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

import androidx.compose.ui.res.stringResource

sealed class Screen(val route: String, val titleResId: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Map : Screen("map", R.string.title_map, Icons.Default.Place)
    object Flights : Screen("flights", R.string.title_flights, Icons.Default.List)
    object Stats : Screen("stats", R.string.title_stats, Icons.Default.Star)
    object Settings : Screen("settings", R.string.title_settings, Icons.Default.Star) // Hidden from bottom bar
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val flightsViewModel: FlightsViewModel by viewModels()
    private val statsViewModel: StatsViewModel by viewModels()
    private val mapViewModel: MapViewModel by viewModels()

    @Inject
    lateinit var repository: FlightRepository

    private lateinit var sharedPrefs: SharedPreferences

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        window.statusBarColor = android.graphics.Color.TRANSPARENT

        super.onCreate(savedInstanceState)

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)

        // OsmDroid Configuration setup
        val osmConfig = org.osmdroid.config.Configuration.getInstance()
        osmConfig.load(this, sharedPrefs)
        osmConfig.userAgentValue = "FlightStatsApp/1.0 (Android Mobile App; com.flightstats.app)"

        // Clear stale osmdroid tile cache to purge any previously cached 403 error tile images
        try {
            val osmdroidCacheDir = java.io.File(cacheDir, "osmdroid")
            if (osmdroidCacheDir.exists()) {
                osmdroidCacheDir.deleteRecursively()
            }
        } catch (_: Exception) {}

        setContent {
            var themeMode by remember { mutableStateOf(sharedPrefs.getInt("theme_mode", 2)) }
            val darkTheme = when (themeMode) {
                0 -> false
                1 -> true
                else -> isSystemInDarkTheme()
            }

            FlightStatsTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    var showAddFlightSheet by remember { mutableStateOf(false) }

                    // Simple wrapper to update local theme mode state
                    val onThemeChanged: (Int) -> Unit = { mode ->
                        themeMode = mode
                    }

                    Scaffold(
                        bottomBar = {
                            val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
                            if (currentRoute != Screen.Settings.route) {
                                NavigationBar {
                                    listOf(Screen.Map, Screen.Flights, Screen.Stats).forEach { screen ->
                                        val title = stringResource(screen.titleResId)
                                        NavigationBarItem(
                                            icon = { Icon(screen.icon, contentDescription = title) },
                                            label = { Text(title) },
                                            selected = currentRoute == screen.route,
                                            onClick = {
                                                navController.navigate(screen.route) {
                                                    popUpTo(navController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = Screen.Map.route,
                            modifier = Modifier.padding(innerPadding)
                        ) {
                            composable(Screen.Map.route) {
                                MapScreen(
                                    viewModel = mapViewModel,
                                    onAddFlightClick = { showAddFlightSheet = true },
                                    onScanFlightClick = { showAddFlightSheet = true }, // Add flight sheet handles launcher
                                    onSettingsClick = { navController.navigate(Screen.Settings.route) }
                                )
                            }
                            composable(Screen.Flights.route) {
                                FlightsScreen(
                                    viewModel = flightsViewModel,
                                    onAddFlightClick = { showAddFlightSheet = true }
                                )
                            }
                            composable(Screen.Stats.route) {
                                StatsScreen(
                                    viewModel = statsViewModel,
                                    isDarkTheme = darkTheme
                                )
                            }
                            composable(Screen.Settings.route) {
                                SettingsScreen(
                                    repository = repository,
                                    onBackClick = { navController.popBackStack() },
                                    onThemeChanged = onThemeChanged
                                )
                            }
                        }

                        // Shared Bottom Sheet for Adding Flights
                        if (showAddFlightSheet) {
                            ModalBottomSheet(
                                onDismissRequest = { showAddFlightSheet = false },
                                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                            ) {
                                AddFlightSheet(
                                    viewModel = flightsViewModel,
                                    onDismiss = { showAddFlightSheet = false }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
