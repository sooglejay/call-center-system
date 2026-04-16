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

    // 当自动拨号状态变化时，刷新任务详情
    LaunchedEffect(autoDialRunning, dialedCount) {
        if (autoDialRunning) {
            viewModel.loadTaskDetail(taskId)
        }
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

    // 当自动拨号的已拨打数量变化时，刷新任务详情以更新客户列表
    LaunchedEffect(dialedCount) {
        if (dialedCount > 0) {
            viewModel.loadTaskDetail(taskId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(VersionInfoUtil.getTitleWithVersion(context, "任务执行")) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
                            action = when (status) {
                                "connected" -> com.callcenter.app.service.AutoDialService.ACTION_CALL_STATUS_CONNECTED
                                "voicemail" -> com.callcenter.app.service.AutoDialService.ACTION_CALL_STATUS_VOICEMAIL
                                "unanswered" -> com.callcenter.app.service.AutoDialService.ACTION_CALL_STATUS_UNANSWERED
                                "rejected" -> com.callcenter.app.service.AutoDialService.ACTION_CALL_STATUS_REJECTED
                                "busy" -> com.callcenter.app.service.AutoDialService.ACTION_CALL_STATUS_BUSY
                                "power_off" -> com.callcenter.app.service.AutoDialService.ACTION_CALL_STATUS_POWER_OFF
                                "no_answer" -> com.callcenter.app.service.AutoDialService.ACTION_CALL_STATUS_NO_ANSWER
                                "ivr" -> com.callcenter.app.service.AutoDialService.ACTION_CALL_STATUS_IVR
                                "other" -> com.callcenter.app.service.AutoDialService.ACTION_CALL_STATUS_OTHER
                                else -> null
                            }
                        }
                        if (intent.action != null) {
                            context.startService(intent)
                        }

                        // 更新本地客户状态
                        val dialCustomer = currentDialCustomer
                        if (dialCustomer != null) {
                            val callResult = when (status) {
                                "connected" -> "已接听"
                                "voicemail" -> "语音信箱"
                                "unanswered" -> "响铃未接"
                                "rejected" -> "对方拒接"
                                "busy" -> "对方忙线"
                                "power_off" -> "关机/停机"
                                "no_answer" -> "无人接听"
                                "ivr" -> "IVR语音"
                                "other" -> "其他"
                                else -> null
                            }
                            if (callResult != null) {
                                viewModel.updateCustomerStatus(
                                    taskId = taskId,
                                    customerId = dialCustomer.id,
                                    status = "called",
                                    callResult = callResult
                                )
                            }
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
                    action = when (status) {
                        "connected" -> com.callcenter.app.service.AutoDialService.ACTION_CALL_STATUS_CONNECTED
                        "voicemail" -> com.callcenter.app.service.AutoDialService.ACTION_CALL_STATUS_VOICEMAIL
                        "unanswered" -> com.callcenter.app.service.AutoDialService.ACTION_CALL_STATUS_UNANSWERED
                        "rejected" -> com.callcenter.app.service.AutoDialService.ACTION_CALL_STATUS_REJECTED
                        "busy" -> com.callcenter.app.service.AutoDialService.ACTION_CALL_STATUS_BUSY
                        "power_off" -> com.callcenter.app.service.AutoDialService.ACTION_CALL_STATUS_POWER_OFF
                        "no_answer" -> com.callcenter.app.service.AutoDialService.ACTION_CALL_STATUS_NO_ANSWER
                        "ivr" -> com.callcenter.app.service.AutoDialService.ACTION_CALL_STATUS_IVR
                        "other" -> com.callcenter.app.service.AutoDialService.ACTION_CALL_STATUS_OTHER
                        else -> {
                            onComplete?.invoke()
                            return@AutoFloatingWindow
                        }
                    }
                }
                context.startService(intent)
                
                // 2. 更新本地客户状态（立即刷新UI）
                if (currentCustomer != null) {
                    val callResult = when (status) {
                        "connected" -> "已接听"
                        "voicemail" -> "语音信箱"
                        "unanswered" -> "响铃未接"
                        "rejected" -> "对方拒接"
                        "busy" -> "对方忙线"
                        "power_off" -> "关机/停机"
                        "no_answer" -> "无人接听"
                        "ivr" -> "IVR语音"
                        "other" -> "其他"
                        else -> null
                    }
                    if (callResult != null) {
                        viewModel.updateCustomerStatus(
                            taskId = taskId,
                            customerId = currentCustomer.id,
                            status = "called",
                            callResult = callResult
                        )
                    }
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

    // 按通话状态分组（与标记通话状态的9个选项对齐）
    val pendingCustomers = customers.filter { it.callStatus == "pending" }
    val connectedCustomers = customers.filter { it.callStatus == "connected" || it.callResult == "已接听" }
    val voicemailCustomers = customers.filter { it.callStatus == "voicemail" || it.callResult == "语音信箱" }
    val unansweredCustomers = customers.filter { it.callStatus == "unanswered" || it.callResult == "响铃未接" }
    val rejectedCustomers = customers.filter { it.callStatus == "rejected" || it.callResult == "对方拒接" }
    val busyCustomers = customers.filter { it.callStatus == "busy" || it.callResult == "对方忙线" }
    val powerOffCustomers = customers.filter { it.callStatus == "power_off" || it.callResult == "关机/停机" }
    val noAnswerCustomers = customers.filter { it.callStatus == "no_answer" || it.callResult == "无人接听" }
    val ivrCustomers = customers.filter { it.callStatus == "ivr" || it.callResult == "IVR语音" }
    val otherCalledCustomers = customers.filter {
        (it.callStatus == "called" || it.callStatus == "completed") &&
        it.callResult != "已接听" &&
        it.callResult != "语音信箱" &&
        it.callResult != "响铃未接" &&
        it.callResult != "对方拒接" &&
        it.callResult != "对方忙线" &&
        it.callResult != "关机/停机" &&
        it.callResult != "无人接听" &&
        it.callResult != "IVR语音"
    }.sortedByDescending { it.calledAt ?: it.callTime ?: "" }

    // 根据选中的Tab过滤客户列表
    val displayedCustomers = when (selectedTab) {
        0 -> customers // 全部
        1 -> pendingCustomers // 待拨打
        2 -> connectedCustomers // 已接听
        3 -> voicemailCustomers // 语音信箱
        4 -> unansweredCustomers // 响铃未接
        5 -> rejectedCustomers // 拒接
        6 -> busyCustomers // 忙线
        7 -> powerOffCustomers // 关机/停机
        8 -> noAnswerCustomers // 无人接听
        9 -> ivrCustomers // IVR语音
        10 -> otherCalledCustomers // 其他
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
                    voicemailCount = voicemailCustomers.size,
                    unansweredCount = unansweredCustomers.size,
                    rejectedCount = rejectedCustomers.size,
                    busyCount = busyCustomers.size,
                    powerOffCount = powerOffCustomers.size,
                    noAnswerCount = noAnswerCustomers.size,
                    ivrCount = ivrCustomers.size,
                    otherCount = otherCalledCustomers.size,
                    selectedTab = selectedTab,
                    onTabSelected = onTabSelected
                )
            }

            // 显示当前分组标题
            item {
                val title = when (selectedTab) {
                    0 -> "全部客户"
                    1 -> "待拨打"
                    2 -> "已接听"
                    3 -> "语音信箱"
                    4 -> "响铃未接"
                    5 -> "拒接"
                    6 -> "忙线"
                    7 -> "关机/停机"
                    8 -> "无人接听"
                    9 -> "IVR语音"
                    10 -> "其他"
                    else -> "全部客户"
                }
                val count = displayedCustomers.size
                val color = when (selectedTab) {
                    0 -> MaterialTheme.colorScheme.primary
                    1 -> MaterialTheme.colorScheme.primary
                    2 -> MaterialTheme.colorScheme.tertiary
                    3 -> MaterialTheme.colorScheme.secondary
                    4 -> MaterialTheme.colorScheme.error
                    5 -> MaterialTheme.colorScheme.outline
                    6 -> MaterialTheme.colorScheme.surfaceVariant
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
 * 支持切换显示全部/待拨打/已接听/语音信箱/响铃未接/拒接/忙线/关机停机/无人接听/IVR语音/其他
 */
@Composable
private fun CustomerFilterTabsWithCallStatus(
    totalCount: Int,
    pendingCount: Int,
    connectedCount: Int,
    voicemailCount: Int,
    unansweredCount: Int,
    rejectedCount: Int,
    busyCount: Int,
    powerOffCount: Int,
    noAnswerCount: Int,
    ivrCount: Int,
    otherCount: Int,
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
            // 第一行Tab: 全部 | 待拨打 | 已接听 | 语音信箱
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
                
                Divider(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                
                FilterTabItem(
                    label = "已接听",
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
                    label = "语音信箱",
                    count = voicemailCount,
                    isSelected = selectedTab == 3,
                    onClick = { onTabSelected(3) }
                )
            }
            
            // 第二行Tab: 响铃未接 | 拒接 | 忙线 | 关机/停机
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FilterTabItem(
                    label = "响铃未接",
                    count = unansweredCount,
                    isSelected = selectedTab == 4,
                    onClick = { onTabSelected(4) }
                )

                Divider(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                FilterTabItem(
                    label = "拒接",
                    count = rejectedCount,
                    isSelected = selectedTab == 5,
                    onClick = { onTabSelected(5) }
                )

                Divider(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                FilterTabItem(
                    label = "忙线",
                    count = busyCount,
                    isSelected = selectedTab == 6,
                    onClick = { onTabSelected(6) }
                )

                Divider(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                FilterTabItem(
                    label = "关机/停机",
                    count = powerOffCount,
                    isSelected = selectedTab == 7,
                    onClick = { onTabSelected(7) }
                )
            }

            // 第三行Tab: 无人接听 | IVR语音 | 其他
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FilterTabItem(
                    label = "无人接听",
                    count = noAnswerCount,
                    isSelected = selectedTab == 8,
                    onClick = { onTabSelected(8) }
                )

                Divider(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                FilterTabItem(
                    label = "IVR语音",
                    count = ivrCount,
                    isSelected = selectedTab == 9,
                    onClick = { onTabSelected(9) }
                )

                Divider(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                FilterTabItem(
                    label = "其他",
                    count = otherCount,
                    isSelected = selectedTab == 10,
                    onClick = { onTabSelected(10) }
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
        2 -> RetryTaskStatusMeta("connected", "已接听")
        3 -> RetryTaskStatusMeta("voicemail", "语音信箱")
        4 -> RetryTaskStatusMeta("unanswered", "响铃未接")
        5 -> RetryTaskStatusMeta("rejected", "拒接")
        6 -> RetryTaskStatusMeta("busy", "忙线")
        7 -> RetryTaskStatusMeta("power_off", "关机/停机")
        8 -> RetryTaskStatusMeta("no_answer", "无人接听")
        9 -> RetryTaskStatusMeta("ivr", "IVR语音")
        10 -> RetryTaskStatusMeta("other", "其他")
        else -> RetryTaskStatusMeta("all", "全部客户")
    }
}

private fun getStatusKeyByTab(tab: Int): String = getStatusMetaByTab(tab).key

private fun filterTaskCustomersByStatus(customers: List<TaskCustomer>, statusKey: String): List<TaskCustomer> {
    return when (statusKey) {
        "pending" -> customers.filter { it.callStatus == "pending" }
        "connected" -> customers.filter { it.callStatus == "connected" || it.callResult == "已接听" }
        "voicemail" -> customers.filter { it.callStatus == "voicemail" || it.callResult == "语音信箱" }
        "unanswered" -> customers.filter { it.callStatus == "unanswered" || it.callResult == "响铃未接" }
        "rejected" -> customers.filter { it.callStatus == "rejected" || it.callResult == "对方拒接" }
        "busy" -> customers.filter { it.callStatus == "busy" || it.callResult == "对方忙线" }
        "power_off" -> customers.filter { it.callStatus == "power_off" || it.callResult == "关机/停机" || it.callResult == "对方关机" }
        "no_answer" -> customers.filter { it.callStatus == "no_answer" || it.callResult == "无人接听" }
        "ivr" -> customers.filter { it.callStatus == "ivr" || it.callResult == "IVR语音" }
        "other" -> customers.filter {
            (it.callStatus == "called" || it.callStatus == "completed") &&
                it.callResult != "已接听" &&
                it.callResult != "语音信箱" &&
                it.callResult != "响铃未接" &&
                it.callResult != "对方拒接" &&
                it.callResult != "对方忙线" &&
                it.callResult != "关机/停机" &&
                it.callResult != "对方关机" &&
                it.callResult != "无人接听" &&
                it.callResult != "IVR语音"
        }
        else -> customers
    }
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
    val statusMeta = remember(statusKey) { getStatusMetaByTab((1..10).firstOrNull { getStatusKeyByTab(it) == statusKey } ?: 0) }

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
                    IconButton(onClick = onNavigateBack) {
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
                        onClick = onNavigateBack,
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
    var showResultDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRecordingDialog by remember { mutableStateOf(false) }
    var callResult by remember { mutableStateOf(customer.callResult ?: "") }
    var recordings by remember(customer.phone, customer.calledAt, customer.callTime, customer.callResult) {
        mutableStateOf(CallRecordingManager.listRecordingsForPhone(context, customer.phone))
    }
    var currentPlayingPath by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

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
                    if (!customer.company.isNullOrBlank()) {
                        Text(
                            text = customer.company,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // 客户列表中显示具体的通话结果，否则显示状态标签
                if (!customer.callResult.isNullOrBlank()) {
                    // 显示具体的通话结果
                    val (resultColor, resultText) = when (customer.callResult) {
                        "已接听" -> MaterialTheme.colorScheme.primary to "已接听"
                        "通话完成" -> MaterialTheme.colorScheme.primary to "通话完成"
                        "语音信箱" -> MaterialTheme.colorScheme.secondary to "语音信箱"
                        "响铃未接" -> MaterialTheme.colorScheme.error to "响铃未接"
                        "对方拒接" -> MaterialTheme.colorScheme.error to "对方拒接"
                        "对方忙线" -> MaterialTheme.colorScheme.tertiary to "对方忙线"
                        "对方关机" -> MaterialTheme.colorScheme.outline to "对方关机"
                        "关机/停机" -> MaterialTheme.colorScheme.outline to "关机/停机"
                        "无人接听" -> MaterialTheme.colorScheme.outline to "无人接听"
                        "IVR语音" -> MaterialTheme.colorScheme.secondary to "IVR语音"
                        "拨打失败" -> MaterialTheme.colorScheme.error to "拨打失败"
                        else -> MaterialTheme.colorScheme.outline to customer.callResult
                    }
                    Surface(
                        color = resultColor.copy(alpha = 0.1f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = resultText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = resultColor
                        )
                    }
                } else {
                    CustomerStatusChip(status = customer.callStatus)
                }
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

            if (recordings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = {
                        recordings = CallRecordingManager.listRecordingsForPhone(context, customer.phone)
                        showRecordingDialog = true
                    }
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("录音(${recordings.size})")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 操作按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 拨打电话按钮 - 待拨打和已拨打状态都启用，允许重复拨打
                Button(
                    onClick = onCall,
                    modifier = Modifier.weight(1f),
                    enabled = customer.callStatus == "pending" || customer.callStatus == "called"
                ) {
                    Icon(Icons.Default.Phone, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("拨打")
                }

                // 标记完成按钮 - 所有状态都显示，让用户手动标记完成
                OutlinedButton(
                    onClick = { showResultDialog = true },
                    modifier = Modifier.weight(1f),
                    enabled = customer.callStatus != "completed"
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (customer.callStatus == "completed") "已完成" else "完成")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 编辑和删除按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
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
        var editName by remember { mutableStateOf(customer.name ?: "") }
        var editPhone by remember { mutableStateOf(customer.phone ?: "") }
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
                                                        prepare()
                                                        setOnCompletionListener {
                                                            currentPlayingPath = null
                                                            runCatching { it.release() }
                                                            mediaPlayer = null
                                                        }
                                                        start()
                                                    }
                                                    mediaPlayer = player
                                                    currentPlayingPath = file.absolutePath
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
                    // 第一行：已接听、语音信箱、响铃未接
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TaskStatusButton(
                            text = "已接听",
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
                            text = "语音信箱",
                            color = Color(0xFF2196F3),
                            isSelected = selectedCallStatus == "voicemail",
                            isLoading = updatingStatus == "voicemail",
                            onClick = {
                                selectedCallStatus = "voicemail"
                                updatingStatus = "voicemail"
                                onCallStatusMarked("voicemail")
                                updatingStatus = null
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TaskStatusButton(
                            text = "响铃未接",
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

                    // 第二行：拒接、忙线、关机/停机
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TaskStatusButton(
                            text = "拒接",
                            color = Color(0xFFE91E63),
                            isSelected = selectedCallStatus == "rejected",
                            isLoading = updatingStatus == "rejected",
                            onClick = {
                                selectedCallStatus = "rejected"
                                updatingStatus = "rejected"
                                onCallStatusMarked("rejected")
                                updatingStatus = null
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TaskStatusButton(
                            text = "忙线",
                            color = Color(0xFFFF9800),
                            isSelected = selectedCallStatus == "busy",
                            isLoading = updatingStatus == "busy",
                            onClick = {
                                selectedCallStatus = "busy"
                                updatingStatus = "busy"
                                onCallStatusMarked("busy")
                                updatingStatus = null
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TaskStatusButton(
                            text = "关机/停机",
                            color = Color(0xFF607D8B),
                            isSelected = selectedCallStatus == "power_off",
                            isLoading = updatingStatus == "power_off",
                            onClick = {
                                selectedCallStatus = "power_off"
                                updatingStatus = "power_off"
                                onCallStatusMarked("power_off")
                                updatingStatus = null
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // 第三行：无人接听、IVR语音、其他
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TaskStatusButton(
                            text = "无人接听",
                            color = Color(0xFFFF9800),
                            isSelected = selectedCallStatus == "no_answer",
                            isLoading = updatingStatus == "no_answer",
                            onClick = {
                                selectedCallStatus = "no_answer"
                                updatingStatus = "no_answer"
                                onCallStatusMarked("no_answer")
                                updatingStatus = null
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TaskStatusButton(
                            text = "IVR语音",
                            color = Color(0xFF00BCD4),
                            isSelected = selectedCallStatus == "ivr",
                            isLoading = updatingStatus == "ivr",
                            onClick = {
                                selectedCallStatus = "ivr"
                                updatingStatus = "ivr"
                                onCallStatusMarked("ivr")
                                updatingStatus = null
                            },
                            modifier = Modifier.weight(1f)
                        )
                        TaskStatusButton(
                            text = "其他",
                            color = Color(0xFF9E9E9E),
                            isSelected = selectedCallStatus == "other",
                            isLoading = updatingStatus == "other",
                            onClick = {
                                selectedCallStatus = "other"
                                updatingStatus = "other"
                                onCallStatusMarked("other")
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
