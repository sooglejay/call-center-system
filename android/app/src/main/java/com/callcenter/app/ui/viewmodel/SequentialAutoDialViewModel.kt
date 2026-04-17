package com.callcenter.app.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.callcenter.app.data.model.Task
import com.callcenter.app.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 顺序任务自动拨号 ViewModel
 * 用于管理按顺序执行多个任务的自动拨号流程
 */
@HiltViewModel
class SequentialAutoDialViewModel @Inject constructor(
    application: Application,
    private val taskRepository: TaskRepository
) : AndroidViewModel(application) {

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _currentTaskIndex = MutableStateFlow(0)
    val currentTaskIndex: StateFlow<Int> = _currentTaskIndex.asStateFlow()

    private val _totalTasks = MutableStateFlow(0)
    val totalTasks: StateFlow<Int> = _totalTasks.asStateFlow()

    private val _currentTask = MutableStateFlow<Task?>(null)
    val currentTask: StateFlow<Task?> = _currentTask.asStateFlow()

    private val _currentCustomerIndex = MutableStateFlow(0)
    val currentCustomerIndex: StateFlow<Int> = _currentCustomerIndex.asStateFlow()

    private val _totalCustomersInTask = MutableStateFlow(0)
    val totalCustomersInTask: StateFlow<Int> = _totalCustomersInTask.asStateFlow()

    private val _overallProgress = MutableStateFlow(0)
    val overallProgress: StateFlow<Int> = _overallProgress.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // 任务列表
    private var tasks: List<Task> = emptyList()

    /**
     * 开始顺序执行所有任务
     */
    fun startSequentialDial(tasks: List<Task>, intervalSeconds: Int = 10, timeoutSeconds: Int = 30) {
        if (tasks.isEmpty()) {
            _error.value = "没有任务需要执行"
            return
        }

        this.tasks = tasks.filter { it.customerCount > it.completedCount }
        if (this.tasks.isEmpty()) {
            _error.value = "所有任务已完成"
            return
        }

        _isRunning.value = true
        _totalTasks.value = this.tasks.size
        _currentTaskIndex.value = 0
        _overallProgress.value = 0

        // 开始执行第一个任务
        executeCurrentTask(intervalSeconds, timeoutSeconds)
    }

    /**
     * 执行当前任务
     * 只检查任务是否有待拨打客户，实际客户加载由 Service 完成
     */
    private fun executeCurrentTask(intervalSeconds: Int, timeoutSeconds: Int) {
        viewModelScope.launch {
            val task = tasks.getOrNull(_currentTaskIndex.value)
            if (task == null) {
                // 所有任务完成
                stopSequentialDial()
                return@launch
            }

            _currentTask.value = task

            // 检查任务是否有待拨打客户
            val pendingCount = task.customerCount - task.completedCount
            if (pendingCount <= 0) {
                // 当前任务没有待拨打客户，移动到下一个任务
                Log.d("SequentialAutoDialViewModel", "任务 ${task.id} 没有待拨打客户，跳过")
                moveToNextTask(intervalSeconds, timeoutSeconds)
                return@launch
            }

            _totalCustomersInTask.value = pendingCount
            _currentCustomerIndex.value = 0

            // 开始自动拨号（Service 会自己加载客户）
            startAutoDialForCurrentTask(intervalSeconds, timeoutSeconds)
        }
    }

    /**
     * 为当前任务启动自动拨号
     * 使用任务加载模式，避免 Intent 大数据限制
     */
    private fun startAutoDialForCurrentTask(intervalSeconds: Int, timeoutSeconds: Int) {
        val taskId = currentTask.value?.id ?: return
        val context = getApplication<Application>().applicationContext
        val intent = android.content.Intent(context, com.callcenter.app.service.AutoDialService::class.java).apply {
            action = com.callcenter.app.service.AutoDialService.ACTION_START
            putExtra(com.callcenter.app.service.AutoDialService.EXTRA_INTERVAL, intervalSeconds)
            putExtra(com.callcenter.app.service.AutoDialService.EXTRA_TIMEOUT, timeoutSeconds)
            putExtra(com.callcenter.app.service.AutoDialService.EXTRA_SCOPE_TYPE, AutoDialScopeType.SPECIFIC_TASK.name)
            putExtra(com.callcenter.app.service.AutoDialService.EXTRA_TASK_ID, taskId)
            // 使用任务加载模式，避免 Intent 大数据限制
            putExtra(com.callcenter.app.service.AutoDialService.EXTRA_LOAD_FROM_TASK, true)
        }
        Log.d("SequentialAutoDialViewModel", "启动任务自动拨号，taskId=$taskId")
        context.startService(intent)
    }

    /**
     * 移动到下一个任务
     */
    fun moveToNextTask(intervalSeconds: Int = 10, timeoutSeconds: Int = 30) {
        _currentTaskIndex.value++
        updateOverallProgress()

        if (_currentTaskIndex.value >= tasks.size) {
            // 所有任务完成
            stopSequentialDial()
        } else {
            // 执行下一个任务
            executeCurrentTask(intervalSeconds, timeoutSeconds)
        }
    }

    /**
     * 更新总体进度
     */
    private fun updateOverallProgress() {
        val totalCustomers = tasks.sumOf { it.customerCount }
        val completedCustomers = tasks.sumOf { it.completedCount }
        _overallProgress.value = if (totalCustomers > 0) {
            (completedCustomers * 100 / totalCustomers)
        } else 0
    }

    /**
     * 停止顺序拨号
     */
    fun stopSequentialDial() {
        _isRunning.value = false
        _currentTaskIndex.value = 0
        _totalTasks.value = 0
        _currentTask.value = null
        tasks = emptyList()

        // 停止自动拨号服务
        val context = getApplication<Application>().applicationContext
        val intent = android.content.Intent(context, com.callcenter.app.service.AutoDialService::class.java).apply {
            action = com.callcenter.app.service.AutoDialService.ACTION_STOP
        }
        context.startService(intent)
    }

    /**
     * 获取当前状态描述
     */
    fun getStatusDescription(): String {
        if (!_isRunning.value) return "未开始"
        val current = _currentTaskIndex.value + 1
        val total = _totalTasks.value
        val taskName = _currentTask.value?.title ?: "未知任务"
        return "任务 $current/$total: $taskName"
    }
}
