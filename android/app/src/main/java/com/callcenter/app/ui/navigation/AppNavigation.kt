package com.callcenter.app.ui.navigation

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.callcenter.app.service.AutoDialService
import com.callcenter.app.ui.screens.admin.AdminCustomerListScreen
import com.callcenter.app.ui.screens.admin.AgentDetailScreen
import com.callcenter.app.ui.screens.admin.AgentListScreen
import com.callcenter.app.ui.screens.admin.CreateTaskScreen
import com.callcenter.app.ui.screens.admin.DashboardScreen
import com.callcenter.app.ui.screens.admin.TaskDetailScreen
import com.callcenter.app.ui.screens.admin.TaskListScreen
import com.callcenter.app.ui.screens.auth.LoginScreen
import com.callcenter.app.ui.screens.customer.CustomerDetailScreen
import com.callcenter.app.ui.screens.main.MainScreen
import com.callcenter.app.ui.screens.settings.CallSettingsScreen
import com.callcenter.app.ui.screens.settings.FeatureToggleScreen
import com.callcenter.app.ui.screens.settings.SettingsScreen
import com.callcenter.app.ui.screens.agent.AgentTaskExecutionScreen
import com.callcenter.app.ui.screens.agent.AgentCreateRetryTaskScreen
import com.callcenter.app.ui.screens.agent.AgentTaskListScreen
import com.callcenter.app.ui.screens.stats.MyStatsScreen
import com.callcenter.app.ui.screens.help.HelpScreen
import com.callcenter.app.ui.viewmodel.AuthViewModel
import com.callcenter.app.ui.viewmodel.SessionViewModel
import com.callcenter.app.util.SessionEvent
import com.callcenter.app.util.SessionManager

/**
 * 安全返回扩展函数
 * 只有当导航栈中有多于一个目的地时才执行返回操作
 * 防止快速点击导致的白屏问题
 */
fun NavHostController.safeNavigateBack(): Boolean {
    return if (currentBackStack.value.size > 1) {
        popBackStack()
    } else {
        false
    }
}

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
    object FeatureToggles : Screen("feature_toggles")
    object PermissionTest : Screen("permission_test")

    // 客服个人
    object MyStats : Screen("my_stats")
    object Help : Screen("help")
    object AgentTaskList : Screen("agent/tasks")
    object AgentTaskExecution : Screen("agent/tasks/{taskId}") {
        fun createRoute(taskId: Int) = "agent/tasks/$taskId"
    }
    object AgentCreateRetryTask : Screen("agent/tasks/{taskId}/retry-create/{statusKey}") {
        fun createRoute(taskId: Int, statusKey: String) = "agent/tasks/$taskId/retry-create/$statusKey"
    }
    
    // 管理员功能
    object Dashboard : Screen("admin/dashboard")
    object AgentList : Screen("admin/agents")
    object AgentDetail : Screen("admin/agent/{agentId}") {
        fun createRoute(agentId: Int) = "admin/agent/$agentId"
    }
    object TaskList : Screen("admin/tasks")
    object CreateTask : Screen("admin/tasks/create")
    object TaskDetail : Screen("admin/tasks/{taskId}") {
        fun createRoute(taskId: Int) = "admin/tasks/$taskId"
    }
    object AdminCustomerList : Screen("admin/customers")
}

/**
 * 主导航组件
 */
@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel = hiltViewModel(),
    sessionViewModel: SessionViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()
    val isCheckingAuth by authViewModel.isCheckingAuth.collectAsState()
    val authError by authViewModel.error.collectAsState()

    // 监听会话过期事件
    LaunchedEffect(Unit) {
        sessionViewModel.sessionManager.sessionEvent.collect { event ->
            when (event) {
                is SessionEvent.TokenExpired -> {
                    Toast.makeText(context, "登录已过期，请重新登录", Toast.LENGTH_LONG).show()
                    // 停止自动拨号
                    val intent = Intent(context, AutoDialService::class.java).apply {
                        action = AutoDialService.ACTION_STOP
                    }
                    context.startService(intent)
                    // 跳转到登录页
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
                is SessionEvent.TokenInvalid -> {
                    Toast.makeText(context, "登录状态无效，请重新登录", Toast.LENGTH_LONG).show()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
                is SessionEvent.KickedOut -> {
                    Toast.makeText(context, event.reason, Toast.LENGTH_LONG).show()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }
    }

    // 显示错误提示
    LaunchedEffect(authError) {
        authError?.let { error ->
            android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_LONG).show()
            authViewModel.clearError()
        }
    }

    // 显示启动加载界面，避免冷启动时的闪烁
    if (isCheckingAuth) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
        return
    }

    // 退出登录时停止自动拨号
    fun stopAutoDialAndLogout() {
        // 停止自动拨号服务
        val intent = Intent(context, AutoDialService::class.java).apply {
            action = AutoDialService.ACTION_STOP
        }
        context.startService(intent)
        // 执行退出登录
        authViewModel.logout()
    }

    NavHost(
        navController = navController,
        startDestination = if (isLoggedIn) {
            Screen.Main.route
        } else {
            Screen.Login.route
        }
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
                onNavigateToTaskDetail = { taskId ->
                    navController.navigate(Screen.TaskDetail.createRoute(taskId))
                },
                onNavigateToAgentTaskExecution = { taskId ->
                    navController.navigate(Screen.AgentTaskExecution.createRoute(taskId))
                },
                onNavigateToHelp = {
                    navController.navigate(Screen.Help.route)
                },
                onNavigateToPermissionTest = {
                    navController.navigate(Screen.PermissionTest.route)
                },
                onNavigateToFeatureToggles = {
                    navController.navigate(Screen.FeatureToggles.route)
                },
                onLogout = {
                    stopAutoDialAndLogout()
                    authViewModel.logout {
                        // 在退出完成后导航
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Main.route) { inclusive = true }
                        }
                    }
                },
                onSwitchAccount = {
                    // 切换账号：只清除认证信息，保留历史账号记录
                    stopAutoDialAndLogout()
                    authViewModel.switchAccount {
                        // 导航到登录页
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Main.route) { inclusive = true }
                        }
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
                onNavigateBack = { navController.safeNavigateBack() },
                onNavigateToCallHistory = {
                    navController.navigate(Screen.CallHistory.createRoute(customerId))
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
                onNavigateBack = { navController.safeNavigateBack() },
                onNavigateToCallSettings = {
                    navController.navigate(Screen.AutoDialSettings.route)
                },
                onNavigateToFeatureToggles = {
                    navController.navigate(Screen.FeatureToggles.route)
                },
                onLogout = {
                    stopAutoDialAndLogout()
                    authViewModel.logout {
                        // 在退出完成后导航
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Main.route) { inclusive = true }
                        }
                    }
                },
                onSwitchAccount = {
                    // 切换账号：只清除认证信息，保留历史账号记录
                    stopAutoDialAndLogout()
                    authViewModel.switchAccount {
                        // 导航到登录页
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Main.route) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Screen.AutoDialSettings.route) {
            CallSettingsScreen(
                onNavigateBack = { navController.safeNavigateBack() }
            )
        }

        composable(Screen.PermissionTest.route) {
            com.callcenter.app.ui.screens.settings.PermissionTestScreen(
                onNavigateBack = { navController.safeNavigateBack() },
                onNavigateToFeatureToggles = { navController.navigate(Screen.FeatureToggles.route) }
            )
        }

        composable(Screen.FeatureToggles.route) {
            FeatureToggleScreen(
                onNavigateBack = { navController.safeNavigateBack() }
            )
        }

        // ==================== 客服个人功能 ====================
        composable(Screen.MyStats.route) {
            MyStatsScreen(
                onNavigateBack = { navController.safeNavigateBack() }
            )
        }

        composable(Screen.Help.route) {
            HelpScreen(
                onNavigateBack = { navController.safeNavigateBack() }
            )
        }

        // 客服任务列表
        composable(Screen.AgentTaskList.route) {
            AgentTaskListScreen(
                onNavigateBack = { navController.safeNavigateBack() },
                onTaskClick = { taskId ->
                    navController.navigate(Screen.AgentTaskExecution.createRoute(taskId))
                }
            )
        }

        // 客服任务执行页面
        composable(
            route = Screen.AgentTaskExecution.route,
            arguments = listOf(navArgument("taskId") { type = NavType.IntType })
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getInt("taskId") ?: 0
            AgentTaskExecutionScreen(
                taskId = taskId,
                onNavigateBack = { navController.safeNavigateBack() },
                onNavigateToCreateRetryTask = { statusKey ->
                    navController.navigate(Screen.AgentCreateRetryTask.createRoute(taskId, statusKey))
                }
            )
        }

        composable(
            route = Screen.AgentCreateRetryTask.route,
            arguments = listOf(
                navArgument("taskId") { type = NavType.IntType },
                navArgument("statusKey") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getInt("taskId") ?: 0
            val statusKey = backStackEntry.arguments?.getString("statusKey") ?: "pending"
            AgentCreateRetryTaskScreen(
                taskId = taskId,
                statusKey = statusKey,
                onNavigateBack = { navController.safeNavigateBack() },
                onTaskCreated = {
                    navController.safeNavigateBack()
                    navController.navigate(Screen.AgentTaskList.route)
                }
            )
        }

        // ==================== 管理员功能 ====================
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateBack = { navController.safeNavigateBack() },
                onNavigateToAgents = {
                    navController.navigate(Screen.AgentList.route)
                },
                onNavigateToTasks = {
                    navController.navigate(Screen.TaskList.route)
                },
                onNavigateToAgentDetail = { agentId ->
                    navController.navigate(Screen.AgentDetail.createRoute(agentId))
                },
                onNavigateToCustomers = {
                    navController.navigate(Screen.AdminCustomerList.route)
                }
            )
        }

        composable(Screen.AgentList.route) {
            AgentListScreen(
                onNavigateBack = { navController.safeNavigateBack() },
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
                onNavigateBack = { navController.safeNavigateBack() }
            )
        }

        composable(Screen.TaskList.route) {
            TaskListScreen(
                onNavigateBack = { navController.safeNavigateBack() },
                onCreateTask = {
                    navController.navigate(Screen.CreateTask.route)
                },
                onTaskClick = { taskId ->
                    navController.navigate(Screen.TaskDetail.createRoute(taskId))
                }
            )
        }

        composable(
            route = Screen.TaskDetail.route,
            arguments = listOf(navArgument("taskId") { type = NavType.IntType })
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getInt("taskId") ?: 0
            TaskDetailScreen(
                taskId = taskId,
                onNavigateBack = { navController.safeNavigateBack() }
            )
        }

        composable(Screen.CreateTask.route) {
            CreateTaskScreen(
                onNavigateBack = { navController.safeNavigateBack() }
            )
        }

        composable(Screen.AdminCustomerList.route) {
            AdminCustomerListScreen(
                onNavigateBack = { navController.safeNavigateBack() },
                onCreateTask = { customerIds ->
                    // 导航到创建任务页面，带上客户ID
                    navController.navigate(Screen.CreateTask.route)
                }
            )
        }
    }
}
