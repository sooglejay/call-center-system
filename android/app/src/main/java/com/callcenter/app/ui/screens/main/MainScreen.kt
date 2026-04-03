package com.callcenter.app.ui.screens.main

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.callcenter.app.ui.viewmodel.AutoDialConfig
import com.callcenter.app.ui.viewmodel.AutoDialScopeType
import com.callcenter.app.ui.viewmodel.CallSettingsViewModel
import com.callcenter.app.ui.viewmodel.CustomerViewModel
import com.callcenter.app.ui.viewmodel.DashboardViewModel
import com.callcenter.app.ui.viewmodel.MyStatsViewModel
import com.callcenter.app.ui.viewmodel.TaskListViewModel
import com.callcenter.app.util.CallHelper
import com.callcenter.app.util.VersionInfoUtil

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
    onNavigateToAgentTaskExecution: (Int) -> Unit,
    onNavigateToHelp: () -> Unit,
    onLogout: () -> Unit,
    onSwitchAccount: () -> Unit,
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

    // 当前选中的底部导航项 - 使用 rememberSaveable 保存状态
    var selectedTab by rememberSaveable { mutableStateOf(0) }

    // 获取自动拨号状态，用于浮动按钮显示
    val autoDialRunning by autoDialViewModel.isRunning.collectAsState()
    val customers by customerViewModel.customers.collectAsState()
    val tasks by taskListViewModel.tasks.collectAsState()
    val callSettings by callSettingsViewModel.callSettings.collectAsState()

    // 合并所有客户：任务客户 + 分配客户（用于客户Tab的自动拨号）
    val allCustomersForDial = remember(tasks, customers) {
        val taskCustomers = mutableListOf<Customer>()

        // 从所有任务中提取客户
        tasks.forEach { task ->
            task.customers?.forEach { taskCustomer ->
                taskCustomers.add(
                    Customer(
                        id = taskCustomer.id,
                        name = taskCustomer.name,
                        phone = taskCustomer.phone,
                        email = taskCustomer.email,
                        company = taskCustomer.company,
                        status = taskCustomer.callStatus,
                        sourceType = "task",
                        taskId = task.id,
                        taskTitle = task.title
                    )
                )
            }
        }

        // 合并分配的客户，去重（以ID为准）
        val mergedMap = mutableMapOf<Int, Customer>()

        // 先添加任务客户
        taskCustomers.forEach { customer ->
            mergedMap[customer.id] = customer
        }

        // 再添加分配的客户（如果ID重复，优先保留任务来源的信息）
        customers.forEach { customer ->
            if (!mergedMap.containsKey(customer.id)) {
                mergedMap[customer.id] = customer
            }
        }

        mergedMap.values.toList()
    }

    // 自动拨号配置对话框状态
    var showAutoDialConfigDialog by remember { mutableStateOf(false) }

    // 自动拨号权限请求
    val callPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "需要电话权限才能自动拨打电话", Toast.LENGTH_LONG).show()
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

    // 在 MainScreen 级别加载任务数据，确保工作台能显示任务
    // 使用 isLoading 标记避免重复加载
    var isInitialLoadDone by rememberSaveable { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        // 首次进入时延迟加载，等待用户信息获取
        if (!isInitialLoadDone) {
            kotlinx.coroutines.delay(500) // 等待500ms让currentUser加载
            isInitialLoadDone = true
        }
    }
    
    LaunchedEffect(currentUser?.id, isInitialLoadDone) {
        // 当用户变化或初始加载完成时加载任务
        if (currentUser != null && isInitialLoadDone) {
            if (currentUser?.role == "agent") {
                taskListViewModel.loadMyTasks()
            } else if (currentUser?.role == "admin") {
                taskListViewModel.refresh()
            }
        }
    }
    
    // 当切换到工作台Tab时，确保任务数据已加载
    LaunchedEffect(selectedTab) {
        if (!isAdmin && selectedTab == 0 && currentUser?.role == "agent") {
            taskListViewModel.loadMyTasks()
        }
    }

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
            BottomNavItem("任务", Icons.Default.Assignment),
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
        },
        floatingActionButton = {
            // 只在客服的客户列表页显示自动拨号按钮
            if (!isAdmin && selectedTab == 2) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (autoDialRunning) {
                            autoDialViewModel.stopAutoDial()
                        } else if (checkAndRequestCallPermission()) {
                            // 显示配置对话框
                            if (allCustomersForDial.isNotEmpty()) {
                                showAutoDialConfigDialog = true
                            }
                        }
                    },
                    icon = {
                        Icon(
                            if (autoDialRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (autoDialRunning) "停止拨号" else "自动拨号"
                        )
                    },
                    text = { Text(if (autoDialRunning) "停止拨号" else "自动拨号") },
                    containerColor = if (autoDialRunning)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primaryContainer
                )
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
                    onLogout = onLogout,
                    onSwitchAccount = onSwitchAccount,
                    onNavigateToHelp = onNavigateToHelp,
                    authViewModel = authViewModel
                )
            }
            // 普通客服视图
            !isAdmin && selectedTab == 0 -> {
                AgentWorkTab(
                    padding = padding,
                    onNavigateToMyStats = onNavigateToMyStats,
                    onNavigateToAgentTaskExecution = onNavigateToAgentTaskExecution,
                    authViewModel = authViewModel,
                    taskListViewModel = taskListViewModel,
                    myStatsViewModel = myStatsViewModel,
                    autoDialViewModel = autoDialViewModel,
                    callSettingsViewModel = callSettingsViewModel
                )
            }
            !isAdmin && selectedTab == 1 -> {
                AgentTasksTab(
                    padding = padding,
                    onNavigateToAgentTaskExecution = onNavigateToAgentTaskExecution,
                    taskListViewModel = taskListViewModel,
                    authViewModel = authViewModel
                )
            }
            !isAdmin && selectedTab == 2 -> {
                AgentCustomersTab(
                    onNavigateToCustomerDetail = onNavigateToCustomerDetail,
                    customerViewModel = customerViewModel,
                    taskListViewModel = taskListViewModel,
                    autoDialViewModel = autoDialViewModel,
                    callSettingsViewModel = callSettingsViewModel
                )
            }
            !isAdmin && selectedTab == 3 -> {
                ProfileTab(
                    padding = padding,
                    onLogout = onLogout,
                    onSwitchAccount = onSwitchAccount,
                    onNavigateToHelp = onNavigateToHelp,
                    authViewModel = authViewModel
                )
            }
        }

        // 自动拨号配置对话框
        if (showAutoDialConfigDialog) {
            // 本地配置状态
            var localInterval by remember { mutableStateOf(callSettings.autoDialInterval) }
            var localTimeout by remember { mutableStateOf(callSettings.callTimeout) }
            var localDialsPerCustomer by remember { mutableStateOf(1) }

            AlertDialog(
                onDismissRequest = { showAutoDialConfigDialog = false },
                title = { Text("自动拨号配置") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // 拨号间隔配置
                        Column {
                            Text(
                                text = "拨号间隔: ${localInterval}秒",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = localInterval.toFloat(),
                                onValueChange = { localInterval = it.toInt() },
                                valueRange = 3f..30f,
                                steps = 26
                            )
                            Text(
                                text = "每次拨打完成后等待的时间",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // 超时时间配置
                        Column {
                            Text(
                                text = "通话超时: ${localTimeout}秒",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = localTimeout.toFloat(),
                                onValueChange = { localTimeout = it.toInt() },
                                valueRange = 10f..120f,
                                steps = 21
                            )
                            Text(
                                text = "等待对方接听的最大时间",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        HorizontalDivider()

                        // 每客户拨打次数配置
                        Column {
                            Text(
                                text = "每个客户连续拨打次数: $localDialsPerCustomer",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = localDialsPerCustomer.toFloat(),
                                onValueChange = { localDialsPerCustomer = it.toInt() },
                                valueRange = 1f..5f,
                                steps = 3
                            )
                            Text(
                                text = when (localDialsPerCustomer) {
                                    1 -> "每个客户拨打1次后流转到下一个"
                                    else -> "每个客户连续拨打${localDialsPerCustomer}次后流转到下一个"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showAutoDialConfigDialog = false
                            // 开始自动拨号，使用本地配置
                            val config = AutoDialConfig(
                                scopeType = AutoDialScopeType.ALL_PENDING,
                                intervalSeconds = localInterval,
                                timeoutSeconds = localTimeout,
                                dialsPerCustomer = localDialsPerCustomer
                            )
                            autoDialViewModel.startAutoDial(allCustomersForDial, config)
                        }
                    ) {
                        Text("开始拨号")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showAutoDialConfigDialog = false }
                    ) {
                        Text("取消")
                    }
                }
            )
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
    containerColor: Color? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = containerColor ?: MaterialTheme.colorScheme.surface
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

// ==================== 客服任务标签页 ====================

/**
 * 客服任务标签页
 * 显示分配给当前客服的任务列表
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentTasksTab(
    padding: PaddingValues,
    onNavigateToAgentTaskExecution: (Int) -> Unit,
    taskListViewModel: TaskListViewModel,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val tasks by taskListViewModel.tasks.collectAsState()
    val isLoading by taskListViewModel.isLoading.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()

    LaunchedEffect(Unit) {
        taskListViewModel.loadMyTasks()
    }

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
                    text = "我的任务",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 任务列表
        if (isLoading && tasks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (tasks.isEmpty()) {
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
                    Text(
                        "请联系管理员分配任务",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    AgentTaskItem(
                        task = task,
                        onClick = { onNavigateToAgentTaskExecution(task.id) }
                    )
                }
            }
        }
    }
}

/**
 * 客服任务项
 */
@Composable
private fun AgentTaskItem(
    task: Task,
    onClick: () -> Unit
) {
    val progress = if (task.customerCount > 0) {
        (task.completedCount * 100 / task.customerCount)
    } else 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                        text = "${task.customerCount}个客户 · 已拨打${task.calledCount}个",
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

            // 进度条
            if (task.customerCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = when {
                        progress >= 100 -> Color(0xFF4CAF50)
                        progress > 50 -> Color(0xFF2196F3)
                        else -> Color(0xFFFF9800)
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "进度: $progress% (${task.completedCount}/${task.customerCount})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ==================== 客服工作台标签页 ====================

/**
 * 客服工作台标签页
 * 显示今日概览、任务列表、自动拨号入口
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentWorkTab(
    padding: PaddingValues,
    onNavigateToMyStats: () -> Unit,
    onNavigateToAgentTaskExecution: (Int) -> Unit,
    authViewModel: AuthViewModel,
    taskListViewModel: TaskListViewModel,
    myStatsViewModel: MyStatsViewModel,
    autoDialViewModel: AutoDialViewModel,
    callSettingsViewModel: CallSettingsViewModel
) {
    val context = LocalContext.current
    val currentUser by authViewModel.currentUser.collectAsState()
    val myStats by myStatsViewModel.stats.collectAsState()
    val tasks by taskListViewModel.tasks.collectAsState()

    // 获取我的任务
    val myTasks = remember(tasks) {
        tasks.filter { it.assignedTo == currentUser?.id }
    }

    // 计算总体进度
    val totalTaskCustomers = myTasks.sumOf { it.customerCount }
    val totalCompletedCustomers = myTasks.sumOf { it.completedCount }
    val overallProgress = if (totalTaskCustomers > 0) {
        (totalCompletedCustomers * 100 / totalTaskCustomers)
    } else 0

    val callSettings by callSettingsViewModel.callSettings.collectAsState()

    // 自动拨号状态
    val autoDialRunning by autoDialViewModel.isRunning.collectAsState()
    val currentDialCustomer by autoDialViewModel.currentCustomer.collectAsState()
    val dialedCount by autoDialViewModel.dialedCount.collectAsState()
    val totalCount by autoDialViewModel.totalCount.collectAsState()
    val currentConfig by autoDialViewModel.currentConfig.collectAsState()

    // 加载任务数据
    LaunchedEffect(Unit) {
        taskListViewModel.loadMyTasks()
    }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
    ) {
        // 顶部欢迎区域（紧贴顶部，无间距）
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

        // 欢迎区域与第一个组件之间的间距
        Spacer(modifier = Modifier.height(12.dp))

        // 自动拨号状态提示条
        if (autoDialRunning) {
            val scopeDesc = autoDialViewModel.getScopeDescription(currentConfig)
            AutoDialStatusBar(
                dialedCount = dialedCount,
                totalCount = totalCount,
                scopeDescription = scopeDesc,
                currentCustomer = currentDialCustomer,
                onStop = { autoDialViewModel.stopAutoDial() }
            )
        }

        // 客户跟进进度（从myStats获取数据）
        myStats?.let { stats ->
            val totalCustomers = stats.pendingCustomers + stats.completedCustomers
            if (totalCustomers > 0) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "客户跟进进度",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        val completedRate = if (totalCustomers > 0) {
                            stats.completedCustomers.toFloat() / totalCustomers
                        } else 0f

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "已完成 ${stats.completedCustomers} / $totalCustomers",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${(completedRate * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { completedRate },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 待跟进
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFF9800).copy(alpha = 0.1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = stats.pendingCustomers.toString(),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFF9800)
                                    )
                                    Text(
                                        text = "待跟进",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // 已完成
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF4CAF50).copy(alpha = 0.1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = stats.completedCustomers.toString(),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF4CAF50)
                                    )
                                    Text(
                                        text = "已完成",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // 业绩指标（包含任务总体进度）
        myStats?.let { stats ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "业绩指标",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 任务总体进度
                    if (myTasks.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Assignment,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "任务进度",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                LinearProgressIndicator(
                                    progress = { overallProgress / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                        .height(4.dp),
                                    color = when {
                                        overallProgress >= 100 -> Color(0xFF4CAF50)
                                        overallProgress > 50 -> Color(0xFF2196F3)
                                        else -> Color(0xFFFF9800)
                                    },
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = "$overallProgress%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 任务统计
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "${myTasks.size} 个任务",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${totalTaskCustomers} 个客户",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // 今日通话
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = "今日通话",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = "${stats.todayCalls} 次",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 今日通话时长
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = "今日通话时长",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )

                        val todayDurationMin = stats.todayDuration / 60
                        Text(
                            text = "${todayDurationMin} 分钟",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 接通率
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = "接通率",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )

                        val totalCalls = stats.todayCalls.coerceAtLeast(stats.totalCalls)
                        val successfulCalls = stats.todaySuccessful.coerceAtLeast(stats.successfulCalls)
                        val rateText = if (totalCalls > 0) {
                            "${(successfulCalls.toFloat() / totalCalls * 100).toInt()}%"
                        } else "0%"

                        Text(
                            text = rateText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 平均通话时长
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = "平均通话时长",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )

                        val avgDuration = if (stats.totalCalls > 0) {
                            stats.totalDuration / stats.totalCalls
                        } else 0
                        val durationText = if (avgDuration < 60) "${avgDuration}秒" else "${avgDuration / 60}分${avgDuration % 60}秒"

                        Text(
                            text = durationText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // 我的任务列表
        if (myTasks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "我的任务 (${myTasks.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            myTasks.forEach { task ->
                AgentTaskItemWithProgress(
                    task = task,
                    isAutoDialing = autoDialRunning && currentConfig?.taskId == task.id,
                    onClick = { onNavigateToAgentTaskExecution(task.id) },
                    onStartDial = {
                        if (checkAndRequestCallPermission()) {
                            onNavigateToAgentTaskExecution(task.id)
                        }
                    }
                )
            }
        } else {
            // 空状态
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Assignment,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "暂无任务",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "请联系管理员分配任务",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * 客服任务项（带进度和自动拨号状态）
 */
@Composable
private fun AgentTaskItemWithProgress(
    task: Task,
    isAutoDialing: Boolean,
    onClick: () -> Unit,
    onStartDial: () -> Unit
) {
    val progress = if (task.customerCount > 0) {
        (task.completedCount * 100 / task.customerCount)
    } else 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                        text = "${task.customerCount}个客户 · 已拨打${task.calledCount}个",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 自动拨号指示器
                if (isAutoDialing) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "拨号中",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
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

            // 进度条
            if (task.customerCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = when {
                        progress >= 100 -> Color(0xFF4CAF50)
                        progress > 50 -> Color(0xFF2196F3)
                        else -> Color(0xFFFF9800)
                    },
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "进度: $progress% (${task.completedCount}/${task.customerCount})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 开始拨号按钮
                    if (!isAutoDialing && task.status != "completed") {
                        TextButton(
                            onClick = onStartDial,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "开始拨号",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 客服客户列表标签页
 * 显示所有任务中的客户 + 分配给自己的客户
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentCustomersTab(
    onNavigateToCustomerDetail: (Int) -> Unit,
    customerViewModel: CustomerViewModel,
    taskListViewModel: TaskListViewModel,
    autoDialViewModel: AutoDialViewModel,
    callSettingsViewModel: CallSettingsViewModel
) {
    val context = LocalContext.current

    // 从CustomerViewModel获取分配给自己的客户
    val assignedCustomers by customerViewModel.customers.collectAsState()
    val isLoadingAssigned by customerViewModel.isLoading.collectAsState()
    val searchQuery by customerViewModel.searchQuery.collectAsState()
    val statusFilter by customerViewModel.statusFilter.collectAsState()

    // 从TaskListViewModel获取任务列表
    val tasks by taskListViewModel.tasks.collectAsState()
    val isLoadingTasks by taskListViewModel.isLoading.collectAsState()

    // 自动拨号状态
    val autoDialRunning by autoDialViewModel.isRunning.collectAsState()
    val currentDialCustomer by autoDialViewModel.currentCustomer.collectAsState()
    val dialedCount by autoDialViewModel.dialedCount.collectAsState()
    val totalCount by autoDialViewModel.totalCount.collectAsState()
    val currentConfig by autoDialViewModel.currentConfig.collectAsState()

    val callSettings by callSettingsViewModel.callSettings.collectAsState()
    val callHelper = remember { CallHelper(context) }

    var showSearchBar by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }

    // 合并所有客户：任务客户 + 分配客户
    val allCustomers = remember(tasks, assignedCustomers) {
        val taskCustomers = mutableListOf<Customer>()

        // 从所有任务中提取客户
        tasks.forEach { task ->
            task.customers?.forEach { taskCustomer ->
                taskCustomers.add(
                    Customer(
                        id = taskCustomer.id,
                        name = taskCustomer.name,
                        phone = taskCustomer.phone,
                        email = taskCustomer.email,
                        company = taskCustomer.company,
                        status = taskCustomer.callStatus,
                        sourceType = "task",
                        taskId = task.id,
                        taskTitle = task.title
                    )
                )
            }
        }

        // 合并分配的客户，去重（以ID为准）
        val mergedMap = mutableMapOf<Int, Customer>()

        // 先添加任务客户
        taskCustomers.forEach { customer ->
            mergedMap[customer.id] = customer
        }

        // 再添加分配的客户（如果ID重复，优先保留任务来源的信息，因为可能包含任务状态）
        assignedCustomers.forEach { customer ->
            if (!mergedMap.containsKey(customer.id)) {
                mergedMap[customer.id] = customer
            }
        }

        mergedMap.values.toList()
    }

    // 根据搜索和筛选条件过滤
    val filteredCustomers = remember(allCustomers, searchQuery, statusFilter) {
        var result = allCustomers

        // 搜索过滤
        if (searchQuery.isNotBlank()) {
            result = result.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.phone.contains(searchQuery, ignoreCase = true) ||
                it.company?.contains(searchQuery, ignoreCase = true) == true
            }
        }

        // 状态过滤
        if (statusFilter != null) {
            result = result.filter { it.status == statusFilter }
        }

        result.sortedBy { it.name }
    }

    val isLoading = isLoadingAssigned || isLoadingTasks

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

    // 加载数据
    LaunchedEffect(Unit) {
        customerViewModel.loadCustomers()
        taskListViewModel.loadMyTasks()
    }

    Scaffold(
        topBar = {
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
                        Text("客户列表 (${filteredCustomers.size})")
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
                    IconButton(onClick = {
                        customerViewModel.refresh()
                        taskListViewModel.loadMyTasks()
                    }) {
                        Icon(Icons.Default.Refresh, "刷新")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 自动拨号进度
            if (autoDialRunning) {
                val scopeDesc = autoDialViewModel.getScopeDescription(currentConfig)
                AutoDialStatusBar(
                    dialedCount = dialedCount,
                    totalCount = totalCount,
                    scopeDescription = scopeDesc,
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
                isLoading && filteredCustomers.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                filteredCustomers.isEmpty() -> {
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
                        items(filteredCustomers, key = { it.id }) { customer ->
                            CustomerItem(
                                customer = customer,
                                onCall = { makeCall(customer.phone) },
                                onClick = { onNavigateToCustomerDetail(customer.id) }
                            )
                        }
                    }
                }
            }
        }
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
    scopeDescription: String,
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
                            text = "$scopeDescription: $dialedCount / $totalCount",
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
 * 个人中心标签页 - 整合设置内容
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileTab(
    padding: PaddingValues,
    onLogout: () -> Unit,
    onSwitchAccount: () -> Unit,
    onNavigateToHelp: () -> Unit,
    authViewModel: AuthViewModel,
    settingsViewModel: com.callcenter.app.ui.viewmodel.SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val currentUser by authViewModel.currentUser.collectAsState()
    
    // 设置相关状态
    val serverUrl by settingsViewModel.serverUrl.collectAsState()
    val stats by settingsViewModel.stats.collectAsState()
    val callSettings by settingsViewModel.callSettings.collectAsState()
    
    var showServerUrlDialog by remember { mutableStateOf(false) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }
    var showSwitchAccountConfirmDialog by remember { mutableStateOf(false) }
    var showAutoDialIntervalDialog by remember { mutableStateOf(false) }
    var showCallTimeoutDialog by remember { mutableStateOf(false) }
    var showReleaseNotesDialog by remember { mutableStateOf(false) }

    // 读取 release notes
    val releaseNotes = remember { VersionInfoUtil.readReleaseNotes(context) }

    LaunchedEffect(Unit) {
        settingsViewModel.loadSettings()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
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

        // 统计信息
        StatsCard(stats)

        Spacer(modifier = Modifier.height(16.dp))

        // 服务器设置
        SettingsGroup(title = "服务器设置") {
            SettingsItem(
                icon = Icons.Default.Dns,
                title = "服务器地址",
                subtitle = serverUrl,
                onClick = { showServerUrlDialog = true }
            )
        }

        // 通话设置
        SettingsGroup(title = "通话设置") {
            SettingsItem(
                icon = Icons.Default.Phone,
                title = "自动拨号间隔",
                subtitle = "${callSettings.autoDialInterval}秒",
                onClick = { showAutoDialIntervalDialog = true }
            )
            SettingsItem(
                icon = Icons.Default.Timer,
                title = "通话超时时间",
                subtitle = "${callSettings.callTimeout}秒",
                onClick = { showCallTimeoutDialog = true }
            )
        }

        // 关于
        SettingsGroup(title = "关于") {
            SettingsItem(
                icon = Icons.Default.Info,
                title = "版本信息",
                subtitle = "v${com.callcenter.app.BuildConfig.VERSION_NAME} (${com.callcenter.app.BuildConfig.VERSION_CODE})",
                onClick = { showReleaseNotesDialog = true }
            )
            SettingsItem(
                icon = Icons.Default.Help,
                title = "帮助文档",
                subtitle = "查看使用说明",
                onClick = onNavigateToHelp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 切换账号和退出登录按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 切换账号按钮
            OutlinedButton(
                onClick = { showSwitchAccountConfirmDialog = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.SwitchAccount, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("切换账号")
            }

            // 退出登录按钮
            Button(
                onClick = { showLogoutConfirmDialog = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("退出登录")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    // 服务器地址设置对话框
    if (showServerUrlDialog) {
        ServerUrlDialog(
            currentUrl = serverUrl,
            onDismiss = { showServerUrlDialog = false },
            onConfirm = { newUrl ->
                settingsViewModel.updateServerUrl(newUrl)
                showServerUrlDialog = false
                Toast.makeText(context, "服务器地址已更新，请重新登录", Toast.LENGTH_LONG).show()
            }
        )
    }

    // 退出确认对话框
    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            title = { Text("确认退出") },
            text = { Text("确定要退出登录吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirmDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("退出")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 切换账号确认对话框
    if (showSwitchAccountConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSwitchAccountConfirmDialog = false },
            title = { Text("切换账号") },
            text = { Text("切换到其他账号登录？") },
            confirmButton = {
                Button(
                    onClick = {
                        showSwitchAccountConfirmDialog = false
                        onSwitchAccount()
                    }
                ) {
                    Text("切换")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSwitchAccountConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 自动拨号间隔设置对话框
    if (showAutoDialIntervalDialog) {
        var interval by remember { mutableStateOf(callSettings.autoDialInterval) }
        AlertDialog(
            onDismissRequest = { showAutoDialIntervalDialog = false },
            title = { Text("自动拨号间隔") },
            text = {
                Column {
                    Text("拨号间隔（秒）")
                    Slider(
                        value = interval.toFloat(),
                        onValueChange = { interval = it.toInt() },
                        valueRange = 2f..60f,
                        steps = 58
                    )
                    Text("$interval 秒", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        settingsViewModel.updateAutoDialInterval(interval)
                        showAutoDialIntervalDialog = false
                        Toast.makeText(context, "自动拨号间隔已更新", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAutoDialIntervalDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 通话超时时间设置对话框
    if (showCallTimeoutDialog) {
        var timeout by remember { mutableStateOf(callSettings.callTimeout) }
        AlertDialog(
            onDismissRequest = { showCallTimeoutDialog = false },
            title = { Text("通话超时时间") },
            text = {
                Column {
                    Text("无人接听超时（秒）")
                    Slider(
                        value = timeout.toFloat(),
                        onValueChange = { timeout = it.toInt() },
                        valueRange = 2f..60f,
                        steps = 58
                    )
                    Text("$timeout 秒", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        settingsViewModel.updateCallTimeout(timeout)
                        showCallTimeoutDialog = false
                        Toast.makeText(context, "通话超时时间已更新", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCallTimeoutDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // Release Notes 对话框
    if (showReleaseNotesDialog) {
        AlertDialog(
            onDismissRequest = { showReleaseNotesDialog = false },
            title = { Text("更新日志") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = releaseNotes,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showReleaseNotesDialog = false }) {
                    Text("确定")
                }
            }
        )
    }
}

// ==================== 通用组件 ====================

@Composable
private fun StatsCard(stats: com.callcenter.app.data.model.Stats?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "今日统计",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                ProfileStatItem(
                    value = stats?.totalCalls?.toString() ?: "0",
                    label = "总通话",
                    modifier = Modifier.weight(1f)
                )
                ProfileStatItem(
                    value = stats?.successfulCalls?.toString() ?: "0",
                    label = "成功",
                    modifier = Modifier.weight(1f)
                )
                ProfileStatItem(
                    value = formatDuration(stats?.totalDuration ?: 0),
                    label = "总时长",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ProfileStatItem(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDuration(seconds: Int): String {
    return if (seconds < 60) {
        "${seconds}s"
    } else {
        val minutes = seconds / 60
        "${minutes}m"
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            content()
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        },
        trailingContent = {
            Icon(Icons.Default.ChevronRight, null)
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun ServerUrlDialog(
    currentUrl: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var url by remember { mutableStateOf(currentUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("服务器地址") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("服务器地址") },
                    placeholder = { Text("http://localhost:8081") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "修改后需要重新登录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(url) },
                enabled = url.isNotBlank()
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = customer.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // 显示任务来源标签
                    if (customer.sourceType == "task" && !customer.taskTitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = customer.taskTitle,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
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

// ==================== 优化版UI组件 ====================

@Composable
private fun VerticalDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(40.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun StatColumn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SimpleStatColumn(
    value: String,
    label: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TaskListItem(
    task: Task,
    isAutoDialing: Boolean,
    onClick: () -> Unit
) {
    val progress = if (task.customerCount > 0) {
        (task.completedCount * 100 / task.customerCount)
    } else 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        when (task.priority) {
                            "urgent" -> Color(0xFFE91E63)
                            "high" -> Color(0xFFFF9800)
                            else -> Color(0xFF4CAF50)
                        }
                    )
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${task.completedCount}/${task.customerCount} 客户",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isAutoDialing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = "$progress%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
