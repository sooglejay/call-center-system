package com.callcenter.app.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.callcenter.app.data.model.Customer
import com.callcenter.app.data.model.Task
import com.callcenter.app.data.local.preferences.AutoDialProgressManager
import com.callcenter.app.data.local.preferences.AutoDialProgress
import com.callcenter.app.service.AutoDialService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    val timeoutSeconds: Int = 30,
    val dialsPerCustomer: Int = 1  // 每个客户连续拨打次数
)

@HiltViewModel
class AutoDialViewModel @Inject constructor(
    application: Application,
    private val progressManager: AutoDialProgressManager
) : AndroidViewModel(application) {

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _isAutoDialMode = MutableStateFlow(true)
    val isAutoDialMode: StateFlow<Boolean> = _isAutoDialMode.asStateFlow()

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

    // 可恢复的进度
    private val _recoverableProgress = MutableStateFlow<AutoDialProgress?>(null)
    val recoverableProgress: StateFlow<AutoDialProgress?> = _recoverableProgress.asStateFlow()

    init {
        // 立即同步服务的当前状态（用于处理返回App时的情况）
        _isRunning.value = AutoDialService.isRunning.value
        _currentCustomer.value = AutoDialService.currentCustomer.value
        _dialedCount.value = AutoDialService.dialedCount.value
        _totalCount.value = AutoDialService.totalCount.value

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
        // 监听总客户数变化
        viewModelScope.launch {
            AutoDialService.totalCount.collect { total ->
                _totalCount.value = total
            }
        }
        viewModelScope.launch {
            AutoDialService.taskCompleted.collect { taskId ->
                taskId?.let { onTaskCompleted(it) }
            }
        }
        // 检查是否有可恢复的进度
        checkRecoverableProgress()
    }

    /**
     * 检查是否有可恢复的进度
     */
    private fun checkRecoverableProgress() {
        viewModelScope.launch {
            progressManager.progressFlow.collect { progress ->
                _recoverableProgress.value = progress
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
     * @param startIndex 起始索引（用于恢复进度）
     * @param dialedCount 已拨打数量（用于恢复进度）
     * @param startDialRound 起始拨打轮次（用于恢复进度）
     */
    fun startAutoDial(
        customers: List<Customer>,
        config: AutoDialConfig,
        startIndex: Int = 0,
        dialedCount: Int = 0,
        startDialRound: Int = 1
    ) {
        if (customers.isEmpty()) return

        _totalCount.value = customers.size
        _dialedCount.value = dialedCount
        _intervalSeconds.value = config.intervalSeconds
        _timeoutSeconds.value = config.timeoutSeconds
        _currentConfig.value = config

        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, AutoDialService::class.java).apply {
            action = AutoDialService.ACTION_START
            putExtra(AutoDialService.EXTRA_INTERVAL, config.intervalSeconds)
            putExtra(AutoDialService.EXTRA_TIMEOUT, config.timeoutSeconds)
            putExtra(AutoDialService.EXTRA_DIALS_PER_CUSTOMER, config.dialsPerCustomer)
            putExtra(AutoDialService.EXTRA_CUSTOMERS, ArrayList(customers))
            putExtra(AutoDialService.EXTRA_SCOPE_TYPE, config.scopeType.name)
            putExtra(AutoDialService.EXTRA_TASK_ID, config.taskId ?: -1)
            putExtra(AutoDialService.EXTRA_TASK_TITLE, config.taskTitle)
            putExtra(AutoDialService.EXTRA_START_INDEX, startIndex)
            putExtra(AutoDialService.EXTRA_DIALED_COUNT, dialedCount)
            putExtra(AutoDialService.EXTRA_START_DIAL_ROUND, startDialRound)
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

    /**
     * 恢复上次的拨号进度
     */
    fun restoreProgress(progress: AutoDialProgress) {
        // 更新总客户数
        _totalCount.value = progress.totalCount
        _dialedCount.value = progress.dialedCount

        val config = AutoDialConfig(
            scopeType = progress.scopeType,
            taskId = progress.taskId,
            taskTitle = progress.taskTitle,
            intervalSeconds = progress.intervalSeconds,
            timeoutSeconds = progress.timeoutSeconds,
            dialsPerCustomer = progress.dialsPerCustomer
        )
        startAutoDial(
            customers = progress.remainingCustomers,
            config = config,
            startIndex = progress.currentIndex,
            dialedCount = progress.dialedCount,
            startDialRound = progress.currentDialRound
        )
    }

    /**
     * 清除可恢复的进度
     */
    fun clearRecoverableProgress() {
        viewModelScope.launch {
            progressManager.clearProgress()
            _recoverableProgress.value = null
        }
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

    /**
     * 获取恢复进度的描述文本
     */
    fun getRecoverProgressDescription(progress: AutoDialProgress?): String {
        if (progress == null) return ""
        val scopeDesc = when (progress.scopeType) {
            AutoDialScopeType.ALL_PENDING -> "全部待拨打客户"
            AutoDialScopeType.SPECIFIC_TASK -> "任务: ${progress.taskTitle ?: "#${progress.taskId}"}"
            AutoDialScopeType.ALL_TASKS -> "全部任务客户"
        }
        return "$scopeDesc (${progress.dialedCount}/${progress.totalCount})"
    }
}
