package com.callcenter.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callcenter.app.data.local.preferences.FeatureToggle
import com.callcenter.app.data.local.preferences.FeatureToggleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 功能开关 ViewModel
 */
@HiltViewModel
class FeatureToggleViewModel @Inject constructor(
    private val featureToggleManager: FeatureToggleManager
) : ViewModel() {

    private val _toggles = MutableStateFlow<Map<FeatureToggle, Boolean>>(emptyMap())
    val toggles: StateFlow<Map<FeatureToggle, Boolean>> = _toggles.asStateFlow()

    init {
        viewModelScope.launch {
            featureToggleManager.getAllTogglesFlow().collect { toggleMap ->
                _toggles.value = toggleMap
            }
        }
    }

    /**
     * 设置单个开关状态
     */
    fun setToggle(toggle: FeatureToggle, enabled: Boolean) {
        viewModelScope.launch {
            featureToggleManager.setEnabled(toggle, enabled)
        }
    }

    /**
     * 重置单个开关为默认值
     */
    fun resetToggle(toggle: FeatureToggle) {
        viewModelScope.launch {
            featureToggleManager.resetToDefault(toggle)
        }
    }

    /**
     * 重置所有开关为默认值
     */
    fun resetAllToDefault() {
        viewModelScope.launch {
            featureToggleManager.resetAllToDefault()
        }
    }

    /**
     * 检查开关是否开启
     */
    suspend fun isEnabled(toggle: FeatureToggle): Boolean {
        return featureToggleManager.isEnabled(toggle)
    }
}
