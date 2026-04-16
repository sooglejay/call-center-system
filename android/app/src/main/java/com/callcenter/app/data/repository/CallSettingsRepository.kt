package com.callcenter.app.data.repository

import com.callcenter.app.data.local.preferences.CallSettingsManager
import com.callcenter.app.data.model.CallSettings
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallSettingsRepository @Inject constructor(
    private val callSettingsManager: CallSettingsManager
) {
    /**
     * 获取通话设置流
     */
    val callSettings: Flow<CallSettings> = callSettingsManager.callSettingsFlow

    /**
     * 保存自动拨号间隔
     */
    suspend fun saveAutoDialInterval(seconds: Int) {
        callSettingsManager.saveAutoDialInterval(seconds)
    }

    /**
     * 保存通话超时时间
     */
    suspend fun saveCallTimeout(seconds: Int) {
        callSettingsManager.saveCallTimeout(seconds)
    }

    /**
     * 保存重试次数
     */
    suspend fun saveRetryCount(count: Int) {
        callSettingsManager.saveRetryCount(count)
    }

    /**
     * 保存自动录音设置
     */
    suspend fun saveAutoRecordCall(enabled: Boolean) {
        callSettingsManager.saveAutoRecordCall(enabled)
    }

    /**
     * 保存自动添加备注设置
     */
    suspend fun saveAutoAddNote(enabled: Boolean) {
        callSettingsManager.saveAutoAddNote(enabled)
    }

    /**
     * 保存默认备注模板
     */
    suspend fun saveDefaultNoteTemplate(template: String) {
        callSettingsManager.saveDefaultNoteTemplate(template)
    }

    /**
     * 保存所有设置
     */
    suspend fun saveSettings(settings: CallSettings) {
        callSettingsManager.saveSettings(settings)
    }
}
