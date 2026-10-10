package com.arsys.netatmo.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.arsys.netatmo.ui.screens.auth.AuthScreen
import com.arsys.netatmo.ui.screens.auth.AuthViewModel
import com.arsys.netatmo.ui.screens.automations.AutomationsScreen
import com.arsys.netatmo.ui.screens.automations.AutomationLogScreen
import com.arsys.netatmo.ui.screens.automations.advanced.AdvancedAutomationScreen
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
import com.arsys.netatmo.ui.screens.automations.ScheduleAutomationScreen
import com.arsys.netatmo.ui.screens.settings.VacationModeScreen
import com.arsys.netatmo.ui.screens.settings.FamilyManagementScreen
import com.arsys.netatmo.ui.screens.airquality.AirQualityScreen
import com.arsys.netatmo.ui.screens.boiler.BoilerStatusScreen
import com.arsys.netatmo.ui.screens.boiler.HydraulicDiagnosticScreen
import com.arsys.netatmo.ui.screens.maintenance.MaintenanceHistoryScreen
import com.arsys.netatmo.ui.screens.maintenance.CertificateDetailScreen
import com.arsys.netatmo.ui.screens.devices.DevicePairingScreen
import com.arsys.netatmo.ui.screens.devices.PurgeSystemScreen
import com.arsys.netatmo.ui.screens.devices.ValveCalibrationScreen

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
    object ScheduleAutomation : Screen("schedule_automation/{id}", "Horario", Icons.Default.Schedule)
    object VacationMode : Screen("vacation_mode", "Vacaciones", Icons.Default.BeachAccess)
    object FamilyManagement : Screen("family_management", "Familia", Icons.Default.Group)
    object AdvancedAutomation : Screen("advanced_automation/{automationId}", "Automatización avanzada", Icons.Default.AutoAwesome)
    object AirQuality : Screen("air_quality", "Calidad del Aire", Icons.Default.Air)
    object BoilerStatus : Screen("boiler_status", "Caldera", Icons.Default.LocalFireDepartment)
    object HydraulicDiagnostic : Screen("hydraulic_diagnostic", "Diagnóstico", Icons.Default.Build)
    object MaintenanceHistory : Screen("maintenance_history", "Mantenimiento", Icons.Default.Assignment)
    object CertificateDetail : Screen("certificate/{recordId}", "Certificado", Icons.Default.Description)
    object DevicePairing : Screen("device_pairing", "Emparejar", Icons.Default.AddCircle)
    object PurgeSystem : Screen("purge_system", "Purga", Icons.Default.WaterDrop)
    object ValveCalibration : Screen("valve_calibration", "Calibración", Icons.Default.Tune)
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
                Column {
                    HorizontalDivider(
                        color = Color(0xFF3F4850),
                        thickness = 1.dp
                    )
                    NavigationBar(
                        containerColor = Color(0xFF0F1419),
                        tonalElevation = 0.dp
                    ) {
                        bottomNavItems.forEach { screen ->
                            NavigationBarItem(
                                icon = { Icon(screen.icon, contentDescription = screen.label) },
                                label = { Text(screen.label) },
                                selected = currentRoute == screen.route,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF93CCFF),
                                    selectedTextColor = Color(0xFF93CCFF),
                                    indicatorColor = Color(0xFF93CCFF).copy(alpha = 0.2f),
                                    unselectedIconColor = Color(0xFFBFC7D2),
                                    unselectedTextColor = Color(0xFFBFC7D2)
                                ),
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
            composable("automation_log") {
                AutomationLogScreen(navController = navController)
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
                    },
                    onNavigateToFamily = { navController.navigate("family_management") },
                    onNavigateToAirQuality = { navController.navigate("air_quality") },
                    onNavigateToBoilerStatus = { navController.navigate("boiler_status") },
                    onNavigateToMaintenance = { navController.navigate("maintenance_history") },
                    onNavigateToDevicePairing = { navController.navigate("device_pairing") },
                    onNavigateToPurge = { navController.navigate("purge_system") }
                )
            }
            composable(Screen.NetatmoCredentials.route) {
                NetatmoCredentialsScreen(navController = navController)
            }
            composable("schedule_automation/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })) { backStackEntry ->
                val id = backStackEntry.arguments?.getLong("id") ?: -1L
                ScheduleAutomationScreen(automationId = id, navController = navController)
            }
            composable("vacation_mode") { VacationModeScreen(navController = navController) }
            composable("family_management") { FamilyManagementScreen(navController = navController) }
            composable(Screen.AirQuality.route) {
                AirQualityScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.BoilerStatus.route) {
                BoilerStatusScreen(
                    onBack = { navController.popBackStack() },
                    onDiagnostic = { navController.navigate(Screen.HydraulicDiagnostic.route) }
                )
            }
            composable(Screen.HydraulicDiagnostic.route) {
                HydraulicDiagnosticScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.MaintenanceHistory.route) {
                MaintenanceHistoryScreen(
                    onBack = { navController.popBackStack() },
                    onCertificate = { id -> navController.navigate("certificate/$id") }
                )
            }
            composable(
                Screen.CertificateDetail.route,
                arguments = listOf(navArgument("recordId") { type = NavType.LongType })
            ) { backStackEntry ->
                CertificateDetailScreen(
                    recordId = backStackEntry.arguments?.getLong("recordId") ?: 0L,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.DevicePairing.route) {
                DevicePairingScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.PurgeSystem.route) {
                PurgeSystemScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToCalibration = { navController.navigate(Screen.ValveCalibration.route) }
                )
            }
            composable(Screen.ValveCalibration.route) {
                ValveCalibrationScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = "advanced_automation/{automationId}",
                arguments = listOf(navArgument("automationId") { type = NavType.LongType; defaultValue = -1L })
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getLong("automationId") ?: -1L
                AdvancedAutomationScreen(automationId = id, navController = navController)
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
