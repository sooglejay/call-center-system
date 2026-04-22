package com.callcenter.app.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.featureToggleDataStore: DataStore<Preferences> by preferencesDataStore(name = "feature_toggles")

/**
 * 功能开关定义
 * 
 * 所有新功能都应该通过开关控制，方便：
 * 1. 灰度发布：逐步放开用户群体
 * 2. 紧急回滚：发现问题可立即关闭
 * 3. A/B测试：对比新旧功能效果
 * 4. 用户选择：让用户自主决定是否使用新功能
 */
enum class FeatureToggle(
    val key: String,
    val defaultEnabled: Boolean,
    val displayName: String,
    val description: String,
    val version: String,  // 引入版本号，方便追踪
    val category: ToggleCategory
) {
    /**
     * 智能通话结果识别
     * 使用分层判断策略，更准确地区分"用户接听"和"语音信箱"
     */
    SMART_CALL_RESULT_DETECTION(
        key = "smart_call_result_detection",
        defaultEnabled = true,  // 默认开启新功能
        displayName = "智能通话结果识别",
        description = "使用分层判断策略，更准确地区分\"用户接听\"和\"语音信箱\"。关闭后将使用传统的时长阈值判断。",
        version = "v1.9.32",
        category = ToggleCategory.CALL_DETECTION
    ),
    
    /**
     * 音频能量分析（第二层判断）
     * 依赖录音权限，分析通话音频能量模式
     */
    AUDIO_ENERGY_ANALYSIS(
        key = "audio_energy_analysis",
        defaultEnabled = false,  // 默认关闭，需要录音权限
        displayName = "音频能量分析",
        description = "通过分析通话音频能量模式，识别单向播放（语音信箱）和双向对话。需要录音权限。",
        version = "v1.9.32",
        category = ToggleCategory.CALL_DETECTION
    ),
    
    /**
     * 预留：AI关键词识别（第三层判断）
     */
    AI_KEYWORD_DETECTION(
        key = "ai_keyword_detection",
        defaultEnabled = false,  // 默认关闭，需要额外资源
        displayName = "AI关键词识别",
        description = "通过语音识别检测关键词，判断通话类型。实验性功能，可能影响性能。",
        version = "v1.9.32",
        category = ToggleCategory.CALL_DETECTION
    ),
    
    /**
     * 预留：Root精确状态检测
     */
    ROOT_CALL_STATE_DETECTION(
        key = "root_call_state_detection",
        defaultEnabled = true,  // Root设备默认开启
        displayName = "Root精确状态检测",
        description = "通过读取RIL日志获取更精确的通话状态。仅Root设备可用。",
        version = "v1.9.30",
        category = ToggleCategory.CALL_DETECTION
    );

    // 转换为 Preferences Key
    internal fun toPreferenceKey(): Preferences.Key<Boolean> {
        return booleanPreferencesKey(key)
    }
}

/**
 * 功能开关分类
 */
enum class ToggleCategory(
    val displayName: String,
    val description: String
) {
    CALL_DETECTION(
        displayName = "通话识别",
        description = "通话状态检测相关功能"
    ),
    UI_EXPERIMENT(
        displayName = "界面体验",
        description = "界面交互相关功能"
    ),
    PERFORMANCE(
        displayName = "性能优化",
        description = "性能相关功能"
    ),
    EXPERIMENTAL(
        displayName = "实验性功能",
        description = "正在测试中的功能"
    )
}

/**
 * 功能开关管理器
 * 
 * 使用方式：
 * ```kotlin
 * // 检查开关状态
 * val isEnabled = featureToggleManager.isEnabled(FeatureToggle.SMART_CALL_RESULT_DETECTION)
 * 
 * // 监听开关状态变化
 * featureToggleManager.getToggleFlow(FeatureToggle.SMART_CALL_RESULT_DETECTION).collect { enabled ->
 *     // 处理状态变化
 * }
 * 
 * // 设置开关状态
 * featureToggleManager.setEnabled(FeatureToggle.SMART_CALL_RESULT_DETECTION, false)
 * ```
 */
class FeatureToggleManager(private val context: Context) {

    /**
     * 获取开关状态 Flow
     */
    fun getToggleFlow(toggle: FeatureToggle): Flow<Boolean> {
        return context.featureToggleDataStore.data.map { prefs ->
            prefs[toggle.toPreferenceKey()] ?: toggle.defaultEnabled
        }
    }

    /**
     * 获取所有开关状态 Flow
     */
    fun getAllTogglesFlow(): Flow<Map<FeatureToggle, Boolean>> {
        return context.featureToggleDataStore.data.map { prefs ->
            FeatureToggle.entries.associateWith { toggle ->
                prefs[toggle.toPreferenceKey()] ?: toggle.defaultEnabled
            }
        }
    }

    /**
     * 获取开关状态（挂起函数）
     */
    suspend fun isEnabled(toggle: FeatureToggle): Boolean {
        return context.featureToggleDataStore.data.map { prefs ->
            prefs[toggle.toPreferenceKey()] ?: toggle.defaultEnabled
        }.first()
    }

    /**
     * 设置开关状态
     */
    suspend fun setEnabled(toggle: FeatureToggle, enabled: Boolean) {
        context.featureToggleDataStore.edit { prefs ->
            prefs[toggle.toPreferenceKey()] = enabled
        }
    }

    /**
     * 重置开关为默认值
     */
    suspend fun resetToDefault(toggle: FeatureToggle) {
        context.featureToggleDataStore.edit { prefs ->
            prefs.remove(toggle.toPreferenceKey())
        }
    }

    /**
     * 重置所有开关为默认值
     */
    suspend fun resetAllToDefault() {
        context.featureToggleDataStore.edit { prefs ->
            FeatureToggle.entries.forEach { toggle ->
                prefs.remove(toggle.toPreferenceKey())
            }
        }
    }

    /**
     * 获取某个分类下的所有开关
     */
    fun getTogglesByCategory(category: ToggleCategory): List<FeatureToggle> {
        return FeatureToggle.entries.filter { it.category == category }
    }

    /**
     * 批量设置开关状态
     */
    suspend fun setToggles(toggles: Map<FeatureToggle, Boolean>) {
        context.featureToggleDataStore.edit { prefs ->
            toggles.forEach { (toggle, enabled) ->
                prefs[toggle.toPreferenceKey()] = enabled
            }
        }
    }
}
