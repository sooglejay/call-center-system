package com.callcenter.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callcenter.app.data.model.Customer
import com.callcenter.app.data.repository.CustomerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 管理员客户列表 ViewModel
 */
@HiltViewModel
class AdminCustomerListViewModel @Inject constructor(
    private val customerRepository: CustomerRepository
) : ViewModel() {

    private val _customers = MutableStateFlow<List<Customer>>(emptyList())
    val customers: StateFlow<List<Customer>> = _customers.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _currentPage = MutableStateFlow(1)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _totalPages = MutableStateFlow(1)
    val totalPages: StateFlow<Int> = _totalPages.asStateFlow()

    private val _totalCustomers = MutableStateFlow(0)
    val totalCustomers: StateFlow<Int> = _totalCustomers.asStateFlow()

    private val _tagStats = MutableStateFlow<Map<String, Int>>(emptyMap())
    val tagStats: StateFlow<Map<String, Int>> = _tagStats.asStateFlow()

    private val _selectedTags = MutableStateFlow<Set<String>>(emptySet())
    val selectedTags: StateFlow<Set<String>> = _selectedTags.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val pageSize = 20

    /**
     * 加载客户列表
     */
    fun loadCustomers(refresh: Boolean = false) {
        viewModelScope.launch {
            if (refresh) {
                _isLoading.value = true
                _currentPage.value = 1
            } else {
                _isLoadingMore.value = true
            }
            _error.value = null

            val page = if (refresh) 1 else _currentPage.value + 1

            val result = customerRepository.getCustomers(
                page = page,
                pageSize = pageSize,
                forceRefresh = true
            )

            result.fold(
                onSuccess = { customerList ->
                    val allCustomers = if (refresh) {
                        customerList
                    } else {
                        _customers.value + customerList
                    }

                    // 根据标签筛选
                    val filteredCustomers = if (_selectedTags.value.isNotEmpty()) {
                        allCustomers.filter { customer ->
                            _selectedTags.value.contains(customer.tag.ifBlank { "未打标客户" })
                        }
                    } else {
                        allCustomers
                    }

                    // 根据搜索词筛选
                    val finalCustomers = if (_searchQuery.value.isNotBlank()) {
                        filteredCustomers.filter {
                            it.name?.contains(_searchQuery.value, ignoreCase = true) == true ||
                            it.phone?.contains(_searchQuery.value) == true
                        }
                    } else {
                        filteredCustomers
                    }

                    _customers.value = if (refresh) {
                        filteredCustomers
                    } else {
                        // 对于加载更多，需要重新筛选所有客户
                        allCustomers
                    }

                    // 计算标签统计
                    updateTagStats(allCustomers)

                    // 假设总数
                    _totalCustomers.value = allCustomers.size
                    _totalPages.value = if (customerList.size < pageSize) page else page + 1
                    _currentPage.value = page
                },
                onFailure = { exception ->
                    _error.value = "加载客户列表失败: ${exception.message}"
                }
            )

            _isLoading.value = false
            _isLoadingMore.value = false
        }
    }

    /**
     * 更新标签统计
     */
    private fun updateTagStats(customers: List<Customer>) {
        val stats = mutableMapOf<String, Int>()
        customers.forEach { customer ->
            val tag = customer.tag.ifBlank { "未打标客户" }
            stats[tag] = (stats[tag] ?: 0) + 1
        }
        _tagStats.value = stats
    }

    /**
     * 切换标签选择
     */
    fun toggleTagSelection(tag: String) {
        val currentTags = _selectedTags.value.toMutableSet()
        if (currentTags.contains(tag)) {
            currentTags.remove(tag)
        } else {
            currentTags.add(tag)
        }
        _selectedTags.value = currentTags
        loadCustomers(refresh = true)
    }

    /**
     * 清除标签筛选
     */
    fun clearTagFilter() {
        _selectedTags.value = emptySet()
        loadCustomers(refresh = true)
    }

    /**
     * 设置搜索词
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        loadCustomers(refresh = true)
    }

    /**
     * 是否有更多数据
     */
    fun hasMore(): Boolean {
        return _currentPage.value < _totalPages.value
    }

    /**
     * 加载更多
     */
    fun loadMore() {
        if (!_isLoadingMore.value && hasMore()) {
            loadCustomers(refresh = false)
        }
    }
}
