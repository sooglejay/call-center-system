package com.callcenter.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callcenter.app.data.model.Customer
import com.callcenter.app.data.repository.AuthRepository
import com.callcenter.app.data.repository.CustomerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomerViewModel @Inject constructor(
    private val customerRepository: CustomerRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _customers = MutableStateFlow<List<Customer>>(emptyList())
    val customers: StateFlow<List<Customer>> = _customers.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _statusFilter = MutableStateFlow<String?>(null)
    val statusFilter: StateFlow<String?> = _statusFilter.asStateFlow()

    private val _tagFilter = MutableStateFlow<String?>(null)
    val tagFilter: StateFlow<String?> = _tagFilter.asStateFlow()

    private val _selectedCustomer = MutableStateFlow<Customer?>(null)
    val selectedCustomer: StateFlow<Customer?> = _selectedCustomer.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadCustomers()
    }

    fun loadCustomers(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            // 获取当前用户角色
            val currentUser = authRepository.currentUser.value
            val isAgent = currentUser?.role == "agent"

            val result = if (isAgent) {
                // 客服使用专属接口
                customerRepository.getAgentCustomers(
                    search = _searchQuery.value.ifBlank { null },
                    status = _statusFilter.value,
                    tag = _tagFilter.value,
                    forceRefresh = forceRefresh
                )
            } else {
                // 管理员使用通用接口
                customerRepository.getCustomers(
                    search = _searchQuery.value.ifBlank { null },
                    status = _statusFilter.value,
                    tag = _tagFilter.value,
                    forceRefresh = forceRefresh
                )
            }

            result.fold(
                onSuccess = { customers ->
                    _customers.value = customers
                },
                onFailure = { exception ->
                    _error.value = exception.message ?: "加载失败"
                }
            )

            _isLoading.value = false
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        loadCustomers()
    }

    fun setStatusFilter(status: String?) {
        _statusFilter.value = status
        loadCustomers(forceRefresh = true)
    }

    fun setTagFilter(tag: String?) {
        _tagFilter.value = tag
        loadCustomers(forceRefresh = true)
    }

    fun selectCustomer(customer: Customer) {
        _selectedCustomer.value = customer
    }

    fun clearSelection() {
        _selectedCustomer.value = null
    }

    fun updateCustomerStatus(customerId: Int, status: String, notes: String? = null) {
        viewModelScope.launch {
            customerRepository.updateCustomerStatus(customerId, status, notes)
            loadCustomers()
        }
    }

    fun refresh() {
        loadCustomers(forceRefresh = true)
    }

    // 多选删除相关状态
    private val _isMultiSelectMode = MutableStateFlow(false)
    val isMultiSelectMode: StateFlow<Boolean> = _isMultiSelectMode.asStateFlow()

    private val _selectedCustomerIds = MutableStateFlow<Set<Int>>(emptySet())
    val selectedCustomerIds: StateFlow<Set<Int>> = _selectedCustomerIds.asStateFlow()

    fun toggleMultiSelectMode() {
        _isMultiSelectMode.value = !_isMultiSelectMode.value
        if (!_isMultiSelectMode.value) {
            _selectedCustomerIds.value = emptySet()
        }
    }

    fun exitMultiSelectMode() {
        _isMultiSelectMode.value = false
        _selectedCustomerIds.value = emptySet()
    }

    fun toggleCustomerSelection(customerId: Int) {
        val currentSelection = _selectedCustomerIds.value.toMutableSet()
        if (currentSelection.contains(customerId)) {
            currentSelection.remove(customerId)
        } else {
            currentSelection.add(customerId)
        }
        _selectedCustomerIds.value = currentSelection
    }

    fun selectAllCustomers(customerIds: List<Int>) {
        _selectedCustomerIds.value = customerIds.toSet()
    }

    fun deleteCustomer(customerId: Int, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        android.util.Log.d("CustomerViewModel", "删除客户请求: customerId=$customerId")
        viewModelScope.launch {
            _isLoading.value = true
            android.util.Log.d("CustomerViewModel", "调用Repository删除客户: customerId=$customerId")
            val result = customerRepository.deleteCustomer(customerId)
            result.fold(
                onSuccess = {
                    android.util.Log.d("CustomerViewModel", "删除成功: customerId=$customerId")
                    _selectedCustomerIds.value = _selectedCustomerIds.value - customerId
                    _customers.value = _customers.value.filter { it.id != customerId }
                    onSuccess()
                },
                onFailure = { exception ->
                    android.util.Log.e("CustomerViewModel", "删除失败: ${exception.message}")
                    _error.value = exception.message ?: "删除失败"
                    onError(_error.value!!)
                }
            )
            _isLoading.value = false
        }
    }

    fun deleteSelectedCustomers(onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        viewModelScope.launch {
            _isLoading.value = true
            val idsToDelete = _selectedCustomerIds.value.toList()
            val result = customerRepository.deleteCustomers(idsToDelete)
            result.fold(
                onSuccess = {
                    _customers.value = _customers.value.filter { it.id !in idsToDelete }
                    _selectedCustomerIds.value = emptySet()
                    _isMultiSelectMode.value = false
                    onSuccess()
                },
                onFailure = { exception ->
                    _error.value = exception.message ?: "批量删除失败"
                    onError(_error.value!!)
                    // 刷新列表以同步状态
                    loadCustomers()
                }
            )
            _isLoading.value = false
        }
    }
    // 获取待拨打客户数量
    fun getPendingCount(): Int {
        return _customers.value.count { it.status == "pending" }
    }

    // 获取下一个待拨打客户
    suspend fun getNextPendingCustomer(): Customer? {
        return _customers.value.firstOrNull { it.status == "pending" }
    }
}
