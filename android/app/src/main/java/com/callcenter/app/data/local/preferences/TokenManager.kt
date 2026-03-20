package com.callcenter.app.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

/**
 * Token 管理器
 * 使用 DataStore 存储认证信息
 */
class TokenManager(private val context: Context) {

    companion object {
        private val TOKEN_KEY = stringPreferencesKey("auth_token")
        private val SERVER_URL_KEY = stringPreferencesKey("server_url")
        private val USERNAME_KEY = stringPreferencesKey("username")
        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val USER_ROLE_KEY = stringPreferencesKey("user_role")
        private val USER_REAL_NAME_KEY = stringPreferencesKey("user_real_name")
    }

    /**
     * 保存认证令牌
     */
    suspend fun saveToken(token: String) {
        context.dataStore.edit { prefs ->
            prefs[TOKEN_KEY] = token
        }
    }

    /**
     * 获取认证令牌
     */
    suspend fun getToken(): String? {
        return context.dataStore.data.map { prefs ->
            prefs[TOKEN_KEY]
        }.first()
    }

    /**
     * 保存服务器地址
     */
    suspend fun saveServerUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[SERVER_URL_KEY] = url
        }
    }

    /**
     * 获取服务器地址
     */
    suspend fun getServerUrl(): String? {
        return context.dataStore.data.map { prefs ->
            prefs[SERVER_URL_KEY]
        }.first()
    }

    /**
     * 保存用户信息
     */
    suspend fun saveUserInfo(
        userId: Int,
        username: String,
        role: String,
        realName: String
    ) {
        context.dataStore.edit { prefs ->
            prefs[USER_ID_KEY] = userId.toString()
            prefs[USERNAME_KEY] = username
            prefs[USER_ROLE_KEY] = role
            prefs[USER_REAL_NAME_KEY] = realName
        }
    }

    /**
     * 获取用户ID
     */
    suspend fun getUserId(): Int? {
        return context.dataStore.data.map { prefs ->
            prefs[USER_ID_KEY]?.toIntOrNull()
        }.first()
    }

    /**
     * 获取用户名
     */
    suspend fun getUsername(): String? {
        return context.dataStore.data.map { prefs ->
            prefs[USERNAME_KEY]
        }.first()
    }

    /**
     * 获取用户角色
     */
    suspend fun getUserRole(): String? {
        return context.dataStore.data.map { prefs ->
            prefs[USER_ROLE_KEY]
        }.first()
    }

    /**
     * 获取用户真实姓名
     */
    suspend fun getUserRealName(): String? {
        return context.dataStore.data.map { prefs ->
            prefs[USER_REAL_NAME_KEY]
        }.first()
    }

    /**
     * 检查是否已登录
     */
    suspend fun isLoggedIn(): Boolean {
        return getToken() != null
    }

    /**
     * 清除所有认证信息（登出）
     */
    suspend fun clearAuth() {
        context.dataStore.edit { prefs ->
            prefs.remove(TOKEN_KEY)
            prefs.remove(USER_ID_KEY)
            prefs.remove(USERNAME_KEY)
            prefs.remove(USER_ROLE_KEY)
            prefs.remove(USER_REAL_NAME_KEY)
        }
    }
}
