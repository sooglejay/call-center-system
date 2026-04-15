package com.callcenter.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

import com.callcenter.app.util.AppLifecycleManager
import com.callcenter.app.data.local.preferences.CallSettingsManager
import com.callcenter.app.service.LogCollectorService
import javax.inject.Inject

@HiltAndroidApp
class CallCenterApp : Application() {

    companion object {
        const val CHANNEL_ID_CALL_MONITOR = "call_monitor"
        const val CHANNEL_ID_AUTO_DIAL = "auto_dial"

        var instance: CallCenterApp? = null
            private set
    }

    @Inject
    lateinit var callSettingsManager: CallSettingsManager

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 初始化应用生命周期管理器
        AppLifecycleManager.init(this)

        createNotificationChannels()

        // 初始化日志收集服务
        initLogCollectorService()
    }

    /**
     * 初始化日志收集服务
     */
    private fun initLogCollectorService() {
        applicationScope.launch {
            try {
                // 读取日志收集配置
                val callSettings = callSettingsManager.callSettingsFlow.first()

                if (callSettings.collectLogcat) {
                    // 开启日志收集
                    LogCollectorService.startCollecting(this@CallCenterApp)
                    // 设置最大缓存大小
                    LogCollectorService.setMaxSize(this@CallCenterApp, callSettings.logcatMaxCacheSize)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val callMonitorChannel = NotificationChannel(
                CHANNEL_ID_CALL_MONITOR,
                getString(R.string.notification_channel_call),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_call_desc)
            }

            val autoDialChannel = NotificationChannel(
                CHANNEL_ID_AUTO_DIAL,
                getString(R.string.notification_channel_call),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_call_desc)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannels(listOf(callMonitorChannel, autoDialChannel))
        }
    }
}
