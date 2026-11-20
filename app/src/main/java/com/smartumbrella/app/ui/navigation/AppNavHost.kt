package com.smartumbrella.app.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.smartumbrella.app.ui.screens.alerts.AlertHistoryScreen
import com.smartumbrella.app.ui.screens.home.HomeScreen
import com.smartumbrella.app.ui.screens.sensors.SensorDetailsScreen
import com.smartumbrella.app.ui.screens.settings.SettingsScreen
import com.smartumbrella.app.ui.screens.splash.SplashScreen
import com.smartumbrella.app.viewmodel.DashboardViewModel

@Composable
fun AppNavHost(
    navController: NavHostController = rememberNavController(),
    viewModel: DashboardViewModel = viewModel()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val uiState by viewModel.uiState.collectAsState()
    val unreadAlertsCount = uiState.alerts.count { !it.isAcknowledged }

    // Show Bottom Bar only for main screens, hide for Splash
    val showBottomBar = currentRoute in BottomNavItems.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                SmartUmbrellaBottomBar(
                    navController = navController,
                    unreadAlertsCount = unreadAlertsCount
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Splash.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(Screen.Splash.route) {
                SplashScreen(
                    onSplashFinished = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Home.route) {
                HomeScreen(viewModel = viewModel)
            }

            composable(Screen.Sensors.route) {
                SensorDetailsScreen(viewModel = viewModel)
            }

            composable(Screen.Alerts.route) {
                AlertHistoryScreen(viewModel = viewModel)
            }

            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = viewModel)
            }
        }
    }
}

