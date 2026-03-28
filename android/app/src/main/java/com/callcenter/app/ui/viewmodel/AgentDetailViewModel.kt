package com.callcenter.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callcenter.app.data.model.AgentStats
import com.callcenter.app.data.model.CreateUserRequest
import com.callcenter.app.data.model.User
import com.callcenter.app.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 客服详情 ViewModel
 */
@HiltViewModel
class AgentDetailViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _agent = MutableStateFlow<User?>(null)
    val agent: StateFlow<User?> = _agent.asStateFlow()

    private val _stats = MutableStateFlow<AgentStats?>(null)
    val stats: StateFlow<AgentStats?> = _stats.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    /**
     * 加载客服信息
     */
    fun loadAgent(agentId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = userRepository.getUser(agentId)

            result.fold(
                onSuccess = { user ->
                    _agent.value = user
                    _error.value = null
                    // 加载统计数据
                    loadAgentStats(agentId)
                },
                onFailure = { exception ->
                    _error.value = exception.message ?: "加载客服信息失败"
                }
            )

            _isLoading.value = false
        }
    }

    /**
     * 加载客服统计数据
     */
    private suspend fun loadAgentStats(agentId: Int) {
        val result = userRepository.getAgentStats(agentId)
        result.fold(
            onSuccess = { stats ->
                _stats.value = stats
            },
            onFailure = {
                // 忽略统计加载失败
            }
        )
    }

    /**
     * 保存客服信息
     */
    fun saveAgent(
        id: Int,
        username: String,
        realName: String,
        password: String?,
        phone: String?,
        email: String?,
        role: String,
        dataAccessType: String
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            _error.value = null
            _saveSuccess.value = false

            val result = if (id == 0) {
                // 创建新客服
                userRepository.createUser(
                    CreateUserRequest(
                        username = username,
                        password = password ?: "",
                        realName = realName,
                        role = role,
                        phone = phone,
                        email = email,
                        dataAccessType = dataAccessType
                    )
                )
            } else {
                // 更新现有客服
                val existingAgent = _agent.value!!
                userRepository.updateUser(
                    userId = id,
                    user = existingAgent.copy(
                        realName = realName,
                        phone = phone,
                        email = email,
                        role = role,
                        dataAccessType = dataAccessType
                    )
                )
            }

            result.fold(
                onSuccess = {
                    _saveSuccess.value = true
                },
                onFailure = { exception ->
                    _error.value = exception.message ?: "保存失败"
                }
            )

            _isSaving.value = false
        }
    }

    /**
     * 重置密码
     */
    fun resetPassword(agentId: Int, newPassword: String?) {
        viewModelScope.launch {
            _error.value = null

            val result = userRepository.resetPassword(agentId, newPassword)

            result.fold(
                onSuccess = {
                    // 密码重置成功
                },
                onFailure = { exception ->
                    _error.value = exception.message ?: "重置密码失败"
                }
            )
        }
    }

    /**
     * 清除错误
     */
    fun clearError() {
        _error.value = null
    }
}
