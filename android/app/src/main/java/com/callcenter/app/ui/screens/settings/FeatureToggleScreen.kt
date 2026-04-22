package com.callcenter.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.callcenter.app.data.local.preferences.FeatureToggle
import com.callcenter.app.data.local.preferences.ToggleCategory
import com.callcenter.app.ui.viewmodel.FeatureToggleViewModel
import com.callcenter.app.util.VersionInfoUtil

/**
 * 功能开关设置页面
 * 
 * 用于管理所有功能开关，支持：
 * 1. 按分类展示开关
 * 2. 显示开关版本和描述
 * 3. 一键重置所有开关
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureToggleScreen(
    onNavigateBack: () -> Unit,
    viewModel: FeatureToggleViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val toggles by viewModel.toggles.collectAsState()
    
    // 按分类分组
    val togglesByCategory = remember(toggles) {
        FeatureToggle.entries.groupBy { it.category }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(VersionInfoUtil.getTitleWithVersion(context, "功能开关")) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
