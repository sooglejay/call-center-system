package com.callcenter.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callcenter.app.data.model.CreateTaskRequest
import com.callcenter.app.data.model.Customer
import com.callcenter.app.data.model.Task
import com.callcenter.app.data.model.User
import com.callcenter.app.data.repository.CustomerRepository
import com.callcenter.app.data.repository.TaskRepository
import com.callcenter.app.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 创建任务 ViewModel
 * 支持加载客服列表、客户列表和创建任务
 */
@HiltViewModel
class CreateTaskViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val customerRepository: CustomerRepository,
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _agents = MutableStateFlow<List<User>>(emptyList())
    val agents: StateFlow<List<User>> = _agents.asStateFlow()

    private val _customers = MutableStateFlow<List<Customer>>(emptyList())
    val customers: StateFlow<List<Customer>> = _customers.asStateFlow()

    private val _nameLetterStats = MutableStateFlow<Map<String, Int>>(emptyMap())
    val nameLetterStats: StateFlow<Map<String, Int>> = _nameLetterStats.asStateFlow()

    private val _tagStats = MutableStateFlow<Map<String, Int>>(emptyMap())
    val tagStats: StateFlow<Map<String, Int>> = _tagStats.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isCreating = MutableStateFlow(false)
    val isCreating: StateFlow<Boolean> = _isCreating.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * 加载所有必要数据
     */
    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            // 并行加载客服列表和客户列表
            loadAgents()
            loadCustomers(false)
            loadNameLetterStats(false)
            loadTagStats(false)

            _isLoading.value = false
        }
    }

    /**
     * 加载客服列表
     */
    private suspend fun loadAgents() {
        val result = userRepository.getAgents()
        result.fold(
            onSuccess = { agentList ->
                _agents.value = agentList
            },
            onFailure = { exception ->
                _error.value = "加载客服列表失败: ${exception.message}"
            }
        )
    }

    /**
     * 加载客户列表
     * @param unassignedOnly 是否只加载未分配的客户
     */
    fun loadCustomers(unassignedOnly: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true

            val result = customerRepository.getCustomers(
                page = 1,
                pageSize = 1000, // 加载更多客户用于选择
                forceRefresh = true
            )

            result.fold(
                onSuccess = { customerList ->
                    // 如果只需要未分配客户，进行过滤
                    _customers.value = if (unassignedOnly) {
                        customerList.filter { it.assignedTo == null }
                    } else {
                        customerList
                    }
                },
                onFailure = { exception ->
                    _error.value = "加载客户列表失败: ${exception.message}"
                }
            )

            _isLoading.value = false
        }
    }

    /**
     * 加载姓氏首字母统计
     * @param unassignedOnly 是否只统计未分配客户
     */
    fun loadNameLetterStats(unassignedOnly: Boolean = false) {
        viewModelScope.launch {
            try {
                // 根据当前客户列表计算姓氏统计
                val customersToCount = if (unassignedOnly) {
                    _customers.value.filter { it.assignedTo == null }
                } else {
                    _customers.value
                }

                val stats = mutableMapOf<String, Int>()
                customersToCount.forEach { customer ->
                    val firstLetter = getFirstLetter(customer.name ?: "")
                    stats[firstLetter] = (stats[firstLetter] ?: 0) + 1
                }

                _nameLetterStats.value = stats
            } catch (e: Exception) {
                // 统计失败不影响主要功能
                _nameLetterStats.value = emptyMap()
            }
        }
    }

    /**
     * 加载标签统计
     * @param unassignedOnly 是否只统计未分配客户
     */
    fun loadTagStats(unassignedOnly: Boolean = false) {
        viewModelScope.launch {
            try {
                val customersToCount = if (unassignedOnly) {
                    _customers.value.filter { it.assignedTo == null }
                } else {
                    _customers.value
                }

                val stats = mutableMapOf<String, Int>()
                customersToCount.forEach { customer ->
                    val tag = customer.tag.ifBlank { "未打标客户" }
                    stats[tag] = (stats[tag] ?: 0) + 1
                }

                _tagStats.value = stats
            } catch (e: Exception) {
                _tagStats.value = emptyMap()
            }
        }
    }

    /**
     * 创建任务
     */
    fun createTask(
        request: CreateTaskRequest,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isCreating.value = true
            _error.value = null

            val result = taskRepository.createTask(request)

            result.fold(
                onSuccess = { _ ->
                    _isCreating.value = false
                    onSuccess()
                },
                onFailure = { exception ->
                    _isCreating.value = false
                    _error.value = exception.message ?: "创建任务失败"
                }
            )
        }
    }

    fun createTaskForSelf(
        request: CreateTaskRequest,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isCreating.value = true
            _error.value = null

            val result = taskRepository.createTaskForSelf(request)

            result.fold(
                onSuccess = {
                    _isCreating.value = false
                    onSuccess()
                },
                onFailure = { exception ->
                    _isCreating.value = false
                    _error.value = exception.message ?: "创建任务失败"
                }
            )
        }
    }

    /**
     * 设置错误信息（供UI调用）
     */
    fun setError(message: String) {
        _error.value = message
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * 获取姓氏首字母
     */
    private fun getFirstLetter(name: String): String {
        if (name.isEmpty()) return "#"
        val firstChar = name.first()
        return if (firstChar in '\u4e00'..'\u9fff') {
            // 简单拼音映射（常用姓氏）
            val pinyinMap = mapOf(
                '阿' to "A", '艾' to "A", '安' to "A",
                '白' to "B", '班' to "B", '包' to "B", '鲍' to "B", '毕' to "B", '边' to "B",
                '蔡' to "C", '曹' to "C", '陈' to "C", '程' to "C", '崔' to "C",
                '戴' to "D", '邓' to "D", '丁' to "D", '董' to "D", '杜' to "D",
                '范' to "F", '方' to "F", '冯' to "F", '傅' to "F",
                '高' to "G", '葛' to "G", '郭' to "G",
                '韩' to "H", '何' to "H", '贺' to "H", '胡' to "H", '黄' to "H",
                '贾' to "J", '江' to "J", '姜' to "J", '蒋' to "J", '金' to "J",
                '康' to "K", '孔' to "K",
                '赖' to "L", '兰' to "L", '雷' to "L", '李' to "L", '梁' to "L",
                '林' to "L", '刘' to "L", '龙' to "L", '卢' to "L", '陆' to "L", '罗' to "L", '吕' to "L",
                '马' to "M", '毛' to "M", '孟' to "M", '莫' to "M",
                '潘' to "P", '彭' to "P",
                '钱' to "Q", '秦' to "Q", '邱' to "Q",
                '任' to "R",
                '沈' to "S", '史' to "S", '宋' to "S", '苏' to "S", '孙' to "S",
                '汤' to "T", '唐' to "T", '陶' to "T", '田' to "T",
                '万' to "W", '汪' to "W", '王' to "W", '韦' to "W", '魏' to "W",
                '吴' to "W", '武' to "W",
                '夏' to "X", '肖' to "X", '谢' to "X", '徐' to "X", '许' to "X",
                '严' to "Y", '杨' to "Y", '叶' to "Y", '易' to "Y", '殷' to "Y",
                '于' to "Y", '余' to "Y", '袁' to "Y",
                '曾' to "Z", '张' to "Z", '章' to "Z", '赵' to "Z", '郑' to "Z", '周' to "Z", '朱' to "Z"
            )
            pinyinMap[firstChar] ?: "#"
        } else {
            firstChar.uppercase()
        }
    }
}
