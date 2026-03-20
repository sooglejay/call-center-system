package com.callcenter.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callcenter.app.data.model.User
import com.callcenter.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _savedServerUrl = MutableStateFlow<String?>(null)
    val savedServerUrl: StateFlow<String?> = _savedServerUrl.asStateFlow()

    private val _savedUsername = MutableStateFlow<String?>(null)
    val savedUsername: StateFlow<String?> = _savedUsername.asStateFlow()

    init {
        checkLoginState()
    }

    private fun checkLoginState() {
        viewModelScope.launch {
            _isLoggedIn.value = authRepository.isLoggedIn()
            _currentUser.value = authRepository.currentUser.value
            _savedServerUrl.value = authRepository.getServerUrl()
            _savedUsername.value = authRepository.getSavedUsername()
        }
    }

    fun login(username: String, password: String, serverUrl: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = authRepository.login(username, password, serverUrl)

            result.fold(
                onSuccess = { user ->
                    _isLoggedIn.value = true
                    _currentUser.value = user
                    _error.value = null
                },
                onFailure = { exception ->
                    _error.value = exception.message ?: "登录失败"
                }
            )

            _isLoading.value = false
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _isLoggedIn.value = false
            _currentUser.value = null
        }
    }

    fun clearError() {
        _error.value = null
    }
}
