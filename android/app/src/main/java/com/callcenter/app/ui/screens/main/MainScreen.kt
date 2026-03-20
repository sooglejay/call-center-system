package com.callcenter.app.ui.screens.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.callcenter.app.data.model.Customer
import com.callcenter.app.ui.viewmodel.AuthViewModel
import com.callcenter.app.ui.viewmodel.CustomerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToCustomerDetail: (Int) -> Unit,
    onLogout: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel(),
    customerViewModel: CustomerViewModel = hiltViewModel()
) {
    val customers by customerViewModel.customers
    val isLoading by customerViewModel.isLoading
    val searchQuery by customerViewModel.searchQuery
    val statusFilter by customerViewModel.statusFilter
    val currentUser by authViewModel.currentUser

    var showSearchBar by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showAutoDialDialog by remember { mutableStateOf(false) }
    var autoDialRunning by remember { mutableStateOf(false) }

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
                    IconButton(onClick = { showAutoDialDialog = true }) {
                        Icon(
                            if (autoDialRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            if (autoDialRunning) "停止自动拨号" else "开始自动拨号"
                        )
                    }
                    IconButton(onClick = { customerViewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "刷新")
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
                                // TODO: 拨打电话
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
            onStart = {
                autoDialRunning = true
                showAutoDialDialog = false
            },
            onStop = {
                autoDialRunning = false
                showAutoDialDialog = false
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
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit
) {
    var intervalSeconds by remember { mutableStateOf(10) }
    var timeoutSeconds by remember { mutableStateOf(30) }

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
                onClick = if (isRunning) onStop else onStart
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
