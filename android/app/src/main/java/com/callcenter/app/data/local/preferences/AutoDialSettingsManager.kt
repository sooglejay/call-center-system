package com.callcenter.app.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.autoDialSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "auto_dial_settings")

/**
 * 自动拨号设置管理器
 * 用于保存自动拨号相关的用户设置
 */
class AutoDialSettingsManager(private val context: Context) {

    companion object {
        private val IS_AUTO_DIAL_MODE = booleanPreferencesKey("is_auto_dial_mode")

        // 默认手动模式（更安全）
        const val DEFAULT_AUTO_DIAL_MODE = false
    }

    /**
     * 获取自动拨号模式设置流
     */
    val isAutoDialModeFlow: Flow<Boolean> = context.autoDialSettingsDataStore.data.map { prefs ->
        prefs[IS_AUTO_DIAL_MODE] ?: DEFAULT_AUTO_DIAL_MODE
    }

    /**
     * 保存自动拨号模式设置
     */
    suspend fun setAutoDialMode(isAutoMode: Boolean) {
        context.autoDialSettingsDataStore.edit { prefs ->
            prefs[IS_AUTO_DIAL_MODE] = isAutoMode
        }
    }

    /**
     * 获取当前自动拨号模式设置
     */
    suspend fun getAutoDialMode(): Boolean {
        var isAutoMode = DEFAULT_AUTO_DIAL_MODE
        context.autoDialSettingsDataStore.data.collect { prefs ->
            isAutoMode = prefs[IS_AUTO_DIAL_MODE] ?: DEFAULT_AUTO_DIAL_MODE
        }
        return isAutoMode
    }
}
