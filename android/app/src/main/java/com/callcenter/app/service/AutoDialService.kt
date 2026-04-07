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
import com.callcenter.app.util.AppLifecycleManager
import com.callcenter.app.util.CallHelper
import com.callcenter.app.util.FloatingWindowManager
import com.callcenter.app.util.root.RootCallState
import com.callcenter.app.util.root.RootCallStateDetector
import com.callcenter.app.util.root.RootPhoneStateListener
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
        // 更多通话状态标记动作
        const val ACTION_CALL_STATUS_REJECTED = "com.callcenter.app.action.CALL_STATUS_REJECTED"
        const val ACTION_CALL_STATUS_BUSY = "com.callcenter.app.action.CALL_STATUS_BUSY"
        const val ACTION_CALL_STATUS_POWER_OFF = "com.callcenter.app.action.CALL_STATUS_POWER_OFF"
        const val ACTION_CALL_STATUS_NO_ANSWER = "com.callcenter.app.action.CALL_STATUS_NO_ANSWER"
        const val ACTION_CALL_STATUS_IVR = "com.callcenter.app.action.CALL_STATUS_IVR"
        const val ACTION_CALL_STATUS_OTHER = "com.callcenter.app.action.CALL_STATUS_OTHER"
        const val ACTION_DIAL_NEXT = "com.callcenter.app.action.DIAL_NEXT"  // 手动拨打下一个
        const val ACTION_SET_AUTO_MODE = "com.callcenter.app.action.SET_AUTO_MODE"  // 设置自动/手动模式

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
        const val EXTRA_AUTO_MODE = "auto_mode"  // 自动/手动模式

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

        // 当前通话状态流（用于悬浮窗监听）
        private val _currentCallState = MutableStateFlow<com.callcenter.app.util.root.RootCallState?>(null)
        val currentCallState: StateFlow<com.callcenter.app.util.root.RootCallState?> = _currentCallState

        // 当前通话号码流
        private val _currentCallNumber = MutableStateFlow<String?>(null)
        val currentCallNumber: StateFlow<String?> = _currentCallNumber

        private const val NOTIFICATION_ID = 1001
        private const val TAG = "AutoDialService"
        
        /**
         * 设置自动拨号模式
         * 供外部组件（如 FloatingCustomerService）调用
         */
        fun setAutoDialMode(isAutoMode: Boolean) {
            _isAutoDialMode.value = isAutoMode
        }
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
    
    // 防止重复拨打标志
    private var isWaitingForNextDial: Boolean = false

    // 自动拨号挂起状态（当不在前台或通话中时挂起）
    private var isAutoDialSuspended: Boolean = false
    private var suspendCheckJob: Job? = null

    // 当前通话状态
    private var currentCallState: Int = TelephonyManager.CALL_STATE_IDLE
    
    // Root 通话状态检测器
    private lateinit var rootCallStateDetector: RootCallStateDetector
    
    // 是否已自动标记当前通话状态
    private var hasAutoMarkedCurrentCall: Boolean = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        
        // 初始化 Root 通话状态检测器
        rootCallStateDetector = RootCallStateDetector()
        setupCallStateListener()
        
        // 启动前台状态监控
        startForegroundMonitoring()
        
        // 设置悬浮窗手动拨号回调
        FloatingCustomerService.manualDialCallback = object : ManualDialCallback {
            override fun onDialNext() {
                // 手动点击拨打下一个
                serviceScope.launch {
                    Log.d(TAG, "[manualDialCallback.onDialNext] 用户点击拨打下一个按钮")
                    Log.d(TAG, "[manualDialCallback.onDialNext] 当前状态: _isRunning=$_isRunning.value, _isAutoDialMode=$_isAutoDialMode.value, manualDialWaitJob=$manualDialWaitJob")
                    
                    // 检查当前通话状态
                    val callState = callHelper.getCallState()
                    Log.d(TAG, "[manualDialCallback.onDialNext] 当前通话状态: $callState")
                    
                    if (callState != TelephonyManager.CALL_STATE_IDLE) {
                        // 当前正在通话中，先挂断当前电话
                        Log.w(TAG, "[manualDialCallback.onDialNext] 当前正在通话中，先挂断当前电话")
                        hangupCurrentCall()
                        
                        // 等待通话挂断（最多等待3秒）
                        var waitCount = 0
                        while (callHelper.getCallState() != TelephonyManager.CALL_STATE_IDLE && waitCount < 30) {
                            delay(100)
                            waitCount++
                        }
                        
                        if (callHelper.getCallState() != TelephonyManager.CALL_STATE_IDLE) {
                            Log.e(TAG, "[manualDialCallback.onDialNext] 无法挂断当前电话，取消拨打下一个")
                            return@launch
                        }
                        
                        Log.d(TAG, "[manualDialCallback.onDialNext] 当前电话已挂断")
                    }
                    
                    // 推进到下一个客户（设置轮次为最大值，让 processQueue 自动推进）
                    val currentCustomer = _currentCustomer.value
                    Log.d(TAG, "[manualDialCallback.onDialNext] 当前客户: $currentCustomer, currentDialRound=$currentDialRound, dialsPerCustomer=$dialsPerCustomer")
                    if (currentCustomer != null) {
                        currentDialRound = dialsPerCustomer
                        Log.d(TAG, "[manualDialCallback.onDialNext] 设置 currentDialRound = $dialsPerCustomer")
                    }
                    
                    // 继续拨打（取消等待）
                    Log.d(TAG, "[manualDialCallback.onDialNext] 调用 resumeManualDial()")
                    resumeManualDial()
                    Log.d(TAG, "[manualDialCallback.onDialNext] 执行完成")
                }
            }

            override fun onAutoDialModeChanged(isAutoMode: Boolean) {
                // 切换自动/手动模式
                _isAutoDialMode.value = isAutoMode
                Log.d(TAG, "自动拨号模式切换为: $isAutoMode")

                // 如果从手动切换到自动，且当前正在等待手动拨号，则继续
                // 但要确保当前不在通话中且没有在等待拨打下一个
                if (isAutoMode && manualDialWaitJob?.isActive == true) {
                    // 检查是否已经在等待拨打下一个，防止重复触发
                    if (isWaitingForNextDial) {
                        Log.w(TAG, "已经在等待拨打下一个，切换到自动模式但不会重复触发")
                        return
                    }
                    
                    // 检查当前通话状态
                    val callState = callHelper.getCallState()
                    if (callState == TelephonyManager.CALL_STATE_IDLE) {
                        // 当前没有在通话中，可以安全地继续
                        serviceScope.launch {
                            resumeManualDial()
                        }
                    } else {
                        Log.w(TAG, "当前正在通话中，切换到自动模式但不会自动拨打下一个")
                    }
                }
            }

            override fun onStopDial() {
                // 停止拨号
                Log.d(TAG, "用户点击停止拨号按钮")
                stopAutoDial()
            }
        }
    }

    private fun resumeManualDial() {
        Log.d(TAG, "resumeManualDial: manualDialWaitJob=${manualDialWaitJob}, isActive=${manualDialWaitJob?.isActive}")
        manualDialWaitJob?.cancel()
        Log.d(TAG, "resumeManualDial: 已取消 manualDialWaitJob")
    }

    /**
     * 设置通话状态监听器
     * 根据 Root 设备检测到的通话状态自动标记通话结果
     */
    private fun setupCallStateListener() {
        rootCallStateDetector.addListener(object : RootPhoneStateListener {
            override fun onActive(number: String?, setupTime: Long) {
                Log.d(TAG, "[CallStateListener] 通话已接通: $number, 接通耗时: ${setupTime}ms")
                // 通话接通时自动标记为已接听
                serviceScope.launch {
                    if (!hasAutoMarkedCurrentCall) {
                        hasAutoMarkedCurrentCall = true
                        autoMarkCallStatus("connected", "已接听")
                    }
                }
            }

            override fun onIdle(duration: Long) {
                Log.d(TAG, "[CallStateListener] 通话结束, 时长: ${duration}ms")
                // 通话结束时，根据时长判断结果
                serviceScope.launch {
                    if (!hasAutoMarkedCurrentCall) {
                        hasAutoMarkedCurrentCall = true
                        // 如果通话时长为0，说明未接通
                        if (duration <= 0) {
                            // 根据之前的通话状态判断
                            val lastState = rootCallStateDetector.getCurrentState()
                            when (lastState) {
                                RootCallState.BUSY -> autoMarkCallStatus("busy", "对方忙线")
                                RootCallState.REJECTED -> autoMarkCallStatus("rejected", "对方拒接")
                                RootCallState.NO_ANSWER -> autoMarkCallStatus("no_answer", "无人接听")
                                RootCallState.POWER_OFF -> autoMarkCallStatus("power_off", "对方关机")
                                RootCallState.VOICEMAIL -> autoMarkCallStatus("voicemail", "语音信箱")
                                else -> autoMarkCallStatus("unanswered", "响铃未接")
                            }
                        }
                    }
                    // 重置标记，为下一次通话做准备
                    hasAutoMarkedCurrentCall = false
                }
            }

            override fun onStateChanged(oldState: RootCallState, newState: RootCallState) {
                Log.d(TAG, "[CallStateListener] 状态变更: $oldState -> $newState")
                
                // 检测特定状态并自动标记
                serviceScope.launch {
                    when (newState) {
                        RootCallState.BUSY -> {
                            if (!hasAutoMarkedCurrentCall) {
                                hasAutoMarkedCurrentCall = true
                                autoMarkCallStatus("busy", "对方忙线")
                            }
                        }
                        RootCallState.REJECTED -> {
                            if (!hasAutoMarkedCurrentCall) {
                                hasAutoMarkedCurrentCall = true
                                autoMarkCallStatus("rejected", "对方拒接")
                            }
                        }
                        RootCallState.VOICEMAIL -> {
                            if (!hasAutoMarkedCurrentCall) {
                                hasAutoMarkedCurrentCall = true
                                autoMarkCallStatus("voicemail", "语音信箱")
                            }
                        }
                        RootCallState.POWER_OFF -> {
                            if (!hasAutoMarkedCurrentCall) {
                                hasAutoMarkedCurrentCall = true
                                autoMarkCallStatus("power_off", "对方关机")
                            }
                        }
                        else -> {}
                    }
                }
            }
        })
        
        // 启动检测
        rootCallStateDetector.startDetection()
        
        Log.d(TAG, "通话状态监听器已设置")
    }

    /**
     * 自动标记通话状态
     */
    private suspend fun autoMarkCallStatus(status: String, displayName: String) {
        Log.d(TAG, "[AutoMark] 自动标记通话状态: $status ($displayName)")
        
        val customer = _currentCustomer.value
        if (customer == null) {
            Log.w(TAG, "[AutoMark] 当前客户为空，无法标记")
            return
        }
        
        val taskId = _taskId.value
        if (taskId == null) {
            Log.w(TAG, "[AutoMark] 当前任务ID为空，无法标记")
            return
        }
        
        try {
            // 调用标记方法
            markCustomerAsCalledWithStatus(customer, status, displayName)
            
            // 添加到通话状态历史记录
            FloatingCustomerService.addCallStateHistory(
                state = displayName,
                number = customer.phone,
                customerName = customer.name,
                duration = rootCallStateDetector.getCurrentCallDuration()
            )
            
            Log.d(TAG, "[AutoMark] 自动标记完成: $status")
        } catch (e: Exception) {
            Log.e(TAG, "[AutoMark] 自动标记失败: ${e.message}")
        }
    }

    /**
     * 根据状态标记客户
     */
    private suspend fun markCustomerAsCalledWithStatus(customer: Customer, status: String, callResult: String) {
        val taskId = _taskId.value ?: return
        
        try {
            // 更新客户状态
            taskRepository.updateTaskCustomerStatus(
                taskId = taskId,
                customerId = customer.id,
                request = UpdateTaskCustomerStatusRequest(
                    status = "called",
                    callResult = callResult
                )
            )
            
            Log.d(TAG, "[AutoMark] 客户状态已更新: ${customer.name} -> $callResult")
        } catch (e: Exception) {
            Log.e(TAG, "[AutoMark] 更新客户状态失败: ${e.message}")
        }
    }

    /**
     * 启动前台状态监控
     * 监控应用是否在前台以及通话状态
     */
    private fun startForegroundMonitoring() {
        suspendCheckJob = serviceScope.launch {
            while (_isRunning.value) {
                // 检查应用是否在前台
                val isInForeground = AppLifecycleManager.isAppInForeground()
                
                // 检查当前通话状态
                currentCallState = callHelper.getCallState()
                val isCallIdle = currentCallState == TelephonyManager.CALL_STATE_IDLE
                
                // 判断是否应该挂起自动拨号
                // 条件：自动模式 + (不在前台 或 通话中)
                val shouldSuspend = _isAutoDialMode.value && (!isInForeground || !isCallIdle)
                
                if (shouldSuspend && !isAutoDialSuspended) {
                    // 需要挂起
                    isAutoDialSuspended = true
                    Log.w(TAG, "自动拨号已挂起: 前台=$isInForeground, 通话空闲=$isCallIdle")
                    updateFloatingWindowStatus("已挂起 - 请回到应用或等待通话结束")
                } else if (!shouldSuspend && isAutoDialSuspended) {
                    // 解除挂起
                    isAutoDialSuspended = false
                    Log.d(TAG, "自动拨号已恢复: 前台=$isInForeground, 通话空闲=$isCallIdle")
                    updateFloatingWindowStatus("自动拨号中")
                    
                    // 如果正在等待，继续拨打
                    if (manualDialWaitJob?.isActive == true) {
                        resumeManualDial()
                    }
                }
                
                delay(1000) // 每秒检查一次
            }
        }
    }

    /**
     * 检查是否可以拨打下一个电话
     * 条件：App在前台 + 当前通话已挂断或没有正在拨号
     */
    private fun canDialNext(): Boolean {
        // 检查是否在前台
        if (!AppLifecycleManager.isAppInForeground()) {
            Log.w(TAG, "不能拨打: App不在前台")
            return false
        }
        
        // 检查通话状态
        val callState = callHelper.getCallState()
        if (callState != TelephonyManager.CALL_STATE_IDLE) {
            Log.w(TAG, "不能拨打: 当前通话状态不是空闲, state=$callState")
            return false
        }
        
        return true
    }

    /**
     * 等待直到可以拨打下一个电话
     * 挂起状态直到条件满足
     */
    private suspend fun waitUntilCanDial() {
        while (_isRunning.value) {
            if (canDialNext()) {
                Log.d(TAG, "条件满足，可以继续拨打")
                return
            }
            
            // 更新悬浮窗状态
            val isInForeground = AppLifecycleManager.isAppInForeground()
            val callState = callHelper.getCallState()
            
            when {
                !isInForeground -> updateFloatingWindowStatus("已挂起 - 请回到应用")
                callState != TelephonyManager.CALL_STATE_IDLE -> updateFloatingWindowStatus("已挂起 - 等待通话结束")
            }
            
            delay(1000) // 每秒检查一次
        }
    }

    /**
     * 挂断当前电话
     */
    private fun hangupCurrentCall() {
        try {
            // 使用反射调用 TelephonyService 挂断电话
            val telephonyService = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
            
            // 尝试通过广播挂断
            val intent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                putExtra(Intent.EXTRA_KEY_EVENT, android.view.KeyEvent(
                    android.view.KeyEvent.ACTION_DOWN,
                    android.view.KeyEvent.KEYCODE_ENDCALL
                ))
            }
            sendOrderedBroadcast(intent, null)
            
            Log.d(TAG, "尝试挂断当前电话")
        } catch (e: Exception) {
            Log.e(TAG, "挂断电话失败: ${e.message}")
        }
    }

    /**
     * 更新悬浮窗状态显示
     */
    private fun updateFloatingWindowStatus(status: String) {
        // 更新悬浮窗显示的状态文本
        FloatingWindowManager.updateStatus(status)
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

                // 默认自动拨号模式
                _isAutoDialMode.value = true
                FloatingCustomerService.setAutoDialMode(true)

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
            ACTION_CALL_STATUS_UNANSWERED, ACTION_CALL_STATUS_FAILED,
            ACTION_CALL_STATUS_REJECTED, ACTION_CALL_STATUS_BUSY,
            ACTION_CALL_STATUS_POWER_OFF, ACTION_CALL_STATUS_NO_ANSWER,
            ACTION_CALL_STATUS_IVR, ACTION_CALL_STATUS_OTHER -> {
                // 处理通话状态确认
                handleCallStatusAction(intent.action)
            }
            ACTION_DIAL_NEXT -> {
                // 手动拨打下一个 - 与 manualDialCallback.onDialNext() 逻辑一致
                serviceScope.launch {
                    Log.d(TAG, "[ACTION_DIAL_NEXT] 收到 Intent，开始执行")
                    Log.d(TAG, "[ACTION_DIAL_NEXT] 当前状态: _isRunning=$_isRunning.value, _isAutoDialMode=$_isAutoDialMode.value, manualDialWaitJob=$manualDialWaitJob")
                    
                    // 检查当前通话状态
                    val callState = callHelper.getCallState()
                    Log.d(TAG, "[ACTION_DIAL_NEXT] 当前通话状态: $callState")
                    
                    if (callState != TelephonyManager.CALL_STATE_IDLE) {
                        // 当前正在通话中，先挂断当前电话
                        Log.w(TAG, "[ACTION_DIAL_NEXT] 当前正在通话中，先挂断当前电话")
                        hangupCurrentCall()
                        
                        // 等待通话挂断（最多等待3秒）
                        var waitCount = 0
                        while (callHelper.getCallState() != TelephonyManager.CALL_STATE_IDLE && waitCount < 30) {
                            delay(100)
                            waitCount++
                        }
                        
                        if (callHelper.getCallState() != TelephonyManager.CALL_STATE_IDLE) {
                            Log.e(TAG, "[ACTION_DIAL_NEXT] 无法挂断当前电话，取消拨打下一个")
                            return@launch
                        }
                        
                        Log.d(TAG, "[ACTION_DIAL_NEXT] 当前电话已挂断")
                    }
                    
                    // 推进到下一个客户（设置轮次为最大值，让 processQueue 自动推进）
                    val currentCustomer = _currentCustomer.value
                    Log.d(TAG, "[ACTION_DIAL_NEXT] 当前客户: $currentCustomer, currentDialRound=$currentDialRound, dialsPerCustomer=$dialsPerCustomer")
                    if (currentCustomer != null) {
                        currentDialRound = dialsPerCustomer
                        Log.d(TAG, "[ACTION_DIAL_NEXT] 设置 currentDialRound = $dialsPerCustomer")
                    }
                    
                    // 继续拨打（取消等待）
                    Log.d(TAG, "[ACTION_DIAL_NEXT] 调用 resumeManualDial()")
                    resumeManualDial()
                    Log.d(TAG, "[ACTION_DIAL_NEXT] 执行完成")
                }
            }
            ACTION_SET_AUTO_MODE -> {
                // 设置自动/手动模式
                val isAutoMode = intent.getBooleanExtra(EXTRA_AUTO_MODE, true)
                _isAutoDialMode.value = isAutoMode
                Log.d(TAG, "通过Intent切换自动拨号模式为: $isAutoMode")
                
                // 同步到 FloatingCustomerService（使用内部方法避免循环）
                FloatingCustomerService.updateAutoDialModeFromService(isAutoMode)
                Log.d(TAG, "已同步自动拨号模式到 FloatingCustomerService: $isAutoMode")
                
                // 如果从手动切换到自动，且当前正在等待手动拨号，则继续
                if (isAutoMode && manualDialWaitJob?.isActive == true && !isWaitingForNextDial) {
                    val callState = callHelper.getCallState()
                    if (callState == TelephonyManager.CALL_STATE_IDLE) {
                        serviceScope.launch {
                            resumeManualDial()
                        }
                    }
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
            ACTION_CALL_STATUS_REJECTED -> "rejected"
            ACTION_CALL_STATUS_BUSY -> "busy"
            ACTION_CALL_STATUS_POWER_OFF -> "power_off"
            ACTION_CALL_STATUS_NO_ANSWER -> "no_answer"
            ACTION_CALL_STATUS_IVR -> "ivr"
            ACTION_CALL_STATUS_OTHER -> "other"
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
                            "rejected" -> "对方拒接"
                            "busy" -> "对方忙线"
                            "power_off" -> "关机/停机"
                            "no_answer" -> "无人接听"
                            "ivr" -> "IVR语音"
                            "other" -> "其他"
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

        // 悬浮窗的显示/隐藏由 AutoFloatingWindow 组件根据应用前后台状态控制
        // 这里不再直接调用 showFloatingWindow()

        job = serviceScope.launch {
            processQueue()
        }
    }

    private fun stopAutoDial() {
        Log.d(TAG, "stopAutoDial: 停止自动拨号")
        _isRunning.value = false
        _currentCustomer.value = null
        _scopeType.value = null
        _taskId.value = null
        _totalCount.value = 0
        _dialedCount.value = 0
        job?.cancel()
        job = null
        manualDialWaitJob?.cancel()
        manualDialWaitJob = null
        customerQueue.clear()
        currentIndex = 0
        isPaused = false
        isWaitingForNextDial = false  // 清除等待标志

        // 隐藏悬浮窗
        hideFloatingWindow()
        Log.d(TAG, "stopAutoDial: 已隐藏悬浮窗")
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

            // 手动模式下，等待用户点击"下一个"才开始拨打
            if (!_isAutoDialMode.value) {
                Log.d(TAG, "[processQueue] 手动模式：等待用户点击下一个开始拨打 ${customer.name}")
                isWaitingForNextDial = true
                waitForManualDial()
                isWaitingForNextDial = false
                Log.d(TAG, "[processQueue] 手动模式：等待结束，继续执行")
                // 用户点击后，检查是否还在运行状态
                if (!_isRunning.value) {
                    Log.d(TAG, "[processQueue] 服务已停止，退出循环")
                    break
                }
            }

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
                        isWaitingForNextDial = true
                        waitForManualDial()
                        isWaitingForNextDial = false
                    } else {
                        // 自动模式下，先检查是否可以拨打
                        waitUntilCanDial()
                        if (_isRunning.value) {
                            delay(intervalSeconds * 1000L)
                        }
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
            
            // 如果自动检测已经标记过该客户，则不再重复标记
            if (!hasAutoMarkedCurrentCall) {
                markCustomerAsCalled(customer)
            } else {
                Log.d(TAG, "[processQueue] 自动检测已标记该客户，跳过 markCustomerAsCalled")
            }
            saveProgress()
            
            // 重置自动标记标志，为下一次通话做准备
            hasAutoMarkedCurrentCall = false

            // 判断是否继续拨打同一客户
            if (currentDialRound < dialsPerCustomer) {
                // 同一客户还有下一轮拨打
                currentDialRound++
                // 等待间隔时间后继续拨打同一客户
                if (_isRunning.value) {
                    // 手动模式下等待用户点击
                    if (!_isAutoDialMode.value) {
                        Log.d(TAG, "[processQueue] 同一客户下一轮：等待用户点击下一个")
                        isWaitingForNextDial = true
                        waitForManualDial()
                        isWaitingForNextDial = false
                        Log.d(TAG, "[processQueue] 同一客户下一轮：等待结束")
                    } else {
                        // 自动模式下，先检查是否可以拨打
                        waitUntilCanDial()
                        if (_isRunning.value) {
                            delay(intervalSeconds * 1000L)
                        }
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
                        Log.d(TAG, "[processQueue] 下一个客户：等待用户点击下一个")
                        isWaitingForNextDial = true
                        waitForManualDial()
                        isWaitingForNextDial = false
                        Log.d(TAG, "[processQueue] 下一个客户：等待结束")
                    } else {
                        // 自动模式下，先检查是否可以拨打
                        waitUntilCanDial()
                        if (_isRunning.value) {
                            delay(intervalSeconds * 1000L)
                        }
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
        Log.d(TAG, "waitForManualDial: 开始等待用户点击拨打下一个...")
        
        // 更新悬浮窗显示"等待中"状态
        updateFloatingWindow()
        
        try {
            // 创建一个可取消的等待任务
            manualDialWaitJob = serviceScope.launch {
                Log.d(TAG, "waitForManualDial: 启动等待循环，_isRunning=$_isRunning.value, _isAutoDialMode=$_isAutoDialMode.value")
                // 无限等待，直到用户点击或切换到自动模式
                while (_isRunning.value && !_isAutoDialMode.value) {
                    delay(500)
                }
                Log.d(TAG, "waitForManualDial: 等待循环结束，_isRunning=$_isRunning.value, _isAutoDialMode=$_isAutoDialMode.value")
            }
            
            // 等待任务完成（用户点击或切换到自动模式）
            Log.d(TAG, "waitForManualDial: 调用 manualDialWaitJob.join()")
            manualDialWaitJob?.join()
            Log.d(TAG, "waitForManualDial: manualDialWaitJob.join() 返回")
        } catch (e: CancellationException) {
            // 正常取消，不需要处理
            Log.d(TAG, "waitForManualDial: 等待被取消 (CancellationException)")
        } finally {
            // 确保无论正常结束还是取消，都清除等待标志
            isWaitingForNextDial = false
            manualDialWaitJob = null
            Log.d(TAG, "waitForManualDial: finally 块执行，清理完成")
        }
        
        Log.d(TAG, "waitForManualDial: 继续拨打下一个")
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
     * 仅在未自动标记时调用，根据最后一次通话状态设置callResult
     */
    private suspend fun markCustomerAsCalled(customer: Customer) {
        val taskId = _taskId.value
        if (taskId != null) {
            try {
                // 根据最后一次通话状态确定callResult
                val lastState = rootCallStateDetector.getCurrentState()
                val callResult = when (lastState) {
                    RootCallState.BUSY -> "对方忙线"
                    RootCallState.REJECTED -> "对方拒接"
                    RootCallState.NO_ANSWER -> "无人接听"
                    RootCallState.POWER_OFF -> "对方关机"
                    RootCallState.VOICEMAIL -> "语音信箱"
                    RootCallState.CONNECTED -> "通话完成"
                    RootCallState.ACTIVE -> "通话完成"
                    else -> "响铃未接"
                }
                val request = UpdateTaskCustomerStatusRequest(
                    status = "called",
                    callResult = callResult
                )
                val result = taskRepository.updateTaskCustomerStatus(taskId, customer.id, request)
                result.fold(
                    onSuccess = {
                        // 标记成功
                        Log.d(TAG, "[markCustomerAsCalled] 客户状态已更新: ${customer.name} -> $callResult")
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
        val MAX_CALL_DURATION = 600000 // 最大通话持续时间 10 分钟（防止无限等待）

        // 更新初始状态为拨号中
        _currentCallState.value = com.callcenter.app.util.root.RootCallState.DIALING
        _currentCallNumber.value = _currentCustomer.value?.phone
        
        // 添加拨号中历史记录
        FloatingCustomerService.addCallStateHistory("拨号中", _currentCustomer.value?.phone, _currentCustomer.value?.name)

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
                            // 更新状态为已接通
                            _currentCallState.value = com.callcenter.app.util.root.RootCallState.ACTIVE
                            FloatingCustomerService.addCallStateHistory("通话已接通", _currentCustomer.value?.phone, _currentCustomer.value?.name)
                            
                            // 自动打开扬声器（免提）
                            android.util.Log.d(TAG, "尝试打开扬声器...")
                            val speakerEnabled = callHelper.enableSpeakerphone()
                            if (speakerEnabled) {
                                android.util.Log.d(TAG, "扬声器已自动打开")
                                FloatingCustomerService.addCallStateHistory("扬声器已开启", _currentCustomer.value?.phone, _currentCustomer.value?.name)
                            } else {
                                android.util.Log.w(TAG, "扬声器打开失败")
                            }
                        }
                    }
                    TelephonyManager.CALL_STATE_IDLE -> {
                        if (callConnected) {
                            // 之前已接通，现在挂断了
                            // 确保通话持续时间超过最小值（避免误判运营商语音播报为有效通话）
                            val callDuration = System.currentTimeMillis() - offHookStartTime
                            if (callDuration >= MIN_CALL_DURATION) {
                                Log.d(TAG, "通话已挂断，持续时间: ${callDuration}ms")
                                // 更新状态为已挂断
                                _currentCallState.value = com.callcenter.app.util.root.RootCallState.IDLE
                                FloatingCustomerService.addCallStateHistory("已挂断", _currentCustomer.value?.phone, _currentCustomer.value?.name, callDuration)
                                return true
                            } else {
                                // 通话时间太短，可能是运营商语音播报号码有误
                                // 等待一下确认状态稳定
                                Log.d(TAG, "通话时间太短(${callDuration}ms)，等待确认...")
                                delay(1000)
                                if (telephonyManager.callState == TelephonyManager.CALL_STATE_IDLE) {
                                    Log.d(TAG, "确认通话已结束")
                                    _currentCallState.value = com.callcenter.app.util.root.RootCallState.IDLE
                                    FloatingCustomerService.addCallStateHistory("已挂断", _currentCustomer.value?.phone, _currentCustomer.value?.name, callDuration)
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
                                _currentCallState.value = com.callcenter.app.util.root.RootCallState.IDLE
                                FloatingCustomerService.addCallStateHistory("未接通", _currentCustomer.value?.phone, _currentCustomer.value?.name)
                                return true
                            }
                        }
                    }
                    TelephonyManager.CALL_STATE_RINGING -> {
                        // 响铃中，继续等待
                        if (elapsedTime % 5000 == 0) { // 每5秒打印一次日志
                            Log.d(TAG, "电话响铃中...")
                        }
                        // 更新状态为响铃中（只在第一次检测到响铃时更新）
                        if (_currentCallState.value != com.callcenter.app.util.root.RootCallState.ALERTING) {
                            _currentCallState.value = com.callcenter.app.util.root.RootCallState.ALERTING
                            FloatingCustomerService.addCallStateHistory("对方响铃中", _currentCustomer.value?.phone, _currentCustomer.value?.name)
                        }
                    }
                }

                // 检查是否超过最大通话时间
                if (callConnected && (System.currentTimeMillis() - offHookStartTime) > MAX_CALL_DURATION) {
                    Log.d(TAG, "通话超过最大时间限制，强制结束等待")
                    _currentCallState.value = com.callcenter.app.util.root.RootCallState.IDLE
                    FloatingCustomerService.addCallStateHistory("已挂断", _currentCustomer.value?.phone, _currentCustomer.value?.name, MAX_CALL_DURATION.toLong())
                    return true
                }

                delay(500)
                elapsedTime += 500
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: ${e.message}")
            // 没有权限读取通话状态，使用超时机制作为后备
            // 但在手动模式下，应该等待用户确认而不是自动超时
            if (!_isAutoDialMode.value) {
                Log.w(TAG, "无通话状态权限且为手动模式，等待用户手动点击下一个")
                // 在手动模式下，无限等待直到用户点击
                while (_isRunning.value && !_isAutoDialMode.value) {
                    delay(1000)
                }
                _currentCallState.value = com.callcenter.app.util.root.RootCallState.IDLE
                return _isRunning.value
            } else {
                Log.w(TAG, "无通话状态权限，使用${timeoutSeconds}秒超时")
                delay(timeoutSeconds * 1000L)
                _currentCallState.value = com.callcenter.app.util.root.RootCallState.IDLE
                return _isRunning.value
            }
        }

        // 服务被停止
        Log.d(TAG, "服务被停止，结束等待")
        _currentCallState.value = com.callcenter.app.util.root.RootCallState.IDLE
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
        // 释放 RootCallStateDetector 资源
        if (::rootCallStateDetector.isInitialized) {
            rootCallStateDetector.release()
        }
    }
}
