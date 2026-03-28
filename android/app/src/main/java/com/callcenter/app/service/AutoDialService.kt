package com.callcenter.app.service

import android.app.NotificationManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.callcenter.app.CallCenterApp
import com.callcenter.app.MainActivity
import com.callcenter.app.R
import com.callcenter.app.data.model.Customer
import com.callcenter.app.util.CallHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * 自动拨号服务
 * 负责按顺序自动拨打客户电话
 */
@AndroidEntryPoint
class AutoDialService : Service() {

    companion object {
        const val ACTION_START = "com.callcenter.app.action.START_AUTO_DIAL"
        const val ACTION_STOP = "com.callcenter.app.action.STOP_AUTO_DIAL"
        const val ACTION_PAUSE = "com.callcenter.app.action.PAUSE_AUTO_DIAL"
        const val ACTION_RESUME = "com.callcenter.app.action.RESUME_AUTO_DIAL"

        const val EXTRA_INTERVAL = "interval_seconds"
        const val EXTRA_TIMEOUT = "timeout_seconds"
        const val EXTRA_RETRY_COUNT = "retry_count"
        const val EXTRA_CUSTOMERS = "customers"

        // 状态流
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning

        private val _currentCustomer = MutableStateFlow<Customer?>(null)
        val currentCustomer: StateFlow<Customer?> = _currentCustomer

        private val _dialedCount = MutableStateFlow(0)
        val dialedCount: StateFlow<Int> = _dialedCount

        private const val NOTIFICATION_ID = 1001
    }

    @Inject
    lateinit var callHelper: CallHelper

    private var job: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var intervalSeconds: Int = 10
    private var timeoutSeconds: Int = 30
    private var retryCount: Int = 0

    private var isPaused: Boolean = false
    private var customerQueue: MutableList<Customer> = mutableListOf()
    private var currentIndex: Int = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                intervalSeconds = intent.getIntExtra(EXTRA_INTERVAL, 10)
                timeoutSeconds = intent.getIntExtra(EXTRA_TIMEOUT, 30)
                retryCount = intent.getIntExtra(EXTRA_RETRY_COUNT, 0)

                // 获取客户列表
                @Suppress("UNCHECKED_CAST")
                val customers = intent.getSerializableExtra(EXTRA_CUSTOMERS) as? ArrayList<Customer>
                if (customers != null) {
                    customerQueue.clear()
                    customerQueue.addAll(customers)
                }

                startAutoDial()
            }
            ACTION_STOP -> {
                stopAutoDial()
                stopSelf()
            }
            ACTION_PAUSE -> {
                isPaused = true
                updateNotification("自动拨号已暂停")
            }
            ACTION_RESUME -> {
                isPaused = false
                updateNotification("自动拨号进行中")
            }
        }
        return START_STICKY
    }

    private fun startAutoDial() {
        if (_isRunning.value) return

        _isRunning.value = true
        startForeground()
        job = serviceScope.launch {
            processQueue()
        }
    }

    private fun stopAutoDial() {
        _isRunning.value = false
        _currentCustomer.value = null
        job?.cancel()
        job = null
        customerQueue.clear()
        currentIndex = 0
        isPaused = false
    }

    private suspend fun processQueue() {
        while (_isRunning.value && currentIndex < customerQueue.size) {
            if (isPaused) {
                delay(1000)
                continue
            }

            val customer = customerQueue[currentIndex]
            _currentCustomer.value = customer

            updateNotification("正在拨打: ${customer.name} (${currentIndex + 1}/${customerQueue.size})")

            // 拨打电话
            callHelper.makeCall(customer.phone)

            // 等待通话结束或超时
            waitForCallEndOrTimeout()

            // 更新状态
            _dialedCount.value = _dialedCount.value + 1
            currentIndex++

            // 如果不是最后一个，等待间隔时间
            if (currentIndex < customerQueue.size && _isRunning.value) {
                updateNotification("等待 ${intervalSeconds} 秒后拨打下一个...")
                delay(intervalSeconds * 1000L)
            }
        }

        // 所有客户拨打完成
        if (_isRunning.value) {
            updateNotification("自动拨号已完成，共拨打 ${customerQueue.size} 个客户")
            delay(3000)
        }
        stopAutoDial()
        stopSelf()
    }

    private suspend fun waitForCallEndOrTimeout() {
        var elapsedTime = 0

        // 尝试使用 TelephonyManager，如果没有权限则使用简单的延时
        try {
            val telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

            // 等待电话接通
            while (_isRunning.value && elapsedTime < timeoutSeconds * 1000) {
                val state = telephonyManager.callState
                if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
                    // 电话已接通，等待挂断
                    while (telephonyManager.callState != TelephonyManager.CALL_STATE_IDLE) {
                        delay(500)
                    }
                    return
                }
                if (state == TelephonyManager.CALL_STATE_IDLE) {
                    // 电话未接通就被挂断了
                    return
                }
                delay(500)
                elapsedTime += 500
            }
        } catch (e: SecurityException) {
            // 没有权限，使用简单的延时策略
            // 等待拨号完成（通常需要几秒钟）
            delay(2000)
            // 然后等待用户手动挂断或超时
            while (_isRunning.value && elapsedTime < timeoutSeconds * 1000) {
                delay(1000)
                elapsedTime += 1000
            }
        }
    }

    private fun startForeground() {
        val notification = createNotification("自动拨号准备中...")

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
        )
    }

    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CallCenterApp.CHANNEL_ID_AUTO_DIAL)
            .setContentTitle("智能呼叫中心 - 自动拨号")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(
                NotificationCompat.Action.Builder(
                    null,
                    "停止",
                    PendingIntent.getService(
                        this,
                        1,
                        Intent(this, AutoDialService::class.java).apply {
                            action = ACTION_STOP
                        },
                        PendingIntent.FLAG_IMMUTABLE
                    )
                ).build()
            )
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutoDial()
        serviceScope.cancel()
    }
}
