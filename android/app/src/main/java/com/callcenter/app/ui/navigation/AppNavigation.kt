package com.callcenter.app.ui.navigation

import android.content.Intent
import android.net.Uri
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
import com.callcenter.app.ui.screens.admin.AgentDetailScreen
import com.callcenter.app.util.CallHelper
import com.callcenter.app.ui.screens.admin.AgentListScreen
import com.callcenter.app.ui.screens.admin.CreateTaskScreen
import com.callcenter.app.ui.screens.admin.DashboardScreen
import com.callcenter.app.ui.screens.admin.TaskDetailScreen
import com.callcenter.app.ui.screens.admin.TaskListScreen
import com.callcenter.app.ui.screens.auth.LoginScreen
import com.callcenter.app.ui.screens.customer.CustomerDetailScreen
import com.callcenter.app.ui.screens.main.MainScreen
import com.callcenter.app.ui.screens.settings.CallSettingsScreen
import com.callcenter.app.ui.screens.settings.SettingsScreen
import com.callcenter.app.ui.screens.agent.AgentTaskExecutionScreen
import com.callcenter.app.ui.screens.agent.AgentCreateRetryTaskScreen
import com.callcenter.app.ui.screens.agent.AgentTaskListScreen
import com.callcenter.app.ui.screens.stats.MyStatsScreen
import com.callcenter.app.ui.screens.help.HelpScreen
import com.callcenter.app.ui.screens.dialer.DialerScreen
import com.callcenter.app.ui.screens.contact.ContactListScreen
import com.callcenter.app.ui.screens.contact.AddEditContactScreen
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
    object PermissionTest : Screen("permission_test")

    // 拨号
    object Dialer : Screen("dialer?phoneNumber={phoneNumber}") {
        fun createRoute(phoneNumber: String? = null): String {
            return if (phoneNumber.isNullOrBlank()) {
                "dialer"
            } else {
                "dialer?phoneNumber=${Uri.encode(phoneNumber)}"
            }
        }
    }

    // 通讯录
    object ContactList : Screen("contacts")
    object AddContact : Screen("contacts/add")
    object EditContact : Screen("contacts/edit/{contactId}") {
        fun createRoute(contactId: Int) = "contacts/edit/$contactId"
    }
    
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
}

/**
 * 主导航组件
 */
@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel = hiltViewModel(),
    pendingOpenDialer: Boolean = false,
    pendingDialNumber: String? = null,
    onDialIntentConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()
    val isCheckingAuth by authViewModel.isCheckingAuth.collectAsState()
    val authError by authViewModel.error.collectAsState()

    // 显示错误提示
    LaunchedEffect(authError) {
        authError?.let { error ->
            android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_LONG).show()
            authViewModel.clearError()
        }
    }

    LaunchedEffect(isLoggedIn, pendingOpenDialer, pendingDialNumber) {
        if (isLoggedIn && (pendingOpenDialer || !pendingDialNumber.isNullOrBlank())) {
            navController.navigate(Screen.Dialer.createRoute(pendingDialNumber))
            onDialIntentConsumed()
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
        startDestination = if (isLoggedIn && (pendingOpenDialer || !pendingDialNumber.isNullOrBlank())) {
            Screen.Dialer.createRoute(pendingDialNumber)
        } else if (isLoggedIn) {
            Screen.Main.route
        } else {
            Screen.Login.route
        }
    ) {
        // ==================== 认证 ====================
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    val targetRoute = if (pendingOpenDialer || !pendingDialNumber.isNullOrBlank()) {
                        Screen.Dialer.createRoute(pendingDialNumber)
                    } else {
                        Screen.Main.route
                    }

                    navController.navigate(targetRoute) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }

                    if (pendingOpenDialer || !pendingDialNumber.isNullOrBlank()) {
                        onDialIntentConsumed()
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
                onNavigateToDialer = {
                    navController.navigate(Screen.Dialer.route)
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
            val context = LocalContext.current
            val callHelper = CallHelper(context)
            val customerId = backStackEntry.arguments?.getInt("customerId") ?: 0
            CustomerDetailScreen(
                customerId = customerId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCallHistory = {
                    navController.navigate(Screen.CallHistory.createRoute(customerId))
                },
                onCallCustomer = { phone ->
                    // 拨打电话
                    callHelper.makeCall(phone, directCall = true)
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
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.PermissionTest.route) {
            com.callcenter.app.ui.screens.settings.PermissionTestScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 手动拨号页面
        composable(
            route = Screen.Dialer.route,
            arguments = listOf(
                navArgument("phoneNumber") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            DialerScreen(
                initialPhoneNumber = backStackEntry.arguments?.getString("phoneNumber") ?: "",
                onNavigateBack = { navController.popBackStack() },
                onNavigateToContacts = {
                    navController.navigate(Screen.ContactList.route)
                }
            )
        }

        // 通讯录列表页面
        composable(Screen.ContactList.route) {
            val context = LocalContext.current
            val callHelper = CallHelper(context)
            ContactListScreen(
                onNavigateBack = { navController.popBackStack() },
                onContactClick = { phone ->
                    // 直接拨打选中的号码
                    callHelper.makeCall(phone, directCall = true)
                },
                onAddContact = {
                    navController.navigate(Screen.AddContact.route)
                }
            )
        }

        // 添加联系人页面
        composable(Screen.AddContact.route) {
            AddEditContactScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 编辑联系人页面
        composable(
            route = Screen.EditContact.route,
            arguments = listOf(navArgument("contactId") { type = NavType.IntType })
        ) { backStackEntry ->
            val contactId = backStackEntry.arguments?.getInt("contactId") ?: 0
            // TODO: 加载联系人数据并传入
            AddEditContactScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ==================== 客服个人功能 ====================
        composable(Screen.MyStats.route) {
            MyStatsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Help.route) {
            HelpScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // 客服任务列表
        composable(Screen.AgentTaskList.route) {
            AgentTaskListScreen(
                onNavigateBack = { navController.popBackStack() },
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
                onNavigateBack = { navController.popBackStack() },
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
                onNavigateBack = { navController.popBackStack() },
                onTaskCreated = {
                    navController.popBackStack()
                    navController.navigate(Screen.AgentTaskList.route)
                }
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
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.CreateTask.route) {
            CreateTaskScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
