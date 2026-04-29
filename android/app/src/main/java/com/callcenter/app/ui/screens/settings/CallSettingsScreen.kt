package com.callcenter.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.callcenter.app.util.VersionInfoUtil
import com.callcenter.app.ui.util.rememberDebounceOnClick
import com.callcenter.app.ui.viewmodel.CallSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: CallSettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val settings by viewModel.callSettings.collectAsState()

    // 本地状态用于编辑
    var autoDialInterval by remember { mutableStateOf(settings.autoDialInterval) }
    var callTimeout by remember { mutableStateOf(settings.callTimeout) }
    var retryCount by remember { mutableStateOf(settings.retryCount) }
    var autoRecordCall by remember { mutableStateOf(settings.autoRecordCall) }
    var autoAddNote by remember { mutableStateOf(settings.autoAddNote) }
    var defaultNoteTemplate by remember { mutableStateOf(settings.defaultNoteTemplate) }

    // 当设置变化时更新本地状态
    LaunchedEffect(settings) {
        autoDialInterval = settings.autoDialInterval
        callTimeout = settings.callTimeout
        retryCount = settings.retryCount
        autoRecordCall = settings.autoRecordCall
        autoAddNote = settings.autoAddNote
        defaultNoteTemplate = settings.defaultNoteTemplate
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(VersionInfoUtil.getTitleWithVersion(context, "通话设置")) },
                navigationIcon = {
                    IconButton(onClick = rememberDebounceOnClick() { onNavigateBack() }) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.saveSettings(
                                settings.copy(
                                    autoDialInterval = autoDialInterval,
                                    callTimeout = callTimeout,
                                    retryCount = retryCount,
                                    autoRecordCall = autoRecordCall,
                                    autoAddNote = autoAddNote,
                                    defaultNoteTemplate = defaultNoteTemplate
                                )
                            )
                            onNavigateBack()
                        }
                    ) {
                        Text("保存")
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
            // 自动拨号设置
            SettingsSection(title = "自动拨号设置") {
                // 自动拨号间隔
                SettingItem(
                    icon = Icons.Default.Timer,
                    title = "拨号间隔",
                    subtitle = "自动拨号时，两次拨号之间的等待时间"
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = autoDialInterval.toString(),
                            onValueChange = { value ->
                                value.toIntOrNull()?.let {
                                    autoDialInterval = it.coerceIn(2, 300)
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(80.dp),
                            singleLine = true
                        )
                        Text("秒", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                // 通话超时时间
                SettingItem(
                    icon = Icons.Default.PhoneCallback,
                    title = "通话超时",
                    subtitle = "无人接听时的等待时间"
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = callTimeout.toString(),
                            onValueChange = { value ->
                                value.toIntOrNull()?.let {
                                    callTimeout = it.coerceIn(2, 120)
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(80.dp),
                            singleLine = true
                        )
                        Text("秒", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                // 重试次数
                SettingItem(
                    icon = Icons.Default.Replay,
                    title = "重试次数",
                    subtitle = "拨号失败后的自动重试次数"
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = retryCount.toString(),
                            onValueChange = { value ->
                                value.toIntOrNull()?.let {
                                    retryCount = it.coerceIn(0, 5)
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(80.dp),
                            singleLine = true
                        )
                        Text("次", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 通话辅助设置
            SettingsSection(title = "通话辅助") {
                // 自动添加备注
                SettingItem(
                    icon = Icons.Default.FiberManualRecord,
                    title = "拨号录音",
                    subtitle = "通话建立后自动开始录音，结束后自动保存到应用目录"
                ) {
                    Switch(
                        checked = autoRecordCall,
                        onCheckedChange = { autoRecordCall = it }
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                // 自动添加备注
                SettingItem(
                    icon = Icons.Default.NoteAdd,
                    title = "自动添加备注",
                    subtitle = "通话结束后自动添加备注"
                ) {
                    Switch(
                        checked = autoAddNote,
                        onCheckedChange = { autoAddNote = it }
                    )
                }

                // 默认备注模板
                if (autoAddNote) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = defaultNoteTemplate,
                        onValueChange = { defaultNoteTemplate = it },
                        label = { Text("默认备注模板") },
                        placeholder = { Text("输入默认备注内容...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 提示信息
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "提示",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• 拨号间隔建议设置为 5-15 秒，给系统足够的响应时间\n" +
                                   "• 通话超时建议设置为 30-60 秒，太短可能导致漏接\n" +
                                   "• 拨号录音依赖设备 ROM、录音权限和音频源支持，部分机型可能只能录到单边或无法录制\n" +
                                   "• 重试次数过多可能会影响拨打效率",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing()
    }
}
