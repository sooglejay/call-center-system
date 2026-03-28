package com.callcenter.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.callcenter.app.data.model.VersionInfo
import com.callcenter.app.util.UpdateState

/**
 * 版本更新对话框
 */
@Composable
fun UpdateDialog(
    versionInfo: VersionInfo?,
    updateState: UpdateState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onRetry: () -> Unit = {}
) {
    if (versionInfo == null) return

    AlertDialog(
        onDismissRequest = {
            // 强制更新时不能取消
            if (!versionInfo.forceUpdate) {
                onDismiss()
            }
        },
        icon = {
            Icon(
                imageVector = Icons.Default.Update,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "发现新版本",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                when (updateState) {
                    is UpdateState.Downloading -> {
                        // 下载中状态
                        Text(
                            text = "正在下载更新...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { updateState.progress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${updateState.progress}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                    is UpdateState.Error -> {
                        // 错误状态
                        Text(
                            text = "更新失败：${updateState.message}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    UpdateState.DownloadComplete -> {
                        // 下载完成
                        Text(
                            text = "下载完成，准备安装...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    UpdateState.Installing -> {
                        // 安装中
                        Text(
                            text = "正在安装...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                    else -> {
                        // 默认状态 - 显示版本信息
                        VersionInfoContent(versionInfo)
                    }
                }
            }
        },
        confirmButton = {
            when (updateState) {
                is UpdateState.Error -> {
                    TextButton(onClick = onRetry) {
                        Text("重试")
                    }
                }
                is UpdateState.Downloading, UpdateState.Installing -> {
                    // 下载/安装中不显示确认按钮
                }
                else -> {
                    Button(onClick = onConfirm) {
                        Text("立即更新")
                    }
                }
            }
        },
        dismissButton = {
            // 强制更新或非错误状态不显示取消按钮
            if (!versionInfo.forceUpdate && updateState !is UpdateState.Error 
                && updateState !is UpdateState.Downloading && updateState != UpdateState.Installing) {
                TextButton(onClick = onDismiss) {
                    Text("稍后更新")
                }
            }
        }
    )
}

/**
 * 版本信息内容
 */
@Composable
private fun VersionInfoContent(versionInfo: VersionInfo) {
    Column {
        // 版本号
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "最新版本",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = versionInfo.versionName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 强制更新提示
        if (versionInfo.forceUpdate) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "⚠️ 此版本为强制更新，必须升级后才能继续使用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 更新日志
        if (!versionInfo.updateLog.isNullOrBlank()) {
            Text(
                text = "更新内容：",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = versionInfo.updateLog,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 检查更新按钮
 */
@Composable
fun CheckUpdateButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Default.Update,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("检查更新")
    }
}

/**
 * 版本信息项
 */
@Composable
fun VersionInfoItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
