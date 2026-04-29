package com.callcenter.app.ui.screens.help

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.callcenter.app.ui.util.rememberDebounceOnClick
import com.callcenter.app.util.VersionInfoUtil

/**
 * 本地帮助文档页面
 * 参考Web端 HelpDocumentation.tsx 内容
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(VersionInfoUtil.getTitleWithVersion(context, "使用说明")) },
                navigationIcon = {
                    IconButton(onClick = rememberDebounceOnClick() { onNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 标题
            Text(
                text = "系统使用说明",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "欢迎使用智能呼叫中心系统！本文档将帮助您快速了解系统的使用方法。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 管理员指南
            AdminGuideSection()

            Spacer(modifier = Modifier.height(24.dp))

            // 客服指南
            AgentGuideSection()

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AdminGuideSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ManageAccounts,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "管理员使用指南",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            InfoCard {
                Text(
                    text = "管理员负责系统的整体管理，包括人员管理、客户数据管理、任务分配和系统监控等工作。",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 系统登录
            GuideItem(
                icon = Icons.Default.Login,
                title = "一、系统登录",
                content = "• 默认管理员账号：admin\n• 默认密码：admin123\n• 登录后进入管理员仪表盘"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 仪表板
            GuideItem(
                icon = Icons.Default.Dashboard,
                title = "二、仪表板功能",
                content = "• 查看今日通话统计、客户总数、接通率等关键指标\n• 查看客服人员的在线状态和工作情况\n• 查看最近通话记录和待处理任务"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 人员管理
            GuideItem(
                icon = Icons.Default.Group,
                title = "三、人员管理",
                content = "• 添加客服：填写基本信息，选择角色为「客服」\n• 编辑信息：修改客服的姓名、账号等信息\n• 重置密码：为忘记密码的客服设置新密码\n• 启用/禁用：控制客服账号的登录权限"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 客户管理
            GuideItem(
                icon = Icons.Default.Contacts,
                title = "四、客户管理",
                content = "• 导入客户：支持Excel文件批量导入\n• 添加客户：手动添加单个客户信息\n• 分配客服：将客户分配给指定客服跟进\n• 搜索筛选：按姓名、电话、状态等条件搜索"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 任务分配
            GuideItem(
                icon = Icons.Default.Assignment,
                title = "五、任务分配",
                content = "• 创建任务：填写任务标题、描述，选择执行客服\n• 分配客户：按姓氏首字母筛选或搜索选择客户\n• 设置优先级：普通、高、紧急三个级别\n• 跟踪进度：查看任务完成率和已拨打客户数"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 常见问题
            FaqItem(
                question = "Q: 如何查看客服的工作情况？",
                answer = "A: 在「监控统计」页面可以查看各客服的通话记录和统计数据。"
            )
            FaqItem(
                question = "Q: 客户数据如何导入？",
                answer = "A: 在「客户管理」页面点击「导入」按钮，按照模板格式上传Excel文件即可。"
            )
        }
    }
}

@Composable
private fun AgentGuideSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SupportAgent,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "客服坐席使用指南",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            InfoCard {
                Text(
                    text = "客服坐席是系统的日常使用人员，主要负责拨打电话、跟进客户、记录通话信息等工作。",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 系统登录
            GuideItem(
                icon = Icons.Default.Login,
                title = "一、系统登录",
                content = "• 账号：由管理员分配\n• 默认密码示例：agent123\n• 登录后进入客服工作台"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 工作台
            GuideItem(
                icon = Icons.Default.Dashboard,
                title = "二、工作台功能",
                content = "• 查看今日待拨打客户列表\n• 查看我的任务和进度\n• 查看今日通话统计数据\n• 快速开始拨打电话"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 拨打电话
            GuideItem(
                icon = Icons.Default.Phone,
                title = "三、拨打电话",
                content = "手动拨号：\n• 在「电话列表」点击客户右侧电话图标\n• 通话结束后选择通话结果\n• 填写通话备注\n\n自动拨号：\n• 点击「自动拨号」按钮，设置间隔后启动\n• 系统自动按顺序拨打\n• 每个电话结束后记录结果，继续下一个"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 客户列表
            GuideItem(
                icon = Icons.Default.Contacts,
                title = "四、客户列表",
                content = "• 查看客户信息：点击姓名查看详细信息\n• 搜索客户：按姓名、电话搜索\n• 筛选状态：按拨打状态筛选\n• 排序：按优先级、创建时间排序"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 通信记录
            GuideItem(
                icon = Icons.Default.History,
                title = "五、通信记录",
                content = "• 查看所有通话历史记录\n• 查看通话时长和通话结果\n• 查看和编辑通话备注\n• 按日期筛选通话记录"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 我的业绩
            GuideItem(
                icon = Icons.Default.BarChart,
                title = "六、我的业绩",
                content = "• 今日统计：今日通话次数、时长、接通率\n• 本周统计：本周累计通话数据\n• 本月统计：本月累计通话数据\n• 排名情况：在团队中的排名位置"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 拨号设置
            GuideItem(
                icon = Icons.Default.Settings,
                title = "七、拨号设置",
                content = "• 设置拨号间隔：自动拨号时两个电话间的等待时间\n• 设置超时时间：无人接听时的等待时间\n• 开启/关闭录音：设置是否自动录制通话"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 任务处理
            GuideItem(
                icon = Icons.Default.Task,
                title = "八、任务处理",
                content = "• 查看任务：在工作台查看分配的任务列表\n• 执行任务：点击任务进入详情，查看客户列表\n• 完成任务：拨打完所有客户后自动标记完成"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 常见问题
            FaqItem(
                question = "Q: 电话拨打失败怎么办？",
                answer = "A: 请检查网络连接和电话权限，或联系管理员检查系统设置。"
            )
            FaqItem(
                question = "Q: 如何查看我的工作量？",
                answer = "A: 在「我的业绩」页面可以查看今日、本周、本月的通话统计数据。"
            )
            FaqItem(
                question = "Q: 通话结果选错了可以修改吗？",
                answer = "A: 可以在「通信记录」页面找到对应记录进行编辑。"
            )
            FaqItem(
                question = "Q: 自动拨号可以暂停吗？",
                answer = "A: 可以，在自动拨号过程中点击「停止」按钮即可暂停。"
            )
        }
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
        )
    ) {
        Box(modifier = Modifier.padding(12.dp)) {
            content()
        }
    }
}

@Composable
private fun GuideItem(
    icon: ImageVector,
    title: String,
    content: String
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 28.dp)
        )
    }
}

@Composable
private fun FaqItem(question: String, answer: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = question,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = answer,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
