package com.callcenter.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.callcenter.app.BuildConfig
import com.callcenter.app.ui.components.UpdateDialog
import com.callcenter.app.ui.viewmodel.SettingsViewModel
import com.callcenter.app.util.UpdateState
import com.callcenter.app.util.VersionInfoUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    onSwitchAccount: () -> Unit,
    onNavigateToCallSettings: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val serverUrl by viewModel.serverUrl.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val callSettings by viewModel.callSettings.collectAsState()
    
    // 版本更新相关状态
    val hasUpdate by viewModel.hasUpdate.collectAsState()
    val latestVersionInfo by viewModel.latestVersionInfo.collectAsState()

    var showServerUrlDialog by remember { mutableStateOf(false) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }
    var showSwitchAccountConfirmDialog by remember { mutableStateOf(false) }
    var showReleaseNotesDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showLogConfigDialog by remember { mutableStateOf(false) }

    // 读取 release notes
    val releaseNotes = remember { VersionInfoUtil.readReleaseNotes(context) }

    LaunchedEffect(Unit) {
        viewModel.loadSettings()
        // 检查版本更新
        viewModel.checkForUpdate()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(VersionInfoUtil.getTitleWithVersion(context, "设置")) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
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
        ) {
            // 用户信息卡片
            currentUser?.let { user ->
                UserInfoCard(user)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 统计信息
            StatsCard(stats)

            Spacer(modifier = Modifier.height(16.dp))

            // 设置选项
            SettingsGroup(title = "服务器设置") {
                SettingsItem(
                    icon = Icons.Default.Dns,
                    title = "服务器地址",
                    subtitle = serverUrl,
                    onClick = { showServerUrlDialog = true }
                )
            }

            SettingsGroup(title = "通话设置") {
                SettingsItem(
                    icon = Icons.Default.Phone,
                    title = "自动拨号间隔",
                    subtitle = "${callSettings.autoDialInterval}秒",
                    onClick = onNavigateToCallSettings
                )
                SettingsItem(
                    icon = Icons.Default.Timer,
                    title = "通话超时时间",
                    subtitle = "${callSettings.callTimeout}秒",
                    onClick = onNavigateToCallSettings
                )
                SettingsItem(
                    icon = Icons.Default.Settings,
                    title = "更多通话设置",
                    subtitle = "重试次数、自动免提等",
                    onClick = onNavigateToCallSettings
                )
            }

            SettingsGroup(title = "关于") {
                // 版本信息项 - 有更新时显示更新提示
                VersionSettingsItem(
                    hasUpdate = hasUpdate,
                    latestVersionName = latestVersionInfo?.versionName,
                    currentVersionName = BuildConfig.VERSION_NAME,
                    currentVersionCode = BuildConfig.VERSION_CODE,
                    onClick = {
                        if (hasUpdate && latestVersionInfo != null) {
                            showUpdateDialog = true
                        } else {
                            showReleaseNotesDialog = true
                        }
                    }
                )
                SettingsItem(
                    icon = Icons.Default.Help,
                    title = "帮助文档",
                    subtitle = "查看使用说明",
                    onClick = {
                        Toast.makeText(context, "功能开发中", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 日志收集设置
            SettingsGroup(title = "调试") {
                // 日志收集开关
                var collectLogcatEnabled by remember { mutableStateOf(callSettings.collectLogcat) }
                val isExporting = viewModel.isExportingLogs.collectAsState()
                var exportResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

                // 监听设置变化
                LaunchedEffect(callSettings.collectLogcat) {
                    collectLogcatEnabled = callSettings.collectLogcat
                }

                // 显示导出结果
                LaunchedEffect(exportResult) {
                    exportResult?.let { (success, message) ->
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        exportResult = null
                    }
                }

                // 日志下载启动器
                val logDownloadLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("text/plain")
                ) { uri: Uri? ->
                    if (uri != null) {
                        viewModel.exportLogsToUri(context, uri) { success, message ->
                            exportResult = Pair(success, message)
                        }
                    }
                }

                // 日志收集开关项
                ListItem(
                    headlineContent = { Text("收集 logcat 日志") },
                    supportingContent = { Text("收集扩音相关的日志（默认最多 10000 条）") },
                    leadingContent = {
                        Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingContent = {
                        Switch(
                            checked = collectLogcatEnabled,
                            onCheckedChange = { enabled ->
                                collectLogcatEnabled = enabled
                                viewModel.updateCollectLogcat(enabled)
                                if (enabled) {
                                    com.callcenter.app.service.LogCollectorService.startCollecting(context)
                                    Toast.makeText(context, "日志收集已开启", Toast.LENGTH_SHORT).show()
                                } else {
                                    com.callcenter.app.service.LogCollectorService.stopCollecting(context)
                                    Toast.makeText(context, "日志收集已关闭", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                )

                Divider()

                // 日志下载项
                ListItem(
                    headlineContent = { Text("下载日志") },
                    supportingContent = { Text("导出扩音相关的日志到文件") },
                    leadingContent = {
                        Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingContent = {
                        if (isExporting.value) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.ChevronRight, null)
                        }
                    },
                    modifier = Modifier.clickable(enabled = !isExporting.value) {
                        val fileName = "callcenter_log_${System.currentTimeMillis()}.txt"
                        logDownloadLauncher.launch(fileName)
                    }
                )

                Divider()

                // 日志收集高级配置
                val logStatus = viewModel.logCollectorStatus.collectAsState()
                ListItem(
                    headlineContent = { Text("高级配置") },
                    supportingContent = {
                        Column {
                            Text(
                                text = "状态: ${if (logStatus.value.isCollecting) "收集中" else "已停止"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "缓存: ${logStatus.value.cacheSize} / ${logStatus.value.maxCacheSize}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    leadingContent = {
                        Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, null)
                    },
                    modifier = Modifier.clickable {
                        showLogConfigDialog = true
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 切换账号和退出登录按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 切换账号按钮
                OutlinedButton(
                    onClick = { showSwitchAccountConfirmDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.SwitchAccount, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("切换账号")
                }

                // 退出登录按钮
                Button(
                    onClick = { showLogoutConfirmDialog = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("退出登录")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // 服务器地址设置对话框
    if (showServerUrlDialog) {
        ServerUrlDialog(
            currentUrl = serverUrl,
            onDismiss = { showServerUrlDialog = false },
            onConfirm = { newUrl ->
                viewModel.updateServerUrl(newUrl)
                showServerUrlDialog = false
                // 重启应用使新服务器地址生效
                com.callcenter.app.util.AppRestartUtil.restartApp(context)
            }
        )
    }

    // 退出确认对话框
    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            title = { Text("确认退出") },
            text = { Text("确定要退出登录吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirmDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("退出")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 切换账号确认对话框
    if (showSwitchAccountConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSwitchAccountConfirmDialog = false },
            title = { Text("切换账号") },
            text = { Text("切换到其他账号登录？") },
            confirmButton = {
                Button(
                    onClick = {
                        showSwitchAccountConfirmDialog = false
                        onSwitchAccount()
                    }
                ) {
                    Text("切换")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSwitchAccountConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    
    // Release Notes 对话框
    if (showReleaseNotesDialog) {
        AlertDialog(
            onDismissRequest = { showReleaseNotesDialog = false },
            title = { Text("版本更新日志") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    Text(
                        text = releaseNotes,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showReleaseNotesDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }
    
    // 版本更新对话框
    if (showUpdateDialog && latestVersionInfo != null) {
        UpdateDialog(
            versionInfo = latestVersionInfo,
            updateState = UpdateState.Idle,
            onDismiss = { showUpdateDialog = false },
            onConfirm = {
                showUpdateDialog = false
                // 使用系统浏览器下载
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(latestVersionInfo?.apkUrl))
                context.startActivity(intent)
            },
            onRetry = {
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(latestVersionInfo?.apkUrl))
                context.startActivity(intent)
            }
        )
    }

    // 日志收集配置对话框
    if (showLogConfigDialog) {
        LogCollectorConfigDialog(
            viewModel = viewModel,
            onDismiss = { showLogConfigDialog = false }
        )
    }
}

@Composable
private fun UserInfoCard(user: com.callcenter.app.data.model.User) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = user.realName,
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "@${user.username}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = when (user.role) {
                            "admin" -> "管理员"
                            "agent" -> "客服"
                            else -> user.role
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsCard(stats: com.callcenter.app.data.model.Stats?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "今日统计",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                StatItem(
                    value = stats?.totalCalls?.toString() ?: "0",
                    label = "总通话",
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    value = stats?.successfulCalls?.toString() ?: "0",
                    label = "成功",
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    value = formatDuration(stats?.totalDuration ?: 0),
                    label = "总时长",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatItem(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            content()
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        },
        trailingContent = {
            Icon(Icons.Default.ChevronRight, null)
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

/**
 * 版本信息设置项 - 支持显示更新提示
 */
@Composable
private fun VersionSettingsItem(
    hasUpdate: Boolean,
    latestVersionName: String?,
    currentVersionName: String,
    currentVersionCode: Int,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("版本信息")
                if (hasUpdate) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.error,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "有更新",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        },
        supportingContent = { 
            if (hasUpdate && latestVersionName != null) {
                Text(
                    text = "当前: v$currentVersionName ($currentVersionCode) → 最新: v$latestVersionName",
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text("v$currentVersionName ($currentVersionCode)")
            }
        },
        leadingContent = {
            Icon(
                Icons.Default.Info, 
                null, 
                tint = if (hasUpdate) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        },
        trailingContent = {
            if (hasUpdate) {
                Icon(
                    Icons.Default.Update,
                    contentDescription = "有更新",
                    tint = MaterialTheme.colorScheme.error
                )
            } else {
                Icon(Icons.Default.ChevronRight, null)
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun ServerUrlDialog(
    currentUrl: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var url by remember { mutableStateOf(currentUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("服务器地址") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("服务器地址") },
                    placeholder = { Text("http://localhost:8081") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "修改后应用将自动重启",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(url) },
                enabled = url.isNotBlank()
            ) {
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

private fun formatDuration(seconds: Int): String {
    return if (seconds < 60) {
        "${seconds}s"
    } else {
        val minutes = seconds / 60
        "${minutes}m"
    }
}

/**
 * 日志收集配置对话框
 */
@Composable
private fun LogCollectorConfigDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val logStatus = viewModel.logCollectorStatus.collectAsState()
    val isExporting = viewModel.isExportingLogs.collectAsState()
    var maxCacheSizeInput by remember { mutableStateOf(logStatus.value.maxCacheSize.toString()) }
    var exportResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    // 日志下载启动器
    val logDownloadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportLogsToUri(context, uri) { success, message ->
                exportResult = Pair(success, message)
            }
        }
    }

    // 显示导出结果
    LaunchedEffect(exportResult) {
        exportResult?.let { (success, message) ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            exportResult = null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("日志收集配置") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // 状态显示
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (logStatus.value.isCollecting) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (logStatus.value.isCollecting) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = null,
                                tint = if (logStatus.value.isCollecting) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (logStatus.value.isCollecting) "正在收集日志" else "日志收集已停止",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (logStatus.value.isCollecting) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "缓存大小: ${logStatus.value.cacheSize} / ${logStatus.value.maxCacheSize} 条",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 控制按钮
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 开始/停止收集
                    Button(
                        onClick = {
                            if (logStatus.value.isCollecting) {
                                viewModel.stopLogCollection(context)
                                Toast.makeText(context, "日志收集已停止", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.startLogCollection(context)
                                Toast.makeText(context, "日志收集已开始", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (logStatus.value.isCollecting) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    ) {
                        Icon(
                            if (logStatus.value.isCollecting) Icons.Default.Stop else Icons.Default.PlayArrow,
                            null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (logStatus.value.isCollecting) "停止收集" else "开始收集")
                    }

                    // 导出日志
                    OutlinedButton(
                        onClick = {
                            val fileName = "callcenter_log_${System.currentTimeMillis()}.txt"
                            logDownloadLauncher.launch(fileName)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isExporting.value
                    ) {
                        if (isExporting.value) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Download, null)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isExporting.value) "导出中..." else "导出日志")
                    }

                    Divider()

                    // 最大缓存大小设置
                    Text(
                        text = "最大缓存大小",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = maxCacheSizeInput,
                        onValueChange = { input ->
                            // 只允许数字
                            if (input.all { it.isDigit() }) {
                                maxCacheSizeInput = input
                            }
                        },
                        label = { Text("最大缓存数量") },
                        placeholder = { Text("默认: 10000") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val newSize = maxCacheSizeInput.toIntOrNull()
                            if (newSize != null && newSize > 0) {
                                viewModel.setLogMaxSize(context, newSize)
                                Toast.makeText(context, "最大缓存大小已更新", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "请输入有效的数字", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Save, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("保存设置")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}
