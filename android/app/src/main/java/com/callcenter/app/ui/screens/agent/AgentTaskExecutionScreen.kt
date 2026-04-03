package com.callcenter.app.ui.screens.agent

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.callcenter.app.data.model.Customer
import com.callcenter.app.data.model.Task
import com.callcenter.app.data.model.TaskCustomer
import com.callcenter.app.ui.viewmodel.AgentTaskViewModel
import com.callcenter.app.ui.viewmodel.AutoDialViewModel
import com.callcenter.app.ui.viewmodel.AutoDialConfig
import com.callcenter.app.ui.viewmodel.AutoDialScopeType
import com.callcenter.app.ui.viewmodel.CallSettingsViewModel
import com.callcenter.app.util.CallHelper

/**
 * 客服任务执行页面
 * 显示任务详情和客户列表，支持拨打电话和更新拨打状态
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentTaskExecutionScreen(
    taskId: Int,
    onNavigateBack: () -> Unit,
    viewModel: AgentTaskViewModel = hiltViewModel(),
    autoDialViewModel: AutoDialViewModel = hiltViewModel(),
    callSettingsViewModel: CallSettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val task by viewModel.task.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    // 初始化 CallHelper
    val callHelper = remember { CallHelper(context) }

    // 自动拨号状态
    val autoDialRunning by autoDialViewModel.isRunning.collectAsState()
    val currentDialCustomer by autoDialViewModel.currentCustomer.collectAsState()
    val nextDialCustomer by autoDialViewModel.nextCustomer.collectAsState()
    val dialedCount by autoDialViewModel.dialedCount.collectAsState()
    val totalCount by autoDialViewModel.totalCount.collectAsState()
    val currentConfig by autoDialViewModel.currentConfig.collectAsState()
    val callSettings by callSettingsViewModel.callSettings.collectAsState()
    val recoverableProgress by autoDialViewModel.recoverableProgress.collectAsState()

    // 自动拨号配置对话框状态
    var showAutoDialConfigDialog by remember { mutableStateOf(false) }
    var showResumeProgressDialog by remember { mutableStateOf(false) }
    var pendingCustomersForDial by remember { mutableStateOf<List<com.callcenter.app.data.model.Customer>>(emptyList()) }
    
    // 客户列表Tab选中状态，提升到父组件以保持状态
    var selectedTab by rememberSaveable { mutableStateOf(0) }

    // 检查是否正在拨打当前任务
    val isAutoDialingThisTask = autoDialRunning && currentConfig?.taskId == taskId

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

    LaunchedEffect(taskId) {
        viewModel.loadTaskDetail(taskId)
    }

    // 当自动拨号的已拨打数量变化时，刷新任务详情以更新客户列表
    LaunchedEffect(dialedCount) {
        if (dialedCount > 0) {
            viewModel.loadTaskDetail(taskId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("任务执行") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            // 自动拨号浮动按钮
            task?.let { currentTask ->
                val pendingCustomers = currentTask.customers?.filter { it.callStatus == "pending" } ?: emptyList()
                if (pendingCustomers.isNotEmpty()) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            if (isAutoDialingThisTask) {
                                autoDialViewModel.stopAutoDial()
                            } else {
                                // 检查是否有可恢复的进度
                                if (recoverableProgress != null && 
                                    recoverableProgress?.taskId == taskId &&
                                    recoverableProgress?.isActive == true) {
                                    // 显示恢复进度对话框
                                    showResumeProgressDialog = true
                                } else {
                                    // 没有可恢复的进度，开始新的自动拨号
                                    if (checkAndRequestCallPermission()) {
                                        pendingCustomersForDial = pendingCustomers.map { tc ->
                                            com.callcenter.app.data.model.Customer(
                                                id = tc.id,
                                                name = tc.name,
                                                phone = tc.phone,
                                                email = tc.email,
                                                company = tc.company
                                            )
                                        }
                                        showAutoDialConfigDialog = true
                                    }
                                }
                            }
                        },
                        icon = {
                            Icon(
                                if (isAutoDialingThisTask) Icons.Default.Stop else Icons.Default.PlayArrow,
                                null
                            )
                        },
                        text = { Text(if (isAutoDialingThisTask) "停止拨号" else "自动拨号") },
                        containerColor = if (isAutoDialingThisTask) 
                            MaterialTheme.colorScheme.error 
                        else 
                            MaterialTheme.colorScheme.primaryContainer
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 自动拨号时，顶部固定显示当前和下一个客户信息
            if (isAutoDialingThisTask && currentDialCustomer != null) {
                TaskAutoDialCustomerPanel(
                    currentCustomer = currentDialCustomer,
                    nextCustomer = nextDialCustomer,
                    dialedCount = dialedCount,
                    totalCount = totalCount,
                    onStop = { autoDialViewModel.stopAutoDial() }
                )
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (error != null) {
                ErrorCard(error = error!!, onRetry = { viewModel.loadTaskDetail(taskId) })
            } else if (task != null) {
                TaskExecutionContent(
                    task = task!!,
                    isAutoDialing = isAutoDialingThisTask,
                    dialedCount = dialedCount,
                    totalCount = totalCount,
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    onCallCustomer = { phone, taskId, customerId ->
                        // 检查并请求权限，然后拨打电话
                        if (checkAndRequestCallPermission()) {
                            callHelper.makeCall(phone, directCall = true)
                        } else {
                            callHelper.makeCall(phone, directCall = false)
                        }
                        // 更新状态为已拨打
                        viewModel.updateCustomerStatus(taskId, customerId, "called", null)
                    },
                    onUpdateStatus = { customerId, status, result ->
                        viewModel.updateCustomerStatus(taskId, customerId, status, result)
                    },
                    onEditCustomer = { customerId, name, phone, company ->
                        viewModel.updateCustomerInfo(
                            taskId = taskId,
                            customerId = customerId,
                            name = name,
                            phone = phone,
                            company = company
                        )
                    },
                    onDeleteCustomer = { customerId ->
                        viewModel.removeCustomer(taskId, customerId)
                    }
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
                                scopeType = AutoDialScopeType.SPECIFIC_TASK,
                                taskId = taskId,
                                taskTitle = task?.title,
                                intervalSeconds = localInterval,
                                timeoutSeconds = localTimeout,
                                dialsPerCustomer = localDialsPerCustomer
                            )
                            autoDialViewModel.startAutoDial(pendingCustomersForDial, config)
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

        // 恢复进度对话框
        if (showResumeProgressDialog && recoverableProgress != null) {
            AlertDialog(
                onDismissRequest = { showResumeProgressDialog = false },
                title = { Text("恢复自动拨号") },
                text = {
                    Column {
                        Text("检测到上次有未完成的自动拨号任务：")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("已拨打: ${recoverableProgress?.dialedCount}/${recoverableProgress?.totalCount}")
                        Text("剩余: ${recoverableProgress?.remainingCustomers?.size ?: 0} 个客户")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("是否从上次暂停的位置继续拨打？")
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showResumeProgressDialog = false
                            // 恢复上次的拨号进度
                            recoverableProgress?.let { progress ->
                                autoDialViewModel.restoreProgress(progress)
                            }
                        }
                    ) {
                        Text("继续拨打")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showResumeProgressDialog = false
                            // 清除进度，开始新的自动拨号
                            autoDialViewModel.clearRecoverableProgress()
                            // 显示配置对话框开始新的拨号
                            if (checkAndRequestCallPermission()) {
                                val pendingCustomers = task?.customers?.filter { it.callStatus == "pending" } ?: emptyList()
                                pendingCustomersForDial = pendingCustomers.map { tc ->
                                    com.callcenter.app.data.model.Customer(
                                        id = tc.id,
                                        name = tc.name,
                                        phone = tc.phone,
                                        email = tc.email,
                                        company = tc.company
                                    )
                                }
                                showAutoDialConfigDialog = true
                            }
                        }
                    ) {
                        Text("重新开始")
                    }
                }
            )
        }
    }
}

@Composable
private fun TaskExecutionContent(
    task: Task,
    isAutoDialing: Boolean,
    dialedCount: Int,
    totalCount: Int,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onCallCustomer: (String, Int, Int) -> Unit,
    onUpdateStatus: (Int, String, String?) -> Unit,
    onEditCustomer: (Int, String?, String?, String?) -> Unit,
    onDeleteCustomer: (Int) -> Unit
) {
    val customers = task.customers ?: emptyList()
    val pendingCustomers = customers.filter { it.callStatus == "pending" }
    val calledCustomers = customers.filter { 
        it.callStatus == "called" || it.callStatus == "completed" || it.callStatus == "connected" 
    }.sortedByDescending { it.calledAt ?: it.callTime ?: "" } // 按拨打时间降序排列

    // 根据选中的Tab过滤客户列表
    val displayedCustomers = when (selectedTab) {
        0 -> customers // 全部
        1 -> pendingCustomers // 待拨打
        2 -> calledCustomers // 已拨打
        else -> customers
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 任务信息卡片
        TaskInfoCard(task = task)

        // Tab按钮：全部/待拨打/已拨打
        CustomerFilterTabs(
            totalCount = customers.size,
            pendingCount = pendingCustomers.size,
            calledCount = calledCustomers.size,
            selectedTab = selectedTab,
            onTabSelected = onTabSelected
        )

        // 客户列表
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 显示当前分组标题
            item {
                val title = when (selectedTab) {
                    0 -> "全部客户"
                    1 -> "待拨打"
                    2 -> "已拨打"
                    else -> "全部客户"
                }
                val count = displayedCustomers.size
                val color = when (selectedTab) {
                    0 -> MaterialTheme.colorScheme.primary
                    1 -> MaterialTheme.colorScheme.primary
                    2 -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                }
                CustomerGroupHeader(
                    title = title,
                    count = count,
                    color = color
                )
            }

            // 显示客户列表
            items(displayedCustomers) { customer ->
                TaskCustomerCard(
                    customer = customer,
                    onCall = { onCallCustomer(customer.phone, task.id, customer.id) },
                    onUpdateStatus = { status, result ->
                        onUpdateStatus(customer.id, status, result)
                    },
                    onEditCustomer = { name, phone, company ->
                        onEditCustomer(customer.id, name, phone, company)
                    },
                    onDeleteCustomer = {
                        onDeleteCustomer(customer.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun TaskInfoCard(task: Task) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.headlineSmall
            )
            if (!task.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TaskStatusChip(status = task.status)
                Spacer(modifier = Modifier.width(8.dp))
                TaskPriorityChip(priority = task.priority)
            }
        }
    }
}

@Composable
private fun TaskStatsCard(total: Int, called: Int, pending: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(value = total.toString(), label = "总客户")
            StatItem(value = called.toString(), label = "已拨打")
            StatItem(value = pending.toString(), label = "待拨打")
        }
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

/**
 * 客户筛选Tab按钮
 * 支持切换显示全部/待拨打/已拨打客户
 */
@Composable
private fun CustomerFilterTabs(
    totalCount: Int,
    pendingCount: Int,
    calledCount: Int,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // 全部Tab
            FilterTabItem(
                label = "全部",
                count = totalCount,
                isSelected = selectedTab == 0,
                onClick = { onTabSelected(0) }
            )

            // 分隔线
            Divider(
                modifier = Modifier
                    .width(1.dp)
                    .height(40.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // 待拨打Tab
            FilterTabItem(
                label = "待拨打",
                count = pendingCount,
                isSelected = selectedTab == 1,
                onClick = { onTabSelected(1) }
            )

            // 分隔线
            Divider(
                modifier = Modifier
                    .width(1.dp)
                    .height(40.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // 已拨打Tab
            FilterTabItem(
                label = "已拨打",
                count = calledCount,
                isSelected = selectedTab == 2,
                onClick = { onTabSelected(2) }
            )
        }
    }
}

/**
 * 单个筛选Tab项
 */
@Composable
private fun FilterTabItem(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        androidx.compose.ui.graphics.Color.Transparent
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.width(80.dp),
        color = backgroundColor,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = if (isSelected) MaterialTheme.colorScheme.primary else contentColor,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor
            )
        }
    }
}

/**
 * 客户分组标题
 */
@Composable
private fun CustomerGroupHeader(
    title: String,
    count: Int,
    color: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧彩色指示条
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(24.dp)
                .background(color, shape = MaterialTheme.shapes.small)
        )
        Spacer(modifier = Modifier.width(12.dp))
        // 标题
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Spacer(modifier = Modifier.width(8.dp))
        // 数量标签
        Surface(
            color = color.copy(alpha = 0.1f),
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = color,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskCustomerCard(
    customer: TaskCustomer,
    onCall: () -> Unit,
    onUpdateStatus: (String, String?) -> Unit,
    onEditCustomer: (String?, String?, String?) -> Unit,
    onDeleteCustomer: () -> Unit
) {
    var showResultDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var callResult by remember { mutableStateOf(customer.callResult ?: "") }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = customer.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = customer.phone,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!customer.company.isNullOrBlank()) {
                        Text(
                            text = customer.company,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                CustomerStatusChip(status = customer.callStatus)
            }

            if (!customer.callResult.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "备注: ${customer.callResult}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 拨打电话按钮
                Button(
                    onClick = onCall,
                    modifier = Modifier.weight(1f),
                    enabled = customer.callStatus == "pending"
                ) {
                    Icon(Icons.Default.Phone, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("拨打")
                }

                // 标记完成按钮 - 只有待拨打状态才显示
                if (customer.callStatus == "pending") {
                    OutlinedButton(
                        onClick = { showResultDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("完成")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 编辑和删除按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 编辑按钮
                OutlinedButton(
                    onClick = { showEditDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("编辑")
                }

                // 删除按钮
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("删除")
                }
            }
        }
    }

    // 通话结果对话框
    if (showResultDialog) {
        AlertDialog(
            onDismissRequest = { showResultDialog = false },
            title = { Text("通话结果") },
            text = {
                Column {
                    Text("请输入通话结果备注：")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = callResult,
                        onValueChange = { callResult = it },
                        placeholder = { Text("例如：已成交、未接通、无意向...") },
                        minLines = 2,
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateStatus("completed", callResult)
                        showResultDialog = false
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResultDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 编辑客户信息对话框
    if (showEditDialog) {
        var editName by remember { mutableStateOf(customer.name) }
        var editPhone by remember { mutableStateOf(customer.phone) }
        var editCompany by remember { mutableStateOf(customer.company ?: "") }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("编辑客户信息") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("姓名") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editPhone,
                        onValueChange = { editPhone = it },
                        label = { Text("电话") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editCompany,
                        onValueChange = { editCompany = it },
                        label = { Text("公司") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onEditCustomer(
                            editName.takeIf { it != customer.name },
                            editPhone.takeIf { it != customer.phone },
                            editCompany.takeIf { it != customer.company }
                        )
                        showEditDialog = false
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 删除确认对话框
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = { Text("确定要从任务中删除客户「${customer.name}」吗？此操作不可恢复。") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteCustomer()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun CustomerStatusChip(status: String) {
    val (color, text) = when (status) {
        "completed", "connected" -> MaterialTheme.colorScheme.primary to "已完成"
        "called" -> MaterialTheme.colorScheme.tertiary to "已拨打"
        "failed" -> MaterialTheme.colorScheme.error to "未接通"
        else -> MaterialTheme.colorScheme.outline to "待拨打"
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun TaskStatusChip(status: String) {
    val (color, text) = when (status) {
        "completed" -> MaterialTheme.colorScheme.primary to "已完成"
        "in_progress" -> MaterialTheme.colorScheme.tertiary to "进行中"
        "cancelled" -> MaterialTheme.colorScheme.error to "已取消"
        else -> MaterialTheme.colorScheme.outline to "待处理"
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun TaskPriorityChip(priority: String) {
    val (color, text) = when (priority) {
        "urgent" -> MaterialTheme.colorScheme.error to "紧急"
        "high" -> MaterialTheme.colorScheme.tertiary to "高"
        "normal" -> MaterialTheme.colorScheme.outline to "普通"
        else -> MaterialTheme.colorScheme.outline to "低"
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun ErrorCard(error: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onRetry) {
                Text("重试")
            }
        }
    }
}

/**
 * 任务执行页自动拨号客户信息面板
 * 在顶部固定显示当前和下一个客户信息
 */
@Composable
private fun TaskAutoDialCustomerPanel(
    currentCustomer: com.callcenter.app.data.model.Customer?,
    nextCustomer: com.callcenter.app.data.model.Customer?,
    dialedCount: Int,
    totalCount: Int,
    onStop: () -> Unit
) {
    if (currentCustomer == null) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // 标题栏：进度和停止按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "自动拨号中: $dialedCount/$totalCount",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                TextButton(
                    onClick = onStop,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("停止", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 当前客户信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 当前客户头像
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = currentCustomer.name.firstOrNull()?.toString() ?: "?",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // 当前客户信息
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentCustomer.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentCustomer.phone,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!currentCustomer.company.isNullOrBlank()) {
                        Text(
                            text = currentCustomer.company,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 分隔线
                Divider(
                    modifier = Modifier
                        .height(40.dp)
                        .width(1.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Spacer(modifier = Modifier.width(12.dp))

                // 下一个客户信息
                Column(
                    modifier = Modifier.width(120.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "下一个",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (nextCustomer != null) {
                        Surface(
                            modifier = Modifier.size(32.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = nextCustomer.name.firstOrNull()?.toString() ?: "?",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = nextCustomer.name,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = nextCustomer.phone,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "最后一个",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
