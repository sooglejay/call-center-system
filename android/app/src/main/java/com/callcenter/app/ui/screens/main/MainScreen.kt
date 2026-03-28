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
import com.callcenter.app.service.AutoDialService
import com.callcenter.app.ui.viewmodel.AuthViewModel
import com.callcenter.app.ui.viewmodel.AutoDialViewModel
import com.callcenter.app.ui.viewmodel.CallSettingsViewModel
import com.callcenter.app.ui.viewmodel.CustomerViewModel
import com.callcenter.app.util.CallHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToCustomerDetail: (Int) -> Unit,
    onLogout: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel(),
    customerViewModel: CustomerViewModel = hiltViewModel(),
    autoDialViewModel: AutoDialViewModel = hiltViewModel(),
    callSettingsViewModel: CallSettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val customers by customerViewModel.customers.collectAsState()
    val isLoading by customerViewModel.isLoading.collectAsState()
    val searchQuery by customerViewModel.searchQuery.collectAsState()
    val statusFilter by customerViewModel.statusFilter.collectAsState()
    val currentUser by authViewModel.currentUser.collectAsState()
    
    // 自动拨号状态
    val autoDialRunning by autoDialViewModel.isRunning.collectAsState()
    val currentDialCustomer by autoDialViewModel.currentCustomer.collectAsState()
    val dialedCount by autoDialViewModel.dialedCount.collectAsState()
    val totalCount by autoDialViewModel.totalCount.collectAsState()
    
    // 通话设置
    val callSettings by callSettingsViewModel.callSettings.collectAsState()
    
    val callHelper = remember { CallHelper(context) }

    var showSearchBar by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showAutoDialDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    // 权限请求
    val callPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "需要电话权限才能拨打电话", Toast.LENGTH_LONG).show()
        }
    }

    // 检查并请求权限
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

    // 拨打电话
    fun makeCall(phone: String) {
        if (checkAndRequestCallPermission()) {
            callHelper.makeCall(phone, directCall = true)
        } else {
            // 没有权限时打开拨号界面
            callHelper.makeCall(phone, directCall = false)
        }
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
                        Text("客户列表")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
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
                        DropdownMenuItem(
                            text = { Text("无人接听") },
                            onClick = {
                                customerViewModel.setStatusFilter("no_answer")
                                showFilterMenu = false
                            }
                        )
                    }
                    IconButton(onClick = { customerViewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "刷新")
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "更多")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("设置") },
                            leadingIcon = { Icon(Icons.Default.Settings, null) },
                            onClick = {
                                showMenu = false
                                onNavigateToSettings()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("退出登录") },
                            leadingIcon = { Icon(Icons.Default.Logout, null) },
                            onClick = {
                                showMenu = false
                                onLogout()
                            }
                        )
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = "待拨打: ${customerViewModel.getPendingCount()} | " +
                           "已登录: ${currentUser?.realName ?: "未知"}",
                    modifier = Modifier.padding(start = 16.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAutoDialDialog = true },
                icon = {
                    Icon(
                        if (autoDialRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        null
                    )
                },
                text = { Text(if (autoDialRunning) "停止" else "自动拨号") },
                containerColor = if (autoDialRunning)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primaryContainer
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 自动拨号进度指示器
            if (autoDialRunning) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "自动拨号进行中",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "$dialedCount / $totalCount",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { if (totalCount > 0) dialedCount.toFloat() / totalCount else 0f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        currentDialCustomer?.let { customer ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "当前: ${customer.name} - ${customer.phone}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
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
                                "no_answer" -> "无人接听"
                                else -> statusFilter ?: ""
                            },
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { customerViewModel.setStatusFilter(null) },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                "清除筛选",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // 客户列表
            if (isLoading && customers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (customers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.People,
                            null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "暂无客户数据",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "请先在后台导入客户数据",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(customers, key = { it.id }) { customer ->
                        CustomerItem(
                            customer = customer,
                            onCall = {
                                makeCall(customer.phone)
                            },
                            onClick = {
                                onNavigateToCustomerDetail(customer.id)
                            }
                        )
                    }
                }
            }
        }
    }

    // 自动拨号设置对话框
    if (showAutoDialDialog) {
        AutoDialDialog(
            isRunning = autoDialRunning,
            defaultInterval = callSettings.autoDialInterval,
            defaultTimeout = callSettings.callTimeout,
            onStart = { interval, timeout ->
                showAutoDialDialog = false
                // 获取待拨打的客户列表
                val pendingCustomers = customers.filter { it.status == "pending" }
                if (pendingCustomers.isEmpty()) {
                    Toast.makeText(context, "没有待拨打的客户", Toast.LENGTH_SHORT).show()
                } else {
                    // 检查权限
                    if (checkAndRequestCallPermission()) {
                        autoDialViewModel.startAutoDial(pendingCustomers, interval, timeout)
                    } else {
                        Toast.makeText(context, "需要电话权限才能自动拨号", Toast.LENGTH_SHORT).show()
                    }
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
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .padding(end = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                when (customer.status) {
                    "pending" -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primary
                        ) {}
                    }
                    "completed" -> Icon(
                        Icons.Default.CheckCircle,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxSize()
                    )
                    "no_answer" -> Icon(
                        Icons.Default.Cancel,
                        null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxSize()
                    )
                    else -> Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.outline
                    ) {}
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customer.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Phone,
                        null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = customer.phone,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (customer.company != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = customer.company,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 拨打按钮
            if (customer.status == "pending") {
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
