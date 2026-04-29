package com.callcenter.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callcenter.app.data.local.preferences.TokenManager
import com.callcenter.app.data.model.CallRecord
import com.callcenter.app.data.model.TaskCustomer
import com.callcenter.app.data.model.Task
import com.callcenter.app.data.model.UpdateTaskCustomerStatusRequest
import com.callcenter.app.data.repository.CallRecordRepository
import com.callcenter.app.data.repository.TaskRepository
import com.callcenter.app.service.AutoDialService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 客服任务视图模型
 * 处理客服视角的任务列表和任务执行
 */
@HiltViewModel
class AgentTaskViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val callRecordRepository: CallRecordRepository,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    private val _task = MutableStateFlow<Task?>(null)
    val task: StateFlow<Task?> = _task.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * 加载我的任务列表
     */
    fun loadMyTasks() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = taskRepository.getMyTasks()
            result.fold(
                onSuccess = { tasks ->
                    _tasks.value = tasks
                },
                onFailure = { exception ->
                    _error.value = exception.message ?: "加载任务失败"
                }
            )

            _isLoading.value = false
        }
    }

    /**
     * 加载任务详情
     */
    fun loadTaskDetail(taskId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = taskRepository.getTask(taskId)
            result.fold(
                onSuccess = { task ->
                    // 过滤掉 name 或 phone 为 null/空的无效客户数据
                    val filteredTask = task.copy(
                        customers = task.customers?.filter { 
                            !it.name.isNullOrBlank() && !it.phone.isNullOrBlank() 
                        }
                    )
                    _task.value = filteredTask
                },
                onFailure = { exception ->
                    _error.value = exception.message ?: "加载任务详情失败"
                }
            )

            _isLoading.value = false
        }
    }

    /**
     * 更新任务中客户的拨打状态
     */
    fun updateCustomerStatus(
        taskId: Int,
        customerId: Int,
        status: String,
        callResult: String? = null,
        callId: Int? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true

            val request = UpdateTaskCustomerStatusRequest(
                status = status,
                callResult = callResult,
                callId = callId
            )

            val result = taskRepository.updateTaskCustomerStatus(taskId, customerId, request)
            result.fold(
                onSuccess = {
                    // 直接更新本地数据，而不是重新加载整个任务
                    _task.value = _task.value?.copy(
                        customers = _task.value?.customers?.map { customer ->
                            if (customer.id == customerId) {
                                customer.copy(
                                    callStatus = status,
                                    callResult = callResult ?: customer.callResult
                                )
                            } else {
                                customer
                            }
                        } ?: emptyList()
                    )
                    _isLoading.value = false
                },
                onFailure = { exception ->
                    _error.value = exception.message ?: "更新状态失败"
                    _isLoading.value = false
                }
            )
        }
    }

    fun createTaskCallRecord(
        customer: TaskCustomer,
        onSuccess: (CallRecord?) -> Unit,
        onFailure: (String) -> Unit
    ) {
        viewModelScope.launch {
            val customerId = customer.id
            if (customerId == null || customer.phone.isNullOrBlank()) {
                onFailure("客户信息不完整，无法创建通话记录")
                return@launch
            }

            val agentId = tokenManager.getUserId()
            if (agentId == null) {
                onFailure("当前登录信息失效，请重新登录")
                return@launch
            }

            val result = callRecordRepository.createCallRecord(
                customerId = customerId,
                customerName = customer.name ?: "",
                phone = customer.phone,
                agentId = agentId,
                status = "calling"
            )

            result.fold(
                onSuccess = { onSuccess(it) },
                onFailure = { onFailure(it.message ?: "创建通话记录失败") }
            )
        }
    }

    fun clearError() {
        _error.value = null
    }

    /**
     * 更新任务中客户的信息（编辑电话号码等）
     */
    fun updateCustomerInfo(
        taskId: Int,
        customerId: Int,
        name: String? = null,
        phone: String? = null,
        company: String? = null,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isLoading.value = true

            val result = taskRepository.updateTaskCustomerInfo(
                taskId = taskId,
                customerId = customerId,
                name = name,
                phone = phone,
                company = company
            )
            result.fold(
                onSuccess = {
                    // 清除之前的错误
                    _error.value = null
                    // 直接更新本地数据，而不是重新加载整个任务
                    _task.value = _task.value?.copy(
                        customers = _task.value?.customers?.map { customer ->
                            if (customer.id == customerId) {
                                customer.copy(
                                    name = name ?: customer.name,
                                    phone = phone ?: customer.phone,
                                    company = company ?: customer.company
                                )
                            } else {
                                customer
                            }
                        } ?: emptyList()
                    )
                    _isLoading.value = false
                    onSuccess()
                },
                onFailure = { exception ->
                    _error.value = exception.message ?: "更新客户信息失败"
                    onError(_error.value!!)
                    _isLoading.value = false
                }
            )
        }
    }

    /**
     * 从任务中移除客户
     */
    fun removeCustomer(
        taskId: Int,
        customerId: Int,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isLoading.value = true

            val result = taskRepository.removeTaskCustomer(taskId, customerId)
            result.fold(
                onSuccess = {
                    // 直接从本地数据中移除客户，而不是重新加载整个任务
                    _task.value = _task.value?.copy(
                        customers = _task.value?.customers?.filter { it.id != customerId } ?: emptyList()
                    )
                    _isLoading.value = false
                    onSuccess()
                },
                onFailure = { exception ->
                    _error.value = exception.message ?: "移除客户失败"
                    onError(_error.value!!)
                    _isLoading.value = false
                }
            )
        }
    }

    /**
     * 开始监听客户状态更新事件
     * 用于自动拨号时实时更新 UI
     */
    fun observeCustomerStatusUpdates() {
        viewModelScope.launch {
            AutoDialService.customerStatusUpdate.collect { event ->
                // 更新本地客户状态
                _task.value = _task.value?.copy(
                    customers = _task.value?.customers?.map { customer ->
                        if (customer.id == event.customerId) {
                            customer.copy(
                                callStatus = event.callStatus,
                                callResult = event.callResult ?: customer.callResult
                            )
                        } else {
                            customer
                        }
                    } ?: emptyList()
                )
            }
        }
    }
}
