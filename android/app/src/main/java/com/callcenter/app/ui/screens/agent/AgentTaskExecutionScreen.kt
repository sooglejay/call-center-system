package com.callcenter.app.ui.screens.agent

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.callcenter.app.data.model.Customer
import com.callcenter.app.data.model.Task
import com.callcenter.app.data.model.TaskCustomer
import com.callcenter.app.service.AutoDialService
import com.callcenter.app.service.CallRecordingManager
import com.callcenter.app.ui.util.rememberDebounceOnClick
import com.callcenter.app.ui.viewmodel.AgentTaskViewModel
import com.callcenter.app.ui.viewmodel.AutoDialViewModel
import com.callcenter.app.ui.viewmodel.AutoDialConfig
import com.callcenter.app.ui.viewmodel.AutoDialScopeType
import com.callcenter.app.ui.viewmodel.CallSettingsViewModel
import com.callcenter.app.ui.viewmodel.CreateTaskViewModel
import com.callcenter.app.util.CallHelper
import com.callcenter.app.util.FloatingWindowManager
import com.callcenter.app.util.VersionInfoUtil
import com.callcenter.app.util.AutoFloatingWindow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 客服任务执行页面
 * 显示任务详情和客户列表，支持拨打电话和更新拨打状态
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentTaskExecutionScreen(
    taskId: Int,
    onNavigateBack: () -> Unit,
    onNavigateToCreateRetryTask: (String) -> Unit,
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
    val recoverableProgress by autoDialViewModel.recoverableProgress.collectAsState()

    // 自动拨号配置对话框状态
    var showAutoDialConfigDialog by remember { mutableStateOf(false) }
    var showResumeProgressDialog by remember { mutableStateOf(false) }
    var pendingCustomersForDial by remember { mutableStateOf<List<com.callcenter.app.data.model.Customer>>(emptyList()) }
    
    // 悬浮窗权限提示对话框
    var showFloatingWindowPermissionDialog by remember { mutableStateOf(false) }

    // 检查并请求悬浮窗权限
    fun checkAndRequestFloatingWindowPermission(): Boolean {
        return if (FloatingWindowManager.canDrawOverlays(context)) {
            true
        } else {
            showFloatingWindowPermissionDialog = true
            false
        }
    }
    
    // 客户列表Tab选中状态，提升到父组件以保持状态
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var showMoreMenu by remember { mutableStateOf(false) }

    // 检查是否正在拨打当前任务
    val isAutoDialingThisTask = autoDialRunning && (currentConfig?.taskId == taskId || currentConfig?.taskId == null)

    val currentStatusKey = remember(selectedTab) { getStatusKeyByTab(selectedTab) }
    val currentStatusCustomers = remember(task, currentStatusKey) {
        filterTaskCustomersByStatus(task?.customers ?: emptyList(), currentStatusKey)
    }

    // 监听自动拨号服务的客户状态更新事件（实时更新 UI）
    LaunchedEffect(Unit) {
        viewModel.observeCustomerStatusUpdates()
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

    LaunchedEffect(taskId) {
        viewModel.loadTaskDetail(taskId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(VersionInfoUtil.getTitleWithVersion(context, "任务执行")) },
                navigationIcon = {
                    IconButton(onClick = rememberDebounceOnClick { onNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, "更多")
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("创建任务") },
                                onClick = {
                                    showMoreMenu = false
                                    if (currentStatusKey == "all") {
                                        Toast.makeText(context, "请先切换到具体通话状态后再创建任务", Toast.LENGTH_SHORT).show()
                                    } else if (currentStatusCustomers.isEmpty()) {
                                        Toast.makeText(context, "当前状态下没有可创建任务的客户", Toast.LENGTH_SHORT).show()
                                    } else {
                                        onNavigateToCreateRetryTask(currentStatusKey)
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.AddTask, contentDescription = null)
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            // 自动拨号按钮已移到任务信息卡片内部
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
    // 自动拨号时，顶部固定显示当前客户信息
    if (isAutoDialingThisTask) {
                TaskAutoDialCustomerPanel(
                    currentCustomer = currentDialCustomer,
                    dialedCount = dialedCount,
                    totalCount = totalCount,
                    isAutoMode = true,
                    onStop = { autoDialViewModel.stopAutoDial() },
                    onCallStatusMarked = { status ->
                        // 发送通话状态标记到AutoDialService
                        val intent = android.content.Intent(context, com.callcenter.app.service.AutoDialService::class.java).apply {
                            // 任务执行页仅两态：connected / unanswered
                            // 兼容：如果外部仍传入 voicemail/rejected/...，统一按 unanswered 处理
                            action = if (status == "connected") {
                                com.callcenter.app.service.AutoDialService.ACTION_CALL_STATUS_CONNECTED
                            } else {
                                com.callcenter.app.service.AutoDialService.ACTION_CALL_STATUS_UNANSWERED
                            }
                        }
                        if (intent.action != null) {
                            context.startService(intent)
                        }

                        // 更新本地客户状态（两态）
                        val dialCustomer = currentDialCustomer
                        if (dialCustomer != null) {
                            val isConnected = status == "connected"
                            val callResult = if (isConnected) "真人已接通" else "响铃未接通"
                            val callStatus = if (isConnected) "connected" else "unanswered"
                            viewModel.updateCustomerStatus(
                                taskId = taskId,
                                customerId = dialCustomer.id,
                                status = callStatus,
                                callResult = callResult
                            )
                        }
                    },
                    callStateHistory = com.callcenter.app.service.FloatingCustomerService.callStateHistory.collectAsState().value
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
                    isRefreshing = isLoading,
                    onTabSelected = { selectedTab = it },
                    onRefresh = { viewModel.loadTaskDetail(taskId) },
                    onCallCustomer = { taskCustomer ->
                        val customerId = taskCustomer.id
                        val phone = taskCustomer.phone
                        if (customerId == null || phone.isNullOrBlank()) {
                            Toast.makeText(context, "客户信息不完整，无法拨打", Toast.LENGTH_SHORT).show()
                            return@TaskExecutionContent
                        }

                        if (!checkAndRequestCallPermission()) {
                            Toast.makeText(context, "请先授予拨号权限后再发起通话，这样才能准确追踪状态并上传录音", Toast.LENGTH_LONG).show()
                            callHelper.makeCall(phone)
                            return@TaskExecutionContent
                        }

                        viewModel.createTaskCallRecord(
                            customer = taskCustomer,
                            onSuccess = { callRecord ->
                                try {
                                    callHelper.makeCall(phone)
                                    viewModel.updateCustomerStatus(taskId, customerId, "called", null, callRecord?.id)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "拨号失败: ${e.message}", Toast.LENGTH_LONG).show()
                                    viewModel.updateCustomerStatus(taskId, customerId, "failed", "拨打失败: ${e.message}", callRecord?.id)
                                }
                            },
                            onFailure = { message ->
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            }
                        )
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
                    },
                    onAutoDialClick = {
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
                                val pendingCustomers = task?.customers?.filter { it.callStatus == "pending" } ?: emptyList()
                                if (checkAndRequestCallPermission()) {
                                    pendingCustomersForDial = pendingCustomers.map { tc ->
                                        com.callcenter.app.data.model.Customer(
                                            id = tc.id ?: tc.taskCustomerId,  // 使用 taskCustomerId 作为备选
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
                                valueRange = 1f..30f,
                                steps = 28
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
                            // 检查悬浮窗权限，如果没有则显示提示并返回
                            if (!checkAndRequestFloatingWindowPermission()) {
                                return@TextButton
                            }
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
                            // 检查悬浮窗权限，如果没有则显示提示并返回
                            if (!checkAndRequestFloatingWindowPermission()) {
                                return@TextButton
                            }
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
                                        id = tc.id ?: tc.taskCustomerId,  // 使用 taskCustomerId 作为备选
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

        // 悬浮窗权限提示对话框
        if (showFloatingWindowPermissionDialog) {
            AlertDialog(
                onDismissRequest = { showFloatingWindowPermissionDialog = false },
                title = { Text("需要悬浮窗权限") },
                text = {
                    Text(
                        "为了在拨号时持续显示客户信息，需要开启悬浮窗权限。\n\n" +
                        "开启后，您可以在通话过程中看到当前客户的详细信息，并且可以拖拽移动到屏幕任意位置。"
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showFloatingWindowPermissionDialog = false
                            // 跳转到设置页面请求权限
                            val activity = context as? android.app.Activity
                            activity?.let {
                                FloatingWindowManager.requestOverlayPermission(it)
                            }
                        }
                    ) {
                        Text("去开启")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showFloatingWindowPermissionDialog = false }
                    ) {
                        Text("暂不开启")
                    }
                }
            )
        }

        // 自动悬浮窗组件 - 在自动拨号时自动显示悬浮窗
        val scope = rememberCoroutineScope()
        AutoFloatingWindow(
            enabled = true,
            onCallStatusMarked = { status, onComplete ->
                // 同时更新到AutoDialService和本地ViewModel
                val currentCustomer = AutoDialService.currentCustomer.value
                
                // 1. 发送通话状态标记到AutoDialService（用于后台记录）
                val intent = android.content.Intent(context, com.callcenter.app.service.AutoDialService::class.java).apply {
                    // 任务执行页仅两态：connected / unanswered
                    // 兼容：如果外部仍传入 voicemail/rejected/...，统一按 unanswered 处理
                    action = if (status == "connected") {
                        com.callcenter.app.service.AutoDialService.ACTION_CALL_STATUS_CONNECTED
                    } else {
                        com.callcenter.app.service.AutoDialService.ACTION_CALL_STATUS_UNANSWERED
                    }
                }
                context.startService(intent)

                // 2. 更新本地客户状态（立即刷新UI，两态）
                if (currentCustomer != null) {
                    val isConnected = status == "connected"
                    val callResult = if (isConnected) "真人已接通" else "响铃未接通"
                    val callStatus = if (isConnected) "connected" else "unanswered"
                    viewModel.updateCustomerStatus(
                        taskId = taskId,
                        customerId = currentCustomer.id,
                        status = callStatus,
                        callResult = callResult
                    )
                }
                
                // 3. 延迟后调用完成回调（给ViewModel操作一些时间）
                scope.launch {
                    kotlinx.coroutines.delay(800)
                    onComplete?.invoke()
                }
            }
        )
    }
}

@Composable
private fun TaskExecutionContent(
    task: Task,
    isAutoDialing: Boolean,
    dialedCount: Int,
    totalCount: Int,
    selectedTab: Int,
    isRefreshing: Boolean,
    onTabSelected: (Int) -> Unit,
    onRefresh: () -> Unit,
    onCallCustomer: (TaskCustomer) -> Unit,
    onUpdateStatus: (Int, String, String?) -> Unit,
    onEditCustomer: (Int, String?, String?, String?) -> Unit,
    onDeleteCustomer: (Int) -> Unit,
    onAutoDialClick: () -> Unit = {}
) {
    val customers = task.customers ?: emptyList()

    // 使用 remember 缓存过滤结果，只有当 customers 列表变化时才重新计算
    data class GroupedCustomers(
        val pending: List<TaskCustomer>,
        val connected: List<TaskCustomer>,
        val unanswered: List<TaskCustomer>
    )
    
    val groupedCustomers = remember(customers) {
        // 按通话状态分组（两态：真人已接通 / 响铃未接通）
        val pending = customers.filter { it.callStatus == "pending" || it.callStatus.isNullOrBlank() }

        val connected = customers.filter {
            it.callStatus == "connected" ||
                it.callResult == "真人已接通" ||
                it.callResult == "已接听" ||
                it.callResult == "connected" ||
                it.callResult == "answered"
        }

        // 除 pending / connected 外的全部，统一归为“响铃未接通”（兼容历史 voicemail/failed/...）
        val unanswered = customers.filter {
            val status = it.callStatus
            if (status == "pending" || status.isNullOrBlank()) return@filter false
            if (status == "connected") return@filter false
            val result = it.callResult
            if (result == "真人已接通" || result == "已接听" || result == "connected" || result == "answered") return@filter false
            true
        }

        GroupedCustomers(pending, connected, unanswered)
    }

    val pendingCustomers = groupedCustomers.pending
    val connectedCustomers = groupedCustomers.connected
    val unansweredCustomers = groupedCustomers.unanswered

    // 根据选中的Tab过滤客户列表（两态）
    val displayedCustomers = when (selectedTab) {
        0 -> customers // 全部
        1 -> pendingCustomers // 待拨打
        2 -> connectedCustomers // 真人已接通
        3 -> unansweredCustomers // 响铃未接通
        else -> customers
    }

    // 客户列表（带下拉刷新）- 使用LazyColumn作为根布局，支持整体滑动
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: 任务信息卡片（包含自动拨号按钮）
            item {
                TaskInfoCard(
                    task = task,
                    isAutoDialing = isAutoDialing,
                    hasPendingCustomers = pendingCustomers.isNotEmpty(),
                    onAutoDialClick = onAutoDialClick
                )
            }

            // Header: Tab按钮
            item {
                CustomerFilterTabsWithCallStatus(
                    totalCount = customers.size,
                    pendingCount = pendingCustomers.size,
                    connectedCount = connectedCustomers.size,
                    unansweredCount = unansweredCustomers.size,
                    selectedTab = selectedTab,
                    onTabSelected = onTabSelected
                )
            }

            // 显示当前分组标题
            item {
                val title = when (selectedTab) {
                    0 -> "全部客户"
                    1 -> "待拨打"
                    2 -> "真人已接通"
                    3 -> "响铃未接通"
                    else -> "全部客户"
                }
                val count = displayedCustomers.size
                val color = when (selectedTab) {
                    0 -> MaterialTheme.colorScheme.primary
                    1 -> MaterialTheme.colorScheme.primary
                    2 -> MaterialTheme.colorScheme.tertiary
                    3 -> MaterialTheme.colorScheme.error
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
                // 使用 customer.id 或 taskCustomerId 作为客户ID
                val customerId = customer.id ?: customer.taskCustomerId
                TaskCustomerCard(
                    customer = customer,
                    selectedTab = selectedTab,
                    onCall = { onCallCustomer(customer) },
                    onUpdateStatus = { status, result ->
                        onUpdateStatus(customerId, status, result)
                    },
                    onEditCustomer = { name, phone, company ->
                        onEditCustomer(customerId, name, phone, company)
                    },
                    onDeleteCustomer = {
                        onDeleteCustomer(customerId)
                    }
                )
            }
        }

    }
}

@Composable
private fun TaskInfoCard(
    task: Task,
    isAutoDialing: Boolean = false,
    hasPendingCustomers: Boolean = false,
    onAutoDialClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：任务信息
            Column(modifier = Modifier.weight(1f)) {
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
                    TaskStatusChip(task = task)
                    Spacer(modifier = Modifier.width(8.dp))
                    TaskPriorityChip(priority = task.priority)
                }
            }

            // 右侧：自动拨号按钮
            if (hasPendingCustomers) {
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = onAutoDialClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAutoDialing)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (isAutoDialing) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isAutoDialing) "停止" else "自动拨号",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
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
 * 客户筛选Tab按钮（支持通话状态筛选）
 * 支持切换显示全部/待拨打/真人已接通/响铃未接通
 */
@Composable
private fun CustomerFilterTabsWithCallStatus(
    totalCount: Int,
    pendingCount: Int,
    connectedCount: Int,
    unansweredCount: Int,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            // 第一行Tab: 全部 | 待拨打
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FilterTabItem(
                    label = "全部",
                    count = totalCount,
                    isSelected = selectedTab == 0,
                    onClick = { onTabSelected(0) }
                )

                Divider(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                FilterTabItem(
                    label = "待拨打",
                    count = pendingCount,
                    isSelected = selectedTab == 1,
                    onClick = { onTabSelected(1) }
                )
            }

            // 第二行Tab: 真人已接通 | 响铃未接通
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FilterTabItem(
                    label = "真人已接通",
                    count = connectedCount,
                    isSelected = selectedTab == 2,
                    onClick = { onTabSelected(2) }
                )

                Divider(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                FilterTabItem(
                    label = "响铃未接通",
                    count = unansweredCount,
                    isSelected = selectedTab == 3,
                    onClick = { onTabSelected(3) }
                )
            }
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
            .padding(vertical = 8.dp, horizontal = 16.dp),
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

private data class RetryTaskStatusMeta(
    val key: String,
    val label: String
)

@Composable
private fun RetryTaskPriorityChip(
    label: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
        border = if (selected) BorderStroke(1.dp, color) else null,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = color)
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(label, color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun getStatusMetaByTab(tab: Int): RetryTaskStatusMeta {
    return when (tab) {
        1 -> RetryTaskStatusMeta("pending", "待拨打")
        2 -> RetryTaskStatusMeta("connected", "真人已接通")
        3 -> RetryTaskStatusMeta("unanswered", "响铃未接通")
        else -> RetryTaskStatusMeta("all", "全部客户")
    }
}

private fun getStatusKeyByTab(tab: Int): String = getStatusMetaByTab(tab).key

private fun filterTaskCustomersByStatus(customers: List<TaskCustomer>, statusKey: String): List<TaskCustomer> {
    return when (statusKey) {
        "pending" -> customers.filter { it.callStatus == "pending" || it.callStatus.isNullOrBlank() }
        "connected" -> customers.filter {
            it.callStatus == "connected" ||
                it.callResult == "真人已接通" ||
                it.callResult == "已接听" ||
                it.callResult == "connected" ||
                it.callResult == "answered"
        }
        "unanswered" -> customers.filter {
            val status = it.callStatus
            if (status == "pending" || status.isNullOrBlank()) return@filter false
            if (status == "connected") return@filter false
            val result = it.callResult
            if (result == "真人已接通" || result == "已接听" || result == "connected" || result == "answered") return@filter false
            true
        }
        else -> customers
    }
}

/**
 * 任务执行页通话状态展示归一：
 * - pending（待拨打）
 * - connected（真人已接通）
 * - unanswered（响铃未接通，兼容历史 voicemail/failed/...）
 */
private fun normalizeTaskCallStatusForDisplay(callStatus: String?, callResult: String?): String {
    val status = callStatus?.trim()?.lowercase()

    // 1) 优先使用 callStatus
    when (status) {
        null, "", "pending" -> return "pending"
        "connected", "completed" -> return "connected"
        "unanswered" -> return "unanswered"
        // 兼容历史值：统一归为响铃未接通
        "voicemail", "failed", "rejected", "busy", "power_off", "no_answer", "ivr", "other", "called", "calling", "ringing" ->
            return "unanswered"
    }

    // 2) 回退使用 callResult
    val r = callResult?.trim()
    if (r.isNullOrBlank()) return "pending"
    if (
        r == "真人已接通" ||
        r == "已接听" ||
        r.equals("connected", ignoreCase = true) ||
        r.equals("answered", ignoreCase = true) ||
        r == "通话完成"
    ) {
        return "connected"
    }
    return "unanswered"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentCreateRetryTaskScreen(
    taskId: Int,
    statusKey: String,
    onNavigateBack: () -> Unit,
    onTaskCreated: () -> Unit,
    taskViewModel: AgentTaskViewModel = hiltViewModel(),
    createTaskViewModel: CreateTaskViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val task by taskViewModel.task.collectAsState()
    val isLoading by taskViewModel.isLoading.collectAsState()
    val error by taskViewModel.error.collectAsState()
    val isCreating by createTaskViewModel.isCreating.collectAsState()
    val createError by createTaskViewModel.error.collectAsState()
    val statusMeta = remember(statusKey) { getStatusMetaByTab((1..3).firstOrNull { getStatusKeyByTab(it) == statusKey } ?: 0) }

    var title by rememberSaveable { mutableStateOf("${statusMeta.label}重拨任务") }
    var description by rememberSaveable { mutableStateOf("从「${statusMeta.label}」客户列表快速创建") }
    var priority by rememberSaveable { mutableStateOf("normal") }

    val candidates = remember(task, statusKey) {
        filterTaskCustomersByStatus(task?.customers ?: emptyList(), statusKey)
    }
    var selectedCustomerIds by remember(candidates) {
        mutableStateOf(candidates.mapNotNull { it.id }.toSet())
    }

    LaunchedEffect(taskId) {
        taskViewModel.loadTaskDetail(taskId)
    }

    LaunchedEffect(createError) {
        createError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("创建任务") },
                navigationIcon = {
                    IconButton(onClick = rememberDebounceOnClick { onNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = rememberDebounceOnClick { onNavigateBack() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消")
                    }
                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                Toast.makeText(context, "请输入任务标题", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (selectedCustomerIds.isEmpty()) {
                                Toast.makeText(context, "请至少保留一个客户", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            createTaskViewModel.createTaskForSelf(
                                com.callcenter.app.data.model.CreateTaskRequest(
                                    title = title.trim(),
                                    description = description.trim().ifBlank { null },
                                    priority = priority,
                                    customerIds = selectedCustomerIds.toList()
                                )
                            ) {
                                Toast.makeText(context, "任务创建成功", Toast.LENGTH_SHORT).show()
                                onTaskCreated()
                            }
                        },
                        enabled = !isCreating,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isCreating) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("创建任务")
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        when {
            isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> ErrorCard(error = error!!, onRetry = { taskViewModel.loadTaskDetail(taskId) })
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("基于当前任务的「${statusMeta.label}」客户创建新任务", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it },
                                label = { Text("任务标题") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = description,
                                onValueChange = { description = it },
                                label = { Text("任务描述") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2
                            )
                            Text("优先级", style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RetryTaskPriorityChip(label = "普通", selected = priority == "normal", color = Color(0xFF2196F3)) { priority = "normal" }
                                RetryTaskPriorityChip(label = "高", selected = priority == "high", color = Color(0xFFFF9800)) { priority = "high" }
                                RetryTaskPriorityChip(label = "紧急", selected = priority == "urgent", color = Color(0xFFE91E63)) { priority = "urgent" }
                            }
                        }
                    }
                }

                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("客户列表（已选 ${selectedCustomerIds.size}/${candidates.size}）", style = MaterialTheme.typography.titleMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = {
                                        selectedCustomerIds = candidates.mapNotNull { it.id }.toSet()
                                    }) { Text("全选") }
                                    TextButton(onClick = { selectedCustomerIds = emptySet() }) { Text("清空") }
                                }
                            }
                        }
                    }
                }

                if (candidates.isEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text("当前状态下没有客户可创建任务")
                            }
                        }
                    }
                } else {
                    items(candidates, key = { it.taskCustomerId }) { customer ->
                        val customerId = customer.id ?: return@items
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedCustomerIds.contains(customerId)) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedCustomerIds.contains(customerId),
                                    onCheckedChange = {
                                        selectedCustomerIds = if (selectedCustomerIds.contains(customerId)) {
                                            selectedCustomerIds - customerId
                                        } else {
                                            selectedCustomerIds + customerId
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(customer.name ?: "未知客户", style = MaterialTheme.typography.titleSmall)
                                    Text(customer.phone ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (!customer.tag.isBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.small) {
                                            Text(customer.tag, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskCustomerCard(
    customer: TaskCustomer,
    selectedTab: Int,
    onCall: () -> Unit,
    onUpdateStatus: (String, String?) -> Unit,
    onEditCustomer: (String?, String?, String?) -> Unit,
    onDeleteCustomer: () -> Unit
) {
    val context = LocalContext.current
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRecordingDialog by remember { mutableStateOf(false) }
    var recordings by remember { mutableStateOf<List<File>>(emptyList()) }
    var isLoadingRecordings by remember { mutableStateOf(false) }
    var currentPlayingPath by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    // 异步加载录音列表（避免主线程阻塞）
    LaunchedEffect(customer.phone, customer.calledAt, customer.callTime, customer.callResult) {
        isLoadingRecordings = true
        recordings = withContext(Dispatchers.IO) {
            CallRecordingManager.listRecordingsForPhone(context, customer.phone)
        }
        isLoadingRecordings = false
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { mediaPlayer?.stop() }
            runCatching { mediaPlayer?.release() }
            mediaPlayer = null
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = customer.name ?: "未知客户",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = customer.phone ?: "无号码",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (customer.tag.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = customer.tag,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
                // 任务执行页统一展示为：待拨打 / 真人已接通 / 响铃未接通（不展示历史细分状态）
                val displayStatus = normalizeTaskCallStatusForDisplay(customer.callStatus, customer.callResult)
                CustomerStatusChip(status = displayStatus)
            }

            if (recordings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = {
                        // 异步刷新录音列表
                        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                            val newRecordings = CallRecordingManager.listRecordingsForPhone(context, customer.phone)
                            withContext(Dispatchers.Main) {
                                recordings = newRecordings
                                showRecordingDialog = true
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("录音(${recordings.size})")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 操作按钮：拨打、编辑、删除（紧凑样式，均分宽度）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 拨打电话按钮
                Button(
                    onClick = onCall,
                    modifier = Modifier.weight(1f),
                    enabled = customer.callStatus == "pending" || customer.callStatus == "called",
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Phone, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("拨打", style = MaterialTheme.typography.labelMedium)
                }

                // 编辑按钮
                OutlinedButton(
                    onClick = { showEditDialog = true },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("编辑", style = MaterialTheme.typography.labelMedium)
                }

                // 删除按钮
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("删除", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }

    // 编辑客户信息对话框
    if (showEditDialog) {
        var editName by remember { mutableStateOf(customer.name ?: "") }
        var editPhone by remember { mutableStateOf(customer.phone ?: "") }
        var editCompany by remember { mutableStateOf(customer.company ?: "") }
        val originalDisplayStatus = remember(customer.callStatus, customer.callResult) {
            normalizeTaskCallStatusForDisplay(customer.callStatus, customer.callResult)
        }
        // 通话状态选择：支持将状态改回待拨打
        var selectedCallStatus by remember { mutableStateOf(originalDisplayStatus) }

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

                    // 通话状态选择
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "通话状态",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 三个状态按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = selectedCallStatus == "pending",
                            onClick = { selectedCallStatus = "pending" },
                            label = { Text("待拨打", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = if (selectedCallStatus == "pending") {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedCallStatus == "connected",
                            onClick = { selectedCallStatus = "connected" },
                            label = { Text("真人已接通", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = if (selectedCallStatus == "connected") {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedCallStatus == "unanswered",
                            onClick = { selectedCallStatus = "unanswered" },
                            label = { Text("响铃未接通", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = if (selectedCallStatus == "unanswered") {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // 更新客户信息
                        onEditCustomer(
                            editName.takeIf { it != customer.name },
                            editPhone.takeIf { it != customer.phone },
                            editCompany.takeIf { it != customer.company }
                        )
                        // 更新通话状态
                        if (selectedCallStatus != originalDisplayStatus) {
                            val callResult = when (selectedCallStatus) {
                                "connected" -> "真人已接通"
                                "unanswered" -> "响铃未接通"
                                else -> null
                            }
                            onUpdateStatus(selectedCallStatus, callResult)
                        }
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
            text = { Text("确定要从任务中删除客户「${customer.name ?: "未知客户"}」吗？此操作不可恢复。") },
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

    if (showRecordingDialog) {
        ModalBottomSheet(
            onDismissRequest = {
                showRecordingDialog = false
                runCatching { mediaPlayer?.stop() }
                runCatching { mediaPlayer?.release() }
                mediaPlayer = null
                currentPlayingPath = null
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "通话录音",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "共 ${recordings.size} 条录音，可用于复盘本次客户沟通细节",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (recordings.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无录音文件")
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        recordings.forEachIndexed { index, file ->
                            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (currentPlayingPath == file.absolutePath && mediaPlayer?.isPlaying == true) {
                                                runCatching { mediaPlayer?.pause() }
                                                currentPlayingPath = null
                                            } else {
                                                runCatching { mediaPlayer?.stop() }
                                                runCatching { mediaPlayer?.release() }
                                                mediaPlayer = null
                                                try {
                                                    val player = MediaPlayer().apply {
                                                        setDataSource(context, Uri.fromFile(file))
                                                        setOnPreparedListener {
                                                            // 异步准备完成后开始播放
                                                            start()
                                                            currentPlayingPath = file.absolutePath
                                                        }
                                                        setOnCompletionListener {
                                                            currentPlayingPath = null
                                                            runCatching { it.release() }
                                                            mediaPlayer = null
                                                        }
                                                        prepareAsync()  // 使用异步准备，避免阻塞主线程
                                                    }
                                                    mediaPlayer = player
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "播放录音失败: ${e.message}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (currentPlayingPath == file.absolutePath && mediaPlayer?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = "播放录音"
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (index == 0) "最新录音 · ${formatRecordingDisplayName(file)}" else formatRecordingDisplayName(file),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "${formatFileSize(file.length())} · ${file.name}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = {
                        showRecordingDialog = false
                        runCatching { mediaPlayer?.stop() }
                        runCatching { mediaPlayer?.release() }
                        mediaPlayer = null
                        currentPlayingPath = null
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("关闭")
                }
            }
        }
    }
}

private fun formatRecordingDisplayName(file: File): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(file.lastModified()))
}

private fun formatFileSize(size: Long): String {
    val kb = size / 1024.0
    return if (kb < 1024) {
        String.format(Locale.getDefault(), "%.0f KB", kb)
    } else {
        String.format(Locale.getDefault(), "%.2f MB", kb / 1024.0)
    }
}

@Composable
private fun CustomerStatusChip(status: String) {
    val (color, text) = when (status) {
        "pending" -> MaterialTheme.colorScheme.outline to "待拨打"
        "connected", "completed" -> MaterialTheme.colorScheme.primary to "真人已接通"
        // 除 pending / connected 外的全部，统一归为响铃未接通（兼容历史 voicemail/failed/...）
        else -> MaterialTheme.colorScheme.error to "响铃未接通"
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
private fun TaskStatusChip(task: Task) {
    // 根据实际拨打进度计算状态
    val (color, text) = when {
        task.customerCount > 0 && task.calledCount >= task.customerCount ->
            MaterialTheme.colorScheme.primary to "已完成"
        task.calledCount > 0 ->
            MaterialTheme.colorScheme.tertiary to "进行中"
        else ->
            MaterialTheme.colorScheme.outline to "待处理"
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
 * 在顶部固定显示当前客户信息
 */
@Composable
private fun TaskAutoDialCustomerPanel(
    currentCustomer: com.callcenter.app.data.model.Customer?,
    dialedCount: Int,
    totalCount: Int,
    isAutoMode: Boolean,
    onStop: () -> Unit,
    onCallStatusMarked: ((String) -> Unit)? = null,
    callStateHistory: List<com.callcenter.app.service.CallStateHistoryItem> = emptyList()
) {
    // 当前选中的通话状态
    var selectedCallStatus by remember { mutableStateOf<String?>(null) }
    // 正在更新中的状态（用于显示loading）
    var updatingStatus by remember { mutableStateOf<String?>(null) }
    // 本地维护自动模式状态，用于Switch显示
    var localAutoMode by remember { mutableStateOf(isAutoMode) }
    // 是否显示通话状态历史记录
    var showHistory by remember { mutableStateOf(false) }
    
    // 同步外部状态到本地状态
    LaunchedEffect(isAutoMode) {
        localAutoMode = isAutoMode
    }

    // 监听客户变化，重置状态
    LaunchedEffect(currentCustomer?.id) {
        selectedCallStatus = null
        updatingStatus = null
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isAutoMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // 标题栏：模式标识、Switch切换和停止按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = if (localAutoMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // 标题行：模式文案 + Switch
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (localAutoMode) "自动拨号" else "手动拨号",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (localAutoMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        // 自动/手动切换开关
                        Switch(
                            checked = localAutoMode,
                            onCheckedChange = { checked ->
                                localAutoMode = checked
                            },
                            modifier = Modifier.scale(0.7f)
                        )
                    }
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
                    Text("停止拨号", style = MaterialTheme.typography.labelSmall)
                }
            }

            // 当前客户信息（如果有）
            if (currentCustomer != null) {
                Spacer(modifier = Modifier.height(8.dp))

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
                                text = (currentCustomer.name?.firstOrNull()?.toString() ?: "?"),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // 当前客户信息
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentCustomer.name ?: "未命名",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentCustomer.phone ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (currentCustomer.tag.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = currentCustomer.tag,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
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
                }

                // 通话状态历史记录
                if (callStateHistory.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = { showHistory = !showHistory }
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("通话记录(${callStateHistory.size})")
                    }
                }

                // 通话状态历史记录列表
                if (showHistory && callStateHistory.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Text(
                                text = "通话状态记录",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            callStateHistory.takeLast(5).forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${item.getFormattedTime()} ${item.customerName ?: ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = item.state,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                // 通话状态标记按钮（已隐藏，完全依靠程序自动识别打标）
                // 自动识别逻辑在 AutoDialService.setupCallStateListener() 中实现
                /*
                if (onCallStatusMarked != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "标记通话结果：",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    // 第一行：待拨打、真人已接通、响铃未接通
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TaskStatusButton(
                            text = "待拨打",
                            color = Color(0xFF9E9E9E),
                            isSelected = selectedCallStatus == "pending",
                            isLoading = updatingStatus == "pending",
                            onClick = {
                                selectedCallStatus = "pending"
                                updatingStatus = "pending"
                                onCallStatusMarked("pending")
                                updatingStatus = null
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TaskStatusButton(
                            text = "真人已接通",
                            color = Color(0xFF4CAF50),
                            isSelected = selectedCallStatus == "connected",
                            isLoading = updatingStatus == "connected",
                            onClick = {
                                selectedCallStatus = "connected"
                                updatingStatus = "connected"
                                onCallStatusMarked("connected")
                                updatingStatus = null
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TaskStatusButton(
                            text = "响铃未接通",
                            color = Color(0xFFFF9800),
                            isSelected = selectedCallStatus == "unanswered",
                            isLoading = updatingStatus == "unanswered",
                            onClick = {
                                selectedCallStatus = "unanswered"
                                updatingStatus = "unanswered"
                                onCallStatusMarked("unanswered")
                                updatingStatus = null
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                */
            } else {
                // 等待状态
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "准备拨打下一个客户...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 任务执行页通话状态按钮组件
 */
@Composable
private fun TaskStatusButton(
    text: String,
    color: Color,
    isSelected: Boolean,
    isLoading: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        enabled = !isLoading,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) color else color.copy(alpha = 0.3f),
            contentColor = if (isSelected) Color.White else color,
            disabledContainerColor = color.copy(alpha = 0.2f),
            disabledContentColor = color.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(horizontal = 6.dp),
        border = if (isSelected) {
            BorderStroke(
                width = 2.dp,
                color = Color.White
            )
        } else null
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = if (isSelected) Color.White else color
                    )
                }
                isSelected -> {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                }
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}
