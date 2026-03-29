package com.callcenter.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callcenter.app.data.local.preferences.CallSettingsManager
import com.callcenter.app.data.local.preferences.TokenManager
import com.callcenter.app.data.model.CallSettings
import com.callcenter.app.data.model.Stats
import com.callcenter.app.data.model.User
import com.callcenter.app.data.repository.AuthRepository
import com.callcenter.app.data.repository.StatsRepository
import com.callcenter.app.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val statsRepository: StatsRepository,
    private val tokenManager: TokenManager,
    private val callSettingsManager: CallSettingsManager
) : ViewModel() {

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _stats = MutableStateFlow<Stats?>(null)
    val stats: StateFlow<Stats?> = _stats.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _callSettings = MutableStateFlow(CallSettings())
    val callSettings: StateFlow<CallSettings> = _callSettings.asStateFlow()

    fun loadSettings() {
        viewModelScope.launch {
            _isLoading.value = true

            // 加载当前用户
            _currentUser.value = authRepository.currentUser.value

            // 加载服务器地址
            _serverUrl.value = tokenManager.getServerUrl() ?: Constants.DEFAULT_SERVER_URL

            // 加载统计数据
            loadStats()

            // 加载通话设置
            callSettingsManager.callSettingsFlow.collect { settings ->
                _callSettings.value = settings
            }

            _isLoading.value = false
        }
    }

    private suspend fun loadStats() {
        val result = statsRepository.getMyStats()
        result.fold(
            onSuccess = { stats ->
                _stats.value = stats
            },
            onFailure = {
                // 忽略错误
            }
        )
    }

    fun updateServerUrl(url: String) {
        viewModelScope.launch {
            // 确保服务器地址以 /api/ 结尾
            val normalizedUrl = if (url.endsWith("/api/")) url else "$url/api/"
            tokenManager.saveServerUrl(normalizedUrl)
            _serverUrl.value = normalizedUrl
        }
    }

    fun updateAutoDialInterval(seconds: Int) {
        viewModelScope.launch {
            callSettingsManager.saveAutoDialInterval(seconds)
        }
    }

    fun updateCallTimeout(seconds: Int) {
        viewModelScope.launch {
            callSettingsManager.saveCallTimeout(seconds)
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }
}
