package com.callcenter.app.data.repository

import com.callcenter.app.data.api.ApiService
import com.callcenter.app.data.local.preferences.TokenManager
import com.callcenter.app.data.model.LoginRequest
import com.callcenter.app.data.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 认证仓库
 */
@Singleton
class AuthRepository @Inject constructor(
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) {
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    /**
     * 登录
     */
    suspend fun login(username: String, password: String, serverUrl: String): Result<User> {
        return try {
            // 先保存服务器地址
            tokenManager.saveServerUrl(serverUrl)
            
            val response = apiService.login(LoginRequest(username, password))
            if (response.isSuccessful && response.body() != null) {
                val loginResponse = response.body()!!
                tokenManager.saveToken(loginResponse.token)
                tokenManager.saveUserInfo(
                    userId = loginResponse.user.id,
                    username = loginResponse.user.username,
                    role = loginResponse.user.role,
                    realName = loginResponse.user.realName
                )
                _currentUser.value = loginResponse.user
                Result.success(loginResponse.user)
            } else {
                Result.failure(Exception(response.message() ?: "登录失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取当前用户
     */
    suspend fun getCurrentUser(): Result<User> {
        return try {
            val response = apiService.getCurrentUser()
            if (response.isSuccessful && response.body() != null) {
                _currentUser.value = response.body()
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("获取用户信息失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 检查是否已登录
     */
    suspend fun isLoggedIn(): Boolean {
        val isLoggedIn = tokenManager.isLoggedIn()
        if (isLoggedIn && _currentUser.value == null) {
            // 尝试获取用户信息
            getCurrentUser()
        }
        return isLoggedIn
    }

    /**
     * 登出
     */
    suspend fun logout() {
        tokenManager.clearAuth()
        _currentUser.value = null
    }

    /**
     * 获取保存的服务器地址
     */
    suspend fun getServerUrl(): String? {
        return tokenManager.getServerUrl()
    }

    /**
     * 获取保存的用户名
     */
    suspend fun getSavedUsername(): String? {
        return tokenManager.getUsername()
    }
}
