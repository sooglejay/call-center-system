package com.callcenter.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callcenter.app.data.local.entity.ContactEntity
import com.callcenter.app.data.repository.ContactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 通讯录 ViewModel
 */
@HiltViewModel
class ContactViewModel @Inject constructor(
    private val contactRepository: ContactRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val contacts: StateFlow<List<ContactEntity>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                contactRepository.getAllContacts()
            } else {
                contactRepository.searchContacts(query)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val contactCount: StateFlow<Int> = contactRepository.getContactCount()
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun addContact(name: String, phone: String, email: String? = null, company: String? = null, notes: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                contactRepository.addContact(name, phone, email, company, notes)
                _error.value = null
            } catch (e: Exception) {
                _error.value = "添加联系人失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateContact(contact: ContactEntity) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                contactRepository.updateContact(contact)
                _error.value = null
            } catch (e: Exception) {
                _error.value = "更新联系人失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteContact(contact: ContactEntity) {
        viewModelScope.launch {
            try {
                contactRepository.deleteContact(contact)
                _error.value = null
            } catch (e: Exception) {
                _error.value = "删除联系人失败: ${e.message}"
            }
        }
    }

    fun deleteContactById(id: Int) {
        viewModelScope.launch {
            try {
                contactRepository.deleteContactById(id)
                _error.value = null
            } catch (e: Exception) {
                _error.value = "删除联系人失败: ${e.message}"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
