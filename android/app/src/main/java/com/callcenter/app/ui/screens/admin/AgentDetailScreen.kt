package com.callcenter.app.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.callcenter.app.data.model.Stats
import com.callcenter.app.data.model.User
import com.callcenter.app.ui.viewmodel.AgentDetailViewModel

/**
 * 客服详情页面
 * 支持新建和编辑客服
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentDetailScreen(
    agentId: Int,
    onNavigateBack: () -> Unit,
    viewModel: AgentDetailViewModel = hiltViewModel()
) {
    val isNewAgent = agentId == 0
    val agent by viewModel.agent.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val error by viewModel.error.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()

    // 表单状态
    var username by remember { mutableStateOf("") }
    var realName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("agent") }
    var dataAccessType by remember { mutableStateOf("all") }
    var passwordVisible by remember { mutableStateOf(false) }

    // 重置密码对话框
    var showResetPasswordDialog by remember { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf("") }

    // 初始化数据
    LaunchedEffect(agent) {
        agent?.let { u ->
            username = u.username
            realName = u.realName
            phone = u.phone ?: ""
            email = u.email ?: ""
            role = u.role
            dataAccessType = u.dataAccessType ?: "all"
        }
    }

    // 保存成功后返回
    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            onNavigateBack()
        }
    }

    // 加载数据
    LaunchedEffect(agentId) {
        if (agentId != 0) {
            viewModel.loadAgent(agentId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNewAgent) "添加客服" else "客服详情") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (!isNewAgent && agent != null) {
                        IconButton(onClick = { showResetPasswordDialog = true }) {
                            Icon(Icons.Default.Key, contentDescription = "重置密码")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading && !isNewAgent) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {
                // 头像和基本信息
                if (!isNewAgent && agent != null) {
                    AgentHeader(agent = agent!!)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 表单
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "基本信息",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        // 用户名
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("用户名 *") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isNewAgent,
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null)
                            },
                            isError = username.isBlank() && error != null
                        )

                        // 姓名
                        OutlinedTextField(
                            value = realName,
                            onValueChange = { realName = it },
                            label = { Text("姓名 *") },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = {
                                Icon(Icons.Default.Badge, contentDescription = null)
                            },
                            isError = realName.isBlank() && error != null
                        )

                        // 密码（新建时必填）
                        if (isNewAgent) {
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("密码 *") },
                                modifier = Modifier.fillMaxWidth(),
                                leadingIcon = {
                                    Icon(Icons.Default.Lock, contentDescription = null)
                                },
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            if (passwordVisible) Icons.Default.VisibilityOff
                                            else Icons.Default.Visibility,
                                            contentDescription = null
                                        )
                                    }
                                },
                                visualTransformation = if (passwordVisible) VisualTransformation.None
                                else PasswordVisualTransformation(),
                                isError = password.isBlank() && error != null
                            )
                        }

                        // 手机号
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("手机号") },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = {
                                Icon(Icons.Default.Phone, contentDescription = null)
                            }
                        )

                        // 邮箱
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("邮箱") },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = {
                                Icon(Icons.Default.Email, contentDescription = null)
                            }
                        )

                        // 角色
                        Text(
                            text = "角色",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RoleChip(
                                selected = role == "agent",
                                onClick = { role = "agent" },
                                label = "客服",
                                icon = Icons.Default.SupportAgent,
                                modifier = Modifier.weight(1f)
                            )
                            RoleChip(
                                selected = role == "admin",
                                onClick = { role = "admin" },
                                label = "管理员",
                                icon = Icons.Default.AdminPanelSettings,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // 数据权限
                        Text(
                            text = "数据权限",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            DataAccessChip(
                                selected = dataAccessType == "all",
                                onClick = { dataAccessType = "all" },
                                label = "全部数据",
                                description = "可查看所有客户数据",
                                icon = Icons.Default.Dataset
                            )
                            DataAccessChip(
                                selected = dataAccessType == "assigned",
                                onClick = { dataAccessType = "assigned" },
                                label = "仅分配给自己的数据",
                                description = "只能查看分配给自己的客户",
                                icon = Icons.Default.AssignmentInd
                            )
                            DataAccessChip(
                                selected = dataAccessType == "disabled",
                                onClick = { dataAccessType = "disabled" },
                                label = "禁用账号",
                                description = "账号已停用，无法登录",
                                icon = Icons.Default.Block,
                                isError = true
                            )
                        }

                        // 错误提示
                        if (error != null) {
                            Text(
                                text = error!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                // 统计信息（仅编辑时显示）
                if (!isNewAgent && stats != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    AgentStatsCard(stats = stats!!)
                }

                // 保存按钮
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        viewModel.saveAgent(
                            id = if (isNewAgent) 0 else agentId,
                            username = username,
                            realName = realName,
                            password = if (isNewAgent) password else null,
                            phone = phone.ifBlank { null },
                            email = email.ifBlank { null },
                            role = role,
                            dataAccessType = dataAccessType
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    enabled = !isSaving &&
                              username.isNotBlank() &&
                              realName.isNotBlank() &&
                              (!isNewAgent || password.isNotBlank())
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(if (isNewAgent) "添加客服" else "保存修改")
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // 重置密码对话框
    if (showResetPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showResetPasswordDialog = false },
            title = { Text("重置密码") },
            text = {
                Column {
                    Text("请输入新密码，留空则使用默认密码")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("新密码") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetPassword(agentId, newPassword.ifBlank { null })
                        showResetPasswordDialog = false
                        newPassword = ""
                    }
                ) {
                    Text("确认重置")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetPasswordDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 客服头部信息
 */
@Composable
private fun AgentHeader(agent: User) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 头像
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = agent.realName.take(1),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.width(24.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = agent.realName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (agent.role == "admin") Color(0xFFE91E63).copy(alpha = 0.2f)
                               else Color(0xFF2196F3).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = if (agent.role == "admin") "管理员" else "客服",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (agent.role == "admin") Color(0xFFE91E63)
                                   else Color(0xFF2196F3),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = " ${agent.username}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    agent.phone?.let { phone ->
                        if (phone.isNotEmpty()) {
                            Text(
                                text = " · ",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = " $phone",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 客服统计卡片
 */
@Composable
private fun AgentStatsCard(stats: Stats) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "工作统计",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 今日统计
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatColumn("今日通话", stats.todayCalls.toString())
                StatColumn("今日时长", formatDuration(stats.todayDuration))
                StatColumn("今日成功", stats.todaySuccessful.toString())
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // 本周/本月统计
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatColumn("本周通话", stats.weekCalls.toString())
                StatColumn("本月通话", stats.monthCalls.toString())
                StatColumn("本月时长", formatDuration(stats.monthDuration))
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // 客户统计
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatColumn("待跟进客户", stats.pendingCustomers.toString())
                StatColumn("已完成客户", stats.completedCustomers.toString())
                StatColumn("总通话", stats.totalCalls.toString())
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDuration(seconds: Int): String {
    if (seconds < 60) return "${seconds}秒"
    val minutes = seconds / 60
    val secs = seconds % 60
    return if (secs > 0) "${minutes}分${secs}秒" else "${minutes}分钟"
}

/**
 * 角色选择卡片
 * 选中状态使用填充色，非选中状态使用边框
 */
@Composable
private fun RoleChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        contentColor = contentColor,
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = borderColor
        ),
        shadowElevation = if (selected) 4.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
            if (selected) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * 数据权限选择卡片
 * 选中状态使用填充色，非选中状态使用边框
 */
@Composable
private fun DataAccessChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    val containerColor = if (selected) {
        if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (selected) {
        if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = if (selected) {
        if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }
    val iconColor = if (selected) {
        if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        contentColor = contentColor,
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = borderColor
        ),
        shadowElevation = if (selected) 4.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = iconColor
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            } else {
                // 未选中状态显示圆形轮廓
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                )
            }
        }
    }
}
