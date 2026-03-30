package com.callcenter.app.ui.screens.agent

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    val dialedCount by autoDialViewModel.dialedCount.collectAsState()
    val totalCount by autoDialViewModel.totalCount.collectAsState()
    val currentConfig by autoDialViewModel.currentConfig.collectAsState()
    val callSettings by callSettingsViewModel.callSettings.collectAsState()

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
                                // 开始自动拨号
                                if (checkAndRequestCallPermission()) {
                                    val customers = pendingCustomers.map { tc ->
                                        Customer(
                                            id = tc.id,
                                            name = tc.name,
                                            phone = tc.phone,
                                            email = tc.email,
                                            company = tc.company
                                        )
                                    }
                                    val config = AutoDialConfig(
                                        scopeType = AutoDialScopeType.SPECIFIC_TASK,
                                        taskId = taskId,
                                        taskTitle = currentTask.title,
                                        intervalSeconds = callSettings.autoDialInterval,
                                        timeoutSeconds = callSettings.callTimeout
                                    )
                                    autoDialViewModel.startAutoDial(customers, config)
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
                    }
                )
            }
        }
    }
}

@Composable
private fun TaskExecutionContent(
    task: Task,
    isAutoDialing: Boolean,
    dialedCount: Int,
    totalCount: Int,
    onCallCustomer: (String, Int, Int) -> Unit,
    onUpdateStatus: (Int, String, String?) -> Unit
) {
    val customers = task.customers ?: emptyList()
    val pendingCustomers = customers.filter { it.callStatus == "pending" }
    val completedCustomers = customers.filter { it.callStatus == "completed" || it.callStatus == "connected" }

    Column(modifier = Modifier.fillMaxSize()) {
        // 任务信息卡片
        TaskInfoCard(task = task)

        // 统计信息
        TaskStatsCard(
            total = customers.size,
            completed = completedCustomers.size,
            pending = pendingCustomers.size
        )

        // 自动拨号进度条
        if (isAutoDialing) {
            AutoDialProgressCard(
                dialedCount = dialedCount,
                totalCount = totalCount,
                taskTitle = task.title
            )
        }

        // 客户列表标题
        Text(
            text = "客户列表 (${customers.size})",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )

        // 客户列表
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(customers) { customer ->
                TaskCustomerCard(
                    customer = customer,
                    onCall = { onCallCustomer(customer.phone, task.id, customer.id) },
                    onUpdateStatus = { status, result ->
                        onUpdateStatus(customer.id, status, result)
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
private fun TaskStatsCard(total: Int, completed: Int, pending: Int) {
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
            StatItem(value = completed.toString(), label = "已完成")
            StatItem(value = pending.toString(), label = "待拨打")
        }
    }
}

@Composable
private fun AutoDialProgressCard(
    dialedCount: Int,
    totalCount: Int,
    taskTitle: String
) {
    val progress = if (totalCount > 0) dialedCount.toFloat() / totalCount else 0f

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
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "自动拨号进行中",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = "$dialedCount / $totalCount",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "任务: $taskTitle",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskCustomerCard(
    customer: TaskCustomer,
    onCall: () -> Unit,
    onUpdateStatus: (String, String?) -> Unit
) {
    var showResultDialog by remember { mutableStateOf(false) }
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
                    enabled = customer.callStatus != "completed"
                ) {
                    Icon(Icons.Default.Phone, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("拨打")
                }

                // 标记完成按钮
                if (customer.callStatus != "completed") {
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
