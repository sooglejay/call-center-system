package com.callcenter.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callcenter.app.data.model.CallRecord
import com.callcenter.app.data.model.Customer
import com.callcenter.app.data.repository.CallRecordRepository
import com.callcenter.app.data.repository.CustomerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomerDetailViewModel @Inject constructor(
    private val customerRepository: CustomerRepository,
    private val callRecordRepository: CallRecordRepository
) : ViewModel() {

    private val _customer = MutableStateFlow<Customer?>(null)
    val customer: StateFlow<Customer?> = _customer.asStateFlow()

    private val _callRecords = MutableStateFlow<List<CallRecord>>(emptyList())
    val callRecords: StateFlow<List<CallRecord>> = _callRecords.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadCustomer(customerId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            // 加载客户详情
            val customerResult = customerRepository.getCustomer(customerId)
            customerResult.fold(
                onSuccess = { customer ->
                    _customer.value = customer
                    // 加载通话记录
                    loadCallRecords(customerId)
                },
                onFailure = { exception ->
                    _error.value = exception.message ?: "加载客户信息失败"
                }
            )

            _isLoading.value = false
        }
    }

    private suspend fun loadCallRecords(customerId: Int) {
        val result = callRecordRepository.getCustomerCalls(customerId)
        result.fold(
            onSuccess = { records ->
                _callRecords.value = records.sortedByDescending { it.createdAt }
            },
            onFailure = { exception ->
                // 通话记录加载失败不影响客户详情显示
            }
        )
    }

    fun updateCustomerStatus(customerId: Int, status: String, notes: String?) {
        viewModelScope.launch {
            _isLoading.value = true

            val result = customerRepository.updateCustomerStatus(customerId, status, notes)
            result.fold(
                onSuccess = { updatedCustomer ->
                    _customer.value = updatedCustomer
                },
                onFailure = { exception ->
                    _error.value = exception.message ?: "更新状态失败"
                }
            )

            _isLoading.value = false
        }
    }

    fun updateCustomerNotes(customerId: Int, notes: String) {
        viewModelScope.launch {
            _isLoading.value = true

            val currentCustomer = _customer.value ?: return@launch
            val updatedCustomer = currentCustomer.copy(notes = notes)

            val result = customerRepository.updateCustomer(customerId, updatedCustomer)
            result.fold(
                onSuccess = { customer ->
                    _customer.value = customer
                },
                onFailure = { exception ->
                    _error.value = exception.message ?: "更新备注失败"
                }
            )

            _isLoading.value = false
        }
    }

    fun refresh() {
        _customer.value?.let {
            loadCustomer(it.id)
        }
    }

    fun clearError() {
        _error.value = null
    }
}
