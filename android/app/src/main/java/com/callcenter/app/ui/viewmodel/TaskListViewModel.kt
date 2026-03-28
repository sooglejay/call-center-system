package com.callcenter.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callcenter.app.data.model.CreateTaskRequest
import com.callcenter.app.data.model.Task
import com.callcenter.app.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 任务列表 ViewModel
 */
@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadTasks()
    }

    /**
     * 加载任务列表
     */
    private fun loadTasks() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = taskRepository.getTasks()

            result.fold(
                onSuccess = { taskList ->
                    _tasks.value = taskList
                    _error.value = null
                },
                onFailure = { exception ->
                    _error.value = exception.message ?: "加载任务列表失败"
                }
            )

            _isLoading.value = false
        }
    }

    /**
     * 刷新数据
     */
    fun refresh() {
        loadTasks()
    }

    /**
     * 删除任务
     */
    fun deleteTask(taskId: Int) {
        viewModelScope.launch {
            val result = taskRepository.deleteTask(taskId)

            result.fold(
                onSuccess = {
                    _tasks.value = _tasks.value.filter { it.id != taskId }
                },
                onFailure = { exception ->
                    _error.value = exception.message ?: "删除任务失败"
                }
            )
        }
    }

    /**
     * 更新任务状态
     */
    fun updateTaskStatus(taskId: Int, status: String) {
        viewModelScope.launch {
            val task = _tasks.value.find { it.id == taskId } ?: return@launch
            val result = taskRepository.updateTask(taskId, task.copy(status = status))

            result.fold(
                onSuccess = { updatedTask ->
                    _tasks.value = _tasks.value.map {
                        if (it.id == taskId) updatedTask else it
                    }
                },
                onFailure = { exception ->
                    _error.value = exception.message ?: "更新任务状态失败"
                }
            )
        }
    }

    /**
     * 创建任务
     */
    fun createTask(
        request: CreateTaskRequest,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = taskRepository.createTask(request)

            result.fold(
                onSuccess = { newTask ->
                    _tasks.value = _tasks.value + newTask
                    onSuccess()
                },
                onFailure = { exception ->
                    onError(exception.message ?: "创建任务失败")
                }
            )
        }
    }
}
