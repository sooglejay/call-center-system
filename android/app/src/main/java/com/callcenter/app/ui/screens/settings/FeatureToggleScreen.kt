package com.callcenter.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.callcenter.app.data.local.preferences.FeatureToggle
import com.callcenter.app.data.local.preferences.ToggleCategory
import com.callcenter.app.ui.util.rememberDebounceOnClick
import com.callcenter.app.ui.viewmodel.FeatureToggleViewModel
import com.callcenter.app.ui.viewmodel.SettingsViewModel
import com.callcenter.app.util.VersionInfoUtil

/**
 * 功能开关设置页面
 * 
 * 用于管理所有功能开关，支持：
 * 1. 按分类展示开关
 * 2. 显示开关版本和描述
 * 3. 一键重置所有开关
 * 4. 语音识别模型下载
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureToggleScreen(
    onNavigateBack: () -> Unit,
    viewModel: FeatureToggleViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val toggles by viewModel.toggles.collectAsState()
    val voskModelState by settingsViewModel.voskModelState.collectAsState()
    
    // 按分类分组
    val togglesByCategory = remember(toggles) {
        FeatureToggle.entries.groupBy { it.category }
    }
    
    LaunchedEffect(Unit) {
        settingsViewModel.checkVoskModelState(context)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(VersionInfoUtil.getTitleWithVersion(context, "功能开关")) },
                navigationIcon = {
                    IconButton(onClick = rememberDebounceOnClick() { onNavigateBack() }) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.resetAllToDefault() }
                    ) {
                        Text("重置全部")
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
            // 说明卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Science,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "功能开关说明",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• 开启新功能可体验最新优化\n" +
                                   "• 关闭可回退到旧版本行为\n" +
                                   "• 如遇到问题，可关闭对应开关",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // ==================== 语音识别模型 ====================
            Text(
                text = "语音识别模型",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "(用于识别语音信箱)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            // 模型状态卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (voskModelState.hasAnyModel)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.RecordVoiceOver,
                            null,
                            tint = if (voskModelState.hasAnyModel)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "离线语音识别",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                if (voskModelState.allModelsReady) {
                                    Surface(
                                        color = Color(0xFF4CAF50),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "全部就绪",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "总大小: ${if (voskModelState.totalSize.isNotEmpty()) voskModelState.totalSize else "0 KB"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (!voskModelState.allModelsReady && voskModelState.models.isNotEmpty()) {
                                Text(
                                    text = "支持中英文双语识别",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (voskModelState.isDownloadingAll) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else if (!voskModelState.allModelsReady && voskModelState.models.isNotEmpty()) {
                            TextButton(onClick = { settingsViewModel.downloadAllVoskModels(context) }) {
                                Text("下载全部")
                            }
                        }
                    }
                }
            }
            
            // 显示每个模型的状态
            voskModelState.models.forEach { modelStatus ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clickable(
                            enabled = !modelStatus.isDownloading && !voskModelState.isDownloadingAll,
                            onClick = {
                                if (modelStatus.isReady) {
                                    settingsViewModel.deleteVoskModel(context, modelStatus.name)
                                } else {
                                    settingsViewModel.downloadVoskModel(context, modelStatus.name)
                                }
                            }
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (modelStatus.isReady)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = modelStatus.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                when {
                                    modelStatus.isReady -> {
                                        Surface(
                                            color = Color(0xFF4CAF50),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "已下载",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    modelStatus.isDownloading -> {
                                        Surface(
                                            color = MaterialTheme.colorScheme.secondary,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "下载中 ${modelStatus.downloadProgress}%",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    modelStatus.error != null -> {
                                        Surface(
                                            color = MaterialTheme.colorScheme.error,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "下载失败",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            when {
                                modelStatus.isDownloading -> {
                                    LinearProgressIndicator(
                                        progress = { modelStatus.downloadProgress / 100f },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                modelStatus.isReady -> {
                                    Text(
                                        text = "大小: ${modelStatus.size} | 点击删除",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                modelStatus.error != null -> {
                                    Text(
                                        text = modelStatus.error,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                else -> {
                                    Text(
                                        text = "离线语音识别模型（约50MB）| 点击下载",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        
                        when {
                            modelStatus.isDownloading -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                            modelStatus.isReady -> {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "已下载",
                                    tint = Color(0xFF4CAF50)
                                )
                            }
                            else -> {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = "下载",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 按分类展示开关
            togglesByCategory.forEach { (category, featureToggles) ->
                ToggleCategorySection(
                    category = category,
                    toggles = featureToggles,
                    toggleStates = toggles,
                    onToggleChange = { toggle, enabled ->
                        viewModel.setToggle(toggle, enabled)
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ToggleCategorySection(
    category: ToggleCategory,
    toggles: List<FeatureToggle>,
    toggleStates: Map<FeatureToggle, Boolean>,
    onToggleChange: (FeatureToggle, Boolean) -> Unit
) {
    Column {
        // 分类标题
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Text(
                text = category.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "(${category.description})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // 开关列表
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                toggles.forEachIndexed { index, toggle ->
                    ToggleItem(
                        toggle = toggle,
                        isEnabled = toggleStates[toggle] ?: toggle.defaultEnabled,
                        onEnabledChange = { enabled ->
                            onToggleChange(toggle, enabled)
                        }
                    )
                    
                    if (index < toggles.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleItem(
    toggle: FeatureToggle,
    isEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = toggle.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // 版本标签
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (isEnabled) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = toggle.version,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = if (isEnabled)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = toggle.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // 显示默认值提示
                if (toggle.defaultEnabled != isEnabled) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (toggle.defaultEnabled) "默认开启，已手动关闭" else "默认关闭，已手动开启",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            
            Switch(
                checked = isEnabled,
                onCheckedChange = onEnabledChange
            )
        }
    }
}
