package com.callcenter.app.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.callcenter.app.data.model.Customer
import com.callcenter.app.service.AutoDialService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AutoDialViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

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

    init {
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
    }

    fun startAutoDial(customers: List<Customer>, interval: Int = 10, timeout: Int = 30) {
        _totalCount.value = customers.size
        _intervalSeconds.value = interval
        _timeoutSeconds.value = timeout

        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, AutoDialService::class.java).apply {
            action = AutoDialService.ACTION_START
            putExtra(AutoDialService.EXTRA_INTERVAL, interval)
            putExtra(AutoDialService.EXTRA_TIMEOUT, timeout)
            putExtra(AutoDialService.EXTRA_CUSTOMERS, ArrayList(customers))
        }
        context.startService(intent)
    }

    fun stopAutoDial() {
        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, AutoDialService::class.java).apply {
            action = AutoDialService.ACTION_STOP
        }
        context.startService(intent)
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
}
