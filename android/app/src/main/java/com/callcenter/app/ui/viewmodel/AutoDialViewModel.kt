package com.callcenter.app.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.callcenter.app.data.model.Customer
import com.callcenter.app.data.model.Task
import com.callcenter.app.service.AutoDialService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 自动拨号范围类型
 */
enum class AutoDialScopeType {
    ALL_PENDING,      // 全部待拨打客户
    SPECIFIC_TASK,    // 指定任务
    ALL_TASKS         // 全部任务客户
}

/**
 * 自动拨号配置
 */
data class AutoDialConfig(
    val scopeType: AutoDialScopeType = AutoDialScopeType.ALL_PENDING,
    val taskId: Int? = null,
    val taskTitle: String? = null,
    val intervalSeconds: Int = 10,
    val timeoutSeconds: Int = 30
)

@HiltViewModel
class AutoDialViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _currentCustomer = MutableStateFlow<Customer?>(null)
    val currentCustomer: StateFlow<Customer?> = _currentCustomer.asStateFlow()

    private val _dialedCount = MutableStateFlow(0)
    val dialedCount: StateFlow<Int> = _dialedCount.asStateFlow()

    private val _totalCount = MutableStateFlow(0)
    val totalCount: StateFlow<Int> = _totalCount.asStateFlow()

    private val _intervalSeconds = MutableStateFlow(10)
    val intervalSeconds: StateFlow<Int> = _intervalSeconds.asStateFlow()

    private val _timeoutSeconds = MutableStateFlow(30)
    val timeoutSeconds: StateFlow<Int> = _timeoutSeconds.asStateFlow()

    // 当前拨号配置
    private val _currentConfig = MutableStateFlow<AutoDialConfig?>(null)
    val currentConfig: StateFlow<AutoDialConfig?> = _currentConfig.asStateFlow()

    init {
        // 监听服务状态
        viewModelScope.launch {
            AutoDialService.isRunning.collect { running ->
                _isRunning.value = running
            }
        }
        viewModelScope.launch {
            AutoDialService.currentCustomer.collect { customer ->
                _currentCustomer.value = customer
            }
        }
        viewModelScope.launch {
            AutoDialService.dialedCount.collect { count ->
                _dialedCount.value = count
            }
        }
        viewModelScope.launch {
            AutoDialService.taskCompleted.collect { taskId ->
                taskId?.let { onTaskCompleted(it) }
            }
        }
    }

    /**
     * 任务完成回调
     */
    private var onTaskCompleted: (Int) -> Unit = {}

    fun setOnTaskCompletedListener(listener: (Int) -> Unit) {
        onTaskCompleted = listener
    }

    /**
     * 开始自动拨号
     * @param customers 要拨打的客户列表
     * @param config 拨号配置
     */
    fun startAutoDial(customers: List<Customer>, config: AutoDialConfig) {
        if (customers.isEmpty()) return

        _totalCount.value = customers.size
        _intervalSeconds.value = config.intervalSeconds
        _timeoutSeconds.value = config.timeoutSeconds
        _currentConfig.value = config

        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, AutoDialService::class.java).apply {
            action = AutoDialService.ACTION_START
            putExtra(AutoDialService.EXTRA_INTERVAL, config.intervalSeconds)
            putExtra(AutoDialService.EXTRA_TIMEOUT, config.timeoutSeconds)
            putExtra(AutoDialService.EXTRA_CUSTOMERS, ArrayList(customers))
            putExtra(AutoDialService.EXTRA_SCOPE_TYPE, config.scopeType.name)
            putExtra(AutoDialService.EXTRA_TASK_ID, config.taskId ?: -1)
        }
        context.startService(intent)
    }

    /**
     * 开始自动拨号（兼容旧版本）
     */
    fun startAutoDial(customers: List<Customer>, interval: Int = 10, timeout: Int = 30) {
        val config = AutoDialConfig(
            scopeType = AutoDialScopeType.ALL_PENDING,
            intervalSeconds = interval,
            timeoutSeconds = timeout
        )
        startAutoDial(customers, config)
    }

    fun stopAutoDial() {
        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, AutoDialService::class.java).apply {
            action = AutoDialService.ACTION_STOP
        }
        context.startService(intent)
        _currentConfig.value = null
    }

    fun pauseAutoDial() {
        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, AutoDialService::class.java).apply {
            action = AutoDialService.ACTION_PAUSE
        }
        context.startService(intent)
    }

    fun resumeAutoDial() {
        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, AutoDialService::class.java).apply {
            action = AutoDialService.ACTION_RESUME
        }
        context.startService(intent)
    }

    fun setInterval(seconds: Int) {
        _intervalSeconds.value = seconds
    }

    fun setTimeout(seconds: Int) {
        _timeoutSeconds.value = seconds
    }

    /**
     * 获取拨号范围的描述文本
     */
    fun getScopeDescription(config: AutoDialConfig?): String {
        return when (config?.scopeType) {
            AutoDialScopeType.ALL_PENDING -> "全部待拨打客户"
            AutoDialScopeType.SPECIFIC_TASK -> "任务: ${config.taskTitle ?: "#${config.taskId}"}"
            AutoDialScopeType.ALL_TASKS -> "全部任务客户"
            else -> "未知范围"
        }
    }
}
