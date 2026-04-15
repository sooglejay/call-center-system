package com.callcenter.app.service

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.callcenter.app.util.RootUtil
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 持续监控并修复扬声器状态的后台服务
 *
 * 核心改进：
 * - 添加持续监控机制，每 300ms 检查一次扬声器状态
 * - 发现状态异常时自动修复
 * - 智能判断是否需要启用扬声器
 * - 支持自动停止，节省资源
 */
@AndroidEntryPoint
class CallStateMonitorService : android.app.Service() {

    companion object {
        private const val TAG = "CallStateMonitor"
        private const val CHECK_INTERVAL_MS = 300L  // 检查间隔：300ms
        private const val MAX_FAILURE_COUNT = 10     // 最大失败次数后停止监控

        const val ACTION_START_MONITORING = "com.callcenter.app.action.START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.callcenter.app.action.STOP_MONITORING"
        const val ACTION_FORCE_SPEAKER = "com.callcenter.app.action.FORCE_SPEAKER"
        const val EXTRA_ENABLE_SPEAKER = "enable_speaker"

        fun startMonitoring(context: Context) {
            val intent = Intent(context, CallStateMonitorService::class.java).apply {
                action = ACTION_START_MONITORING
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopMonitoring(context: Context) {
            val intent = Intent(context, CallStateMonitorService::class.java).apply {
                action = ACTION_STOP_MONITORING
            }
            context.startService(intent)
        }

        fun forceSpeaker(context: Context, enable: Boolean = true) {
            val intent = Intent(context, CallStateMonitorService::class.java).apply {
                action = ACTION_FORCE_SPEAKER
                putExtra(EXTRA_ENABLE_SPEAKER, enable)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }

    @Inject
    lateinit var callManager: CallManager

    private lateinit var audioManager: AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private val monitorRunnable = object : Runnable {
        override fun run() {
            checkAndFixSpeakerState()
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    private var isMonitoring = false
    private var failureCount = 0
    private var lastEnabledState = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        Log.d(TAG, "服务已创建")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MONITORING -> {
                startMonitoring()
            }
            ACTION_STOP_MONITORING -> {
                stopMonitoring()
            }
            ACTION_FORCE_SPEAKER -> {
                val enable = intent.getBooleanExtra(EXTRA_ENABLE_SPEAKER, true)
                forceSpeaker(enable)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring()
        Log.d(TAG, "服务已销毁")
    }

    /**
     * 开始监控
     */
    private fun startMonitoring() {
        if (isMonitoring) {
            Log.d(TAG, "监控已在运行")
            return
        }

        isMonitoring = true
        failureCount = 0
        lastEnabledState = false

        Log.d(TAG, "开始监控扬声器状态（间隔: ${CHECK_INTERVAL_MS}ms）")
        handler.post(monitorRunnable)
    }

    /**
     * 停止监控
     */
    private fun stopMonitoring() {
        if (!isMonitoring) {
            return
        }

        isMonitoring = false
        handler.removeCallbacks(monitorRunnable)
        Log.d(TAG, "停止监控")
    }

    /**
     * 检查并修复扬声器状态
     */
    private fun checkAndFixSpeakerState() {
        try {
            // 检查是否在通话中
            val isInCall = callManager.isInCall()
            if (!isInCall) {
                Log.d(TAG, "不在通话中，跳过检查")
                stopMonitoring()
                return
            }

            // 检查扬声器状态
            @Suppress("DEPRECATION")
            val isSpeakerOn = audioManager.isSpeakerphoneOn
            val mode = audioManager.mode

            Log.d(TAG, "状态检查: speaker=$isSpeakerOn, mode=$mode")

            // 如果扬声器未开启，尝试启用
            if (!isSpeakerOn) {
                Log.w(TAG, "扬声器未启用，尝试启用...")
                val success = enableSpeaker("monitor_check")

                if (success) {
                    failureCount = 0
                    lastEnabledState = true
                    Log.d(TAG, "扬声器启用成功")
                } else {
                    failureCount++
                    Log.e(TAG, "扬声器启用失败 (失败次数: $failureCount)")

                    // 如果连续失败超过阈值，停止监控
                    if (failureCount >= MAX_FAILURE_COUNT) {
                        Log.e(TAG, "连续失败 $MAX_FAILURE_COUNT 次，停止监控")
                        stopMonitoring()
                    }
                }
            } else {
                // 扬声器已启用，重置失败计数
                failureCount = 0
                lastEnabledState = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查扬声器状态失败: ${e.message}")
            failureCount++
        }
    }

    /**
     * 强制启用/禁用扬声器
     */
    private fun forceSpeaker(enable: Boolean) {
        Log.d(TAG, "强制设置扬声器: $enable")
        if (enable) {
            enableSpeaker("force_enable")
        } else {
            disableSpeaker("force_disable")
        }
    }

    /**
     * 启用扬声器
     */
    private fun enableSpeaker(reason: String): Boolean {
        try {
            Log.d(TAG, "[$reason] 启用扬声器...")

            // 1. 设置音频模式
            try {
                audioManager.mode = AudioManager.MODE_IN_CALL
            } catch (e: Exception) {
                Log.e(TAG, "[$reason] 设置音频模式失败: ${e.message}")
            }

            // 2. 使用反射调用 AudioSystem.setForceUse
            try {
                val audioSystemClass = Class.forName("android.media.AudioSystem")
                val setForceUse = try {
                    audioSystemClass.getDeclaredMethod(
                        "setForceUse",
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType
                    )
                } catch (e: NoSuchMethodException) {
                    try {
                        audioSystemClass.getDeclaredMethod(
                            "setForceUse",
                            String::class.java,
                            Int::class.javaPrimitiveType
                        )
                    } catch (e2: Exception) {
                        Log.w(TAG, "[$reason] 无法找到 setForceUse 方法")
                        return false
                    }
                }

                setForceUse.invoke(null, 0, 1)  // FOR_COMMUNICATION, FORCE_SPEAKER
                setForceUse.invoke(null, 1, 1)  // FOR_MEDIA, FORCE_SPEAKER
                Log.d(TAG, "[$reason] AudioSystem.setForceUse 已执行")
            } catch (e: Throwable) {
                Log.w(TAG, "[$reason] AudioSystem.setForceUse 失败: ${e.message}")
            }

            // 3. 使用 AudioManager API
            try {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = true

                // 设置音量到最大
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVolume, 0)
                Log.d(TAG, "[$reason] isSpeakerphoneOn = true")
            } catch (e: Exception) {
                Log.e(TAG, "[$reason] isSpeakerphoneOn 失败: ${e.message}")
            }

            // 4. Android 12+ 使用 setCommunicationDevice
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                try {
                    audioManager.clearCommunicationDevice()
                    val availableDevices = audioManager.availableCommunicationDevices
                    val speakerDevice = availableDevices.firstOrNull {
                        it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    if (speakerDevice != null) {
                        audioManager.setCommunicationDevice(speakerDevice)
                        Log.d(TAG, "[$reason] setCommunicationDevice 已执行")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "[$reason] setCommunicationDevice 失败: ${e.message}")
                }
            }

            // 5. Root 设备使用系统命令
            if (RootUtil.isDeviceRooted()) {
                try {
                    val commands = listOf(
                        "cmd audio set-force-use communication speaker",
                        "cmd audio set-force-use media speaker",
                        "cmd audio set-mode in_call",
                        "cmd audio set-speakerphone-on true",
                        "service call audio 2 i32 3",
                        "service call audio 24 i32 1 i32 0"
                    )
                    RootUtil.executeMultipleWithRoot(commands)
                    Log.d(TAG, "[$reason] Root 命令已执行")
                } catch (e: Exception) {
                    Log.e(TAG, "[$reason] Root 命令失败: ${e.message}")
                }
            }

            // 验证结果
            @Suppress("DEPRECATION")
            val isSuccess = audioManager.isSpeakerphoneOn
            Log.d(TAG, "[$reason] 验证结果: $isSuccess")
            return isSuccess
        } catch (e: Exception) {
            Log.e(TAG, "[$reason] 启用扬声器失败: ${e.message}")
            return false
        }
    }

    /**
     * 禁用扬声器
     */
    private fun disableSpeaker(reason: String): Boolean {
        try {
            Log.d(TAG, "[$reason] 禁用扬声器...")

            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false

            // 重置为听筒模式
            try {
                val audioSystemClass = Class.forName("android.media.AudioSystem")
                val setForceUse = audioSystemClass.getDeclaredMethod(
                    "setForceUse",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                setForceUse.invoke(null, 0, 0)  // FOR_COMMUNICATION, FORCE_NONE
            } catch (e: Throwable) {
                Log.w(TAG, "[$reason] 重置 AudioSystem.setForceUse 失败: ${e.message}")
            }

            Log.d(TAG, "[$reason] 扬声器已禁用")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "[$reason] 禁用扬声器失败: ${e.message}")
            return false
        }
    }
}
