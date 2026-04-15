package com.callcenter.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.callcenter.app.R
import com.callcenter.app.util.RootUtil
import com.callcenter.app.util.root.RootCallStateDetector
import com.callcenter.app.util.root.RootPhoneStateListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 通话状态监听服务
 * 监听通话状态变化，在通话接通时自动开启免提
 */
class CallStateMonitorService : Service() {

    companion object {
        private const val TAG = "CallStateMonitorService"
        private const val NOTIFICATION_CHANNEL_ID = "call_monitor_channel"
        private const val NOTIFICATION_ID = 1002

        const val ACTION_START_MONITOR = "com.callcenter.app.ACTION_START_MONITOR"
        const val ACTION_STOP_MONITOR = "com.callcenter.app.ACTION_STOP_MONITOR"
        const val EXTRA_AUTO_SPEAKER = "extra_auto_speaker"
        const val EXTRA_DELAY_MS = "extra_delay_ms"

        // 默认延迟时间（毫秒）- 通话接通后等待一段时间再开启免提
        const val DEFAULT_SPEAKER_DELAY = 600L

        // 最大监听时长（毫秒）- 防止服务一直运行
        const val MAX_MONITOR_DURATION = 120000L

        // 持续监控检查间隔（毫秒）- 持续检查扬声器状态
        const val CONTINUOUS_CHECK_INTERVAL = 300L

        /**
         * 启动通话监听服务
         * @param context 上下文
         * @param autoSpeaker 是否自动开启免提
         * @param delayMs 延迟时间（毫秒）
         */
        fun startService(context: Context, autoSpeaker: Boolean = true, delayMs: Long = DEFAULT_SPEAKER_DELAY) {
            val intent = Intent(context, CallStateMonitorService::class.java).apply {
                action = ACTION_START_MONITOR
                putExtra(EXTRA_AUTO_SPEAKER, autoSpeaker)
                putExtra(EXTRA_DELAY_MS, delayMs)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * 停止通话监听服务
         */
        fun stopService(context: Context) {
            val intent = Intent(context, CallStateMonitorService::class.java).apply {
                action = ACTION_STOP_MONITOR
            }
            context.startService(intent)
        }
    }

    private lateinit var telephonyManager: TelephonyManager
    private var phoneStateListener: PhoneStateListener? = null
    private var audioManager: AudioManager? = null
    private var rootCallStateDetector: RootCallStateDetector? = null
    private var rootPhoneStateListener: RootPhoneStateListener? = null

    private var isMonitoring = false
    private var autoSpeakerEnabled = true
    private var speakerDelayMs = DEFAULT_SPEAKER_DELAY
    private var lastCallState = TelephonyManager.CALL_STATE_IDLE
    private var callConnected = false
    private var speakerRetryJob: Job? = null

    // 持续监控任务 - 定期检查扬声器状态
    private var continuousMonitorJob: Job? = null
    // 上次确认扬声器开启的时间
    private var lastSpeakerEnabledTime = 0L

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private val handler = Handler(Looper.getMainLooper())

    // 超时停止服务的 Runnable
    private val stopServiceRunnable = Runnable {
        Log.d(TAG, "监听超时，停止服务")
        stopSelf()
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "服务创建")
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")

        when (intent?.action) {
            ACTION_START_MONITOR -> {
                autoSpeakerEnabled = intent.getBooleanExtra(EXTRA_AUTO_SPEAKER, true)
                speakerDelayMs = intent.getLongExtra(EXTRA_DELAY_MS, DEFAULT_SPEAKER_DELAY)
                startMonitoring()
            }
            ACTION_STOP_MONITOR -> {
                stopMonitoring()
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "服务销毁")
        stopMonitoring()
        serviceJob.cancel()
        handler.removeCallbacksAndMessages(null)
    }

    /**
     * 开始监听通话状态
     */
    private fun startMonitoring() {
        if (isMonitoring) {
            Log.d(TAG, "已经在监听中")
            return
        }

        isMonitoring = true
        callConnected = false
        lastCallState = TelephonyManager.CALL_STATE_IDLE

        // 启动前台服务
        startForeground(NOTIFICATION_ID, createNotification())

        // 设置超时自动停止
        handler.postDelayed(stopServiceRunnable, MAX_MONITOR_DURATION)

        // 注册通话状态监听
        registerPhoneStateListener()

        // root 设备额外监听更早的拨号状态
        registerRootCallStateListener()

        // 用户预期是“电话拨出就外放”，所以服务启动后立即开始多轮尝试
        if (autoSpeakerEnabled) {
            startSpeakerRetryLoop("service_started")
        }

        Log.d(TAG, "开始监听通话状态，自动免提=$autoSpeakerEnabled，延迟=${speakerDelayMs}ms")
    }

    /**
     * 停止监听通话状态
     */
    private fun stopMonitoring() {
        if (!isMonitoring) return

        isMonitoring = false
        unregisterPhoneStateListener()
        unregisterRootCallStateListener()
        handler.removeCallbacks(stopServiceRunnable)
        speakerRetryJob?.cancel()
        speakerRetryJob = null
        continuousMonitorJob?.cancel()
        continuousMonitorJob = null

        Log.d(TAG, "停止监听通话状态")
    }

    private fun registerRootCallStateListener() {
        if (!RootUtil.isDeviceRooted()) {
            return
        }

        if (rootCallStateDetector != null) {
            return
        }

        val detector = RootCallStateDetector()
        val listener = object : RootPhoneStateListener {
            override fun onDialing(number: String?) {
                Log.d(TAG, "Root 检测到拨号中: $number")
                startSpeakerRetryLoop("root_dialing")
            }

            override fun onAlerting(number: String?) {
                Log.d(TAG, "Root 检测到对方响铃: $number")
                startSpeakerRetryLoop("root_alerting")
            }

            override fun onActive(number: String?, setupTime: Long) {
                Log.d(TAG, "Root 检测到通话接通: $number, setupTime=$setupTime")
                startSpeakerRetryLoop("root_active")
            }

            override fun onIdle(duration: Long) {
                Log.d(TAG, "Root 检测到通话结束: duration=$duration")
                speakerRetryJob?.cancel()
                speakerRetryJob = null
            }
        }

        detector.addListener(listener)
        detector.startDetection()
        rootCallStateDetector = detector
        rootPhoneStateListener = listener
    }

    private fun unregisterRootCallStateListener() {
        rootPhoneStateListener?.let { listener ->
            rootCallStateDetector?.removeListener(listener)
        }
        rootCallStateDetector?.stopDetection()
        rootCallStateDetector = null
        rootPhoneStateListener = null
    }

    /**
     * 注册通话状态监听器
     */
    private fun registerPhoneStateListener() {
        phoneStateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                super.onCallStateChanged(state, phoneNumber)
                handleCallStateChanged(state, phoneNumber)
            }
        }

        phoneStateListener?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyManager.registerTelephonyCallback(
                    mainExecutor,
                    object : android.telephony.TelephonyCallback(),
                        android.telephony.TelephonyCallback.CallStateListener {
                        override fun onCallStateChanged(state: Int) {
                            handleCallStateChanged(state, null)
                        }
                    }
                )
            } else {
                @Suppress("DEPRECATION")
                telephonyManager.listen(it, PhoneStateListener.LISTEN_CALL_STATE)
            }
        }
    }

    /**
     * 注销通话状态监听器
     */
    private fun unregisterPhoneStateListener() {
        phoneStateListener?.let {
            @Suppress("DEPRECATION")
            telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
            phoneStateListener = null
        }
    }

    /**
     * 处理通话状态变化
     */
    private fun handleCallStateChanged(state: Int, phoneNumber: String?) {
        Log.d(TAG, "通话状态变化: $lastCallState -> $state, 号码: $phoneNumber")

        when (state) {
            TelephonyManager.CALL_STATE_IDLE -> {
                // 通话结束
                if (lastCallState != TelephonyManager.CALL_STATE_IDLE) {
                    Log.d(TAG, "通话结束")
                    callConnected = false
                    // 停止持续监控
                    continuousMonitorJob?.cancel()
                    continuousMonitorJob = null
                    // 延迟停止服务
                    handler.postDelayed({ stopSelf() }, 1000)
                }
            }
            TelephonyManager.CALL_STATE_RINGING -> {
                // 来电响铃
                Log.d(TAG, "来电响铃: $phoneNumber")
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                // 拨出/接通阶段都应继续尝试免提，而不是只在接通后尝试一次
                if (autoSpeakerEnabled) {
                    startSpeakerRetryLoop("telephony_offhook")
                }

                // 通话中（拨出或接通）
                if (lastCallState == TelephonyManager.CALL_STATE_IDLE) {
                    Log.d(TAG, "通话建立（拨出/接听）")
                    // 延迟后开启免提
                    if (autoSpeakerEnabled && !callConnected) {
                        callConnected = true
                        enableSpeakerWithDelay()
                    }
                }

                // 启动持续监控，防止系统重置扬声器状态
                if (autoSpeakerEnabled && continuousMonitorJob?.isActive != true) {
                    startContinuousMonitoring()
                }
            }
        }

        lastCallState = state
    }

    /**
     * 延迟开启免提
     */
    private fun enableSpeakerWithDelay() {
        serviceScope.launch {
            Log.d(TAG, "等待 ${speakerDelayMs}ms 后开启免提...")
            delay(speakerDelayMs)

            if (isMonitoring) {
                enableSpeakerphone("delayed_after_offhook")
            }
        }
    }

    private fun startSpeakerRetryLoop(reason: String) {
        if (!autoSpeakerEnabled || !isMonitoring) {
            return
        }

        speakerRetryJob?.cancel()
        speakerRetryJob = serviceScope.launch {
            val retryDelays = listOf(0L, speakerDelayMs, 1200L, 2200L, 3500L, 5000L, 8000L)
            retryDelays.forEachIndexed { index, waitMs ->
                if (waitMs > 0) {
                    delay(waitMs)
                }
                if (!isMonitoring) {
                    return@launch
                }
                enableSpeakerphone("$reason#$index")
            }
        }
    }

    /**
     * 开启免提
     */
    private fun enableSpeakerphone(reason: String) {
        Log.d(TAG, "尝试开启免提, reason=$reason")

        try {
            // 方法1: Android 标准 API，优先显式选择扬声器设备
            audioManager?.let { audio ->
                audio.mode = AudioManager.MODE_IN_CALL
                forceSpeakerUsingHiddenAudioSystem(reason)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val speakerDevice = audio.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    if (speakerDevice != null) {
                        val routed = audio.setCommunicationDevice(speakerDevice)
                        Log.d(TAG, "setCommunicationDevice routed=$routed")
                    }
                }

                @Suppress("DEPRECATION")
                run {
                    audio.isSpeakerphoneOn = true
                    Log.d(TAG, "AudioManager 免提状态: ${audio.isSpeakerphoneOn}")
                }
            }

            // 方法2: Root 方式兜底，适合客服 root 手机
            if (RootUtil.isDeviceRooted()) {
                val rootSuccess = RootUtil.forceEnableSpeaker()
                Log.d(TAG, "Root 方式开启免提: $rootSuccess")
            }

            // 方法3: 再次确认 AudioManager 设置
            handler.postDelayed({
                audioManager?.let { audio ->
                    @Suppress("DEPRECATION")
                    if (!audio.isSpeakerphoneOn) {
                        Log.d(TAG, "免提未开启，重试...")
                        audio.mode = AudioManager.MODE_IN_CALL
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val speakerDevice = audio.availableCommunicationDevices.firstOrNull {
                                it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                            }
                            if (speakerDevice != null) {
                                audio.setCommunicationDevice(speakerDevice)
                            }
                        }
                        audio.isSpeakerphoneOn = true
                    }
                }
            }, 500)

        } catch (e: Exception) {
            Log.e(TAG, "开启免提失败: ${e.message}")
        }
    }

    private fun forceSpeakerUsingHiddenAudioSystem(reason: String) {
        try {
            val audioSystemClass = Class.forName("android.media.AudioSystem")
            val setForceUse = audioSystemClass.getDeclaredMethod(
                "setForceUse",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )

            // FOR_COMMUNICATION = 0, FOR_MEDIA = 1, FORCE_SPEAKER = 1
            setForceUse.invoke(null, 0, 1)
            setForceUse.invoke(null, 1, 1)
            Log.d(TAG, "[$reason] AudioSystem.setForceUse(FOR_COMMUNICATION/FOR_MEDIA, FORCE_SPEAKER) 已执行")
        } catch (e: Throwable) {
            Log.w(TAG, "[$reason] AudioSystem.setForceUse 调用失败: ${e.message}")
        }
    }

    /**
     * 启动持续监控
     * 在通话期间定期检查扬声器状态，防止系统重置
     */
    private fun startContinuousMonitoring() {
        continuousMonitorJob?.cancel()
        continuousMonitorJob = serviceScope.launch {
            var checkCount = 0
            while (isMonitoring && lastCallState == TelephonyManager.CALL_STATE_OFFHOOK) {
                try {
                    checkCount++

                    // 检查扬声器状态
                    audioManager?.let { audio ->
                        @Suppress("DEPRECATION")
                        val isSpeakerOn = audio.isSpeakerphoneOn

                        // 如果扬声器未开启，立即重新开启
                        if (!isSpeakerOn) {
                            Log.w(TAG, "[ContinuousMonitor#$checkCount] 检测到扬声器被关闭，重新开启")
                            enableSpeakerphone("continuous_monitor_recover")
                        } else {
                            // 扬声器已开启，记录时间
                            lastSpeakerEnabledTime = System.currentTimeMillis()
                        }
                    }

                    // 每隔一定时间强制确认一次（即使状态显示开启）
                    if (checkCount % 5 == 0) {
                        Log.d(TAG, "[ContinuousMonitor#$checkCount] 强制确认扬声器状态")
                        enableSpeakerphone("continuous_monitor_confirm")
                    }

                    // 等待下一次检查
                    delay(CONTINUOUS_CHECK_INTERVAL)

                } catch (e: Exception) {
                    Log.e(TAG, "持续监控出错: ${e.message}")
                }
            }
            Log.d(TAG, "持续监控结束，共检查 $checkCount 次")
        }
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "通话监听",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "监听通话状态并自动开启免提"
                setSound(null, null)
                enableVibration(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 创建前台服务通知
     */
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("通话监听中")
            .setContentText("正在监听通话状态，接通后将自动开启免提")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
