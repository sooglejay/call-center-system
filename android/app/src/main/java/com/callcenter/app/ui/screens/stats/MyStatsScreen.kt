package com.callcenter.app.ui.screens.stats

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.callcenter.app.data.model.Stats
import com.callcenter.app.util.VersionInfoUtil
import com.callcenter.app.ui.viewmodel.MyStatsViewModel

/**
 * 客服个人统计页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyStatsScreen(
    onNavigateBack: () -> Unit,
    viewModel: MyStatsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val stats by viewModel.stats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(VersionInfoUtil.getTitleWithVersion(context, "我的统计")) },
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                stats?.let { s ->
                    // 今日统计卡片
                    TodayStatsCard(stats = s)

                    // 业绩指标
                    PerformanceMetricsCard(stats = s)

                    // 工作进度
                    WorkProgressCard(stats = s)

                    // 快捷操作
                    QuickTipsCard()
                }
            }
        }
    }
}

/**
 * 今日统计卡片
 */
@Composable
private fun TodayStatsCard(
    stats: Stats
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "今日工作数据",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 使用 todayCalls 扩展字段，如果没有则使用 totalCalls
            val displayTodayCalls = if (stats.todayCalls > 0) stats.todayCalls else stats.totalCalls
            val displayTodayDuration = if (stats.todayDuration > 0) stats.todayDuration else stats.totalDuration
            val displaySuccessfulCalls = if (stats.successfulCalls > 0) stats.successfulCalls else stats.connectedCalls

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CircularProgressItem(
                    value = displayTodayCalls,
                    maxValue = 50,
                    label = "通话次数",
                    color = MaterialTheme.colorScheme.primary
                )

                CircularProgressItem(
                    value = displaySuccessfulCalls,
                    maxValue = displayTodayCalls.coerceAtLeast(1),
                    label = "成功通话",
                    color = Color(0xFF4CAF50)
                )

                CircularProgressItem(
                    value = displayTodayDuration / 60,
                    maxValue = 120,
                    label = "通话时长(分)",
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

/**
 * 圆形进度指示器
 */
@Composable
private fun CircularProgressItem(
    value: Int,
    maxValue: Int,
    label: String,
    color: Color
) {
    val progress = (value.toFloat() / maxValue).coerceIn(0f, 1f)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(80.dp),
            contentAlignment = Alignment.Center
        ) {
            // 背景圆
            CircularProgressIndicator(
                progress = 1f,
                modifier = Modifier.size(80.dp),
                color = color.copy(alpha = 0.2f),
                strokeWidth = 6.dp
            )
            // 进度圆
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(80.dp),
                color = color,
                strokeWidth = 6.dp
            )
            // 数值
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 业绩指标卡片
 */
@Composable
private fun PerformanceMetricsCard(stats: Stats) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "业绩指标",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 接通率
            MetricRow(
                icon = Icons.Default.Call,
                label = "接通率",
                value = if (stats.totalCalls > 0) {
                    "${(stats.successfulCalls.toFloat() / stats.totalCalls * 100).toInt()}%"
                } else "0%",
                color = Color(0xFF4CAF50)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 平均通话时长
            MetricRow(
                icon = Icons.Default.Timer,
                label = "平均通话时长",
                value = if (stats.totalCalls > 0) {
                    formatDuration(stats.totalDuration / stats.totalCalls)
                } else "0秒",
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 失败通话
            MetricRow(
                icon = Icons.Default.CallEnd,
                label = "未接通",
                value = "${stats.failedCalls} 次",
                color = if (stats.failedCalls > 0) Color(0xFFFF5722) else Color(0xFF9E9E9E)
            )
        }
    }
}

/**
 * 指标行
 */
@Composable
private fun MetricRow(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

/**
 * 工作进度卡片
 */
@Composable
private fun WorkProgressCard(stats: Stats) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "客户跟进进度",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 待跟进客户
            val totalCustomers = stats.pendingCustomers + stats.completedCustomers
            val completedRate = if (totalCustomers > 0) {
                stats.completedCustomers.toFloat() / totalCustomers
            } else 0f

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "已完成 ${stats.completedCustomers} / $totalCustomers",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${(completedRate * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { completedRate },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 待跟进
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFF9800).copy(alpha = 0.1f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stats.pendingCustomers.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF9800)
                        )
                        Text(
                            text = "待跟进",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 已完成
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF4CAF50).copy(alpha = 0.1f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stats.completedCustomers.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50)
                        )
                        Text(
                            text = "已完成",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * 快捷提示卡片
 */
@Composable
private fun QuickTipsCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = Color(0xFFFFC107)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "工作小贴士",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val tips = listOf(
                "保持良好的通话节奏，避免疲劳工作",
                "及时添加通话备注，方便后续跟进",
                "合理安排休息时间，提高工作效率",
                "遇到问题及时向管理员反馈"
            )

            tips.forEach { tip ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = tip,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
