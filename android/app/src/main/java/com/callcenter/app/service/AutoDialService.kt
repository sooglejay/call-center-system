package com.callcenter.app.service

import android.app.NotificationManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.callcenter.app.CallCenterApp
import com.callcenter.app.MainActivity
import com.callcenter.app.R
import com.callcenter.app.data.model.Customer
import com.callcenter.app.data.model.UpdateTaskCustomerStatusRequest
import com.callcenter.app.data.local.preferences.AutoDialProgressManager
import com.callcenter.app.data.local.preferences.AutoDialProgress
import com.callcenter.app.data.repository.TaskRepository
import com.callcenter.app.ui.viewmodel.AutoDialScopeType
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
        const val ACTION_RESTORE = "com.callcenter.app.action.RESTORE_AUTO_DIAL"
        const val ACTION_CLOSE_NOTIFICATION = "com.callcenter.app.action.CLOSE_NOTIFICATION"

        const val EXTRA_INTERVAL = "interval_seconds"
        const val EXTRA_TIMEOUT = "timeout_seconds"
        const val EXTRA_RETRY_COUNT = "retry_count"
        const val EXTRA_DIALS_PER_CUSTOMER = "dials_per_customer"  // 每个客户连续拨打次数
        const val EXTRA_CUSTOMERS = "customers"
        const val EXTRA_SCOPE_TYPE = "scope_type"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TASK_TITLE = "task_title"
        const val EXTRA_START_INDEX = "start_index"
        const val EXTRA_DIALED_COUNT = "dialed_count"
        const val EXTRA_START_DIAL_ROUND = "start_dial_round"  // 恢复进度时的当前轮次

        // 状态流
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning

        private val _currentCustomer = MutableStateFlow<Customer?>(null)
        val currentCustomer: StateFlow<Customer?> = _currentCustomer

        private val _dialedCount = MutableStateFlow(0)
        val dialedCount: StateFlow<Int> = _dialedCount

        private val _scopeType = MutableStateFlow<String?>(null)
        val scopeType: StateFlow<String?> = _scopeType

        private val _taskId = MutableStateFlow<Int?>(null)
        val taskId: StateFlow<Int?> = _taskId

        // 任务完成回调流
        private val _taskCompleted = MutableStateFlow<Int?>(null)
        val taskCompleted: StateFlow<Int?> = _taskCompleted

        private const val NOTIFICATION_ID = 1001
    }

    @Inject
    lateinit var callHelper: CallHelper

    @Inject
    lateinit var progressManager: AutoDialProgressManager

    @Inject
    lateinit var taskRepository: TaskRepository

    private var job: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var intervalSeconds: Int = 10
    private var timeoutSeconds: Int = 30
    private var retryCount: Int = 0
    private var dialsPerCustomer: Int = 1  // 每个客户连续拨打次数，默认1次
    private var currentDialRound: Int = 1  // 当前客户的第几轮拨打
    private var taskTitle: String? = null

    private var isPaused: Boolean = false
    private var customerQueue: MutableList<Customer> = mutableListOf()
    private var currentIndex: Int = 0
    private var scopeType: AutoDialScopeType = AutoDialScopeType.ALL_PENDING

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                intervalSeconds = intent.getIntExtra(EXTRA_INTERVAL, 10)
                timeoutSeconds = intent.getIntExtra(EXTRA_TIMEOUT, 30)
                retryCount = intent.getIntExtra(EXTRA_RETRY_COUNT, 0)
                dialsPerCustomer = intent.getIntExtra(EXTRA_DIALS_PER_CUSTOMER, 1)
                currentDialRound = intent.getIntExtra(EXTRA_START_DIAL_ROUND, 1)

                // 获取拨号范围信息
                _scopeType.value = intent.getStringExtra(EXTRA_SCOPE_TYPE)
                scopeType = try {
                    AutoDialScopeType.valueOf(intent.getStringExtra(EXTRA_SCOPE_TYPE) ?: AutoDialScopeType.ALL_PENDING.name)
                } catch (e: Exception) {
                    AutoDialScopeType.ALL_PENDING
                }
                _taskId.value = intent.getIntExtra(EXTRA_TASK_ID, -1).takeIf { it != -1 }
                taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE)

                // 获取客户列表
                @Suppress("UNCHECKED_CAST")
                val customers = intent.getSerializableExtra(EXTRA_CUSTOMERS) as? ArrayList<Customer>
                if (customers != null) {
                    customerQueue.clear()
                    customerQueue.addAll(customers)
                }

                // 获取起始索引（用于恢复进度）
                currentIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)
                _dialedCount.value = intent.getIntExtra(EXTRA_DIALED_COUNT, 0)

                // 保存初始进度
                serviceScope.launch {
                    saveProgress()
                }

                startAutoDial()
            }
            ACTION_STOP -> {
                // 清除进度
                serviceScope.launch {
                    progressManager.clearProgress()
                }
                stopAutoDial()
                stopSelf()
            }
            ACTION_PAUSE -> {
                isPaused = true
                // 保存当前进度，以便恢复时从当前位置继续
                serviceScope.launch {
                    saveProgress()
                }
                updateNotification("自动拨号已暂停")
            }
            ACTION_RESUME -> {
                isPaused = false
                updateNotification("自动拨号进行中")
            }
            ACTION_CLOSE_NOTIFICATION -> {
                // 用户手动关闭通知，停止前台服务但保持拨号状态
                stopForeground(STOP_FOREGROUND_REMOVE)
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
        _scopeType.value = null
        _taskId.value = null
        job?.cancel()
        job = null
        customerQueue.clear()
        currentIndex = 0
        isPaused = false
    }

    private fun completeTask() {
        // 通知任务完成
        _taskCompleted.value = _taskId.value
        _taskCompleted.value = null // 重置
    }

    private suspend fun processQueue() {
        while (_isRunning.value && currentIndex < customerQueue.size) {
            if (isPaused) {
                delay(1000)
                continue
            }

            val customer = customerQueue[currentIndex]
            _currentCustomer.value = customer

            updateNotification("正在拨打: ${customer.name} (${currentIndex + 1}/${customerQueue.size}, 第${currentDialRound}/${dialsPerCustomer}次)")

            // 保存进度（每拨打一个客户前保存）
            saveProgress()

            // 拨打电话
            try {
                callHelper.makeCall(customer.phone)
            } catch (e: Exception) {
                // 拨号异常，停止自动拨号
                updateNotification("拨号异常: ${e.message}，自动拨号已停止")
                stopAutoDial()
                break
            }

            // 等待通话结束或超时
            val callEndedNormally = waitForCallEndOrTimeout()
            
            // 如果服务被停止（如权限异常），则退出循环
            if (!callEndedNormally) {
                Log.e("AutoDialService", "callEndedNormally: false")
                break
            }

            // 通话结束（挂断）后标记为已拨打
            _dialedCount.value = _dialedCount.value + 1
            markCustomerAsCalled(customer)
            saveProgress()

            // 判断是否继续拨打同一客户
            if (currentDialRound < dialsPerCustomer) {
                // 同一客户还有下一轮拨打
                currentDialRound++
                // 等待间隔时间后继续拨打同一客户
                if (_isRunning.value) {
                    updateNotification("等待 ${intervalSeconds} 秒后再次拨打 ${customer.name}...")
                    delay(intervalSeconds * 1000L)
                }
            } else {
                // 当前客户所有轮次完成，流转到下一个客户
                currentDialRound = 1
                currentIndex++

                // 如果不是最后一个客户，等待间隔时间
                if (currentIndex < customerQueue.size && _isRunning.value) {
                    updateNotification("等待 ${intervalSeconds} 秒后拨打下一个...")
                    delay(intervalSeconds * 1000L)
                }
            }
        }

        // 所有客户拨打完成
        if (_isRunning.value) {
            updateNotification("自动拨号已完成，共拨打 ${customerQueue.size} 个客户")
            // 清除进度
            progressManager.clearProgress()
            // 通知任务完成
            completeTask()
            delay(3000)
        }
        stopAutoDial()
        stopSelf()
    }

    private suspend fun saveProgress() {
        if (customerQueue.isEmpty()) return

        val remainingCustomers = customerQueue.subList(currentIndex, customerQueue.size)
        val allCustomerIds = customerQueue.map { it.id }

        val progress = AutoDialProgress(
            scopeType = scopeType,
            taskId = _taskId.value,
            taskTitle = taskTitle,
            currentIndex = currentIndex,
            totalCount = customerQueue.size,
            dialedCount = _dialedCount.value,
            intervalSeconds = intervalSeconds,
            timeoutSeconds = timeoutSeconds,
            dialsPerCustomer = dialsPerCustomer,
            currentDialRound = currentDialRound,
            remainingCustomers = remainingCustomers,
            allCustomerIds = allCustomerIds,
            isActive = true,
            lastUpdateTime = System.currentTimeMillis()
        )

        progressManager.saveProgress(progress)
    }

    /**
     * 标记客户为已拨打状态
     * 不论拨号是否接通，都标记为"called"状态
     */
    private suspend fun markCustomerAsCalled(customer: Customer) {
        val taskId = _taskId.value
        if (taskId != null) {
            try {
                val request = UpdateTaskCustomerStatusRequest(
                    status = "called",
                    callResult = "自动拨号完成"
                )
                val result = taskRepository.updateTaskCustomerStatus(taskId, customer.id, request)
                result.fold(
                    onSuccess = {
                        updateNotification("已标记客户 ${customer.name} 为已拨打")
                    },
                    onFailure = { e ->
                        updateNotification("标记客户 ${customer.name} 失败: ${e.message}")
                        e.printStackTrace()
                    }
                )
            } catch (e: Exception) {
                updateNotification("标记客户 ${customer.name} 异常: ${e.message}")
                e.printStackTrace()
            }
        } else {
            updateNotification("无法标记客户: taskId为空")
        }
    }

    /**
     * 等待通话结束或超时
     * @return 是否正常结束（true=正常结束，false=服务被停止）
     */
    private suspend fun waitForCallEndOrTimeout(): Boolean {
        var elapsedTime = 0
        var callConnected = false

        // 尝试使用 TelephonyManager，如果没有权限则使用简单的延时
        try {
            val telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

            // 等待电话接通或失败
            while (_isRunning.value && elapsedTime < timeoutSeconds * 1000) {
                val state = telephonyManager.callState
                
                when (state) {
                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                        // 电话已接通，标记为已连接
                        callConnected = true
                        updateNotification("通话中...")
                    }
                    TelephonyManager.CALL_STATE_IDLE -> {
                        if (callConnected) {
                            // 之前已接通，现在挂断了
                            Log.e("AutoDialService", "SecurityException3: 之前已接通，现在挂断了")
                            return true
                        }
                        // 电话未接通就被挂断了（如无法连接到移动网络）
                        // 等待一小段时间确保状态稳定
                        delay(500)
                        // 再次检查，如果仍然是IDLE，说明确实已结束
                        if (telephonyManager.callState == TelephonyManager.CALL_STATE_IDLE) {
                            Log.e("AutoDialService", "SecurityException2:")
                            return true
                        }
                    }
                    TelephonyManager.CALL_STATE_RINGING -> {
                        // 响铃中，继续等待
                    }
                }
                
                delay(500)
                elapsedTime += 500
            }
        } catch (e: SecurityException) {
            Log.e("AutoDialService", "SecurityException1: ${e.message}")
            // 没有权限读取通话状态，但应该继续标记该电话已完成
            // 而不是停止自动拨号任务
            updateNotification("无法读取通话状态，继续标记为已完成")
            // 返回true表示正常结束，让流程继续标记该客户为已拨打
            return true
        }
        
        // 正常结束（超时或服务停止）
        return _isRunning.value
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
        val currentCustomer = _currentCustomer.value
        val notification = if (currentCustomer != null) {
            createNotificationWithCustomer(content, currentCustomer)
        } else {
            createNotification(content)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationWithCustomer(content: String, customer: Customer): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        // 构建客户信息文本 - 显示姓名、电话、公司
        val customerInfo = buildString {
            append(customer.name)
            append(" | ")
            append(customer.phone)
            if (!customer.company.isNullOrBlank()) {
                append(" | ")
                append(customer.company)
            }
        }

        return NotificationCompat.Builder(this, CallCenterApp.CHANNEL_ID_AUTO_DIAL)
            .setContentTitle(customerInfo)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setStyle(NotificationCompat.BigTextStyle()
                .setBigContentTitle(customerInfo)
                .bigText(content))
            .addAction(
                NotificationCompat.Action.Builder(
                    null,
                    "关闭",
                    PendingIntent.getService(
                        this,
                        2,
                        Intent(this, AutoDialService::class.java).apply {
                            action = ACTION_CLOSE_NOTIFICATION
                        },
                        PendingIntent.FLAG_IMMUTABLE
                    )
                ).build()
            )
            .build()
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
