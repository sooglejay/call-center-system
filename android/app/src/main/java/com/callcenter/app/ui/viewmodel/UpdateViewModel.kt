package com.callcenter.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callcenter.app.data.model.VersionInfo
import com.callcenter.app.util.AppUpdateManager
import com.callcenter.app.util.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 版本更新ViewModel
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val appUpdateManager: AppUpdateManager
) : ViewModel() {

    private val _versionInfo = MutableStateFlow<VersionInfo?>(null)
    val versionInfo: StateFlow<VersionInfo?> = _versionInfo.asStateFlow()

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    val updateState: StateFlow<UpdateState> = appUpdateManager.updateState

    private val _currentVersion = MutableStateFlow("")
    val currentVersion: StateFlow<String> = _currentVersion.asStateFlow()

    init {
        _currentVersion.value = appUpdateManager.getCurrentVersionName()
    }

    /**
     * 检查版本更新
     * @param showDialog 是否显示更新对话框
     */
    fun checkForUpdate(showDialog: Boolean = true) {
        viewModelScope.launch {
            val newVersion = appUpdateManager.checkForUpdate()
            if (newVersion != null) {
                _versionInfo.value = newVersion
                if (showDialog) {
                    _showUpdateDialog.value = true
                }
            }
        }
    }

    /**
     * 开始下载更新
     */
    fun startUpdate() {
        _versionInfo.value?.let { versionInfo ->
            appUpdateManager.startDownload(versionInfo)
        }
    }

    /**
     * 关闭更新对话框
     */
    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
        appUpdateManager.resetState()
    }

    /**
     * 重试下载
     */
    fun retryDownload() {
        appUpdateManager.resetState()
        startUpdate()
    }

    /**
     * 获取当前版本号
     */
    fun getCurrentVersionCode(): Int {
        return appUpdateManager.getCurrentVersionCode()
    }

    /**
     * 获取当前版本名称
     */
    fun getCurrentVersionName(): String {
        return appUpdateManager.getCurrentVersionName()
    }

    override fun onCleared() {
        super.onCleared()
        appUpdateManager.cleanup()
    }
}
