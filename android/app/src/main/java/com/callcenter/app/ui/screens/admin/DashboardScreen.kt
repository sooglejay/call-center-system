package com.callcenter.app.ui.screens.admin

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.callcenter.app.data.model.AgentRanking
import com.callcenter.app.data.model.CallRecord
import com.callcenter.app.data.model.DashboardStats
import com.callcenter.app.util.VersionInfoUtil
import com.callcenter.app.ui.viewmodel.DashboardViewModel
import com.callcenter.app.ui.viewmodel.MyStatsViewModel

/**
 * 管理员仪表盘页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAgents: () -> Unit,
    onNavigateToTasks: () -> Unit,
    onNavigateToAgentDetail: (Int) -> Unit,
    onNavigateToCustomers: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
    myStatsViewModel: MyStatsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val dashboardStats by viewModel.dashboardStats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    // 日志上传状态
    val isUploadingLogs by myStatsViewModel.isUploadingLogs.collectAsState()
    val logUploadMessage by myStatsViewModel.logUploadMessage.collectAsState()

    // 显示上传结果消息
    LaunchedEffect(logUploadMessage) {
        logUploadMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            myStatsViewModel.clearLogUploadMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(VersionInfoUtil.getTitleWithVersion(context, "管理仪表盘")) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (error != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
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
                    Button(onClick = { viewModel.refresh() }) {
                        Text("重试")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 今日统计卡片
                item {
                    TodayStatsCard(stats = dashboardStats)
                }

                // 快捷操作
                item {
                    QuickActionsCard(
                        onNavigateToAgents = onNavigateToAgents,
                        onNavigateToTasks = onNavigateToTasks,
                        onNavigateToCustomers = onNavigateToCustomers,
                        onUploadLogs = { myStatsViewModel.uploadDeviceLogs(context) },
                        isUploadingLogs = isUploadingLogs
                    )
                }

                // 客服排行榜
                dashboardStats?.let { stats ->
                    item {
                        AgentRankingCard(
                            rankings = stats.agentRanking,
                            onAgentClick = onNavigateToAgentDetail
                        )
                    }

                    // 最近通话
                    if (stats.recentCalls.isNotEmpty()) {
                        item {
                            RecentCallsCard(calls = stats.recentCalls)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 今日统计卡片
 */
@Composable
private fun TodayStatsCard(stats: DashboardStats?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "今日数据概览",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            stats?.let { s ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatItem(
                        icon = Icons.Default.Phone,
                        label = "通话次数",
                        value = s.todayCalls.toString(),
                        color = MaterialTheme.colorScheme.primary
                    )
                    StatItem(
                        icon = Icons.Default.AccessTime,
                        label = "通话时长",
                        value = formatDuration(s.todayDuration),
                        color = MaterialTheme.colorScheme.secondary
                    )
                    StatItem(
                        icon = Icons.Default.CheckCircle,
                        label = "接通率",
                        value = "${(s.todaySuccessRate * 100).toInt()}%",
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatItem(
                        icon = Icons.Default.Person,
                        label = "在线客服",
                        value = "${s.activeAgents}/${s.totalAgents}",
                        color = Color(0xFF4CAF50)
                    )
                    StatItem(
                        icon = Icons.Default.Group,
                        label = "待跟进客户",
                        value = s.pendingCustomers.toString(),
                        color = Color(0xFFFF9800)
                    )
                    StatItem(
                        icon = Icons.Default.People,
                        label = "客户总数",
                        value = s.totalCustomers.toString(),
                        color = Color(0xFF2196F3)
                    )
                }
            }
        }
    }
}

/**
 * 单个统计项
 */
@Composable
private fun StatItem(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
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

/**
 * 快捷操作卡片
 */
@Composable
private fun QuickActionsCard(
    onNavigateToAgents: () -> Unit,
    onNavigateToTasks: () -> Unit,
    onNavigateToCustomers: () -> Unit,
    onUploadLogs: () -> Unit,
    isUploadingLogs: Boolean
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "快捷操作",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionButton(
                    icon = Icons.Default.Group,
                    label = "客服管理",
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToAgents
                )
                QuickActionButton(
                    icon = Icons.Default.Assignment,
                    label = "任务管理",
                    color = Color(0xFF2196F3),
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToTasks
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionButton(
                    icon = Icons.Default.People,
                    label = "客户列表",
                    color = Color(0xFF9C27B0),
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToCustomers
                )
                QuickActionButton(
                    icon = if (isUploadingLogs) Icons.Default.HourglassTop else Icons.Default.Upload,
                    label = if (isUploadingLogs) "上传中..." else "上传日志",
                    color = Color(0xFFE91E63),
                    modifier = Modifier.weight(1f),
                    onClick = onUploadLogs,
                    enabled = !isUploadingLogs
                )
            }
        }
    }
}

/**
 * 快捷操作按钮
 */
@Composable
private fun QuickActionButton(
    icon: ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (enabled) color.copy(alpha = 0.1f) else color.copy(alpha = 0.05f),
        onClick = onClick,
        enabled = enabled
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) color else color.copy(alpha = 0.5f),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) color else color.copy(alpha = 0.5f),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 客服排行榜卡片
 */
@Composable
private fun AgentRankingCard(
    rankings: List<AgentRanking>,
    onAgentClick: (Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "今日排行榜",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (rankings.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无数据",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                rankings.take(5).forEachIndexed { index, ranking ->
                    AgentRankingItem(
                        rank = index + 1,
                        ranking = ranking,
                        onClick = { onAgentClick(ranking.agentId) }
                    )
                    if (index < rankings.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}

/**
 * 排行榜单项
 */
@Composable
private fun AgentRankingItem(
    rank: Int,
    ranking: AgentRanking,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 排名
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    when (rank) {
                        1 -> Color(0xFFFFD700)
                        2 -> Color(0xFFC0C0C0)
                        3 -> Color(0xFFCD7F32)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (rank <= 3) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 客服名称
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ranking.agentName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "通话 ${ranking.totalCalls} 次 · 时长 ${formatDuration(ranking.totalDuration)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 接通率 - 优先使用 successRate，否则使用 connectionRate
        val displayRate = if (ranking.successRate > 0) ranking.successRate else ranking.connectionRate
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${(displayRate * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (displayRate >= 0.5) Color(0xFF4CAF50) else Color(0xFFFF9800)
            )
            Text(
                text = "接通率",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 最近通话卡片
 */
@Composable
private fun RecentCallsCard(calls: List<CallRecord>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "最近通话",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            calls.take(5).forEach { call ->
                RecentCallItem(call = call)
                if (call != calls.last()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

/**
 * 最近通话单项
 */
@Composable
private fun RecentCallItem(call: CallRecord) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 状态图标
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (call.status == "completed") Color(0xFF4CAF50).copy(alpha = 0.2f)
                    else Color(0xFFFF9800).copy(alpha = 0.2f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (call.status == "completed") Icons.Default.Check else Icons.Default.PhoneMissed,
                contentDescription = null,
                tint = if (call.status == "completed") Color(0xFF4CAF50) else Color(0xFFFF9800),
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = call.customer?.name ?: call.phone,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = call.agent?.realName ?: "未知客服",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatDuration(call.duration),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = formatTimeAgo(call.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 格式化时长
 */
private fun formatDuration(seconds: Int): String {
    if (seconds < 60) return "${seconds}秒"
    val minutes = seconds / 60
    val secs = seconds % 60
    return if (secs > 0) "${minutes}分${secs}秒" else "${minutes}分钟"
}

/**
 * 格式化相对时间
 */
private fun formatTimeAgo(timeStr: String?): String {
    if (timeStr == null) return ""
    // TODO: 实现时间格式化
    return timeStr
}
