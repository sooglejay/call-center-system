package com.callcenter.app.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appConfigDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_config")

/**
 * 应用配置项定义（数值类型）
 * 
 * 用于存储可配置的数值参数
 */
enum class AppConfig(
    val key: String,
    val defaultValue: Int,
    val displayName: String,
    val description: String,
    val minValue: Int,
    val maxValue: Int,
    val unit: String = ""
) {
    /**
     * 实时识别超时时间（秒）
     * 在 OFFHOOK 状态下，多长时间内未识别到文本则判定为语音信箱
     */
    REALTIME_RECOGNITION_TIMEOUT(
        key = "realtime_recognition_timeout",
        defaultValue = 6,
        displayName = "实时识别超时",
        description = "通话接通后，多长时间内未识别到语音则判定为语音信箱",
        minValue = 3,
        maxValue = 15,
        unit = "秒"
    ),
    
    /**
     * 文本长度阈值
     * 识别到的文本长度超过此值则判定为真人接听
     */
    TEXT_LENGTH_THRESHOLD(
        key = "text_length_threshold",
        defaultValue = 5,
        displayName = "文本长度阈值",
        description = "识别到的文本长度超过此值则判定为真人接听",
        minValue = 1,
        maxValue = 20,
        unit = "字"
    ),
    
    /**
     * 自动拨号间隔（秒）
     */
    AUTO_DIAL_INTERVAL(
        key = "auto_dial_interval",
        defaultValue = 5,
        displayName = "自动拨号间隔",
        description = "每次拨号完成后等待的时间",
        minValue = 2,
        maxValue = 60,
        unit = "秒"
    ),
    
    /**
     * 通话超时时间（秒）
     */
    CALL_TIMEOUT(
        key = "call_timeout",
        defaultValue = 30,
        displayName = "通话超时时间",
        description = "等待对方接听的最大时间",
        minValue = 10,
        maxValue = 120,
        unit = "秒"
    );

    // 转换为 Preferences Key
    internal fun toPreferenceKey(): Preferences.Key<Int> {
        return intPreferencesKey(key)
    }
}

/**
 * 应用配置管理器
 * 
 * 使用方式：
 * ```kotlin
 * // 获取配置值
 * val timeout = appConfigManager.getValue(AppConfig.REALTIME_RECOGNITION_TIMEOUT)
 * 
 * // 监听配置变化
 * appConfigManager.getValueFlow(AppConfig.REALTIME_RECOGNITION_TIMEOUT).collect { value ->
 *     // 处理配置变化
 * }
 * 
 * // 设置配置值
 * appConfigManager.setValue(AppConfig.REALTIME_RECOGNITION_TIMEOUT, 8)
 * ```
 */
@Singleton
class AppConfigManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * 获取配置值 Flow
     */
    fun getValueFlow(config: AppConfig): Flow<Int> {
        return context.appConfigDataStore.data.map { prefs ->
            prefs[config.toPreferenceKey()] ?: config.defaultValue
        }
    }

    /**
     * 获取所有配置值 Flow
     */
    fun getAllValuesFlow(): Flow<Map<AppConfig, Int>> {
        return context.appConfigDataStore.data.map { prefs ->
            AppConfig.entries.associateWith { config ->
                prefs[config.toPreferenceKey()] ?: config.defaultValue
            }
        }
    }

    /**
     * 获取配置值（挂起函数）
     */
    suspend fun getValue(config: AppConfig): Int {
        return context.appConfigDataStore.data.map { prefs ->
            prefs[config.toPreferenceKey()] ?: config.defaultValue
        }.first()
    }

    /**
     * 设置配置值
     */
    suspend fun setValue(config: AppConfig, value: Int) {
        // 确保值在有效范围内
        val clampedValue = value.coerceIn(config.minValue, config.maxValue)
        context.appConfigDataStore.edit { prefs ->
            prefs[config.toPreferenceKey()] = clampedValue
        }
    }

    /**
     * 重置配置为默认值
     */
    suspend fun resetToDefault(config: AppConfig) {
        context.appConfigDataStore.edit { prefs ->
            prefs.remove(config.toPreferenceKey())
        }
    }

    /**
     * 重置所有配置为默认值
     */
    suspend fun resetAllToDefault() {
        context.appConfigDataStore.edit { prefs ->
            AppConfig.entries.forEach { config ->
                prefs.remove(config.toPreferenceKey())
            }
        }
    }

    /**
     * 批量设置配置值
     */
    suspend fun setValues(values: Map<AppConfig, Int>) {
        context.appConfigDataStore.edit { prefs ->
            values.forEach { (config, value) ->
                val clampedValue = value.coerceIn(config.minValue, config.maxValue)
                prefs[config.toPreferenceKey()] = clampedValue
            }
        }
    }
}
