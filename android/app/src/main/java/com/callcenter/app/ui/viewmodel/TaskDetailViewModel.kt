package com.callcenter.app.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
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
 * 任务详情 ViewModel
 */
@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _task = MutableStateFlow<Task?>(null)
    val task: StateFlow<Task?> = _task.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * 加载任务详情
     */
    fun loadTaskDetail(taskId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            Log.d("TaskDetailViewModel", "开始加载任务详情: taskId=$taskId")
            val result = taskRepository.getTask(taskId)

            result.fold(
                onSuccess = { task ->
                    Log.d("TaskDetailViewModel", "加载任务详情成功: taskId=${task.id}, customers=${task.customers?.size ?: 0}, customerCount=${task.customerCount}")
                    _task.value = task
                    _error.value = null
                },
                onFailure = { exception ->
                    Log.e("TaskDetailViewModel", "加载任务详情失败: ${exception.message}")
                    _error.value = exception.message ?: "加载任务详情失败"
                }
            )

            _isLoading.value = false
        }
    }

    /**
     * 刷新任务详情
     */
    fun refresh() {
        _task.value?.let { task ->
            loadTaskDetail(task.id)
        }
    }
}
