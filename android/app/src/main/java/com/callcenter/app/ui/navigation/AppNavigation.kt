package com.callcenter.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.callcenter.app.ui.screens.auth.LoginScreen
import com.callcenter.app.ui.screens.main.MainScreen
import com.callcenter.app.ui.viewmodel.AuthViewModel

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Main : Screen("main")
    object CustomerDetail : Screen("customer/{customerId}") {
        fun createRoute(customerId: Int) = "customer/$customerId"
    }
    object CallHistory : Screen("call_history/{customerId}") {
        fun createRoute(customerId: Int) = "call_history/$customerId"
    }
    object Settings : Screen("settings")
    object AutoDialSettings : Screen("auto_dial_settings")
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val isLoggedIn = authViewModel.isLoggedIn.value

    NavHost(
        navController = navController,
        startDestination = if (isLoggedIn) Screen.Main.route else Screen.Login.route
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Main.route) {
            MainScreen(
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToCustomerDetail = { customerId ->
                    navController.navigate(Screen.CustomerDetail.createRoute(customerId))
                },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Main.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.CustomerDetail.route) { backStackEntry ->
            val customerId = backStackEntry.arguments?.getString("customerId")?.toIntOrNull()
            if (customerId != null) {
                // CustomerDetailScreen(customerId = customerId, ...)
            }
        }

        composable(Screen.Settings.route) {
            // SettingsScreen(...)
        }

        composable(Screen.AutoDialSettings.route) {
            // AutoDialSettingsScreen(...)
        }
    }
}
