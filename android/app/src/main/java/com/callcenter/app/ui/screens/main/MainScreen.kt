package com.callcenter.app.ui.screens.main

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.callcenter.app.data.model.Customer
import com.callcenter.app.ui.viewmodel.AuthViewModel
import com.callcenter.app.ui.viewmodel.AutoDialViewModel
import com.callcenter.app.ui.viewmodel.CallSettingsViewModel
import com.callcenter.app.ui.viewmodel.CustomerViewModel
import com.callcenter.app.util.CallHelper

/**
 * 主页面
 * 根据用户角色显示不同的底部导航
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
    onLogout: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel(),
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
            BottomNavItem("工作台", Icons.Default.Home),
            BottomNavItem("管理", Icons.Default.AdminPanelSettings),
            BottomNavItem("我的", Icons.Default.Person)
        )
    } else {
        listOf(
            BottomNavItem("客户", Icons.Default.ContactPhone),
            BottomNavItem("统计", Icons.Default.BarChart),
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
                AgentWorkTab(
                    padding = padding,
                    onNavigateToCustomerDetail = onNavigateToCustomerDetail,
                    onNavigateToMyStats = onNavigateToMyStats,
                    authViewModel = authViewModel,
                    customerViewModel = customerViewModel,
                    autoDialViewModel = autoDialViewModel,
                    callSettingsViewModel = callSettingsViewModel
                )
            }
            isAdmin && selectedTab == 1 -> {
                AdminManageTab(
                    padding = padding,
                    onNavigateToDashboard = onNavigateToDashboard,
                    onNavigateToAgents = onNavigateToAgents,
                    onNavigateToTasks = onNavigateToTasks
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
                    authViewModel = authViewModel,
                    customerViewModel = customerViewModel,
                    autoDialViewModel = autoDialViewModel,
                    callSettingsViewModel = callSettingsViewModel
                )
            }
            !isAdmin && selectedTab == 1 -> {
                StatsTab(
                    padding = padding,
                    onNavigateToMyStats = onNavigateToMyStats
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

/**
 * 客服工作台标签页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentWorkTab(
    padding: PaddingValues,
    onNavigateToCustomerDetail: (Int) -> Unit,
    onNavigateToMyStats: () -> Unit,
    authViewModel: AuthViewModel,
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
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("自动拨号进行中", style = MaterialTheme.typography.titleSmall)
                        Text("$dialedCount / $totalCount", style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { if (totalCount > 0) dialedCount.toFloat() / totalCount else 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    currentDialCustomer?.let { customer ->
                        Text(
                            "当前: ${customer.name} - ${customer.phone}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
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
 * 管理员管理标签页
 */
@Composable
private fun AdminManageTab(
    padding: PaddingValues,
    onNavigateToDashboard: () -> Unit,
    onNavigateToAgents: () -> Unit,
    onNavigateToTasks: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("管理中心", style = MaterialTheme.typography.headlineSmall)

        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = onNavigateToDashboard
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Dashboard,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("仪表盘", style = MaterialTheme.typography.titleMedium)
                    Text("查看整体运营数据", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = onNavigateToAgents
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Group,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("客服管理", style = MaterialTheme.typography.titleMedium)
                    Text("管理客服员工账号", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = onNavigateToTasks
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Assignment,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("任务管理", style = MaterialTheme.typography.titleMedium)
                    Text("创建和分配工作任务", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/**
 * 统计标签页
 */
@Composable
private fun StatsTab(
    padding: PaddingValues,
    onNavigateToMyStats: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("数据统计", style = MaterialTheme.typography.headlineSmall)

        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = onNavigateToMyStats
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.BarChart,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("我的统计", style = MaterialTheme.typography.titleMedium)
                    Text("查看个人工作数据", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

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
                Text(
                    text = currentUser?.realName ?: "未知用户",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (currentUser?.role == "admin") "管理员" else "客服",
                    style = MaterialTheme.typography.bodyMedium
                )
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
