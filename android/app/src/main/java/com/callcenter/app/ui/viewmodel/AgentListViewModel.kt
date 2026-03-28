package com.callcenter.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callcenter.app.data.model.User
import com.callcenter.app.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 客服列表 ViewModel
 */
@HiltViewModel
class AgentListViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _agents = MutableStateFlow<List<User>>(emptyList())
    val agents: StateFlow<List<User>> = _agents.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadAgents()
    }

    /**
     * 加载客服列表
     */
    private fun loadAgents() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = userRepository.getUsers(role = "agent")

            result.fold(
                onSuccess = { users ->
                    _agents.value = users
                    _error.value = null
                },
                onFailure = { exception ->
                    _error.value = exception.message ?: "加载客服列表失败"
                }
            )

            _isLoading.value = false
        }
    }

    /**
     * 刷新数据
     */
    fun refresh() {
        loadAgents()
    }

    /**
     * 切换客服状态
     */
    fun toggleAgentStatus(agentId: Int, enabled: Boolean) {
        viewModelScope.launch {
            val result = userRepository.updateDataAccess(
                userId = agentId,
                dataAccessType = if (enabled) "all" else "disabled"
            )

            result.fold(
                onSuccess = {
                    // 更新本地列表
                    _agents.value = _agents.value.map { agent ->
                        if (agent.id == agentId) {
                            agent.copy(dataAccessType = if (enabled) "all" else "disabled")
                        } else agent
                    }
                },
                onFailure = { exception ->
                    _error.value = exception.message ?: "更新状态失败"
                }
            )
        }
    }
}
