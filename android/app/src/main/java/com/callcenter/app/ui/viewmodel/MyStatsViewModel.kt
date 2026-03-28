package com.callcenter.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callcenter.app.data.model.Stats
import com.callcenter.app.data.repository.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 个人统计 ViewModel
 */
@HiltViewModel
class MyStatsViewModel @Inject constructor(
    private val statsRepository: StatsRepository
) : ViewModel() {

    private val _stats = MutableStateFlow<Stats?>(null)
    val stats: StateFlow<Stats?> = _stats.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadStats()
    }

    /**
     * 加载统计数据
     */
    private fun loadStats() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = statsRepository.getMyStats()

            result.fold(
                onSuccess = { stats ->
                    _stats.value = stats
                    _error.value = null
                },
                onFailure = { exception ->
                    _error.value = exception.message ?: "加载统计数据失败"
                }
            )

            _isLoading.value = false
        }
    }

    /**
     * 刷新数据
     */
    fun refresh() {
        loadStats()
    }
}
