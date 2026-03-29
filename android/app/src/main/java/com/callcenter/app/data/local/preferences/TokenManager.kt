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
        private val PASSWORD_KEY = stringPreferencesKey("password")
        private val USER_ID_KEY = stringPreferencesKey("user_id")
        private val USER_ROLE_KEY = stringPreferencesKey("user_role")
        private val USER_REAL_NAME_KEY = stringPreferencesKey("user_real_name")
        private val SAVED_ACCOUNTS_KEY = stringPreferencesKey("saved_accounts")
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
     * 保存登录凭证（用于自动填充）
     */
    suspend fun saveCredentials(username: String, password: String) {
        context.dataStore.edit { prefs ->
            prefs[USERNAME_KEY] = username
            prefs[PASSWORD_KEY] = password
        }
    }

    /**
     * 获取保存的密码
     */
    suspend fun getPassword(): String? {
        return context.dataStore.data.map { prefs ->
            prefs[PASSWORD_KEY]
        }.first()
    }

    /**
     * 保存账号历史（支持多账号）
     * 格式: "username1:password1|username2:password2"
     */
    suspend fun saveAccountHistory(username: String, password: String) {
        context.dataStore.edit { prefs ->
            val existing = prefs[SAVED_ACCOUNTS_KEY] ?: ""
            val accounts = if (existing.isEmpty()) {
                mutableListOf()
            } else {
                existing.split("|").toMutableList()
            }
            
            // 移除已存在的相同账号
            accounts.removeAll { it.startsWith("$username:") }
            
            // 添加新账号到开头
            accounts.add(0, "$username:$password")
            
            // 最多保存 5 个账号
            while (accounts.size > 5) {
                accounts.removeAt(accounts.size - 1)
            }
            
            prefs[SAVED_ACCOUNTS_KEY] = accounts.joinToString("|")
        }
    }

    /**
     * 获取保存的账号历史
     * 返回: List<Pair<username, password>>
     */
    suspend fun getSavedAccounts(): List<Pair<String, String>> {
        val accountsStr = context.dataStore.data.map { prefs ->
            prefs[SAVED_ACCOUNTS_KEY]
        }.first() ?: return emptyList()
        
        return accountsStr.split("|").mapNotNull { account ->
            val parts = account.split(":", limit = 2)
            if (parts.size == 2) {
                parts[0] to parts[1]
            } else null
        }
    }

    /**
     * 删除指定账号
     */
    suspend fun removeSavedAccount(username: String) {
        context.dataStore.edit { prefs ->
            val existing = prefs[SAVED_ACCOUNTS_KEY] ?: return@edit
            val accounts = existing.split("|").toMutableList()
            accounts.removeAll { it.startsWith("$username:") }
            if (accounts.isEmpty()) {
                prefs.remove(SAVED_ACCOUNTS_KEY)
            } else {
                prefs[SAVED_ACCOUNTS_KEY] = accounts.joinToString("|")
            }
        }
    }

    /**
     * 清除所有保存的账号
     */
    suspend fun clearSavedAccounts() {
        context.dataStore.edit { prefs ->
            prefs.remove(SAVED_ACCOUNTS_KEY)
            prefs.remove(PASSWORD_KEY)
        }
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
            prefs.remove(PASSWORD_KEY)  // 清除保存的密码
        }
    }

    /**
     * 仅清除认证令牌（切换账号时使用，保留历史账号记录）
     */
    suspend fun clearAuthOnly() {
        context.dataStore.edit { prefs ->
            prefs.remove(TOKEN_KEY)
            prefs.remove(USER_ID_KEY)
            prefs.remove(USER_ROLE_KEY)
            prefs.remove(USER_REAL_NAME_KEY)
            // 保留 USERNAME_KEY 和 PASSWORD_KEY 用于显示
        }
    }
}
