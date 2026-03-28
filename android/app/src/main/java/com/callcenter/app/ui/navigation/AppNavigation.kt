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
import com.callcenter.app.ui.screens.admin.AgentDetailScreen
import com.callcenter.app.ui.screens.admin.AgentListScreen
import com.callcenter.app.ui.screens.admin.DashboardScreen
import com.callcenter.app.ui.screens.admin.TaskListScreen
import com.callcenter.app.ui.screens.auth.LoginScreen
import com.callcenter.app.ui.screens.customer.CustomerDetailScreen
import com.callcenter.app.ui.screens.main.MainScreen
import com.callcenter.app.ui.screens.settings.CallSettingsScreen
import com.callcenter.app.ui.screens.settings.SettingsScreen
import com.callcenter.app.ui.screens.stats.MyStatsScreen
import com.callcenter.app.ui.viewmodel.AuthViewModel

/**
 * 导航路由定义
 */
sealed class Screen(val route: String) {
    // 认证
    object Login : Screen("login")
    
    // 主页面（根据角色显示不同内容）
    object Main : Screen("main")
    
    // 客户相关
    object CustomerDetail : Screen("customer/{customerId}") {
        fun createRoute(customerId: Int) = "customer/$customerId"
    }
    object CallHistory : Screen("call_history/{customerId}") {
        fun createRoute(customerId: Int) = "call_history/$customerId"
    }
    
    // 设置
    object Settings : Screen("settings")
    object AutoDialSettings : Screen("auto_dial_settings")
    
    // 客服个人
    object MyStats : Screen("my_stats")
    
    // 管理员功能
    object Dashboard : Screen("admin/dashboard")
    object AgentList : Screen("admin/agents")
    object AgentDetail : Screen("admin/agent/{agentId}") {
        fun createRoute(agentId: Int) = "admin/agent/$agentId"
    }
    object TaskList : Screen("admin/tasks")
    object CreateTask : Screen("admin/tasks/create")
}

/**
 * 主导航组件
 */
@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()

    NavHost(
        navController = navController,
        startDestination = if (isLoggedIn) Screen.Main.route else Screen.Login.route
    ) {
        // ==================== 认证 ====================
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        // ==================== 主页面 ====================
        composable(Screen.Main.route) {
            MainScreen(
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToCustomerDetail = { customerId ->
                    navController.navigate(Screen.CustomerDetail.createRoute(customerId))
                },
                onNavigateToMyStats = {
                    navController.navigate(Screen.MyStats.route)
                },
                onNavigateToDashboard = {
                    navController.navigate(Screen.Dashboard.route)
                },
                onNavigateToAgents = {
                    navController.navigate(Screen.AgentList.route)
                },
                onNavigateToTasks = {
                    navController.navigate(Screen.TaskList.route)
                },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Main.route) { inclusive = true }
                    }
                }
            )
        }

        // ==================== 客户相关 ====================
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
                    // 拨打电话
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

        // ==================== 设置 ====================
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

        // ==================== 客服个人功能 ====================
        composable(Screen.MyStats.route) {
            MyStatsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ==================== 管理员功能 ====================
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAgents = {
                    navController.navigate(Screen.AgentList.route)
                },
                onNavigateToTasks = {
                    navController.navigate(Screen.TaskList.route)
                },
                onNavigateToAgentDetail = { agentId ->
                    navController.navigate(Screen.AgentDetail.createRoute(agentId))
                }
            )
        }

        composable(Screen.AgentList.route) {
            AgentListScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAgentDetail = { agentId ->
                    navController.navigate(Screen.AgentDetail.createRoute(agentId))
                },
                onAddAgent = {
                    navController.navigate(Screen.AgentDetail.createRoute(0))
                }
            )
        }

        composable(
            route = Screen.AgentDetail.route,
            arguments = listOf(navArgument("agentId") { type = NavType.IntType })
        ) { backStackEntry ->
            val agentId = backStackEntry.arguments?.getInt("agentId") ?: 0
            AgentDetailScreen(
                agentId = agentId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.TaskList.route) {
            TaskListScreen(
                onNavigateBack = { navController.popBackStack() },
                onCreateTask = {
                    navController.navigate(Screen.CreateTask.route)
                }
            )
        }

        composable(Screen.CreateTask.route) {
            // TODO: 实现创建任务页面
        }
    }
}
