package com.callcenter.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.callcenter.app.ui.screens.auth.LoginScreen
import com.callcenter.app.ui.screens.customer.CustomerDetailScreen
import com.callcenter.app.ui.screens.main.MainScreen
import com.callcenter.app.ui.screens.settings.CallSettingsScreen
import com.callcenter.app.ui.screens.settings.SettingsScreen
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
    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()

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
                    authViewModel.logout()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Main.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.CustomerDetail.route,
            arguments = listOf(navArgument("customerId") { type = NavType.IntType })
        ) { backStackEntry ->
            val customerId = backStackEntry.arguments?.getInt("customerId") ?: 0
            CustomerDetailScreen(
                customerId = customerId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCallHistory = {
                    navController.navigate(Screen.CallHistory.createRoute(customerId))
                },
                onCallCustomer = { phone ->
                    // 拨打电话的逻辑将在 MainScreen 中处理
                }
            )
        }

        composable(
            route = Screen.CallHistory.route,
            arguments = listOf(navArgument("customerId") { type = NavType.IntType })
        ) { backStackEntry ->
            val customerId = backStackEntry.arguments?.getInt("customerId") ?: 0
            // TODO: 实现通话历史页面
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCallSettings = {
                    navController.navigate(Screen.AutoDialSettings.route)
                },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Main.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.AutoDialSettings.route) {
            CallSettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
