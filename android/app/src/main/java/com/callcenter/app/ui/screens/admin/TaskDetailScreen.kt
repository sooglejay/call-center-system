package com.callcenter.app.ui.screens.admin

import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.callcenter.app.data.model.CreateTaskRequest
import com.callcenter.app.data.model.Task
import com.callcenter.app.data.model.TaskCustomer
import com.callcenter.app.data.model.User
import com.callcenter.app.util.VersionInfoUtil
import com.callcenter.app.ui.viewmodel.TaskDetailViewModel
import com.callcenter.app.ui.viewmodel.CreateTaskViewModel

/**
 * 任务详情页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    taskId: Int,
    onNavigateBack: () -> Unit,
    viewModel: TaskDetailViewModel = hiltViewModel(),
    createTaskViewModel: CreateTaskViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val task by viewModel.task.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    // 客服列表
    val agents by createTaskViewModel.agents.collectAsState()
    val isCreating by createTaskViewModel.isCreating.collectAsState()

    // 筛选Tab状态
    var selectedTab by rememberSaveable { mutableStateOf(0) }

    // 创建任务对话框状态
    var showCreateTaskDialog by remember { mutableStateOf(false) }

    LaunchedEffect(taskId) {
        viewModel.loadTaskDetail(taskId)
        createTaskViewModel.loadData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(VersionInfoUtil.getTitleWithVersion(context, "任务详情")) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            if (task != null && selectedTab > 0) {
                ExtendedFloatingActionButton(
                    onClick = { showCreateTaskDialog = true },
                    icon = { Icon(Icons.Default.AddTask, contentDescription = null) },
                    text = { Text("创建重拨任务") },
                    containerColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(error!!, color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.loadTaskDetail(taskId) }) {
                                Text("重试")
                            }
                        }
                    }
                }
                task != null -> {
                    TaskDetailContent(
                        task = task!!,
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    // 创建重拨任务对话框
    if (showCreateTaskDialog && task != null) {
        CreateRetryTaskDialog(
            task = task!!,
            selectedTab = selectedTab,
            agents = agents,
            isCreating = isCreating,
            onDismiss = { showCreateTaskDialog = false },
            onCreate = { title, description, priority, agentId, customerIds ->
                createTaskViewModel.createTask(
                    request = CreateTaskRequest(
                        title = title,
                        description = description,
                        priority = priority,
                        assignedTo = agentId,
                        customerIds = customerIds
                    ),
                    onSuccess = {
                        Toast.makeText(context, "任务创建成功", Toast.LENGTH_SHORT).show()
                        showCreateTaskDialog = false
                        viewModel.loadTaskDetail(taskId)
                    }
                )
            }
        )
    }
}

@Composable
private fun TaskDetailContent(
    task: Task,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val customers = task.customers ?: emptyList()

    // 按通话状态分组（与客服视角对齐）
    val pendingCustomers = customers.filter { it.callStatus == "pending" }
    val connectedCustomers = customers.filter { it.callStatus == "connected" || it.callResult == "已接听" }
    val voicemailCustomers = customers.filter { it.callStatus == "voicemail" || it.callResult == "语音信箱" }
    val unansweredCustomers = customers.filter { it.callStatus == "unanswered" || it.callResult == "响铃未接" }
    val rejectedCustomers = customers.filter { it.callStatus == "rejected" || it.callResult == "对方拒接" }
    val busyCustomers = customers.filter { it.callStatus == "busy" || it.callResult == "对方忙线" }
    val powerOffCustomers = customers.filter { it.callStatus == "power_off" || it.callResult == "关机/停机" || it.callResult == "对方关机" }
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
        it.callResult != "对方关机" &&
        it.callResult != "无人接听" &&
        it.callResult != "IVR语音"
    }

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

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 任务标题和状态
        item {
            TaskHeader(task = task)
        }

        // 任务信息卡片
        item {
            TaskInfoCard(task = task)
        }

        // 统计概览
        item {
            TaskStatsRow(task = task)
        }

        // 筛选Tab
        item {
            CustomerFilterTabs(
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

        // 客户列表标题
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
            Text(
                text = "$title (${displayedCustomers.size}人)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // 客户列表
        if (displayedCustomers.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.People,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "暂无客户",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(displayedCustomers, key = { it.taskCustomerId }) { customer ->
                TaskCustomerItem(customer = customer)
            }
        }
    }
}

@Composable
private fun TaskHeader(task: Task) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TaskStatusChip(task = task)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 优先级标签
        TaskPriorityChip(priority = task.priority)
    }
}

@Composable
private fun TaskInfoCard(task: Task) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 分配客服
            task.assignedAgent?.let { agent ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = agent.realName.take(1),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = agent.realName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "负责客服",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 截止日期
            task.dueDate?.let { dueDate ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "截止日期: $dueDate",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 任务描述
            if (!task.description.isNullOrBlank()) {
                Text(
                    text = "任务描述",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = task.description,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun TaskStatsRow(task: Task) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            label = "客户总数",
            value = task.customerCount.toString(),
            icon = Icons.Default.People,
            color = Color(0xFF2196F3),
            modifier = Modifier.weight(1f)
        )
        StatCard(
            label = "已拨打",
            value = task.calledCount.toString(),
            icon = Icons.Default.Phone,
            color = Color(0xFFFF9800),
            modifier = Modifier.weight(1f)
        )
        StatCard(
            label = "已完成",
            value = task.completedCount.toString(),
            icon = Icons.Default.CheckCircle,
            color = Color(0xFF4CAF50),
            modifier = Modifier.weight(1f)
        )
        StatCard(
            label = "完成率",
            value = "${task.progress}%",
            icon = Icons.Default.TrendingUp,
            color = Color(0xFF9C27B0),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskCustomerItem(customer: TaskCustomer) {
    val context = LocalContext.current
    var showRecordingSheet by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { mediaPlayer?.stop() }
            runCatching { mediaPlayer?.release() }
            mediaPlayer = null
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = customer.name ?: "未知客户",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Phone,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = customer.phone ?: "无号码",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (customer.tag.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = customer.tag,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                    customer.company?.let { company ->
                        Text(
                            text = company,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                CallStatusChip(status = customer.callStatus)
            }

            // 通话信息
            if (customer.callStatus != "pending") {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    customer.callDuration?.let { duration ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${duration}秒",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    customer.calledAt?.let { calledAt ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = calledAt.substring(5, 16).replace("T", " "),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                customer.callResult?.let { result ->
                    if (result.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "备注: $result",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!customer.recordingUrl.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = {
                            showRecordingSheet = true
                        }
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("播放录音")
                    }
                }
            }
        }
    }

    if (showRecordingSheet && !customer.recordingUrl.isNullOrBlank()) {
        ModalBottomSheet(
            onDismissRequest = {
                showRecordingSheet = false
                runCatching { mediaPlayer?.stop() }
                runCatching { mediaPlayer?.release() }
                mediaPlayer = null
                isPlaying = false
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
                    text = "${customer.name ?: "未知客户"} · ${customer.phone ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "录音文件",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            IconButton(
                                onClick = {
                                    try {
                                        if (isPlaying) {
                                            runCatching { mediaPlayer?.pause() }
                                            isPlaying = false
                                        } else {
                                            if (mediaPlayer == null) {
                                                val player = MediaPlayer().apply {
                                                    setDataSource(context, Uri.parse(customer.recordingUrl))
                                                    prepare()
                                                    setOnCompletionListener {
                                                        isPlaying = false
                                                        runCatching { it.release() }
                                                        mediaPlayer = null
                                                    }
                                                }
                                                mediaPlayer = player
                                            }
                                            mediaPlayer?.start()
                                            isPlaying = true
                                        }
                                    } catch (e: Exception) {
                                        isPlaying = false
                                        runCatching { mediaPlayer?.release() }
                                        mediaPlayer = null
                                        android.widget.Toast.makeText(context, "播放失败: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "播放"
                                )
                            }
                        }

                        val metaLines = buildList {
                            customer.calledAt?.let { add("拨打时间: ${it.replace('T', ' ').take(16)}") }
                            customer.callDuration?.let { add("通话时长: ${it}秒") }
                            customer.callResult?.takeIf { it.isNotBlank() }?.let { add("通话结果: $it") }
                            customer.tag.takeIf { it.isNotBlank() }?.let { add("客户标签: $it") }
                        }

                        if (metaLines.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                metaLines.forEach { line ->
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Text(
                            text = customer.recordingUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = {
                        showRecordingSheet = false
                        runCatching { mediaPlayer?.stop() }
                        runCatching { mediaPlayer?.release() }
                        mediaPlayer = null
                        isPlaying = false
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("关闭")
                }
            }
        }
    }
}

@Composable
private fun TaskStatusChip(task: Task) {
    // 根据实际拨打进度计算状态
    val (color, text) = when {
        task.customerCount > 0 && task.calledCount >= task.customerCount ->
            Color(0xFF4CAF50) to "已完成"
        task.calledCount > 0 ->
            Color(0xFF2196F3) to "进行中"
        else ->
            Color(0xFFFF9800) to "待处理"
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.2f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun TaskPriorityChip(priority: String) {
    val (color, text) = when (priority) {
        "urgent" -> Color(0xFFE91E63) to "紧急"
        "high" -> Color(0xFFFF9800) to "高"
        "normal" -> Color(0xFF2196F3) to "普通"
        "low" -> Color(0xFF9E9E9E) to "低"
        else -> Color(0xFF2196F3) to "普通"
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.2f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun CallStatusChip(status: String) {
    val (color, text, icon) = when (status) {
        "pending" -> Triple(Color(0xFF9E9E9E), "待拨打", Icons.Default.Schedule)
        "called" -> Triple(Color(0xFFFF9800), "已拨打", Icons.Default.Phone)
        "connected" -> Triple(Color(0xFF4CAF50), "已接听", Icons.Default.CheckCircle)
        "completed" -> Triple(Color(0xFF4CAF50), "已完成", Icons.Default.CheckCircle)
        "failed" -> Triple(Color(0xFFE91E63), "未接通", Icons.Default.Close)
        "voicemail" -> Triple(Color(0xFF9C27B0), "语音信箱", Icons.Default.Voicemail)
        "unanswered" -> Triple(Color(0xFFFF5722), "响铃未接", Icons.Default.PhoneMissed)
        "rejected" -> Triple(Color(0xFFE91E63), "拒接", Icons.Default.Block)
        "busy" -> Triple(Color(0xFFFF9800), "忙线", Icons.Default.PhoneInTalk)
        "power_off" -> Triple(Color(0xFF607D8B), "关机/停机", Icons.Default.PhoneDisabled)
        "no_answer" -> Triple(Color(0xFFFF9800), "无人接听", Icons.Default.PhonePaused)
        "ivr" -> Triple(Color(0xFF00BCD4), "IVR语音", Icons.Default.RecordVoiceOver)
        else -> Triple(Color(0xFF9E9E9E), "待拨打", Icons.Default.Schedule)
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.2f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = color
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

/**
 * 客户筛选Tab组件
 */
@Composable
private fun CustomerFilterTabs(
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
        modifier = Modifier.fillMaxWidth()
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

                HorizontalDivider(
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

                HorizontalDivider(
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

                HorizontalDivider(
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

                HorizontalDivider(
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

                HorizontalDivider(
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

                HorizontalDivider(
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

                HorizontalDivider(
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

                HorizontalDivider(
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
        Color.Transparent
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
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor
            )
        }
    }
}

/**
 * 创建重拨任务对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateRetryTaskDialog(
    task: Task,
    selectedTab: Int,
    agents: List<User>,
    isCreating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String?, String, Int?, List<Int>) -> Unit
) {
    // 获取当前选中状态对应的客户列表
    val customers = task.customers ?: emptyList()
    val filteredCustomers = remember(customers, selectedTab) {
        filterCustomersByTab(customers, selectedTab)
    }

    // 获取状态标签
    val statusLabel = remember(selectedTab) {
        when (selectedTab) {
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
            else -> "全部"
        }
    }

    // 表单状态
    var title by remember { mutableStateOf("${statusLabel}重拨任务") }
    var description by remember { mutableStateOf("从「${statusLabel}」客户列表创建") }
    var priority by remember { mutableStateOf("normal") }
    var selectedAgent by remember { mutableStateOf<User?>(task.assignedAgent) }
    var showAgentDropdown by remember { mutableStateOf(false) }
    var showPriorityDropdown by remember { mutableStateOf(false) }

    // 客户选择状态
    var selectedCustomerIds by remember(filteredCustomers) {
        mutableStateOf(filteredCustomers.mapNotNull { it.id }.toSet())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建重拨任务") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 客户数量提示
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "已选择 ${selectedCustomerIds.size} 位「${statusLabel}」客户",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // 任务标题
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("任务标题") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // 任务描述
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("任务描述（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                // 优先级选择
                ExposedDropdownMenuBox(
                    expanded = showPriorityDropdown,
                    onExpandedChange = { showPriorityDropdown = it }
                ) {
                    OutlinedTextField(
                        value = when (priority) {
                            "urgent" -> "紧急"
                            "high" -> "高"
                            "normal" -> "普通"
                            "low" -> "低"
                            else -> "普通"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("优先级") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showPriorityDropdown) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = showPriorityDropdown,
                        onDismissRequest = { showPriorityDropdown = false }
                    ) {
                        listOf("urgent" to "紧急", "high" to "高", "normal" to "普通", "low" to "低").forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    priority = value
                                    showPriorityDropdown = false
                                }
                            )
                        }
                    }
                }

                // 分配客服选择
                ExposedDropdownMenuBox(
                    expanded = showAgentDropdown,
                    onExpandedChange = { showAgentDropdown = it }
                ) {
                    OutlinedTextField(
                        value = selectedAgent?.realName ?: "请选择客服",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("分配给") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showAgentDropdown) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = showAgentDropdown,
                        onDismissRequest = { showAgentDropdown = false }
                    ) {
                        agents.forEach { agent ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = agent.realName.take(1),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(agent.realName)
                                    }
                                },
                                onClick = {
                                    selectedAgent = agent
                                    showAgentDropdown = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        return@Button
                    }
                    onCreate(
                        title.trim(),
                        description.trim().ifBlank { null },
                        priority,
                        selectedAgent?.id,
                        selectedCustomerIds.toList()
                    )
                },
                enabled = !isCreating && title.isNotBlank() && selectedCustomerIds.isNotEmpty()
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("创建任务")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 根据Tab筛选客户
 */
private fun filterCustomersByTab(customers: List<TaskCustomer>, tab: Int): List<TaskCustomer> {
    return when (tab) {
        1 -> customers.filter { it.callStatus == "pending" }
        2 -> customers.filter { it.callStatus == "connected" || it.callResult == "已接听" }
        3 -> customers.filter { it.callStatus == "voicemail" || it.callResult == "语音信箱" }
        4 -> customers.filter { it.callStatus == "unanswered" || it.callResult == "响铃未接" }
        5 -> customers.filter { it.callStatus == "rejected" || it.callResult == "对方拒接" }
        6 -> customers.filter { it.callStatus == "busy" || it.callResult == "对方忙线" }
        7 -> customers.filter { it.callStatus == "power_off" || it.callResult == "关机/停机" || it.callResult == "对方关机" }
        8 -> customers.filter { it.callStatus == "no_answer" || it.callResult == "无人接听" }
        9 -> customers.filter { it.callStatus == "ivr" || it.callResult == "IVR语音" }
        10 -> customers.filter {
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
