package com.callcenter.app.ui.screens.main

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.callcenter.app.data.model.Customer
import com.callcenter.app.data.model.Task
import com.callcenter.app.ui.viewmodel.AuthViewModel
import com.callcenter.app.ui.viewmodel.AutoDialViewModel
import com.callcenter.app.ui.viewmodel.CallSettingsViewModel
import com.callcenter.app.ui.viewmodel.CustomerViewModel
import com.callcenter.app.ui.viewmodel.DashboardViewModel
import com.callcenter.app.ui.viewmodel.MyStatsViewModel
import com.callcenter.app.ui.viewmodel.TaskListViewModel
import com.callcenter.app.util.CallHelper

/**
 * 主页面 - 重新设计
 * 
 * 管理员首页：仪表盘概览 - 显示关键统计数据、快捷操作入口
 * 客服首页：工作台 - 显示今日任务、待拨打客户、快捷拨号
 * 
 * 解决原设计直接显示完整客户列表的问题，提升大数据量下的用户体验
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToCustomerDetail: (Int) -> Unit,
    onNavigateToMyStats: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    onNavigateToAgents: () -> Unit,
    onNavigateToTasks: () -> Unit,
    onNavigateToTaskDetail: (Int) -> Unit,
    onLogout: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel(),
    dashboardViewModel: DashboardViewModel = hiltViewModel(),
    taskListViewModel: TaskListViewModel = hiltViewModel(),
    myStatsViewModel: MyStatsViewModel = hiltViewModel(),
    customerViewModel: CustomerViewModel = hiltViewModel(),
    autoDialViewModel: AutoDialViewModel = hiltViewModel(),
    callSettingsViewModel: CallSettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val currentUser by authViewModel.currentUser.collectAsState()
    val isAdmin = currentUser?.role == "admin"

    // 当前选中的底部导航项
    var selectedTab by remember { mutableStateOf(0) }

    // 根据角色决定显示的导航项
    val navItems = if (isAdmin) {
        listOf(
            BottomNavItem("首页", Icons.Default.Home),
            BottomNavItem("任务", Icons.Default.Assignment),
            BottomNavItem("我的", Icons.Default.Person)
        )
    } else {
        listOf(
            BottomNavItem("工作台", Icons.Default.WorkOutline),
            BottomNavItem("客户", Icons.Default.ContactPhone),
            BottomNavItem("我的", Icons.Default.Person)
        )
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                navItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        when {
            // 管理员视图
            isAdmin && selectedTab == 0 -> {
                AdminHomeTab(
                    padding = padding,
                    onNavigateToDashboard = onNavigateToDashboard,
                    onNavigateToAgents = onNavigateToAgents,
                    onNavigateToTasks = onNavigateToTasks,
                    onNavigateToTaskDetail = onNavigateToTaskDetail,
                    dashboardViewModel = dashboardViewModel,
                    taskListViewModel = taskListViewModel
                )
            }
            isAdmin && selectedTab == 1 -> {
                AdminTasksTab(
                    padding = padding,
                    onNavigateToTasks = onNavigateToTasks,
                    onNavigateToTaskDetail = onNavigateToTaskDetail,
                    taskListViewModel = taskListViewModel
                )
            }
            isAdmin && selectedTab == 2 -> {
                ProfileTab(
                    padding = padding,
                    onNavigateToSettings = onNavigateToSettings,
                    onLogout = onLogout,
                    authViewModel = authViewModel
                )
            }
            // 普通客服视图
            !isAdmin && selectedTab == 0 -> {
                AgentWorkTab(
                    padding = padding,
                    onNavigateToCustomerDetail = onNavigateToCustomerDetail,
                    onNavigateToMyStats = onNavigateToMyStats,
                    onNavigateToTaskDetail = onNavigateToTaskDetail,
                    authViewModel = authViewModel,
                    taskListViewModel = taskListViewModel,
                    myStatsViewModel = myStatsViewModel,
                    customerViewModel = customerViewModel,
                    autoDialViewModel = autoDialViewModel,
                    callSettingsViewModel = callSettingsViewModel
                )
            }
            !isAdmin && selectedTab == 1 -> {
                AgentCustomersTab(
                    padding = padding,
                    onNavigateToCustomerDetail = onNavigateToCustomerDetail,
                    customerViewModel = customerViewModel,
                    autoDialViewModel = autoDialViewModel,
                    callSettingsViewModel = callSettingsViewModel
                )
            }
            !isAdmin && selectedTab == 2 -> {
                ProfileTab(
                    padding = padding,
                    onNavigateToSettings = onNavigateToSettings,
                    onLogout = onLogout,
                    authViewModel = authViewModel
                )
            }
        }
    }
}

/**
 * 底部导航项
 */
data class BottomNavItem(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

// ==================== 管理员首页标签页 ====================

/**
 * 管理员首页 - 仪表盘概览
 * 显示关键统计数据、快捷入口、最近任务
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminHomeTab(
    padding: PaddingValues,
    onNavigateToDashboard: () -> Unit,
    onNavigateToAgents: () -> Unit,
    onNavigateToTasks: () -> Unit,
    onNavigateToTaskDetail: (Int) -> Unit,
    dashboardViewModel: DashboardViewModel,
    taskListViewModel: TaskListViewModel
) {
    val dashboardStats by dashboardViewModel.dashboardStats.collectAsState()
    val isLoading by dashboardViewModel.isLoading.collectAsState()
    val tasks by taskListViewModel.tasks.collectAsState()

    // 只显示最近的3个任务
    val recentTasks = tasks.take(3)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
    ) {
        // 顶部欢迎区域
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "欢迎回来，管理员",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "今日运营概览",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }

        // 统计卡片区域
        if (isLoading && dashboardStats == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            dashboardStats?.let { stats ->
                // 关键指标卡片
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = "总客户",
                        value = stats.totalCustomers.toString(),
                        icon = Icons.Default.People,
                        color = MaterialTheme.colorScheme.primary
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = "今日通话",
                        value = stats.todayCalls.toString(),
                        icon = Icons.Default.Phone,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = "接通率",
                        value = "${(stats.connectionRate * 100).toInt()}%",
                        icon = Icons.Default.TrendingUp,
                        color = Color(0xFF4CAF50)
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = "在线客服",
                        value = stats.activeAgents.toString(),
                        icon = Icons.Default.Group,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 快捷入口
        Text(
            text = "快捷入口",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionButton(
                modifier = Modifier.weight(1f),
                title = "仪表盘",
                icon = Icons.Default.Dashboard,
                onClick = onNavigateToDashboard
            )
            QuickActionButton(
                modifier = Modifier.weight(1f),
                title = "客服管理",
                icon = Icons.Default.Group,
                onClick = onNavigateToAgents
            )
            QuickActionButton(
                modifier = Modifier.weight(1f),
                title = "任务管理",
                icon = Icons.Default.Assignment,
                onClick = onNavigateToTasks
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 最近任务
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "最近任务",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = onNavigateToTasks) {
                Text("查看全部")
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (recentTasks.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无任务",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            recentTasks.forEach { task ->
                TaskItem(
                    task = task,
                    onClick = { onNavigateToTaskDetail(task.id) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * 统计卡片
 */
@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 快捷操作按钮
 */
@Composable
private fun QuickActionButton(
    modifier: Modifier = Modifier,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * 任务项
 */
@Composable
private fun TaskItem(
    task: Task,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 优先级指示器
            val priorityColor = when (task.priority) {
                "urgent" -> Color(0xFFE91E63)
                "high" -> Color(0xFFFF9800)
                else -> Color(0xFF2196F3)
            }
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(priorityColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${task.customerCount}个客户 · 进度 ${task.progress}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 状态标签
            val (statusText, statusColor) = when (task.status) {
                "completed" -> "已完成" to Color(0xFF4CAF50)
                "in_progress" -> "进行中" to Color(0xFF2196F3)
                else -> "待开始" to Color(0xFFFF9800)
            }
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = statusColor.copy(alpha = 0.1f)
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

// ==================== 管理员任务标签页 ====================

/**
 * 管理员任务标签页
 */
@Composable
private fun AdminTasksTab(
    padding: PaddingValues,
    onNavigateToTasks: () -> Unit,
    onNavigateToTaskDetail: (Int) -> Unit,
    taskListViewModel: TaskListViewModel
) {
    val tasks by taskListViewModel.tasks.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        // 顶部标题栏
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "任务管理",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onNavigateToTasks) {
                    Icon(Icons.Default.Add, contentDescription = "创建任务")
                }
            }
        }

        // 任务列表
        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Assignment,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "暂无任务",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onNavigateToTasks) {
                        Text("创建任务")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    TaskItem(
                        task = task,
                        onClick = { onNavigateToTaskDetail(task.id) }
                    )
                }
            }
        }
    }
}

// ==================== 客服工作台标签页 ====================

/**
 * 客服工作台标签页
 * 显示今日概览、待处理任务、快捷拨号
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentWorkTab(
    padding: PaddingValues,
    onNavigateToCustomerDetail: (Int) -> Unit,
    onNavigateToMyStats: () -> Unit,
    onNavigateToTaskDetail: (Int) -> Unit,
    authViewModel: AuthViewModel,
    taskListViewModel: TaskListViewModel,
    myStatsViewModel: MyStatsViewModel,
    customerViewModel: CustomerViewModel,
    autoDialViewModel: AutoDialViewModel,
    callSettingsViewModel: CallSettingsViewModel
) {
    val context = LocalContext.current
    val currentUser by authViewModel.currentUser.collectAsState()
    val myStats by myStatsViewModel.stats.collectAsState()
    val tasks by taskListViewModel.tasks.collectAsState()
    val customers by customerViewModel.customers.collectAsState()

    // 获取待拨打客户（限制数量）
    val pendingCustomers = remember(customers) {
        customers.filter { it.status == "pending" }.take(5)
    }

    // 获取我的任务（限制数量）
    val myTasks = remember(tasks) {
        tasks.filter { it.assignedTo == currentUser?.id }.take(3)
    }

    val callSettings by callSettingsViewModel.callSettings.collectAsState()
    val callHelper = remember { CallHelper(context) }

    var showAutoDialDialog by remember { mutableStateOf(false) }

    // 自动拨号状态
    val autoDialRunning by autoDialViewModel.isRunning.collectAsState()
    val currentDialCustomer by autoDialViewModel.currentCustomer.collectAsState()
    val dialedCount by autoDialViewModel.dialedCount.collectAsState()
    val totalCount by autoDialViewModel.totalCount.collectAsState()

    val callPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "需要电话权限才能拨打电话", Toast.LENGTH_LONG).show()
        }
    }

    fun checkAndRequestCallPermission(): Boolean {
        return when {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED -> true
            else -> {
                callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                false
            }
        }
    }

    fun makeCall(phone: String) {
        if (checkAndRequestCallPermission()) {
            callHelper.makeCall(phone, directCall = true)
        } else {
            callHelper.makeCall(phone, directCall = false)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
    ) {
        // 顶部欢迎区域
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "欢迎，${currentUser?.realName ?: "客服"}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "今日工作概览",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }

        // 自动拨号状态提示条
        if (autoDialRunning) {
            AutoDialStatusBar(
                dialedCount = dialedCount,
                totalCount = totalCount,
                currentCustomer = currentDialCustomer,
                onStop = { autoDialViewModel.stopAutoDial() }
            )
        }

        // 今日统计卡片
        myStats?.let { stats ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "今日通话",
                    value = stats.todayCalls.toString(),
                    icon = Icons.Default.Phone,
                    color = MaterialTheme.colorScheme.primary
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "通话时长",
                    value = "${stats.todayDuration / 60}分",
                    icon = Icons.Default.Timer,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // 快捷操作
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionButton(
                modifier = Modifier.weight(1f),
                title = "我的统计",
                icon = Icons.Default.BarChart,
                onClick = onNavigateToMyStats
            )
            QuickActionButton(
                modifier = Modifier.weight(1f),
                title = "自动拨号",
                icon = Icons.Default.PlayArrow,
                onClick = { showAutoDialDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 我的任务
        if (myTasks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "我的任务",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            myTasks.forEach { task ->
                TaskItem(
                    task = task,
                    onClick = { onNavigateToTaskDetail(task.id) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // 待拨打客户
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "待拨打客户",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = { /* 切换到客户列表页 */ }) {
                Text("查看全部")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (pendingCustomers.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无待拨打客户",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            pendingCustomers.forEach { customer ->
                CustomerQuickItem(
                    customer = customer,
                    onCall = { makeCall(customer.phone) },
                    onClick = { onNavigateToCustomerDetail(customer.id) }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // 自动拨号对话框
    if (showAutoDialDialog) {
        AutoDialDialog(
            isRunning = false,
            defaultInterval = callSettings.autoDialInterval,
            defaultTimeout = callSettings.callTimeout,
            onStart = { interval, timeout ->
                showAutoDialDialog = false
                val allPending = customers.filter { it.status == "pending" }
                if (allPending.isEmpty()) {
                    Toast.makeText(context, "没有待拨打的客户", Toast.LENGTH_SHORT).show()
                } else if (checkAndRequestCallPermission()) {
                    autoDialViewModel.startAutoDial(allPending, interval, timeout)
                }
            },
            onStop = { showAutoDialDialog = false },
            onDismiss = { showAutoDialDialog = false }
        )
    }
}

/**
 * 客服客户列表标签页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentCustomersTab(
    padding: PaddingValues,
    onNavigateToCustomerDetail: (Int) -> Unit,
    customerViewModel: CustomerViewModel,
    autoDialViewModel: AutoDialViewModel,
    callSettingsViewModel: CallSettingsViewModel
) {
    val context = LocalContext.current
    val customers by customerViewModel.customers.collectAsState()
    val isLoading by customerViewModel.isLoading.collectAsState()
    val searchQuery by customerViewModel.searchQuery.collectAsState()
    val statusFilter by customerViewModel.statusFilter.collectAsState()

    val autoDialRunning by autoDialViewModel.isRunning.collectAsState()
    val currentDialCustomer by autoDialViewModel.currentCustomer.collectAsState()
    val dialedCount by autoDialViewModel.dialedCount.collectAsState()
    val totalCount by autoDialViewModel.totalCount.collectAsState()

    val callSettings by callSettingsViewModel.callSettings.collectAsState()
    val callHelper = remember { CallHelper(context) }

    var showSearchBar by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showAutoDialDialog by remember { mutableStateOf(false) }

    val callPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "需要电话权限才能拨打电话", Toast.LENGTH_LONG).show()
        }
    }

    fun checkAndRequestCallPermission(): Boolean {
        return when {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED -> true
            else -> {
                callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                false
            }
        }
    }

    fun makeCall(phone: String) {
        if (checkAndRequestCallPermission()) {
            callHelper.makeCall(phone, directCall = true)
        } else {
            callHelper.makeCall(phone, directCall = false)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        // 顶部工具栏
        TopAppBar(
            title = {
                if (showSearchBar) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { customerViewModel.setSearchQuery(it) },
                        placeholder = { Text("搜索客户...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                showSearchBar = false
                                customerViewModel.setSearchQuery("")
                            }) {
                                Icon(Icons.Default.Close, "关闭搜索")
                            }
                        }
                    )
                } else {
                    Text("客户列表")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            actions = {
                IconButton(onClick = { showSearchBar = !showSearchBar }) {
                    Icon(Icons.Default.Search, "搜索")
                }
                IconButton(onClick = { showFilterMenu = true }) {
                    Icon(Icons.Default.FilterList, "筛选")
                }
                DropdownMenu(
                    expanded = showFilterMenu,
                    onDismissRequest = { showFilterMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("全部") },
                        onClick = {
                            customerViewModel.setStatusFilter(null)
                            showFilterMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("待拨打") },
                        onClick = {
                            customerViewModel.setStatusFilter("pending")
                            showFilterMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("已完成") },
                        onClick = {
                            customerViewModel.setStatusFilter("completed")
                            showFilterMenu = false
                        }
                    )
                }
                IconButton(onClick = { customerViewModel.refresh() }) {
                    Icon(Icons.Default.Refresh, "刷新")
                }
            }
        )

        // 自动拨号进度
        if (autoDialRunning) {
            AutoDialStatusBar(
                dialedCount = dialedCount,
                totalCount = totalCount,
                currentCustomer = currentDialCustomer,
                onStop = { autoDialViewModel.stopAutoDial() }
            )
        }

        // 筛选标签
        if (statusFilter != null) {
            Surface(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (statusFilter) {
                            "pending" -> "待拨打"
                            "completed" -> "已完成"
                            else -> statusFilter ?: ""
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { customerViewModel.setStatusFilter(null) },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(Icons.Default.Close, "清除", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // 客户列表
        when {
            isLoading && customers.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            customers.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.People, null, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("暂无客户数据", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(customers, key = { it.id }) { customer ->
                        CustomerItem(
                            customer = customer,
                            onCall = { makeCall(customer.phone) },
                            onClick = { onNavigateToCustomerDetail(customer.id) }
                        )
                    }
                }
            }
        }

        // 自动拨号按钮
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            ExtendedFloatingActionButton(
                onClick = { showAutoDialDialog = true },
                icon = { Icon(if (autoDialRunning) Icons.Default.Stop else Icons.Default.PlayArrow, null) },
                text = { Text(if (autoDialRunning) "停止" else "自动拨号") },
                containerColor = if (autoDialRunning) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(end = 16.dp)
            )
        }
    }

    // 自动拨号对话框
    if (showAutoDialDialog) {
        AutoDialDialog(
            isRunning = autoDialRunning,
            defaultInterval = callSettings.autoDialInterval,
            defaultTimeout = callSettings.callTimeout,
            onStart = { interval, timeout ->
                showAutoDialDialog = false
                val pendingCustomers = customers.filter { it.status == "pending" }
                if (pendingCustomers.isEmpty()) {
                    Toast.makeText(context, "没有待拨打的客户", Toast.LENGTH_SHORT).show()
                } else if (checkAndRequestCallPermission()) {
                    autoDialViewModel.startAutoDial(pendingCustomers, interval, timeout)
                }
            },
            onStop = {
                showAutoDialDialog = false
                autoDialViewModel.stopAutoDial()
            },
            onDismiss = { showAutoDialDialog = false }
        )
    }
}

/**
 * 自动拨号状态提示条
 * 显示当前拨号进度和取消按钮
 */
@Composable
private fun AutoDialStatusBar(
    dialedCount: Int,
    totalCount: Int,
    currentCustomer: Customer?,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 动画指示器
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "自动拨号进行中",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "$dialedCount / $totalCount",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }

                // 取消按钮
                TextButton(
                    onClick = onStop,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("取消")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 进度条
            LinearProgressIndicator(
                progress = { if (totalCount > 0) dialedCount.toFloat() / totalCount else 0f },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)
            )

            // 当前客户信息
            currentCustomer?.let { customer ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "当前: ${customer.name} - ${customer.phone}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

/**
 * 快捷客户项（用于工作台）
 */
@Composable
private fun CustomerQuickItem(
    customer: Customer,
    onCall: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 状态指示器
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customer.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Phone,
                        null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        " ${customer.phone}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onCall) {
                Icon(
                    Icons.Default.Phone,
                    "拨打电话",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ==================== 个人中心标签页 ====================

/**
 * 个人中心标签页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileTab(
    padding: PaddingValues,
    onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit,
    authViewModel: AuthViewModel
) {
    val currentUser by authViewModel.currentUser.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
    ) {
        // 用户信息卡片
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 头像
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = currentUser?.realName?.take(1) ?: "?",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = currentUser?.realName ?: "未知用户",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "@${currentUser?.username ?: ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = if (currentUser?.role == "admin") "管理员" else "客服",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 菜单项
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            onClick = onNavigateToSettings
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(modifier = Modifier.width(16.dp))
                Text("设置", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 退出登录
        Button(
            onClick = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.Default.Logout, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("退出登录")
        }
    }
}

// ==================== 通用组件 ====================

@Composable
fun CustomerItem(
    customer: Customer,
    onCall: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 状态指示器
            when (customer.status) {
                "pending" -> Surface(
                    modifier = Modifier.size(12.dp),
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.primary
                ) {}
                "completed" -> Icon(
                    Icons.Default.CheckCircle,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                else -> Icon(
                    Icons.Default.Cancel,
                    null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customer.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Phone,
                        null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        " ${customer.phone}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (customer.status == "pending") {
                IconButton(onClick = onCall) {
                    Icon(Icons.Default.Phone, "拨打电话", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun AutoDialDialog(
    isRunning: Boolean,
    defaultInterval: Int,
    defaultTimeout: Int,
    onStart: (interval: Int, timeout: Int) -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit
) {
    var intervalSeconds by remember { mutableStateOf(defaultInterval) }
    var timeoutSeconds by remember { mutableStateOf(defaultTimeout) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isRunning) "停止自动拨号" else "自动拨号设置") },
        text = {
            if (isRunning) {
                Text("自动拨号正在进行中，确定要停止吗？")
            } else {
                Column {
                    Text("拨号间隔（秒）")
                    Slider(
                        value = intervalSeconds.toFloat(),
                        onValueChange = { intervalSeconds = it.toInt() },
                        valueRange = 5f..60f,
                        steps = 11
                    )
                    Text("$intervalSeconds 秒", style = MaterialTheme.typography.bodySmall)

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("无人接听超时（秒）")
                    Slider(
                        value = timeoutSeconds.toFloat(),
                        onValueChange = { timeoutSeconds = it.toInt() },
                        valueRange = 10f..60f,
                        steps = 5
                    )
                    Text("$timeoutSeconds 秒", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isRunning) onStop() else onStart(intervalSeconds, timeoutSeconds)
                }
            ) {
                Text(if (isRunning) "停止" else "开始")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
