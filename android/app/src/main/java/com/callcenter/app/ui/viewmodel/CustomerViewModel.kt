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
                    forceRefresh = forceRefresh
                )
            } else {
                // 管理员使用通用接口
                customerRepository.getCustomers(
                    search = _searchQuery.value.ifBlank { null },
                    status = _statusFilter.value,
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
        loadCustomers()
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

    // 获取待拨打客户数量
    fun getPendingCount(): Int {
        return _customers.value.count { it.status == "pending" }
    }

    // 获取下一个待拨打客户
    suspend fun getNextPendingCustomer(): Customer? {
        return _customers.value.firstOrNull { it.status == "pending" }
    }
}
