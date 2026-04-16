package com.callcenter.app.ui.screens.customer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.callcenter.app.data.model.Customer
import com.callcenter.app.util.VersionInfoUtil
import com.callcenter.app.ui.viewmodel.CustomerDetailViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailScreen(
    customerId: Int,
    onNavigateBack: () -> Unit,
    onNavigateToCallHistory: () -> Unit,
    viewModel: CustomerDetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val customer by viewModel.customer.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val callRecords by viewModel.callRecords.collectAsState()
    val error by viewModel.error.collectAsState()

    var showStatusDialog by remember { mutableStateOf(false) }
    var showNotesDialog by remember { mutableStateOf(false) }

    LaunchedEffect(customerId) {
        viewModel.loadCustomer(customerId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(VersionInfoUtil.getTitleWithVersion(context, "客户详情")) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showStatusDialog = true }) {
                        Icon(Icons.Default.Edit, "修改状态")
                    }
                    IconButton(onClick = onNavigateToCallHistory) {
                        Icon(Icons.Default.History, "通话记录")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                error != null -> {
                    ErrorContent(
                        error = error!!,
                        onRetry = { viewModel.loadCustomer(customerId) }
                    )
                }
                customer != null -> {
                    CustomerDetailContent(
                        customer = customer!!,
                        recentCalls = callRecords.take(3),
                        onUpdateNotes = { showNotesDialog = true }
                    )
                }
            }
        }
    }

    // 状态更新对话框
    if (showStatusDialog) {
        StatusUpdateDialog(
            currentStatus = customer?.status ?: "pending",
            onDismiss = { showStatusDialog = false },
            onConfirm = { newStatus, notes ->
                viewModel.updateCustomerStatus(customerId, newStatus, notes)
                showStatusDialog = false
            }
        )
    }

    // 备注更新对话框
    if (showNotesDialog) {
        NotesUpdateDialog(
            currentNotes = customer?.notes ?: "",
            onDismiss = { showNotesDialog = false },
            onConfirm = { notes ->
                viewModel.updateCustomerNotes(customerId, notes)
                showNotesDialog = false
            }
        )
    }
}

@Composable
private fun CustomerDetailContent(
    customer: Customer,
    recentCalls: List<com.callcenter.app.data.model.CallRecord>,
    onUpdateNotes: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // 客户基本信息卡片
        CustomerInfoCard(customer)

        Spacer(modifier = Modifier.height(16.dp))

        // 联系信息
        ContactInfoCard(customer)

        Spacer(modifier = Modifier.height(16.dp))

        // 备注信息
        NotesCard(
            notes = customer.notes,
            onEdit = onUpdateNotes
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 最近通话记录
        RecentCallsCard(recentCalls)

        Spacer(modifier = Modifier.height(80.dp)) // FAB 空间
    }
}

@Composable
private fun CustomerInfoCard(customer: Customer) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(56.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = (customer.name?.firstOrNull()?.toString() ?: "?"),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = customer.name ?: "未命名",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    StatusChip(status = customer.status)
                }

                if (customer.priority > 1) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "高优先级",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (customer.company != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Business,
                        null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = customer.company,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CalendarToday,
                    null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "创建于: ${formatDate(customer.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ContactInfoCard(customer: Customer) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "联系信息",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 电话
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Phone,
                    null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "电话",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = customer.phone ?: "",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            if (customer.email != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Email,
                        null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "邮箱",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = customer.email,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            if (customer.address != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn,
                        null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "地址",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = customer.address,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesCard(notes: String?, onEdit: () -> Unit) {
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
                Text(
                    text = "备注",
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = onEdit) {
                    Text("编辑")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (notes.isNullOrBlank()) {
                Text(
                    text = "暂无备注",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun RecentCallsCard(recentCalls: List<com.callcenter.app.data.model.CallRecord>) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "最近通话",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (recentCalls.isEmpty()) {
                Text(
                    text = "暂无通话记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                recentCalls.forEach { call ->
                    CallRecordItem(call)
                    if (call != recentCalls.last()) {
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CallRecordItem(call: com.callcenter.app.data.model.CallRecord) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when (call.status) {
                "completed" -> Icons.Default.CheckCircle
                "no_answer" -> Icons.Default.Cancel
                "failed" -> Icons.Default.Error
                else -> Icons.Default.Phone
            },
            contentDescription = null,
            tint = when (call.status) {
                "completed" -> MaterialTheme.colorScheme.primary
                "no_answer" -> MaterialTheme.colorScheme.error
                "failed" -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatDateTime(call.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "通话时长: ${formatDuration(call.duration)}",
                style = MaterialTheme.typography.bodyMedium
            )
            if (!call.notes.isNullOrBlank()) {
                Text(
                    text = call.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val (text, color) = when (status) {
        "pending" -> "待拨打" to MaterialTheme.colorScheme.primary
        "completed" -> "已完成" to MaterialTheme.colorScheme.tertiary
        "no_answer" -> "无人接听" to MaterialTheme.colorScheme.error
        "in_progress" -> "进行中" to MaterialTheme.colorScheme.secondary
        else -> status to MaterialTheme.colorScheme.outline
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}

@Composable
private fun ErrorContent(error: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Error,
            null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = error,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("重试")
        }
    }
}

@Composable
private fun StatusUpdateDialog(
    currentStatus: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var selectedStatus by remember { mutableStateOf(currentStatus) }
    var notes by remember { mutableStateOf("") }

    val statusOptions = listOf(
        "pending" to "待拨打",
        "completed" to "已完成",
        "no_answer" to "无人接听",
        "interested" to "有意向",
        "not_interested" to "无意向"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更新客户状态") },
        text = {
            Column {
                Text("选择状态:")
                Spacer(modifier = Modifier.height(8.dp))
                statusOptions.forEach { (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = selectedStatus == value,
                            onClick = { selectedStatus = value }
                        )
                        Text(label)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("备注（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedStatus, notes) }) {
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
private fun NotesUpdateDialog(
    currentNotes: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var notes by remember { mutableStateOf(currentNotes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑备注") },
        text = {
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("备注") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(notes) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

private fun formatDate(dateString: String?): String {
    if (dateString == null) return "未知"
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        val outputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = inputFormat.parse(dateString)
        outputFormat.format(date!!)
    } catch (e: Exception) {
        dateString
    }
}

private fun formatDateTime(dateString: String?): String {
    if (dateString == null) return "未知"
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        val outputFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        val date = inputFormat.parse(dateString)
        outputFormat.format(date!!)
    } catch (e: Exception) {
        dateString
    }
}

private fun formatDuration(seconds: Int): String {
    return if (seconds < 60) {
        "${seconds}秒"
    } else {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        "${minutes}分${remainingSeconds}秒"
    }
}
