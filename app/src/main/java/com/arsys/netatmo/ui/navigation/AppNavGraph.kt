package com.arsys.netatmo.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.arsys.netatmo.ui.screens.auth.AuthScreen
import com.arsys.netatmo.ui.screens.auth.AuthViewModel
import com.arsys.netatmo.ui.screens.automations.AutomationsScreen
import com.arsys.netatmo.ui.screens.automations.GeofenceDetailScreen
import com.arsys.netatmo.ui.screens.automations.CalendarAutomationScreen
import com.arsys.netatmo.ui.screens.home.HomeScreen
import com.arsys.netatmo.ui.screens.scenarios.ScenariosScreen
import com.arsys.netatmo.ui.screens.scenarios.ScenarioDetailScreen
import com.arsys.netatmo.ui.screens.schedule.ScheduleEditorScreen
import com.arsys.netatmo.ui.screens.settings.NetatmoCredentialsScreen
import com.arsys.netatmo.ui.screens.settings.SettingsScreen
import com.arsys.netatmo.ui.screens.onboarding.OnboardingScreen
import com.arsys.netatmo.ui.screens.onboarding.OnboardingViewModel
import com.arsys.netatmo.ui.screens.statistics.StatisticsScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "Inicio", Icons.Default.Home)
    object Automations : Screen("automations", "Automatizar", Icons.Default.AutoMode)
    object Statistics : Screen("statistics", "Estadísticas", Icons.Default.BarChart)
    object Scenarios : Screen("scenarios", "Escenarios", Icons.Default.AutoAwesome)
    object Settings : Screen("settings", "Ajustes", Icons.Default.Settings)
    object Auth : Screen("auth", "Login", Icons.Default.Login)
    object GeofenceDetail : Screen("geofence/{id}", "Geovalla", Icons.Default.LocationOn)
    object CalendarAutomation : Screen("calendar_automation/{id}", "Calendario", Icons.Default.CalendarToday)
    object ScenarioDetail : Screen("scenario/{id}", "Escenario", Icons.Default.AutoAwesome)
    object ScheduleDetail : Screen("schedule/{id}", "Programación", Icons.Default.Schedule)
    object NetatmoCredentials : Screen("netatmo_credentials", "Credenciales", Icons.Default.Key)
    object Onboarding : Screen("onboarding", "Bienvenido", Icons.Default.StarOutline)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Automations,
    Screen.Statistics,
    Screen.Scenarios,
    Screen.Settings
)

@Composable
fun AppNavGraph(pendingRoute: String? = null, onRoutePending: () -> Unit = {}) {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()
    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
    val isOnboardingCompleted by onboardingViewModel.onboardingCompleted.collectAsState()

    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    // Single effect: onboarding takes priority over login state.
    // isOnboardingCompleted is null while DataStore is loading — skip until real value arrives.
    LaunchedEffect(isLoggedIn, isOnboardingCompleted) {
        val completed = isOnboardingCompleted ?: return@LaunchedEffect
        val onAuthScreen = currentRoute == Screen.Auth.route || currentRoute == null
        val onOnboardingScreen = currentRoute == Screen.Onboarding.route
        when {
            !completed -> {
                if (!onOnboardingScreen) {
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            isLoggedIn && (onAuthScreen || onOnboardingScreen) -> {
                navController.navigate(Screen.Home.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
            !isLoggedIn && !onAuthScreen && !onOnboardingScreen -> {
                navController.navigate(Screen.Auth.route) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    // Navigate to pending route (e.g. from a home screen shortcut)
    LaunchedEffect(pendingRoute, isLoggedIn) {
        if (pendingRoute != null && isLoggedIn) {
            navController.navigate(pendingRoute) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            onRoutePending()
        }
    }

    val showBottomBar = bottomNavItems.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
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
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Onboarding.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    onComplete = {
                        navController.navigate(Screen.Auth.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    },
                    onSkip = {
                        navController.navigate(Screen.Auth.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Auth.route) {
                AuthScreen(
                    onLoginSuccess = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Auth.route) { inclusive = true }
                        }
                    },
                    onConfigureCredentials = {
                        navController.navigate(Screen.NetatmoCredentials.route)
                    }
                )
            }
            composable(Screen.Home.route) {
                HomeScreen(navController = navController)
            }
            composable(Screen.Automations.route) {
                AutomationsScreen(navController = navController)
            }
            composable(
                route = Screen.GeofenceDetail.route,
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getLong("id") ?: -1L
                GeofenceDetailScreen(automationId = id, navController = navController)
            }
            composable(
                route = Screen.CalendarAutomation.route,
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getLong("id") ?: -1L
                CalendarAutomationScreen(automationId = id, navController = navController)
            }
            composable(Screen.Statistics.route) {
                StatisticsScreen()
            }
            composable(Screen.Scenarios.route) {
                ScenariosScreen(navController = navController)
            }
            composable(
                route = Screen.ScenarioDetail.route,
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getLong("id") ?: -1L
                ScenarioDetailScreen(scenarioId = id, navController = navController)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onLogout = {
                        navController.navigate(Screen.Auth.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onNavigateToCredentials = {
                        navController.navigate(Screen.NetatmoCredentials.route)
                    }
                )
            }
            composable(Screen.NetatmoCredentials.route) {
                NetatmoCredentialsScreen(navController = navController)
            }
            composable(
                route = Screen.ScheduleDetail.route,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType; defaultValue = "new" }
                )
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id") ?: "new"
                ScheduleEditorScreen(
                    scheduleId = id,
                    navController = navController
                )
            }
        }
    }
}
