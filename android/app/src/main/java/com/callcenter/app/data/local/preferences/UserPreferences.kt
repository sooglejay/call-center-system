package com.callcenter.app.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.userDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

/**
 * 用户偏好设置管理器
 * 用于管理用户相关的偏好设置
 */
class UserPreferences(private val context: Context) {

    companion object {
        private val DATA_ACCESS_TYPE_KEY = stringPreferencesKey("data_access_type")
        private val DEFAULT_VIEW_KEY = stringPreferencesKey("default_view")
        private val LAST_SYNC_TIME_KEY = stringPreferencesKey("last_sync_time")
    }

    /**
     * 保存数据访问类型
     */
    suspend fun saveDataAccessType(accessType: String) {
        context.userDataStore.edit { prefs ->
            prefs[DATA_ACCESS_TYPE_KEY] = accessType
        }
    }

    /**
     * 获取数据访问类型
     */
    suspend fun getDataAccessType(): String? {
        return context.userDataStore.data.map { prefs ->
            prefs[DATA_ACCESS_TYPE_KEY]
        }.first()
    }

    /**
     * 观察数据访问类型变化
     */
    fun observeDataAccessType(): Flow<String?> {
        return context.userDataStore.data.map { prefs ->
            prefs[DATA_ACCESS_TYPE_KEY]
        }
    }

    /**
     * 保存默认视图
     */
    suspend fun saveDefaultView(view: String) {
        context.userDataStore.edit { prefs ->
            prefs[DEFAULT_VIEW_KEY] = view
        }
    }

    /**
     * 获取默认视图
     */
    suspend fun getDefaultView(): String? {
        return context.userDataStore.data.map { prefs ->
            prefs[DEFAULT_VIEW_KEY]
        }.first()
    }

    /**
     * 保存最后同步时间
     */
    suspend fun saveLastSyncTime(time: String) {
        context.userDataStore.edit { prefs ->
            prefs[LAST_SYNC_TIME_KEY] = time
        }
    }

    /**
     * 获取最后同步时间
     */
    suspend fun getLastSyncTime(): String? {
        return context.userDataStore.data.map { prefs ->
            prefs[LAST_SYNC_TIME_KEY]
        }.first()
    }

    /**
     * 清除所有用户偏好设置
     */
    suspend fun clearAll() {
        context.userDataStore.edit { prefs ->
            prefs.clear()
        }
    }
}
