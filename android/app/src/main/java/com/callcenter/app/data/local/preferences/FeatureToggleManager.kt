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
     * 录音识别功能
     * 通过实时语音识别判断通话类型
     */
    VOICE_RECOGNITION_DETECTION(
        key = "voice_recognition_detection",
        defaultEnabled = true,  // 默认开启
        displayName = "录音识别",
        description = "通过实时语音识别判断通话类型：识别到文本长度超过阈值则为真人接听，超时未识别则为语音信箱。",
        version = "v1.9.43",
        category = ToggleCategory.CALL_DETECTION
    ),
    
    /**
     * 检测到语音信箱自动挂断并拨打下一个
     */
    AUTO_HANGUP_ON_VOICEMAIL(
        key = "auto_hangup_on_voicemail",
        defaultEnabled = true,  // 默认开启
        displayName = "语音信箱自动挂断",
        description = "当检测到语音信箱时，自动挂断并拨打下一个客户。如果没有下一个客户，则停止自动拨号。",
        version = "v1.9.43",
        category = ToggleCategory.AUTO_DIAL
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
    AUTO_DIAL(
        displayName = "自动拨号",
        description = "自动拨号相关功能"
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
 * val isEnabled = featureToggleManager.isEnabled(FeatureToggle.VOICE_RECOGNITION_DETECTION)
 * 
 * // 监听开关状态变化
 * featureToggleManager.getToggleFlow(FeatureToggle.VOICE_RECOGNITION_DETECTION).collect { enabled ->
 *     // 处理状态变化
 * }
 * 
 * // 设置开关状态
 * featureToggleManager.setEnabled(FeatureToggle.VOICE_RECOGNITION_DETECTION, false)
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
