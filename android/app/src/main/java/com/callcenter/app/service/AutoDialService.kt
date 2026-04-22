package com.callcenter.app.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.IBinder
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.callcenter.app.CallCenterApp
import com.callcenter.app.MainActivity
import com.callcenter.app.R
import com.callcenter.app.data.model.Customer
import com.callcenter.app.data.model.UpdateTaskCustomerStatusRequest
import com.callcenter.app.data.local.preferences.AutoDialProgressManager
import com.callcenter.app.data.local.preferences.AutoDialProgress
import com.callcenter.app.data.local.preferences.TokenManager
import com.callcenter.app.data.repository.CallRecordRepository
import com.callcenter.app.data.repository.TaskRepository
import com.callcenter.app.ui.activity.PermissionRequestActivity
import com.callcenter.app.ui.viewmodel.AutoDialScopeType
import com.callcenter.app.util.AppLifecycleManager
import com.callcenter.app.util.CallHelper
import com.callcenter.app.util.DebugLogger
import com.callcenter.app.util.FloatingWindowManager
import com.callcenter.app.util.UserNotifier
import com.callcenter.app.util.call.AudioEnergyAnalyzer
import com.callcenter.app.util.call.AudioEnergyPattern
import com.callcenter.app.util.call.CallContext
import com.callcenter.app.util.call.CallResultClassifier
import com.callcenter.app.util.call.CallResultType
import com.callcenter.app.util.call.KeywordCallType
import com.callcenter.app.util.call.KeywordDetector
import com.callcenter.app.util.root.RootCallState
import com.callcenter.app.util.root.RootCallStateDetector
import com.callcenter.app.util.root.RootPhoneStateListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
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
        const val EXTRA_LOAD_FROM_TASK = "load_from_task"  // 是否从任务加载客户（避免 Intent 大数据）

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

        // 当前通话状态流（用于悬浮窗监听）
        private val _currentCallState = MutableStateFlow<com.callcenter.app.util.root.RootCallState?>(null)
        val currentCallState: StateFlow<com.callcenter.app.util.root.RootCallState?> = _currentCallState

        // 当前通话号码流
        private val _currentCallNumber = MutableStateFlow<String?>(null)
        val currentCallNumber: StateFlow<String?> = _currentCallNumber

        private const val NOTIFICATION_ID = 1001
        private const val TAG = "AutoDialService"

        // 通话状态检测常量
        private const val MIN_CALL_DURATION = 3000L      // 最小通话持续时间 3 秒
        private const val MAX_CALL_DURATION = 600000L    // 最大通话持续时间 10 分钟
        private const val MIN_CONFIRM_IDLE_TIME = 3000L  // 确认通话结束的最小等待时间

        // 通话结果判断阈值已迁移到 CallResultClassifier
        // CONNECTED_THRESHOLD = 35秒（通话时长超过此值判定为已接听）
        // 详细阈值配置见 CallResultClassifier.kt
    }

    @Inject
    lateinit var callHelper: CallHelper

    @Inject
    lateinit var progressManager: AutoDialProgressManager

    @Inject
    lateinit var taskRepository: TaskRepository

    @Inject
    lateinit var callRecordRepository: CallRecordRepository

    @Inject
    lateinit var tokenManager: TokenManager

    @Inject
    lateinit var callResultClassifier: CallResultClassifier

    private var job: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var intervalSeconds: Int = 10
    private var timeoutSeconds: Int = 30
    private var retryCount: Int = 0
    private var dialsPerCustomer: Int = 1  // 每个客户连续拨打次数，默认1次
    private var currentDialRound: Int = 1  // 当前客户的第几轮拨打
    private var taskTitle: String? = null

    private var isPaused: Boolean = false
    private var currentCallRecordId: Int? = null
    private var customerQueue: MutableList<Customer> = mutableListOf()
    private var currentIndex: Int = 0
    private var scopeType: AutoDialScopeType = AutoDialScopeType.ALL_PENDING

    // 自动拨号挂起状态（当不在前台或通话中时挂起）
    private var isAutoDialSuspended: Boolean = false
    private var suspendCheckJob: Job? = null

    // 当前通话状态
    private var currentCallState: Int = TelephonyManager.CALL_STATE_IDLE

    // Root 通话状态检测器
    private lateinit var rootCallStateDetector: RootCallStateDetector

    // TelephonyManager
    private val telephonyManager by lazy {
        getSystemService(android.content.Context.TELEPHONY_SERVICE) as TelephonyManager
    }

    // 是否已自动标记当前通话状态
    private var hasAutoMarkedCurrentCall: Boolean = false

    // 当前通话是否明确接通过，用于避免挂断后状态回落到 IDLE 时被误判为未接
    private var lastCallWasConnected: Boolean = false
    private var lastResolvedCallResult: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        DebugLogger.logSeparator("AutoDialService onCreate")
        DebugLogger.log("[Service] 服务创建开始")
        DebugLogger.log("[Service] 当前时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())}")
        
        // 检查权限
        val hasCallPhone = checkSelfPermission(android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasReadPhoneState = checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasReadCallLog = checkSelfPermission(android.Manifest.permission.READ_CALL_LOG) == android.content.pm.PackageManager.PERMISSION_GRANTED
        DebugLogger.log("[Service] 权限检查: CALL_PHONE=$hasCallPhone, READ_PHONE_STATE=$hasReadPhoneState, READ_CALL_LOG=$hasReadCallLog")

        // 初始化 Root 通话状态检测器
        rootCallStateDetector = RootCallStateDetector()
        setupCallStateListener()

        // 启动前台状态监控
        startForegroundMonitoring()
        DebugLogger.log("[Service] 服务创建完成")
    }

    /**
     * 设置通话状态监听器
     * 根据 Root 设备检测到的通话状态自动标记通话结果
     * 非 Root 设备会跳过，使用 TelephonyManager 的基础状态
     */
    private fun setupCallStateListener() {
        DebugLogger.log("[CallStateListener] 开始设置通话状态监听器")
        
        // 检查是否有 Root 权限
        val hasRoot = com.callcenter.app.util.root.RootUtils.hasRootPermission()
        DebugLogger.log("[CallStateListener] Root权限检查: $hasRoot")
        
        if (!hasRoot) {
            Log.d(TAG, "无 Root 权限，跳过精确通话状态检测，使用 TelephonyManager 基础状态")
            DebugLogger.log("[CallStateListener] 无Root权限，将使用 TelephonyManager 检测")
            return
        }

        // 启动 Root 通话状态检测
        rootCallStateDetector.addListener(object : RootPhoneStateListener {
            override fun onActive(number: String?, setupTime: Long) {
                Log.d(TAG, "[CallStateListener] 通话已接通: $number, 接通耗时: ${setupTime}ms")
                DebugLogger.log("[CallStateListener] Root检测到通话已接通: number=$number, setupTime=${setupTime}ms")
                lastCallWasConnected = true
                lastResolvedCallResult = "已接听"
                // 通话接通时自动标记为已接听
                serviceScope.launch {
                    if (!hasAutoMarkedCurrentCall) {
                        hasAutoMarkedCurrentCall = true
                        DebugLogger.log("[CallStateListener] 自动标记为已接听")
                        autoMarkCallStatus("connected", "已接听")
                    }
                }
            }

            override fun onIdle(duration: Long) {
                Log.d(TAG, "[CallStateListener] 通话结束, 时长: ${duration}ms")
                DebugLogger.log("[CallStateListener] Root检测到通话结束: duration=${duration}ms")
                // 通话结束时，根据时长判断结果
                serviceScope.launch {
                    if (!hasAutoMarkedCurrentCall) {
                        hasAutoMarkedCurrentCall = true
                        // 如果通话时长大于0，说明发生过真实接通
                        if (duration > 0) {
                            lastCallWasConnected = true
                            lastResolvedCallResult = "已接听"
                            DebugLogger.log("[CallStateListener] 通话时长>0，标记为已接听")
                            autoMarkCallStatus("connected", "已接听")
                        } else {
                            // 根据之前的通话状态判断
                            val lastState = rootCallStateDetector.getCurrentState()
                            DebugLogger.log("[CallStateListener] 通话时长=0，根据Root状态判断: $lastState")
                            when (lastState) {
                                RootCallState.BUSY -> {
                                    lastResolvedCallResult = "对方忙线"
                                    autoMarkCallStatus("busy", "对方忙线")
                                }
                                RootCallState.REJECTED -> {
                                    lastResolvedCallResult = "对方拒接"
                                    autoMarkCallStatus("rejected", "对方拒接")
                                }
                                RootCallState.NO_ANSWER -> {
                                    lastResolvedCallResult = "无人接听"
                                    autoMarkCallStatus("no_answer", "无人接听")
                                }
                                RootCallState.POWER_OFF -> {
                                    lastResolvedCallResult = "对方关机"
                                    autoMarkCallStatus("power_off", "对方关机")
                                }
                                RootCallState.VOICEMAIL -> {
                                    lastResolvedCallResult = "语音信箱"
                                    autoMarkCallStatus("voicemail", "语音信箱")
                                }
                                else -> {
                                    lastResolvedCallResult = "响铃未接"
                                    autoMarkCallStatus("unanswered", "响铃未接")
                                }
                            }
                        }
                    }
                }
            }

            override fun onStateChanged(oldState: RootCallState, newState: RootCallState) {
                Log.d(TAG, "[CallStateListener] 状态变更: $oldState -> $newState")
                DebugLogger.log("[CallStateListener] Root状态变更: $oldState -> $newState")
                
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
        Log.d(TAG, "========== [AutoMark] 自动标记通话状态 ==========")
        Log.d(TAG, "[AutoMark] status=$status, displayName=$displayName")
        Log.d(TAG, "[AutoMark] 当前客户: ${_currentCustomer.value?.name}, phone=${_currentCustomer.value?.phone}")
        Log.d(TAG, "[AutoMark] 当前任务ID: ${_taskId.value}")
        
        DebugLogger.log("[AutoMark] ========== 自动标记通话状态 ==========")
        DebugLogger.log("[AutoMark] status=$status, displayName=$displayName")
        DebugLogger.log("[AutoMark] 客户: ${_currentCustomer.value?.name}, phone=${_currentCustomer.value?.phone}")
        DebugLogger.log("[AutoMark] taskId=${_taskId.value}")
        
        lastResolvedCallResult = displayName
        if (status == "connected") {
            lastCallWasConnected = true
        }
        
        val customer = _currentCustomer.value
        if (customer == null) {
            Log.w(TAG, "[AutoMark] 当前客户为空，无法标记")
            DebugLogger.log("[AutoMark] 错误: 当前客户为空，无法标记")
            return
        }
        
        val taskId = _taskId.value
        if (taskId == null) {
            Log.w(TAG, "[AutoMark] 当前任务ID为空，无法标记")
            DebugLogger.log("[AutoMark] 错误: 当前任务ID为空，无法标记")
            return
        }
        
        try {
            // 调用标记方法
            DebugLogger.log("[AutoMark] 调用 markCustomerAsCalledWithStatus...")
            markCustomerAsCalledWithStatus(customer, status, displayName)
            
            // 添加到通话状态历史记录
            FloatingCustomerService.addCallStateHistory(
                state = displayName,
                number = customer.phone,
                customerName = customer.name,
                duration = rootCallStateDetector.getCurrentCallDuration()
            )
            
            Log.d(TAG, "[AutoMark] 自动标记完成: $status")
            DebugLogger.log("[AutoMark] 自动标记完成: $status")
            Log.d(TAG, "========== [AutoMark] 标记完成 ==========")
        } catch (e: Exception) {
            Log.e(TAG, "[AutoMark] 自动标记失败: ${e.message}")
            DebugLogger.log("[AutoMark] 异常: 自动标记失败: ${e.message}")
            UserNotifier.showError("自动标记通话状态失败: ${e.message}")
        }
    }

    /**
     * 根据状态标记客户
     */
    private suspend fun markCustomerAsCalledWithStatus(customer: Customer, status: String, callResult: String) {
        val taskId = _taskId.value
        Log.d(TAG, "[AutoMark] markCustomerAsCalledWithStatus: taskId=$taskId, customerId=${customer.id}, status=$status, callResult=$callResult")
        DebugLogger.log("[markCustomerAsCalledWithStatus] taskId=$taskId, customerId=${customer.id}, status=$status, callResult=$callResult")
        
        if (taskId == null) {
            Log.e(TAG, "[AutoMark] taskId为空，无法更新客户状态")
            DebugLogger.log("[markCustomerAsCalledWithStatus] 错误: taskId为空")
            return
        }
        
        try {
            // 更新客户状态
            DebugLogger.log("[markCustomerAsCalledWithStatus] 开始调用 API 更新客户状态...")
            val result = taskRepository.updateTaskCustomerStatus(
                taskId = taskId,
                customerId = customer.id,
                request = UpdateTaskCustomerStatusRequest(
                    status = "called",
                    callResult = callResult,
                    callId = currentCallRecordId
                )
            )
            
            result.fold(
                onSuccess = {
                    Log.d(TAG, "[AutoMark] 客户状态已更新: ${customer.name} -> $callResult")
                    DebugLogger.log("[markCustomerAsCalledWithStatus] 成功: 客户状态已更新 -> $callResult")
                },
                onFailure = { e ->
                    Log.e(TAG, "[AutoMark] 更新客户状态失败: ${e.message}")
                    DebugLogger.log("[markCustomerAsCalledWithStatus] 失败: ${e.message}")
                    UserNotifier.showError("更新客户状态失败: ${e.message}")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "[AutoMark] 更新客户状态异常: ${e.message}")
            DebugLogger.log("[markCustomerAsCalledWithStatus] 异常: ${e.message}")
            UserNotifier.showError("更新客户状态失败: ${e.message}")
        }
    }

    /**
     * 启动前台状态监控
     * 监控应用是否在前台以及通话状态
     * 
     * 挂起条件：不在前台 且 通话空闲（即准备拨下一个电话时）
     * 不挂起的情况：正在通话中（即使不在前台也继续）
     */
    private fun startForegroundMonitoring() {
        suspendCheckJob = serviceScope.launch {
            while (_isRunning.value) {
                // 检查应用是否在前台
                val isInForeground = AppLifecycleManager.isAppInForeground()
                
                // 检查当前通话状态
                currentCallState = getCallState()
                val isCallIdle = currentCallState == TelephonyManager.CALL_STATE_IDLE
                
                // 判断是否应该挂起自动拨号
                // 条件：不在前台 且 通话空闲（准备拨下一个电话）
                // 注意：正在通话中时不应挂起，即使不在前台
                val shouldSuspend = !isInForeground && isCallIdle
                
                if (shouldSuspend && !isAutoDialSuspended) {
                    // 需要挂起
                    isAutoDialSuspended = true
                    Log.w(TAG, "自动拨号已挂起: 前台=$isInForeground, 通话空闲=$isCallIdle")
                    updateFloatingWindowStatus("已挂起 - 请回到应用")
                } else if (!shouldSuspend && isAutoDialSuspended) {
                    // 解除挂起
                    isAutoDialSuspended = false
                    Log.d(TAG, "自动拨号已恢复: 前台=$isInForeground, 通话空闲=$isCallIdle")
                    updateFloatingWindowStatus("自动拨号中")
                }
                
                delay(1000) // 每秒检查一次
            }
        }
    }

    /**
     * 自动开启免提
     * 优先使用 InCallService，其次使用无障碍服务，最后使用 AudioManager
     */
    private suspend fun enableSpeakerphoneWithRetry() {
        Log.d(TAG, "开始自动开启免提...")
        DebugLogger.log("[AutoDialService] 开始自动开启免提...")
        
        // 检查 InCallService 是否激活
        if (AutoSpeakerInCallService.isServiceActive) {
            Log.d(TAG, "InCallService 已激活，扬声器将由 InCallService 自动控制")
            DebugLogger.log("[AutoDialService] ✓ InCallService 已激活，将由 InCallService 控制扬声器")
            FloatingCustomerService.addCallStateHistory(
                "InCallService 控制免提",
                _currentCustomer.value?.phone,
                _currentCustomer.value?.name
            )
            return
        }
        
        // 检查无障碍服务是否激活
        if (AutoSpeakerAccessibilityService.isServiceEnabled) {
            Log.d(TAG, "无障碍服务已激活，扬声器将由无障碍服务自动控制")
            DebugLogger.log("[AutoDialService] ✓ 无障碍服务已激活，将由无障碍服务控制扬声器")
            FloatingCustomerService.addCallStateHistory(
                "无障碍服务控制免提",
                _currentCustomer.value?.phone,
                _currentCustomer.value?.name
            )
            return
        }
        
        Log.w(TAG, "InCallService 和无障碍服务都未激活，使用 AudioManager 方式（可能不稳定）")
        DebugLogger.log("[AutoDialService] ⚠️ InCallService 和无障碍服务都未激活！使用 AudioManager 方式")
        
        val success = callHelper.enableSpeakerphoneAsync()
        
        if (success) {
            Log.d(TAG, "免提已自动开启")
            DebugLogger.log("[AutoDialService] ✓ AudioManager 方式开启扬声器成功")
            FloatingCustomerService.addCallStateHistory(
                "免提已开启",
                _currentCustomer.value?.phone,
                _currentCustomer.value?.name
            )
        } else {
            Log.e(TAG, "免提开启失败")
            DebugLogger.log("[AutoDialService] ✗ AudioManager 方式开启扬声器失败")
            FloatingCustomerService.addCallStateHistory(
                "免提开启失败",
                _currentCustomer.value?.phone,
                _currentCustomer.value?.name
            )
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
        val callState = getCallState()
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
            val callState = getCallState()
            
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
            UserNotifier.showError("挂断电话失败: ${e.message}")
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

                // 获取客户列表 - 支持两种模式
                val loadFromTask = intent.getBooleanExtra(EXTRA_LOAD_FROM_TASK, false)
                
                if (loadFromTask && _taskId.value != null) {
                    // 模式1：从任务分页加载客户（避免 Intent 大数据限制）
                    Log.d(TAG, "从任务分页加载客户，taskId=${_taskId.value}")
                    serviceScope.launch {
                        loadCustomersFromTask(_taskId.value!!, intent)
                    }
                } else {
                    // 模式2：从 Intent 获取客户列表（兼容旧逻辑，适用于少量客户）
                    @Suppress("UNCHECKED_CAST")
                    val customers = intent.getSerializableExtra(EXTRA_CUSTOMERS) as? ArrayList<Customer>
                    if (customers != null) {
                        customerQueue.clear()
                        customerQueue.addAll(customers)
                        _totalCount.value = customers.size
                        
                        // 获取起始索引
                        currentIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)
                        _dialedCount.value = intent.getIntExtra(EXTRA_DIALED_COUNT, 0)
                        
                        // 保存初始进度并启动
                        serviceScope.launch {
                            saveProgress()
                            startAutoDial()
                        }
                    } else {
                        Log.e(TAG, "客户列表为空且未指定从任务加载")
                        stopSelf()
                    }
                }
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
                        },
                        callId = currentCallRecordId
                    )
                    Log.d(TAG, "发送通话状态更新请求: customerId=${customer.id}, callResult=${request.callResult}")
                    val result = taskRepository.updateTaskCustomerStatus(taskId, customer.id, request)
                    result.fold(
                        onSuccess = {
                            Log.d(TAG, "通话状态标记成功")
                        },
                        onFailure = { e ->
                            Log.e(TAG, "通话状态标记失败: ${e.message}")
                            UserNotifier.showError("通话状态标记失败: ${e.message}")
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "标记客户状态失败: ${e.message}", e)
                    UserNotifier.showError("标记客户状态失败: ${e.message}")
                }
            }
        } else {
            Log.w(TAG, "无法标记通话状态: customer=$customer, taskId=$taskId")
        }
    }

    /**
     * 从任务分页加载客户列表
     * 解决 Intent 数据大小限制问题（1000+ 客户会超过 1MB 限制）
     */
    private suspend fun loadCustomersFromTask(taskId: Int, intent: Intent) {
        try {
            // 更新状态为加载中
            updateFloatingWindowStatus("正在加载客户列表...")
            
            // 分页加载所有待拨打客户
            val result = taskRepository.getAllPendingTaskCustomers(taskId)
            result.fold(
                onSuccess = { taskCustomers ->
                    if (taskCustomers.isEmpty()) {
                        Log.w(TAG, "任务 $taskId 没有待拨打的客户")
                        UserNotifier.showError("没有待拨打的客户")
                        stopSelf()
                        return
                    }

                    // 转换为 Customer 对象
                    customerQueue.clear()
                    taskCustomers.forEach { tc ->
                        customerQueue.add(
                            Customer(
                                id = tc.id ?: tc.taskCustomerId,
                                name = tc.name,
                                phone = tc.phone,
                                email = tc.email,
                                company = tc.company,
                                status = tc.callStatus,
                                sourceType = "task",
                                taskId = taskId,
                                taskTitle = taskTitle
                            )
                        )
                    }
                    
                    _totalCount.value = customerQueue.size
                    currentIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)
                    _dialedCount.value = intent.getIntExtra(EXTRA_DIALED_COUNT, 0)
                    
                    Log.d(TAG, "从任务加载客户完成: taskId=$taskId, 客户数=${customerQueue.size}")
                    
                    // 保存初始进度
                    saveProgress()
                    startAutoDial()
                },
                onFailure = { e ->
                    Log.e(TAG, "加载任务客户失败: ${e.message}", e)
                    UserNotifier.showError("加载客户列表失败: ${e.message}")
                    stopSelf()
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "加载任务客户异常: ${e.message}", e)
            UserNotifier.showError("加载客户列表异常: ${e.message}")
            stopSelf()
        }
    }

    private fun startAutoDial() {
        if (_isRunning.value) return

        // 检查并请求录音权限（用于语音信箱识别）
        checkAndRequestRecordPermission()

        _isRunning.value = true
        startForeground()

        // 悬浮窗的显示/隐藏由 AutoFloatingWindow 组件根据应用前后台状态控制
        // 这里不再直接调用 showFloatingWindow()

        job = serviceScope.launch {
            processQueue()
        }
    }

    /**
     * 检查并请求录音权限
     * 如果没有录音权限，启动透明的权限请求 Activity
     */
    private fun checkAndRequestRecordPermission() {
        val hasRecordPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        DebugLogger.log("[AutoDial] 录音权限检查: $hasRecordPermission")

        if (!hasRecordPermission) {
            DebugLogger.log("[AutoDial] 无录音权限，启动权限请求 Activity")
            val intent = PermissionRequestActivity.createIntent(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                requestCode = 1001,
                showRationale = true
            )
            startActivity(intent)
        }
    }

    private fun stopAutoDial() {
        Log.d(TAG, "stopAutoDial: 停止自动拨号")
        DebugLogger.log("[stopAutoDial] ========== 停止自动拨号 ==========")
        DebugLogger.log("[stopAutoDial] 当前客户: ${_currentCustomer.value?.name}, phone=${_currentCustomer.value?.phone}")
        DebugLogger.log("[stopAutoDial] taskId=${_taskId.value}, hasAutoMarkedCurrentCall=$hasAutoMarkedCurrentCall")
        DebugLogger.log("[stopAutoDial] lastCallWasConnected=$lastCallWasConnected, lastResolvedCallResult=$lastResolvedCallResult")

        // 检查是否需要执行最终分类
        val shouldClassify = _isRunning.value && _currentCustomer.value != null && !hasAutoMarkedCurrentCall
        
        if (shouldClassify) {
            // 在停止前，先对当前通话进行分类标记（同步执行，确保完成后再清理状态）
            DebugLogger.log("[stopAutoDial] 开始执行 performFinalCallClassificationSync...")
            try {
                runBlocking {
                    performFinalCallClassificationSync()
                }
            } catch (e: Exception) {
                Log.e(TAG, "[stopAutoDial] 分类失败: ${e.message}")
                DebugLogger.log("[stopAutoDial] 分类异常: ${e.message}")
            }
            DebugLogger.log("[stopAutoDial] performFinalCallClassificationSync 执行完成")
        }

        // 最后再设置 isRunning = false，这样 UI 刷新时 API 已经完成了
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
        Log.d(TAG, "stopAutoDial: 已隐藏悬浮窗")
    }

    /**
     * 执行最终的通话分类（同步版本）
     * 用于用户手动停止时，对当前通话进行分类标记
     * 注意：此方法必须在 runBlocking 中调用，确保 API 调用完成后再继续
     */
    private suspend fun performFinalCallClassificationSync() {
        Log.d(TAG, "========== [FinalClassifySync] 开始最终通话分类 ==========")
        DebugLogger.log("[FinalClassifySync] ========== 开始最终通话分类 ==========")

        val customer = _currentCustomer.value
        if (customer == null) {
            Log.d(TAG, "[FinalClassifySync] 当前没有正在拨打的客户，跳过分类")
            DebugLogger.log("[FinalClassifySync] 当前没有正在拨打的客户，跳过分类")
            return
        }

        // 如果已经自动标记过，跳过
        if (hasAutoMarkedCurrentCall) {
            Log.d(TAG, "[FinalClassifySync] 已自动标记过，跳过分类")
            DebugLogger.log("[FinalClassifySync] 已自动标记过，跳过分类")
            return
        }

        val taskId = _taskId.value
        if (taskId == null) {
            Log.w(TAG, "[FinalClassifySync] 任务ID为空，无法标记")
            DebugLogger.log("[FinalClassifySync] 任务ID为空，无法标记")
            return
        }

        // 获取当前通话状态
        val currentCallStateVal = try {
            telephonyManager.callState
        } catch (e: Exception) {
            TelephonyManager.CALL_STATE_IDLE
        }

        Log.d(TAG, "[FinalClassifySync] 当前通话状态: $currentCallStateVal, lastCallWasConnected=$lastCallWasConnected, lastResolvedCallResult=$lastResolvedCallResult")
        DebugLogger.log("[FinalClassifySync] customer=${customer.name}, phone=${customer.phone}, taskId=$taskId")
        DebugLogger.log("[FinalClassifySync] callState=$currentCallStateVal, lastCallWasConnected=$lastCallWasConnected, lastResolvedCallResult=$lastResolvedCallResult")

        // 检查是否有 Root 检测到的状态
        val rootState = if (::rootCallStateDetector.isInitialized) {
            rootCallStateDetector.getCurrentState()
        } else null
        DebugLogger.log("[FinalClassifySync] rootState=$rootState")

        // 确定通话结果
        val result = when {
            // 如果已经有解析结果，直接使用
            lastResolvedCallResult != null -> {
                val statusKey = when (lastResolvedCallResult) {
                    "已接听" -> "connected"
                    "语音信箱" -> "voicemail"
                    "对方忙线" -> "busy"
                    "对方拒接" -> "rejected"
                    "对方关机" -> "power_off"
                    "无人接听" -> "no_answer"
                    else -> "unanswered"
                }
                Pair(statusKey, lastResolvedCallResult!!)
            }
            // 如果当前还在通话中（用户停止时电话还没挂断）
            currentCallStateVal == TelephonyManager.CALL_STATE_OFFHOOK -> {
                lastCallWasConnected = true
                lastResolvedCallResult = "已接听"
                Pair("connected", "已接听")
            }
            // 如果之前检测到接通过
            lastCallWasConnected -> {
                Pair("connected", "已接听")
            }
            // 根据 Root 检测状态判断
            rootState != null -> when (rootState) {
                RootCallState.BUSY -> Pair("busy", "对方忙线")
                RootCallState.REJECTED -> Pair("rejected", "对方拒接")
                RootCallState.POWER_OFF -> Pair("power_off", "对方关机")
                RootCallState.VOICEMAIL -> Pair("voicemail", "语音信箱")
                RootCallState.NO_ANSWER -> Pair("no_answer", "无人接听")
                RootCallState.ACTIVE, RootCallState.CONNECTED -> Pair("connected", "已接听")
                else -> Pair("unanswered", "响铃未接")
            }
            // 默认：标记为响铃未接
            else -> Pair("unanswered", "响铃未接")
        }

        val (statusKey, displayName) = result
        Log.d(TAG, "[FinalClassifySync] 最终结果: statusKey=$statusKey, displayName=$displayName")
        DebugLogger.log("[FinalClassifySync] 最终结果: statusKey=$statusKey, displayName=$displayName")

        // 同步调用 API 更新客户状态
        try {
            val request = UpdateTaskCustomerStatusRequest(
                status = "called",
                callResult = displayName,
                callId = currentCallRecordId
            )
            
            DebugLogger.log("[FinalClassifySync] 调用 API 更新客户状态: taskId=$taskId, customerId=${customer.id}")
            val apiResult = taskRepository.updateTaskCustomerStatus(taskId, customer.id, request)
            
            apiResult.fold(
                onSuccess = {
                    Log.d(TAG, "[FinalClassifySync] 客户状态已更新: ${customer.name} -> $displayName")
                    DebugLogger.log("[FinalClassifySync] ✓ API 调用成功: $displayName")
                    
                    // 添加到通话状态历史记录
                    FloatingCustomerService.addCallStateHistory(
                        state = displayName,
                        number = customer.phone,
                        customerName = customer.name,
                        duration = if (::rootCallStateDetector.isInitialized) rootCallStateDetector.getCurrentCallDuration() else 0
                    )
                },
                onFailure = { e ->
                    Log.e(TAG, "[FinalClassifySync] 更新客户状态失败: ${e.message}")
                    DebugLogger.log("[FinalClassifySync] ✗ API 调用失败: ${e.message}")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "[FinalClassifySync] API 调用异常: ${e.message}")
            DebugLogger.log("[FinalClassifySync] ✗ API 调用异常: ${e.message}")
        }

        // 标记已处理
        hasAutoMarkedCurrentCall = true
        DebugLogger.log("[FinalClassifySync] ========== 分类完成 ==========")
    }

    /**
     * 执行最终的通话分类
     * 用于用户手动停止时，对当前通话进行分类标记
     */
    private suspend fun performFinalCallClassification() {
        Log.d(TAG, "========== [FinalClassify] 开始最终通话分类 ==========")
        DebugLogger.log("[FinalClassify] ========== 开始最终通话分类 ==========")

        val customer = _currentCustomer.value
        if (customer == null) {
            Log.d(TAG, "[FinalClassify] 当前没有正在拨打的客户，跳过分类")
            DebugLogger.log("[FinalClassify] 当前没有正在拨打的客户，跳过分类")
            return
        }

        // 如果已经自动标记过，跳过
        if (hasAutoMarkedCurrentCall) {
            Log.d(TAG, "[FinalClassify] 已自动标记过，跳过分类")
            DebugLogger.log("[FinalClassify] 已自动标记过，跳过分类")
            return
        }

        val taskId = _taskId.value
        if (taskId == null) {
            Log.w(TAG, "[FinalClassify] 任务ID为空，无法标记")
            DebugLogger.log("[FinalClassify] 任务ID为空，无法标记")
            return
        }

        // 获取当前通话状态
        val currentCallStateVal = try {
            telephonyManager.callState
        } catch (e: Exception) {
            TelephonyManager.CALL_STATE_IDLE
        }

        Log.d(TAG, "[FinalClassify] 当前通话状态: $currentCallStateVal, lastCallWasConnected=$lastCallWasConnected, lastResolvedCallResult=$lastResolvedCallResult")
        DebugLogger.log("[FinalClassify] customer=${customer.name}, phone=${customer.phone}, taskId=$taskId")
        DebugLogger.log("[FinalClassify] callState=$currentCallStateVal, lastCallWasConnected=$lastCallWasConnected, lastResolvedCallResult=$lastResolvedCallResult")

        // 检查是否有 Root 检测到的状态
        val rootState = if (::rootCallStateDetector.isInitialized) {
            rootCallStateDetector.getCurrentState()
        } else null
        DebugLogger.log("[FinalClassify] rootState=$rootState")

        // 如果已经有 Root 检测结果，直接使用
        if (lastResolvedCallResult != null) {
            Log.d(TAG, "[FinalClassify] 使用已解析的结果: $lastResolvedCallResult")
            DebugLogger.log("[FinalClassify] 使用已解析的结果: $lastResolvedCallResult")
            val statusKey = when (lastResolvedCallResult) {
                "已接听" -> "connected"
                "语音信箱" -> "voicemail"
                "对方忙线" -> "busy"
                "对方拒接" -> "rejected"
                "对方关机" -> "power_off"
                "无人接听" -> "no_answer"
                else -> "unanswered"
            }
            DebugLogger.log("[FinalClassify] 调用 autoMarkCallStatus: statusKey=$statusKey")
            autoMarkCallStatus(statusKey, lastResolvedCallResult!!)
            return
        }

        // 如果当前还在通话中（用户停止时电话还没挂断）
        if (currentCallStateVal == TelephonyManager.CALL_STATE_OFFHOOK) {
            Log.d(TAG, "[FinalClassify] 当前仍在通话中，标记为已接听")
            DebugLogger.log("[FinalClassify] 当前仍在通话中(OFFHOOK)，标记为已接听")
            lastCallWasConnected = true
            lastResolvedCallResult = "已接听"
            autoMarkCallStatus("connected", "已接听")
            return
        }

        // 如果之前检测到接通过
        if (lastCallWasConnected) {
            Log.d(TAG, "[FinalClassify] 之前检测到已接通")
            DebugLogger.log("[FinalClassify] 之前检测到已接通，标记为已接听")
            autoMarkCallStatus("connected", "已接听")
            return
        }

        // 根据 Root 检测状态判断
        if (rootState != null) {
            Log.d(TAG, "[FinalClassify] 使用 Root 状态: $rootState")
            DebugLogger.log("[FinalClassify] 使用 Root 状态: $rootState")
            when (rootState) {
                RootCallState.BUSY -> {
                    autoMarkCallStatus("busy", "对方忙线")
                    return
                }
                RootCallState.REJECTED -> {
                    autoMarkCallStatus("rejected", "对方拒接")
                    return
                }
                RootCallState.POWER_OFF -> {
                    autoMarkCallStatus("power_off", "对方关机")
                    return
                }
                RootCallState.VOICEMAIL -> {
                    autoMarkCallStatus("voicemail", "语音信箱")
                    return
                }
                RootCallState.NO_ANSWER -> {
                    autoMarkCallStatus("no_answer", "无人接听")
                    return
                }
                RootCallState.ACTIVE, RootCallState.CONNECTED -> {
                    autoMarkCallStatus("connected", "已接听")
                    return
                }
                else -> {}
            }
        }

        // 默认：标记为响铃未接
        Log.d(TAG, "[FinalClassify] 默认标记为响铃未接")
        DebugLogger.log("[FinalClassify] 默认标记为响铃未接")
        autoMarkCallStatus("unanswered", "响铃未接")
        DebugLogger.log("[FinalClassify] ========== 分类完成 ==========")
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
        DebugLogger.logSeparator("processQueue 开始")
        DebugLogger.log("[ProcessQueue] 客户队列大小: ${customerQueue.size}, 起始索引: $currentIndex")
        DebugLogger.log("[ProcessQueue] 任务ID: ${_taskId.value}, 任务标题: $taskTitle")
        DebugLogger.log("[ProcessQueue] 拨号参数: intervalSeconds=$intervalSeconds, timeoutSeconds=$timeoutSeconds, dialsPerCustomer=$dialsPerCustomer")
        
        while (_isRunning.value && currentIndex < customerQueue.size) {
            if (isPaused) {
                delay(1000)
                continue
            }

            val customer = customerQueue[currentIndex]
            DebugLogger.logSeparator("处理客户 #${currentIndex + 1}/${customerQueue.size}")
            DebugLogger.log("[ProcessQueue] 客户: ${customer.name}, 电话: ${customer.phone}, ID: ${customer.id}")
            
            _currentCustomer.value = customer
            hasAutoMarkedCurrentCall = false
            lastCallWasConnected = false
            lastResolvedCallResult = null
            currentCallRecordId = null

            // 更新悬浮窗
            updateFloatingWindow()

            // 保存进度（每拨打一个客户前保存）
            saveProgress()

            // 拨打电话
            DebugLogger.log("[ProcessQueue] 开始拨号...")
            var callFailed = false
            var failReason = ""
            try {
                currentCallRecordId = createServerCallRecord(customer)
                DebugLogger.log("[ProcessQueue] 创建通话记录: callRecordId=$currentCallRecordId")
                callHelper.makeCall(customer.phone ?: "")
                DebugLogger.log("[ProcessQueue] 拨号命令已发送")
            } catch (e: Exception) {
                // 拨号异常（如无SIM卡、权限被拒绝等），标记为拨打失败并继续下一个
                callFailed = true
                failReason = e.message ?: "拨号失败"
                DebugLogger.logError("ProcessQueue-拨号失败", e)
                // 标记为拨打失败
                markCustomerAsFailed(customer, failReason)
            }
            
            // 如果拨号失败，跳过等待流程，直接处理下一个客户
            if (callFailed) {
                DebugLogger.log("[ProcessQueue] 拨号失败，跳过等待流程")
                _dialedCount.value = _dialedCount.value + 1
                currentDialRound = 1
                currentIndex++
                saveProgress()
                
                // 如果还有下一个客户需要拨打
                if (currentIndex < customerQueue.size && _isRunning.value) {
                    // 先等待间隔时间
                    delay(intervalSeconds * 1000L)
                    // 在拨打之前必须等待 App 回到前台
                    waitUntilCanDial()
                }
                continue
            }

            // 等待通话结束或超时
            DebugLogger.log("[ProcessQueue] 进入 waitForCallEndOrTimeout 等待通话结束...")
            val callEndedNormally = waitForCallEndOrTimeout()
            DebugLogger.log("[ProcessQueue] waitForCallEndOrTimeout 返回: $callEndedNormally")
            
            // 如果服务被停止（如权限异常），则退出循环
            if (!callEndedNormally) {
                Log.e(TAG, "callEndedNormally: false")
                DebugLogger.log("[ProcessQueue] ✗ 通话状态监控异常中断")
                UserNotifier.showError("通话状态监控异常中断，请检查通话权限")
                break
            }

            // 通话结束（挂断）后标记为已拨打
            DebugLogger.log("[ProcessQueue] 通话结束，更新状态")
            DebugLogger.log("[ProcessQueue] hasAutoMarkedCurrentCall=$hasAutoMarkedCurrentCall, lastCallWasConnected=$lastCallWasConnected, lastResolvedCallResult=$lastResolvedCallResult")
            _dialedCount.value = _dialedCount.value + 1
            
            // 如果自动检测已经标记过该客户，则不再重复标记
            if (!hasAutoMarkedCurrentCall) {
                DebugLogger.log("[ProcessQueue] 调用 markCustomerAsCalled")
                markCustomerAsCalled(customer)
            } else {
                Log.d(TAG, "[processQueue] 自动检测已标记该客户，跳过 markCustomerAsCalled")
                DebugLogger.log("[ProcessQueue] 自动检测已标记，跳过 markCustomerAsCalled")
            }
            saveProgress()
            
            // 重置自动标记标志，为下一次通话做准备
            hasAutoMarkedCurrentCall = false
            lastCallWasConnected = false
            lastResolvedCallResult = null
            currentCallRecordId = null

            // 判断是否继续拨打同一客户
            if (currentDialRound < dialsPerCustomer) {
                // 同一客户还有下一轮拨打
                DebugLogger.log("[ProcessQueue] 同一客户第 ${currentDialRound + 1} 轮拨打")
                currentDialRound++
            } else {
                // 当前客户所有轮次完成，流转到下一个客户
                DebugLogger.log("[ProcessQueue] 当前客户所有轮次完成，流转到下一个客户")
                currentDialRound = 1
                currentIndex++
            }

            // 如果还有下一个客户需要拨打
            if (currentIndex < customerQueue.size && _isRunning.value) {
                // 先等待间隔时间
                DebugLogger.log("[ProcessQueue] 等待 ${intervalSeconds}秒 后拨打下一个客户")
                delay(intervalSeconds * 1000L)
                
                // 关键：在拨打之前必须等待 App 回到前台
                // 这样即使用户在等待期间切换到后台，也会等待用户回来
                waitUntilCanDial()
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
                val callResult = lastResolvedCallResult ?: when {
                    lastCallWasConnected -> "已接听"
                    else -> when (lastState) {
                    RootCallState.BUSY -> "对方忙线"
                    RootCallState.REJECTED -> "对方拒接"
                    RootCallState.NO_ANSWER -> "无人接听"
                    RootCallState.POWER_OFF -> "对方关机"
                    RootCallState.VOICEMAIL -> "语音信箱"
                    RootCallState.CONNECTED -> "通话完成"
                    RootCallState.ACTIVE -> "通话完成"
                    else -> "响铃未接"
                    }
                }
                val request = UpdateTaskCustomerStatusRequest(
                    status = "called",
                    callResult = callResult,
                    callId = currentCallRecordId
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
                    callResult = "拨打失败: $reason",
                    callId = currentCallRecordId
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

    private suspend fun createServerCallRecord(customer: Customer): Int? {
        val phone = customer.phone ?: return null
        val agentId = tokenManager.getUserId() ?: return null
        val result = callRecordRepository.createCallRecord(
            customerId = customer.id,
            customerName = customer.name ?: "",
            phone = phone,
            agentId = agentId,
            status = "calling"
        )
        return result.getOrNull()?.id?.takeIf { it > 0 }
    }

    /**
     * 等待通话结束
     * @return 是否正常结束（true=通话已挂断，false=服务被停止）
     */
    private suspend fun waitForCallEndOrTimeout(): Boolean {
        DebugLogger.logSeparator("waitForCallEndOrTimeout 开始")
        DebugLogger.log("[WaitCall] 客户: ${_currentCustomer.value?.name}, 电话: ${_currentCustomer.value?.phone}")
        DebugLogger.log("[WaitCall] taskId=${_taskId.value}, isRunning=${_isRunning.value}")
        
        var callConnected = false
        var offHookStartTime: Long = 0
        var alertingStartTime: Long = 0
        var hasAlertingState = false
        var hasActiveState = false

        // 音频能量分析器
        var audioEnergyAnalyzer: AudioEnergyAnalyzer? = null
        var audioEnergyPattern = AudioEnergyPattern.UNKNOWN

        // 关键词检测器
        var keywordDetector: KeywordDetector? = null
        var detectedKeywords = emptyList<String>()
        var keywordCallType: KeywordCallType? = null

        // 更新初始状态为拨号中
        _currentCallState.value = com.callcenter.app.util.root.RootCallState.DIALING
        _currentCallNumber.value = _currentCustomer.value?.phone

        // 添加拨号中历史记录
        FloatingCustomerService.addCallStateHistory("拨号中", _currentCustomer.value?.phone, _currentCustomer.value?.name)

        try {
            // 等待电话接通
            Log.d(TAG, "等待电话接通...")
            var idleConfirmStartTime: Long = 0

            // 【关键修复】记录拨号开始时间，在拨号后的一段时间内不应该判定为"未接通"
            val dialStartTime = System.currentTimeMillis()
            val minWaitForOffhook = 10000L  // 至少等待10秒，给通话建立足够的时间
            
            DebugLogger.log("[WaitCall] 拨号开始时间: $dialStartTime, 最小等待时间: ${minWaitForOffhook}ms")
            
            var loopCount = 0
            
            while (_isRunning.value) {
                loopCount++
                val state = telephonyManager.callState
                val timeSinceDial = System.currentTimeMillis() - dialStartTime
                
                // 获取 AudioManager 模式
                val audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as? AudioManager
                val audioMode = audioManager?.mode ?: -1
                
                // 每10次循环记录一次详细状态（每5秒）
                if (loopCount % 10 == 0) {
                    DebugLogger.logCallState(
                        phase = "WaitCall-Loop-$loopCount",
                        callState = state,
                        audioMode = audioMode,
                        isServiceRunning = _isRunning.value,
                        currentCustomer = _currentCustomer.value?.name,
                        extraInfo = mapOf(
                            "callConnected" to callConnected,
                            "timeSinceDial" to "${timeSinceDial}ms",
                            "hasActiveState" to hasActiveState,
                            "hasAlertingState" to hasAlertingState
                        )
                    )
                }

                when (state) {
                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                        // 电话已接通（包括运营商语音播报的情况）
                        if (!callConnected) {
                            callConnected = true
                            offHookStartTime = System.currentTimeMillis()
                            idleConfirmStartTime = 0
                            hasActiveState = true
                            Log.d(TAG, "电话已接通/进入通话状态")
                            DebugLogger.log("[WaitCall] ✓ 检测到 OFFHOOK 状态！电话已接通")
                            DebugLogger.log("[WaitCall] offHookStartTime=$offHookStartTime")
                            _currentCallState.value = com.callcenter.app.util.root.RootCallState.ACTIVE
                            // 注意：此时还不知道是用户接听还是语音信箱，等通话结束后根据时长判断
                            FloatingCustomerService.addCallStateHistory("已进入通话", _currentCustomer.value?.phone, _currentCustomer.value?.name)

                            // 延迟后自动开启免提（等待音频系统准备好）
                            delay(500)  // 延迟 500ms
                            enableSpeakerphoneWithRetry()

                            // 启动音频能量分析（同时保存音频数据用于关键词识别）
                            serviceScope.launch {
                                try {
                                    DebugLogger.logSeparator("音频能量分析启动")
                                    DebugLogger.log("[AudioAnalysis] 创建 AudioEnergyAnalyzer 实例")
                                    audioEnergyAnalyzer = AudioEnergyAnalyzer(this@AutoDialService)
                                    
                                    // 检查是否启用了关键词检测功能
                                    val keywordDetectionEnabled = try {
                                        val enabled = callResultClassifier.isKeywordDetectionEnabled()
                                        DebugLogger.log("[AudioAnalysis] 关键词检测功能开关: $enabled")
                                        enabled
                                    } catch (e: Exception) {
                                        DebugLogger.log("[AudioAnalysis] 检查关键词检测功能异常: ${e.message}")
                                        false
                                    }
                                    
                                    // 设置是否保存音频数据
                                    audioEnergyAnalyzer?.setSaveAudioData(keywordDetectionEnabled)
                                    DebugLogger.log("[AudioAnalysis] 保存音频数据: $keywordDetectionEnabled")
                                    
                                    // 检查录音权限
                                    val hasRecordPermission = audioEnergyAnalyzer?.hasRecordPermission() ?: false
                                    DebugLogger.log("[AudioAnalysis] 录音权限: $hasRecordPermission")
                                    
                                    if (!hasRecordPermission) {
                                        DebugLogger.log("[AudioAnalysis] ✗ 无录音权限，无法进行音频分析")
                                        FloatingCustomerService.addCallStateHistory(
                                            "无录音权限",
                                            _currentCustomer.value?.phone,
                                            _currentCustomer.value?.name
                                        )
                                        audioEnergyAnalyzer = null
                                        return@launch
                                    }
                                    
                                    // 启动音频采集
                                    DebugLogger.log("[AudioAnalysis] 开始启动音频采集...")
                                    val started = audioEnergyAnalyzer?.start() ?: false
                                    
                                    if (started) {
                                        Log.d(TAG, "音频能量分析已启动")
                                        DebugLogger.log("[AudioAnalysis] ✓ 音频能量分析启动成功")
                                        FloatingCustomerService.addCallStateHistory(
                                            "音频分析中",
                                            _currentCustomer.value?.phone,
                                            _currentCustomer.value?.name
                                        )
                                    } else {
                                        Log.w(TAG, "音频能量分析启动失败")
                                        DebugLogger.log("[AudioAnalysis] ✗ 音频能量分析启动失败")
                                        FloatingCustomerService.addCallStateHistory(
                                            "音频分析启动失败",
                                            _currentCustomer.value?.phone,
                                            _currentCustomer.value?.name
                                        )
                                        audioEnergyAnalyzer = null
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "启动音频能量分析异常: ${e.message}")
                                    DebugLogger.log("[AudioAnalysis] ✗ 启动异常: ${e.message}")
                                    DebugLogger.log("[AudioAnalysis] 异常堆栈: ${e.stackTraceToString().take(500)}")
                                    audioEnergyAnalyzer = null
                                }
                            }
                        } else {
                            // 状态从 IDLE 变回 OFFHOOK，重置确认计时
                            if (idleConfirmStartTime > 0) {
                                Log.d(TAG, "状态从 IDLE 变回 OFFHOOK，重置确认计时")
                                idleConfirmStartTime = 0
                            }
                        }
                    }
                    TelephonyManager.CALL_STATE_IDLE -> {
                        if (callConnected) {
                            // 之前已接通，现在变成 IDLE
                            // 开始或继续确认流程
                            if (idleConfirmStartTime == 0L) {
                                idleConfirmStartTime = System.currentTimeMillis()
                                Log.d(TAG, "检测到 IDLE 状态，开始确认流程...")
                            }

                            val idleDuration = System.currentTimeMillis() - idleConfirmStartTime
                            val callDuration = System.currentTimeMillis() - offHookStartTime

                            // 必须满足两个条件才能确认通话结束：
                            // 1. IDLE 状态持续时间超过最小确认时间
                            // 2. 通话总时长超过最小通话时间（或确认时间足够长）
                            if (idleDuration >= MIN_CONFIRM_IDLE_TIME) {
                                // 确认通话结束
                                Log.d(TAG, "通话已确认结束，总持续时间: ${callDuration}ms，IDLE确认时间: ${idleDuration}ms")
                                _currentCallState.value = com.callcenter.app.util.root.RootCallState.IDLE
                                callHelper.disableSpeakerphone()

                                // 【关键修复】立即标记为已接通（因为 callConnected=true 且 callDuration > 0）
                                // 这样即使用户点击"停止"，也能正确判断通话状态
                                if (callConnected && callDuration > 0) {
                                    lastCallWasConnected = true
                                    DebugLogger.log("[WaitCall] ✓ 立即标记 lastCallWasConnected=true, callDuration=${callDuration}ms")
                                }

                                // 停止音频能量分析并获取结果
                                DebugLogger.logSeparator("停止音频能量分析")
                                Log.d(TAG, "开始停止音频能量分析...")
                                DebugLogger.log("[AudioAnalysis] 停止音频能量分析...")
                                
                                try {
                                    val audioResult = audioEnergyAnalyzer?.stopAndAnalyze()
                                    audioEnergyPattern = audioResult?.pattern ?: AudioEnergyPattern.UNKNOWN
                                    
                                    // 详细记录音频分析结果
                                    DebugLogger.log("[AudioAnalysis] ========== 音频分析结果 ==========")
                                    DebugLogger.log("[AudioAnalysis] 模式: $audioEnergyPattern")
                                    DebugLogger.log("[AudioAnalysis] 置信度: ${audioResult?.confidence}")
                                    DebugLogger.log("[AudioAnalysis] 平均能量: ${audioResult?.averageEnergy}")
                                    DebugLogger.log("[AudioAnalysis] 能量变化次数: ${audioResult?.energyChanges}")
                                    DebugLogger.log("[AudioAnalysis] 分析时长: ${audioResult?.durationMs}ms")
                                    DebugLogger.log("[AudioAnalysis] 原因: ${audioResult?.reason}")
                                    DebugLogger.log("[AudioAnalysis] 音频文件: ${audioResult?.audioFilePath}")
                                    
                                    Log.d(TAG, "音频能量分析结果: pattern=$audioEnergyPattern, confidence=${audioResult?.confidence}, reason=${audioResult?.reason}")
                                    
                                    if (audioResult != null && audioEnergyPattern != AudioEnergyPattern.UNKNOWN) {
                                        FloatingCustomerService.addCallStateHistory(
                                            "音频分析: ${audioEnergyPattern.name}",
                                            _currentCustomer.value?.phone,
                                            _currentCustomer.value?.name
                                        )
                                    } else {
                                        DebugLogger.log("[AudioAnalysis] ✗ 音频分析结果无效或未知")
                                    }

                                    // 如果有音频文件，进行关键词识别
                                    val audioFilePath = audioResult?.audioFilePath
                                    DebugLogger.log("[KeywordDetect] 音频文件路径: $audioFilePath")
                                    
                                    if (!audioFilePath.isNullOrBlank()) {
                                        DebugLogger.logSeparator("关键词识别")
                                        try {
                                            keywordDetector = KeywordDetector(this@AutoDialService)
                                            
                                            // 检查模型是否可用
                                            val modelInit = keywordDetector?.init() ?: false
                                            val modelAvailable = keywordDetector?.isModelAvailable() ?: false
                                            val availableModels = keywordDetector?.getAvailableModels() ?: emptyList()
                                            
                                            DebugLogger.log("[KeywordDetect] 模型初始化: $modelInit")
                                            DebugLogger.log("[KeywordDetect] 模型可用: $modelAvailable")
                                            DebugLogger.log("[KeywordDetect] 可用模型列表: $availableModels")
                                            
                                            if (modelInit && modelAvailable) {
                                                Log.d(TAG, "开始从音频文件识别关键词: $audioFilePath")
                                                DebugLogger.log("[KeywordDetect] 开始识别音频文件: $audioFilePath")
                                                DebugLogger.log("[KeywordDetect] 音频文件大小: ${File(audioFilePath).length()} bytes")
                                                
                                                FloatingCustomerService.addCallStateHistory(
                                                    "关键词识别中",
                                                    _currentCustomer.value?.phone,
                                                    _currentCustomer.value?.name
                                                )
                                                
                                                val keywordResult = keywordDetector?.recognizeFromFile(audioFilePath)
                                                detectedKeywords = keywordResult?.keywords ?: emptyList()
                                                keywordCallType = keywordResult?.callType
                                                
                                                // 详细记录关键词识别结果
                                                DebugLogger.log("[KeywordDetect] ========== 关键词识别结果 ==========")
                                                DebugLogger.log("[KeywordDetect] 检测到关键词: ${keywordResult?.detected}")
                                                DebugLogger.log("[KeywordDetect] 通话类型: $keywordCallType")
                                                DebugLogger.log("[KeywordDetect] 关键词列表: $detectedKeywords")
                                                DebugLogger.log("[KeywordDetect] 置信度: ${keywordResult?.confidence}")
                                                DebugLogger.log("[KeywordDetect] 完整文本: ${keywordResult?.fullText}")
                                                DebugLogger.log("[KeywordDetect] 原因: ${keywordResult?.reason}")
                                                
                                                Log.d(TAG, "关键词检测结果: type=$keywordCallType, keywords=$detectedKeywords, text=${keywordResult?.fullText}")
                                                
                                                if (keywordResult != null && keywordResult.detected) {
                                                    FloatingCustomerService.addCallStateHistory(
                                                        "关键词: ${keywordResult.keywords.joinToString()}",
                                                        _currentCustomer.value?.phone,
                                                        _currentCustomer.value?.name
                                                    )
                                                } else {
                                                    DebugLogger.log("[KeywordDetect] ✗ 未检测到有效关键词")
                                                }
                                            } else {
                                                Log.d(TAG, "Vosk模型不可用，跳过关键词识别")
                                                DebugLogger.log("[KeywordDetect] ✗ Vosk模型不可用，跳过关键词识别")
                                                DebugLogger.log("[KeywordDetect] 提示: 请在权限测试页面下载语音识别模型")
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "关键词识别异常: ${e.message}")
                                            DebugLogger.log("[KeywordDetect] ✗ 关键词识别异常: ${e.message}")
                                            DebugLogger.log("[KeywordDetect] 异常堆栈: ${e.stackTraceToString().take(500)}")
                                        } finally {
                                            keywordDetector?.release()
                                            keywordDetector = null
                                            DebugLogger.log("[KeywordDetect] 关键词检测器已释放")
                                        }
                                    } else {
                                        DebugLogger.log("[KeywordDetect] ✗ 无音频文件，跳过关键词识别")
                                        DebugLogger.log("[KeywordDetect] 可能原因: 未开启关键词检测功能 或 录音失败")
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "停止音频能量分析异常: ${e.message}")
                                    DebugLogger.log("[AudioAnalysis] ✗ 停止音频分析异常: ${e.message}")
                                    DebugLogger.log("[AudioAnalysis] 异常堆栈: ${e.stackTraceToString().take(500)}")
                                }
                                audioEnergyAnalyzer = null

                                // 使用智能分类器判断通话结果
                                DebugLogger.logSeparator("智能分类判断")
                                Log.d(TAG, "开始智能分类判断...")
                                
                                val alertingDuration = if (hasAlertingState && alertingStartTime > 0) {
                                    offHookStartTime - alertingStartTime
                                } else 0
                                
                                DebugLogger.log("[Classify] ========== 分类参数 ==========")
                                DebugLogger.log("[Classify] 通话时长(offhookDuration): $callDuration ms")
                                DebugLogger.log("[Classify] 响铃时长(alertingDuration): $alertingDuration ms")
                                DebugLogger.log("[Classify] 是否有ACTIVE状态: $hasActiveState")
                                DebugLogger.log("[Classify] 是否有ALERTING状态: $hasAlertingState")
                                DebugLogger.log("[Classify] 音频能量模式: $audioEnergyPattern")
                                DebugLogger.log("[Classify] 关键词类型: $keywordCallType")
                                DebugLogger.log("[Classify] 检测到的关键词: $detectedKeywords")
                                Log.d(TAG, "分类参数: offhookDuration=$callDuration, hasActiveState=$hasActiveState, audioEnergyPattern=$audioEnergyPattern")
                                
                                val callResult = determineCallResult(
                                    offhookDuration = callDuration,
                                    alertingDuration = alertingDuration,
                                    hasActiveState = hasActiveState,
                                    hasAlertingState = hasAlertingState,
                                    audioEnergyPattern = audioEnergyPattern,
                                    detectedKeywords = detectedKeywords,
                                    keywordCallType = keywordCallType
                                )

                                DebugLogger.log("[Classify] ========== 分类结果 ==========")
                                DebugLogger.log("[Classify] 是否接通: ${callResult.first}")
                                DebugLogger.log("[Classify] 结果描述: ${callResult.second}")
                                Log.d(TAG, "智能分类完成: isConnected=${callResult.first}, result=${callResult.second}")
                                
                                lastCallWasConnected = callResult.first
                                lastResolvedCallResult = callResult.second

                                FloatingCustomerService.addCallStateHistory(
                                    callResult.second,
                                    _currentCustomer.value?.phone,
                                    _currentCustomer.value?.name,
                                    callDuration
                                )
                                
                                // 自动标记通话状态到服务器
                                Log.d(TAG, "准备自动标记通话状态...")
                                val statusKey = when (callResult.second) {
                                    "已接听" -> "connected"
                                    "语音信箱" -> "voicemail"
                                    else -> "unanswered"
                                }
                                autoMarkCallStatus(statusKey, callResult.second)
                                
                                Log.d(TAG, "通话结果处理完成，返回")
                                return true
                            }
                        } else {
                            // 电话未接通就被挂断了（如无法连接到移动网络）
                            // 【关键修复】在拨号后的最小等待时间内，继续等待状态变化
                            DebugLogger.log("[WaitCall] IDLE状态: callConnected=$callConnected, timeSinceDial=${timeSinceDial}ms, minWaitForOffhook=${minWaitForOffhook}ms")
                            
                            if (timeSinceDial < minWaitForOffhook) {
                                // 拨号后不足10秒，继续等待，可能是状态更新延迟
                                Log.d(TAG, "拨号后 ${timeSinceDial}ms，状态为IDLE但等待时间不足，继续等待...")
                                DebugLogger.log("[WaitCall] ⏳ 拨号后${timeSinceDial}ms，IDLE但等待时间不足${minWaitForOffhook}ms，继续等待...")
                                if (idleConfirmStartTime == 0L) {
                                    idleConfirmStartTime = System.currentTimeMillis()
                                }
                                // 继续循环等待
                            } else {
                                // 已超过最小等待时间，开始确认流程
                                if (idleConfirmStartTime == 0L) {
                                    idleConfirmStartTime = System.currentTimeMillis()
                                    Log.d(TAG, "未接通状态，开始确认流程...")
                                    DebugLogger.log("[WaitCall] ⚠️ 未接通状态，开始确认流程...")
                                }

                                val idleDuration = System.currentTimeMillis() - idleConfirmStartTime
                                DebugLogger.log("[WaitCall] IDLE确认时间: idleDuration=${idleDuration}ms, MIN_CONFIRM_IDLE_TIME=${MIN_CONFIRM_IDLE_TIME}ms")
                                
                                if (idleDuration >= MIN_CONFIRM_IDLE_TIME) {
                                    Log.d(TAG, "确认电话未接通即结束")
                                    DebugLogger.log("[WaitCall] ✗ 确认电话未接通即结束")
                                    _currentCallState.value = com.callcenter.app.util.root.RootCallState.IDLE
                                    FloatingCustomerService.addCallStateHistory("未接通", _currentCustomer.value?.phone, _currentCustomer.value?.name)
                                    lastCallWasConnected = false
                                    lastResolvedCallResult = "响铃未接"
                                    // 自动标记为未接通
                                    autoMarkCallStatus("unanswered", "响铃未接")
                                    return true
                                }
                            }
                        }
                    }
                    TelephonyManager.CALL_STATE_RINGING -> {
                        // 响铃中，继续等待
                        Log.d(TAG, "电话响铃中...")
                        DebugLogger.log("[WaitCall] 🔔 检测到 RINGING 状态")
                        // 更新状态为响铃中（只在第一次检测到响铃时更新）
                        if (!hasAlertingState) {
                            hasAlertingState = true
                            alertingStartTime = System.currentTimeMillis()
                            DebugLogger.log("[WaitCall] 首次检测到响铃，alertingStartTime=$alertingStartTime")
                        }
                        if (_currentCallState.value != com.callcenter.app.util.root.RootCallState.ALERTING) {
                            _currentCallState.value = com.callcenter.app.util.root.RootCallState.ALERTING
                            FloatingCustomerService.addCallStateHistory("对方响铃中", _currentCustomer.value?.phone, _currentCustomer.value?.name)
                        }
                    }
                }

                // 【增强检测】备用检测机制：使用 AudioManager 模式判断通话状态
                // 某些设备上 telephonyManager.callState 可能不准确
                if (!callConnected && timeSinceDial > 3000) {
                    // 使用前面已经获取的 audioMode
                    if (audioMode == AudioManager.MODE_IN_CALL ||
                        audioMode == AudioManager.MODE_IN_COMMUNICATION) {
                        // 音频模式为通话中，说明电话已接通
                        Log.d(TAG, "AudioManager 检测到通话模式: mode=$audioMode，判定为已接通")
                        DebugLogger.log("[WaitCall] ✓ AudioManager检测到通话模式！audioMode=$audioMode，判定为已接通")
                        callConnected = true
                        offHookStartTime = System.currentTimeMillis()
                        idleConfirmStartTime = 0
                        hasActiveState = true
                        _currentCallState.value = com.callcenter.app.util.root.RootCallState.ACTIVE
                        FloatingCustomerService.addCallStateHistory("已进入通话(AudioManager检测)", _currentCustomer.value?.phone, _currentCustomer.value?.name)
                        
                        // 开启免提
                        delay(500)
                        enableSpeakerphoneWithRetry()
                    } else if (loopCount % 20 == 0) {
                        // 每10秒记录一次 AudioManager 状态
                        DebugLogger.log("[WaitCall] AudioManager状态: mode=$audioMode (0=NORMAL, 2=IN_CALL, 3=IN_COMMUNICATION)")
                    }
                }

                // 【超时保护】如果超过拨号超时时间且从未进入 OFFHOOK，也结束等待
                // 这可以防止在某些异常情况下无限等待
                if (!callConnected && timeSinceDial > timeoutSeconds * 1000L) {
                    Log.d(TAG, "拨号后 ${timeSinceDial}ms 超过超时时间 ${timeoutSeconds}s，结束等待")
                    DebugLogger.log("[WaitCall] ⏱️ 拨号超时！timeSinceDial=${timeSinceDial}ms, timeoutSeconds=$timeoutSeconds")
                    _currentCallState.value = com.callcenter.app.util.root.RootCallState.IDLE
                    FloatingCustomerService.addCallStateHistory("拨号超时", _currentCustomer.value?.phone, _currentCustomer.value?.name)
                    lastCallWasConnected = false
                    lastResolvedCallResult = "响铃未接"
                    autoMarkCallStatus("unanswered", "响铃未接")
                    return true
                }

                // 检查是否超过最大通话时间
                if (callConnected && (System.currentTimeMillis() - offHookStartTime) > MAX_CALL_DURATION) {
                    Log.d(TAG, "通话超过最大时间限制，强制结束等待")
                    DebugLogger.log("[WaitCall] 通话超过最大时间限制 ${MAX_CALL_DURATION}ms，强制结束")
                    _currentCallState.value = com.callcenter.app.util.root.RootCallState.IDLE
                    FloatingCustomerService.addCallStateHistory("已挂断", _currentCustomer.value?.phone, _currentCustomer.value?.name, MAX_CALL_DURATION.toLong())
                    // 对于非 root 设备，长时间通话肯定是接通了
                    lastCallWasConnected = true
                    lastResolvedCallResult = "已接听"
                    // 自动标记为已接听
                    autoMarkCallStatus("connected", "已接听")
                    return true
                }

                delay(500)
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: ${e.message}")
            UserNotifier.showError("缺少读取通话状态权限，状态识别将降级")
            // 没有权限读取通话状态，使用超时机制作为后备
            Log.w(TAG, "无通话状态权限，使用${timeoutSeconds}秒超时")
            delay(timeoutSeconds * 1000L)
            _currentCallState.value = com.callcenter.app.util.root.RootCallState.IDLE
            return _isRunning.value
        }

        // 服务被停止
        Log.d(TAG, "服务被停止，结束等待")
        _currentCallState.value = com.callcenter.app.util.root.RootCallState.IDLE
        return false
    }

    /**
     * 使用智能分类器判断通话结果
     * @return Pair<是否接通, 结果描述>
     */
    private suspend fun determineCallResult(
        offhookDuration: Long,
        alertingDuration: Long,
        hasActiveState: Boolean,
        hasAlertingState: Boolean,
        audioEnergyPattern: AudioEnergyPattern = AudioEnergyPattern.UNKNOWN,
        detectedKeywords: List<String> = emptyList(),
        keywordCallType: KeywordCallType? = null
    ): Pair<Boolean, String> {
        // 构建通话上下文
        val context = CallContext(
            offhookDuration = offhookDuration,
            alertingDuration = alertingDuration,
            totalDuration = offhookDuration,
            hasActiveState = hasActiveState,
            hasAlertingState = hasAlertingState,
            audioEnergyPattern = audioEnergyPattern,
            rootDetectedState = null, // Root状态由 RootCallStateDetector 单独处理
            detectedKeywords = detectedKeywords,
            keywordCallType = keywordCallType
        )

        // 使用分类器判断
        val result = callResultClassifier.classify(context)

        Log.d(TAG, "智能分类结果: type=${result.type}, confidence=${result.confidence}, reason=${result.reason}, layer=${result.layer}, audioPattern=$audioEnergyPattern, keywords=$detectedKeywords")

        return when (result.type) {
            CallResultType.CONNECTED -> {
                Pair(true, "已接听")
            }
            CallResultType.VOICEMAIL -> {
                Pair(false, "语音信箱")
            }
            CallResultType.UNKNOWN -> {
                // 无法确定，需要用户手动确认
                // 默认标记为需要确认的状态
                Pair(false, "待确认")
            }
        }
    }

    private fun startForeground() {
        // Android要求前台服务必须有通知，创建一个简单的通知
        val notification = createSimpleNotification()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
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

    /**
     * 获取当前通话状态
     */
    private fun getCallState(): Int {
        return try {
            telephonyManager.callState
        } catch (e: Exception) {
            Log.e(TAG, "获取通话状态失败: ${e.message}")
            TelephonyManager.CALL_STATE_IDLE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutoDial()
        
        serviceScope.cancel()
        lastCallWasConnected = false
        lastResolvedCallResult = null
        hasAutoMarkedCurrentCall = false
        // 释放 RootCallStateDetector 资源
        if (::rootCallStateDetector.isInitialized) {
            rootCallStateDetector.release()
        }
    }
}
