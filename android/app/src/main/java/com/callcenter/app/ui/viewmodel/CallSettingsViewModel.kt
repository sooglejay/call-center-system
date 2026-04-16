package com.callcenter.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callcenter.app.data.model.CallSettings
import com.callcenter.app.data.repository.CallSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CallSettingsViewModel @Inject constructor(
    private val callSettingsRepository: CallSettingsRepository
) : ViewModel() {

    private val _callSettings = MutableStateFlow(CallSettings())
    val callSettings: StateFlow<CallSettings> = _callSettings.asStateFlow()

    init {
        viewModelScope.launch {
            callSettingsRepository.callSettings.collect { settings ->
                _callSettings.value = settings
            }
        }
    }

    /**
     * 保存自动拨号间隔
     */
    fun saveAutoDialInterval(seconds: Int) {
        viewModelScope.launch {
            callSettingsRepository.saveAutoDialInterval(seconds)
        }
    }

    /**
     * 保存通话超时时间
     */
    fun saveCallTimeout(seconds: Int) {
        viewModelScope.launch {
            callSettingsRepository.saveCallTimeout(seconds)
        }
    }

    /**
     * 保存重试次数
     */
    fun saveRetryCount(count: Int) {
        viewModelScope.launch {
            callSettingsRepository.saveRetryCount(count)
        }
    }

    /**
     * 保存自动录音设置
     */
    fun saveAutoRecordCall(enabled: Boolean) {
        viewModelScope.launch {
            callSettingsRepository.saveAutoRecordCall(enabled)
        }
    }

    /**
     * 保存自动添加备注设置
     */
    fun saveAutoAddNote(enabled: Boolean) {
        viewModelScope.launch {
            callSettingsRepository.saveAutoAddNote(enabled)
        }
    }

    /**
     * 保存默认备注模板
     */
    fun saveDefaultNoteTemplate(template: String) {
        viewModelScope.launch {
            callSettingsRepository.saveDefaultNoteTemplate(template)
        }
    }

    /**
     * 保存所有设置
     */
    fun saveSettings(settings: CallSettings) {
        viewModelScope.launch {
            callSettingsRepository.saveSettings(settings)
        }
    }
}
