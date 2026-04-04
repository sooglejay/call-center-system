package com.callcenter.app.service

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
import com.callcenter.app.util.FloatingWindowManager
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
        const val ACTION_CALL_STATUS_CONNECTED = "com.callcenter.app.action.CALL_STATUS_CONNECTED"
        const val ACTION_CALL_STATUS_VOICEMAIL = "com.callcenter.app.action.CALL_STATUS_VOICEMAIL"
        const val ACTION_CALL_STATUS_UNANSWERED = "com.callcenter.app.action.CALL_STATUS_UNANSWERED"
        const val ACTION_CALL_STATUS_FAILED = "com.callcenter.app.action.CALL_STATUS_FAILED"
        const val ACTION_DIAL_NEXT = "com.callcenter.app.action.DIAL_NEXT"  // 手动拨打下一个

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

        private val _totalCount = MutableStateFlow(0)
        val totalCount: StateFlow<Int> = _totalCount

        private val _scopeType = MutableStateFlow<String?>(null)
        val scopeType: StateFlow<String?> = _scopeType

        private val _taskId = MutableStateFlow<Int?>(null)
        val taskId: StateFlow<Int?> = _taskId

        // 任务完成回调流
        private val _taskCompleted = MutableStateFlow<Int?>(null)
        val taskCompleted: StateFlow<Int?> = _taskCompleted

        // 是否自动拨号模式
        private val _isAutoDialMode = MutableStateFlow(false)
        val isAutoDialMode: StateFlow<Boolean> = _isAutoDialMode

        private const val NOTIFICATION_ID = 1001
        private const val TAG = "AutoDialService"
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

    // 手动拨号等待协程
    private var manualDialWaitJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // 设置悬浮窗手动拨号回调
        FloatingCustomerService.manualDialCallback = object : ManualDialCallback {
            override fun onDialNext() {
                // 手动点击拨打下一个
                serviceScope.launch {
                    resumeManualDial()
                }
            }

            override fun onAutoDialModeChanged(isAutoMode: Boolean) {
                // 切换自动/手动模式
                _isAutoDialMode.value = isAutoMode
                Log.d(TAG, "自动拨号模式切换为: $isAutoMode")

                // 如果从手动切换到自动，且当前正在等待手动拨号，则继续
                if (isAutoMode && manualDialWaitJob?.isActive == true) {
                    serviceScope.launch {
                        resumeManualDial()
                    }
                }
            }
        }
    }

    private fun resumeManualDial() {
        manualDialWaitJob?.cancel()
    }

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
                    // 设置总客户数
                    _totalCount.value = customers.size
                }

                // 获取起始索引（用于恢复进度）
                currentIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)
                _dialedCount.value = intent.getIntExtra(EXTRA_DIALED_COUNT, 0)

                // 默认手动模式（更安全）
                _isAutoDialMode.value = false
                FloatingCustomerService.setAutoDialMode(false)

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
            }
            ACTION_RESUME -> {
                isPaused = false
            }
            ACTION_CALL_STATUS_CONNECTED, ACTION_CALL_STATUS_VOICEMAIL, 
            ACTION_CALL_STATUS_UNANSWERED, ACTION_CALL_STATUS_FAILED -> {
                // 处理通话状态确认
                handleCallStatusAction(intent.action)
            }
            ACTION_DIAL_NEXT -> {
                // 手动拨打下一个
                serviceScope.launch {
                    resumeManualDial()
                }
            }
        }
        return START_STICKY
    }

    private fun handleCallStatusAction(action: String?) {
        val callStatus = when (action) {
            ACTION_CALL_STATUS_CONNECTED -> "connected"
            ACTION_CALL_STATUS_VOICEMAIL -> "voicemail"
            ACTION_CALL_STATUS_UNANSWERED -> "unanswered"
            ACTION_CALL_STATUS_FAILED -> "failed"
            else -> return
        }
        
        val customer = _currentCustomer.value
        val taskId = _taskId.value
        
        Log.d(TAG, "处理通话状态标记: status=$callStatus, customer=${customer?.id}, taskId=$taskId")
        
        if (customer != null && taskId != null) {
            serviceScope.launch {
                try {
                    // 更新客户通话状态
                    val request = UpdateTaskCustomerStatusRequest(
                        status = "called",
                        callResult = when (callStatus) {
                            "connected" -> "已接听"
                            "voicemail" -> "语音信箱"
                            "unanswered" -> "响铃未接"
                            "failed" -> "拨打失败"
                            else -> "未知状态"
                        }
                    )
                    Log.d(TAG, "发送通话状态更新请求: customerId=${customer.id}, callResult=${request.callResult}")
                    val result = taskRepository.updateTaskCustomerStatus(taskId, customer.id, request)
                    result.fold(
                        onSuccess = {
                            Log.d(TAG, "通话状态标记成功")
                        },
                        onFailure = { e ->
                            Log.e(TAG, "通话状态标记失败: ${e.message}")
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "标记客户状态失败: ${e.message}", e)
                }
            }
        } else {
            Log.w(TAG, "无法标记通话状态: customer=$customer, taskId=$taskId")
        }
    }

    private fun startAutoDial() {
        if (_isRunning.value) return

        _isRunning.value = true
        startForeground()

        // 显示悬浮窗
        showFloatingWindow()

        job = serviceScope.launch {
            processQueue()
        }
    }

    private fun stopAutoDial() {
        _isRunning.value = false
        _currentCustomer.value = null
        _scopeType.value = null
        _taskId.value = null
        _totalCount.value = 0
        _dialedCount.value = 0
        job?.cancel()
        job = null
        customerQueue.clear()
        currentIndex = 0
        isPaused = false

        // 隐藏悬浮窗
        hideFloatingWindow()
    }

    private fun completeTask() {
        // 通知任务完成
        _taskCompleted.value = _taskId.value
        _taskCompleted.value = null // 重置
    }

    /**
     * 显示悬浮窗客户信息面板
     */
    private fun showFloatingWindow() {
        if (FloatingWindowManager.canDrawOverlays(this)) {
            FloatingWindowManager.showFloatingWindow(
                context = this,
                customer = _currentCustomer.value,
                dialedCount = _dialedCount.value,
                totalCount = customerQueue.size,
                isCalling = _currentCustomer.value != null
            )
        } else {
            Log.w(TAG, "没有悬浮窗权限，使用通知方式显示")
        }
    }

    /**
     * 更新悬浮窗客户信息
     */
    private fun updateFloatingWindow() {
        if (FloatingWindowManager.canDrawOverlays(this)) {
            FloatingWindowManager.updateCustomer(
                context = this,
                customer = _currentCustomer.value,
                isCalling = _currentCustomer.value != null
            )
            FloatingWindowManager.updateProgress(
                context = this,
                dialedCount = _dialedCount.value,
                totalCount = customerQueue.size
            )
        }
    }

    /**
     * 隐藏悬浮窗客户信息面板
     */
    private fun hideFloatingWindow() {
        FloatingWindowManager.hideFloatingWindow(this)
    }

    private suspend fun processQueue() {
        while (_isRunning.value && currentIndex < customerQueue.size) {
            if (isPaused) {
                delay(1000)
                continue
            }

            val customer = customerQueue[currentIndex]
            _currentCustomer.value = customer

            // 更新悬浮窗
            updateFloatingWindow()

            // 保存进度（每拨打一个客户前保存）
            saveProgress()

            // 拨打电话
            var callFailed = false
            var failReason = ""
            try {
                callHelper.makeCall(customer.phone ?: "")
            } catch (e: Exception) {
                // 拨号异常（如无SIM卡、权限被拒绝等），标记为拨打失败并继续下一个
                callFailed = true
                failReason = e.message ?: "拨号失败"
                // 标记为拨打失败
                markCustomerAsFailed(customer, failReason)
            }
            
            // 如果拨号失败，跳过等待流程，直接处理下一个客户
            if (callFailed) {
                _dialedCount.value = _dialedCount.value + 1
                currentDialRound = 1
                currentIndex++
                saveProgress()
                
                // 等待间隔时间后继续下一个
                if (currentIndex < customerQueue.size && _isRunning.value) {
                    // 手动模式下等待用户点击
                    if (!_isAutoDialMode.value) {
                        waitForManualDial()
                    } else {
                        delay(intervalSeconds * 1000L)
                    }
                }
                continue
            }

            // 等待通话结束或超时
            val callEndedNormally = waitForCallEndOrTimeout()
            
            // 如果服务被停止（如权限异常），则退出循环
            if (!callEndedNormally) {
                Log.e(TAG, "callEndedNormally: false")
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
                    // 手动模式下等待用户点击
                    if (!_isAutoDialMode.value) {
                        waitForManualDial()
                    } else {
                        delay(intervalSeconds * 1000L)
                    }
                }
            } else {
                // 当前客户所有轮次完成，流转到下一个客户
                currentDialRound = 1
                currentIndex++

                // 如果不是最后一个客户，等待间隔时间
                if (currentIndex < customerQueue.size && _isRunning.value) {
                    // 手动模式下等待用户点击
                    if (!_isAutoDialMode.value) {
                        waitForManualDial()
                    } else {
                        delay(intervalSeconds * 1000L)
                    }
                }
            }
        }

            // 所有客户拨打完成
            if (_isRunning.value) {
                // 清除进度
                progressManager.clearProgress()
                // 通知任务完成
                completeTask()
                delay(3000)
            }
            stopAutoDial()
            stopSelf()
    }

    /**
     * 等待手动拨号（手动模式下使用）
     */
    private suspend fun waitForManualDial() {
        Log.d(TAG, "手动模式：等待用户点击拨打下一个...")
        
        // 更新悬浮窗显示"等待中"状态
        updateFloatingWindow()
        
        // 创建一个可取消的等待任务
        manualDialWaitJob = serviceScope.launch {
            // 无限等待，直到用户点击或切换到自动模式
            while (_isRunning.value && !_isAutoDialMode.value) {
                delay(500)
            }
        }
        
        // 等待任务完成（用户点击或切换到自动模式）
        manualDialWaitJob?.join()
        manualDialWaitJob = null
        
        Log.d(TAG, "手动模式：继续拨打下一个")
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
                        // 标记成功
                    },
                    onFailure = { e ->
                        e.printStackTrace()
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            Log.w(TAG, "无法标记客户: taskId为空")
        }
    }

    /**
     * 标记客户为拨打失败状态
     * 用于无SIM卡、权限被拒绝等拨号异常情况
     */
    private suspend fun markCustomerAsFailed(customer: Customer, reason: String) {
        val taskId = _taskId.value
        if (taskId != null) {
            try {
                val request = UpdateTaskCustomerStatusRequest(
                    status = "failed",
                    callResult = "拨打失败: $reason"
                )
                val result = taskRepository.updateTaskCustomerStatus(taskId, customer.id, request)
                result.fold(
                    onSuccess = {
                        // 标记成功
                    },
                    onFailure = { e ->
                        e.printStackTrace()
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            Log.w(TAG, "无法标记客户: taskId为空")
        }
    }

    /**
     * 等待通话结束
     * @return 是否正常结束（true=通话已挂断，false=服务被停止）
     */
    private suspend fun waitForCallEndOrTimeout(): Boolean {
        var elapsedTime = 0
        var callConnected = false
        var offHookStartTime: Long = 0
        val MIN_CALL_DURATION = 3000 // 最小通话持续时间 3 秒

        // 尝试使用 TelephonyManager，如果没有权限则使用简单的延时
        try {
            val telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager

            // 等待电话接通
            Log.d(TAG, "等待电话接通...")
            while (_isRunning.value) {
                val state = telephonyManager.callState

                when (state) {
                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                        // 电话已接通（包括运营商语音播报的情况）
                        if (!callConnected) {
                            callConnected = true
                            offHookStartTime = System.currentTimeMillis()
                            Log.d(TAG, "电话已接通/进入通话状态")
                        }
                    }
                    TelephonyManager.CALL_STATE_IDLE -> {
                        if (callConnected) {
                            // 之前已接通，现在挂断了
                            // 确保通话持续时间超过最小值（避免误判运营商语音播报为有效通话）
                            val callDuration = System.currentTimeMillis() - offHookStartTime
                            if (callDuration >= MIN_CALL_DURATION) {
                                Log.d(TAG, "通话已挂断，持续时间: ${callDuration}ms")
                                return true
                            } else {
                                // 通话时间太短，可能是运营商语音播报号码有误
                                // 等待一下确认状态稳定
                                Log.d(TAG, "通话时间太短(${callDuration}ms)，等待确认...")
                                delay(1000)
                                if (telephonyManager.callState == TelephonyManager.CALL_STATE_IDLE) {
                                    Log.d(TAG, "确认通话已结束")
                                    return true
                                }
                            }
                        } else {
                            // 电话未接通就被挂断了（如无法连接到移动网络）
                            // 等待一小段时间确保状态稳定
                            delay(500)
                            // 再次检查，如果仍然是IDLE，说明确实已结束
                            if (telephonyManager.callState == TelephonyManager.CALL_STATE_IDLE) {
                                Log.d(TAG, "电话未接通即结束")
                                return true
                            }
                        }
                    }
                    TelephonyManager.CALL_STATE_RINGING -> {
                        // 响铃中，继续等待
                        if (elapsedTime % 5000 == 0) { // 每5秒打印一次日志
                            Log.d(TAG, "电话响铃中...")
                        }
                    }
                }

                delay(500)
                elapsedTime += 500
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: ${e.message}")
            // 没有权限读取通话状态，使用超时机制作为后备
            Log.w(TAG, "无通话状态权限，使用${timeoutSeconds}秒超时")
            delay(timeoutSeconds * 1000L)
            return _isRunning.value
        }

        // 服务被停止
        Log.d(TAG, "服务被停止，结束等待")
        return false
    }

    private fun startForeground() {
        // Android要求前台服务必须有通知，创建一个简单的通知
        val notification = createSimpleNotification()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
        )
    }

    /**
     * 创建简单的前台服务通知（Android要求）
     */
    private fun createSimpleNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CallCenterApp.CHANNEL_ID_AUTO_DIAL)
            .setContentTitle("智能呼叫中心")
            .setContentText("自动拨号服务运行中")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutoDial()
        serviceScope.cancel()
        // 清理回调
        FloatingCustomerService.manualDialCallback = null
    }
}
