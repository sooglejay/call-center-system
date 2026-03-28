package com.callcenter.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callcenter.app.data.model.DashboardStats
import com.callcenter.app.data.repository.DashboardRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 仪表盘 ViewModel
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dashboardRepository: DashboardRepository
) : ViewModel() {

    private val _dashboardStats = MutableStateFlow<DashboardStats?>(null)
    val dashboardStats: StateFlow<DashboardStats?> = _dashboardStats.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadDashboardStats()
    }

    /**
     * 加载仪表盘统计数据
     */
    private fun loadDashboardStats() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = dashboardRepository.getDashboardStats()

            result.fold(
                onSuccess = { stats ->
                    _dashboardStats.value = stats
                    _error.value = null
                },
                onFailure = { exception ->
                    _error.value = exception.message ?: "加载数据失败"
                }
            )

            _isLoading.value = false
        }
    }

    /**
     * 刷新数据
     */
    fun refresh() {
        loadDashboardStats()
    }
}
