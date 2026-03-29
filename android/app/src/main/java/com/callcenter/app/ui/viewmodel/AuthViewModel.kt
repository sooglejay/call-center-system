package com.callcenter.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callcenter.app.data.model.User
import com.callcenter.app.data.repository.AuthRepository
import com.callcenter.app.data.repository.CustomerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val customerRepository: CustomerRepository
) : ViewModel() {

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _savedServerUrl = MutableStateFlow("")
    val savedServerUrl: StateFlow<String> = _savedServerUrl.asStateFlow()

    private val _savedUsername = MutableStateFlow("")
    val savedUsername: StateFlow<String> = _savedUsername.asStateFlow()

    private val _savedPassword = MutableStateFlow("")
    val savedPassword: StateFlow<String> = _savedPassword.asStateFlow()

    private val _savedAccounts = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val savedAccounts: StateFlow<List<Pair<String, String>>> = _savedAccounts.asStateFlow()

    init {
        checkLoginState()
        loadSavedAccounts()
    }

    private fun checkLoginState() {
        viewModelScope.launch {
            _isLoggedIn.value = authRepository.isLoggedIn()
            _currentUser.value = authRepository.currentUser.value
            _savedServerUrl.value = authRepository.getServerUrl() ?: ""
            _savedUsername.value = authRepository.getSavedUsername() ?: ""
            _savedPassword.value = authRepository.getSavedPassword() ?: ""
        }
    }

    private fun loadSavedAccounts() {
        viewModelScope.launch {
            _savedAccounts.value = authRepository.getSavedAccounts()
        }
    }

    fun login(username: String, password: String, serverUrl: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = authRepository.login(username, password, serverUrl)

            result.fold(
                onSuccess = { user ->
                    _isLoggedIn.value = true
                    _currentUser.value = user
                    _error.value = null
                    // 刷新保存的账号列表
                    loadSavedAccounts()
                },
                onFailure = { exception ->
                    _error.value = when {
                        exception.message?.contains("Unable to resolve host") == true -> 
                            "无法连接到服务器，请检查网络和服务器地址"
                        exception.message?.contains("timeout") == true -> 
                            "连接超时，请检查服务器地址是否正确"
                        exception.message?.contains("401") == true -> 
                            "用户名或密码错误"
                        exception.message?.contains("403") == true -> 
                            "没有权限访问"
                        exception.message?.contains("404") == true -> 
                            "服务器地址不存在，请检查 URL"
                        else -> exception.message ?: "登录失败，请重试"
                    }
                }
            )

            _isLoading.value = false
        }
    }

    fun logout(onLogoutComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            // 先清空客户数据
            customerRepository.clearAllData()
            // 执行退出登录（清除所有认证信息）
            authRepository.logout()
            _isLoggedIn.value = false
            _currentUser.value = null
            // 回调通知退出完成
            onLogoutComplete?.invoke()
        }
    }

    /**
     * 切换账号：只清除认证状态，保留历史账号记录
     */
    fun switchAccount(onSwitchComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            // 清空客户数据
            customerRepository.clearAllData()
            // 只清除认证令牌和当前用户信息，保留历史账号
            authRepository.clearAuthOnly()
            _isLoggedIn.value = false
            _currentUser.value = null
            // 回调通知切换完成
            onSwitchComplete?.invoke()
        }
    }

    fun removeSavedAccount(username: String) {
        viewModelScope.launch {
            authRepository.removeSavedAccount(username)
            loadSavedAccounts()
        }
    }

    fun clearError() {
        _error.value = null
    }
}
