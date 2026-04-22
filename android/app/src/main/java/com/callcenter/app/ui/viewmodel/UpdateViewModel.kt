package com.callcenter.app.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.callcenter.app.data.model.VersionInfo
import com.callcenter.app.util.AppUpdateManager
import com.callcenter.app.util.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context,
    private val appUpdateManager: AppUpdateManager
) : ViewModel() {

    companion object {
        private const val PREFS_NAME = "update_prefs"
        private const val KEY_SKIPPED_VERSION = "skipped_version_code"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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
                // 检查用户是否已跳过此版本
                val skippedVersion = prefs.getInt(KEY_SKIPPED_VERSION, -1)
                if (newVersion.versionCode == skippedVersion) {
                    // 用户已跳过此版本，不显示弹窗
                    return@launch
                }
                
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
     * 跳过本次版本
     */
    fun skipThisVersion() {
        _versionInfo.value?.let { versionInfo ->
            prefs.edit().putInt(KEY_SKIPPED_VERSION, versionInfo.versionCode).apply()
        }
        dismissUpdateDialog()
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
