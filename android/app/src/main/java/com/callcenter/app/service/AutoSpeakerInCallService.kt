package com.callcenter.app.service

import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log
import com.callcenter.app.util.RootUtil
import kotlinx.coroutines.*

/**
 * 自动扬声器 InCallService
 * 
 * 设计原理：
 * 1. InCallService.setAudioRoute() 是电信框架级API，优先级最高
 * 2. 系统的 AudioService 会在通话状态变化时重置音频路由
 * 3. 必须在 onCallAudioStateChanged 中检测并修复被重置的路由
 * 4. 使用协程持续监控，防止后台被重置
 * 
 * 关键时机：
 * - onCallAdded: 通话添加时，立即设置（最早时机）
 * - onCallAudioStateChanged: 音频路由变化时，检测并修复
 * - Call.STATE_ACTIVE: 通话接通时，再次确认
 * 
 * 为什么之前不稳定：
 * - 只在 onCallAdded 设置一次，系统后续重置无法修复
 * - 没有持续监控机制
 * - 没有处理所有状态变化
 */
class AutoSpeakerInCallService : InCallService() {

    companion object {
        private const val TAG = "AutoSpeaker"
        private const val MONITOR_INTERVAL = 500L  // 监控间隔 500ms
        private const val MAX_MONITOR_DURATION = 600_000L  // 最长监控 10 分钟
        
        // 用于外部检查 InCallService 是否已激活
        var isServiceActive = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "========== InCallService onCreate ==========")
        isServiceActive = true
    }

    override fun onDestroy() {
        Log.d(TAG, "========== InCallService onDestroy ==========")
        isServiceActive = false
        stopMonitoring()
        serviceScope.cancel()
        super.onDestroy()
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var monitorJob: Job? = null
    private var currentCall: Call? = null
    
    // 扬声器目标状态
    private var targetSpeakerOn = true
    
    // 上次检测到的路由状态
    private var lastRoute: Int = CallAudioState.ROUTE_EARPIECE

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            Log.d(TAG, "Call state changed: ${stateToString(state)}")
            
            when (state) {
                Call.STATE_DIALING -> {
                    // 拨号中：立即设置扬声器
                    forceSpeakerOn("state_dialing")
                }
                Call.STATE_CONNECTING -> {
                    // 连接中：再次确认
                    forceSpeakerOn("state_connecting")
                }
                Call.STATE_ACTIVE -> {
                    // 通话接通：关键时机，系统可能刚重置路由
                    forceSpeakerOn("state_active")
                    // 启动持续监控
                    startMonitoring()
                }
                Call.STATE_DISCONNECTED -> {
                    // 通话结束：停止监控
                    stopMonitoring()
                }
            }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "========== onCallAdded ==========")
        
        currentCall = call
        call.registerCallback(callCallback)
        targetSpeakerOn = true
        
        // 立即设置扬声器（最早时机）
        forceSpeakerOn("on_call_added")
    }

    override fun onCallRemoved(call: Call) {
        Log.d(TAG, "========== onCallRemoved ==========")
        
        call.unregisterCallback(callCallback)
        currentCall = null
        stopMonitoring()
        
        super.onCallRemoved(call)
    }

    /**
     * 关键：监听音频路由变化
     * 这是系统重置音频路由的时机，必须在这里修复
     */
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        
        val route = audioState?.route ?: CallAudioState.ROUTE_EARPIECE
        Log.d(TAG, "onCallAudioStateChanged: route=${routeToString(route)}, lastRoute=${routeToString(lastRoute)}")
        
        // 检测路由是否被系统重置（不是扬声器）
        if (targetSpeakerOn && route != CallAudioState.ROUTE_SPEAKER) {
            Log.w(TAG, "⚠️ 检测到音频路由被系统重置！route=${routeToString(route)}，立即修复")
            forceSpeakerOn("audio_reset_detected")
        }
        
        lastRoute = route
    }

    /**
     * 强制开启扬声器（核心方法）
     * 使用多层策略确保成功
     */
    private fun forceSpeakerOn(reason: String) {
        Log.d(TAG, "[$reason] 开始强制开启扬声器...")
        
        try {
            // 第一层：InCallService.setAudioRoute（最高优先级）
            setAudioRoute(CallAudioState.ROUTE_SPEAKER)
            Log.d(TAG, "[$reason] ✓ setAudioRoute(ROUTE_SPEAKER)")

            // 第二层：AudioSystem.setForceUse（系统级强制）
            forceAudioSystemSpeaker(reason)

            // 第三层：AudioManager API
            val audioManager = getSystemService(AudioManager::class.java)
            audioManager?.let { am ->
                am.mode = AudioManager.MODE_IN_CALL
                
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = true
                
                // 第四层：Android 12+ setCommunicationDevice
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val speakerDevice = am.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    speakerDevice?.let { device ->
                        am.setCommunicationDevice(device)
                        Log.d(TAG, "[$reason] ✓ setCommunicationDevice")
                    }
                }
                
                // 设置最大音量
                val maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVolume, 0)
            }

            // 第五层：Root 命令（可选）
            if (RootUtil.isDeviceRooted()) {
                forceSpeakerWithRoot(reason)
            }

            // 验证结果
            verifySpeakerState(reason)

        } catch (e: Exception) {
            Log.e(TAG, "[$reason] 强制开启扬声器失败: ${e.message}", e)
        }
    }

    /**
     * 使用 AudioSystem.setForceUse 强制音频路由
     * 这是隐藏API，优先级高于 AudioManager
     */
    private fun forceAudioSystemSpeaker(reason: String) {
        try {
            val audioSystemClass = Class.forName("android.media.AudioSystem")
            val setForceUse = audioSystemClass.getDeclaredMethod(
                "setForceUse",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            
            // FOR_COMMUNICATION = 0, FORCE_SPEAKER = 1
            setForceUse.invoke(null, 0, 1)
            // FOR_MEDIA = 1, FORCE_SPEAKER = 1
            setForceUse.invoke(null, 1, 1)
            // FOR_SYSTEM = 2, FORCE_SPEAKER = 1（某些系统需要）
            setForceUse.invoke(null, 2, 1)
            // FOR_RING = 3, FORCE_SPEAKER = 1
            setForceUse.invoke(null, 3, 1)
            
            Log.d(TAG, "[$reason] ✓ AudioSystem.setForceUse (通话+媒体+系统+铃声)")
        } catch (e: Throwable) {
            Log.w(TAG, "[$reason] AudioSystem.setForceUse 失败: ${e.message}")
        }
    }

    /**
     * Root 设备使用系统命令
     */
    private fun forceSpeakerWithRoot(reason: String) {
        try {
            val commands = listOf(
                "cmd audio set-force-use communication speaker",
                "cmd audio set-force-use media speaker",
                "cmd audio set-force-use system speaker",
                "cmd audio set-mode in_call"
            )
            val result = RootUtil.executeMultipleWithRoot(commands)
            if (result.success) {
                Log.d(TAG, "[$reason] ✓ Root 命令执行成功")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[$reason] Root 命令失败: ${e.message}")
        }
    }

    /**
     * 验证扬声器状态
     */
    private fun verifySpeakerState(reason: String) {
        try {
            val audioManager = getSystemService(AudioManager::class.java)
            @Suppress("DEPRECATION")
            val isSpeakerOn = audioManager?.isSpeakerphoneOn == true
            val mode = audioManager?.mode ?: -1
            
            if (isSpeakerOn) {
                Log.d(TAG, "[$reason] ✓✓✓ 扬声器已成功开启 (mode=$mode)")
            } else {
                Log.e(TAG, "[$reason] ✗✗✗ 扬声器开启失败！可能需要持续监控")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[$reason] 验证扬声器状态失败: ${e.message}")
        }
    }

    /**
     * 启动持续监控
     * 防止系统在后台重置音频路由
     */
    private fun startMonitoring() {
        if (monitorJob?.isActive == true) return
        
        Log.d(TAG, "启动音频路由持续监控...")
        
        monitorJob = serviceScope.launch {
            val startTime = System.currentTimeMillis()
            
            while (isActive && targetSpeakerOn) {
                // 检查是否超时
                if (System.currentTimeMillis() - startTime > MAX_MONITOR_DURATION) {
                    Log.d(TAG, "监控超时，停止监控")
                    break
                }
                
                // 检查扬声器状态
                try {
                    val audioManager = getSystemService(AudioManager::class.java)
                    @Suppress("DEPRECATION")
                    val isSpeakerOn = audioManager?.isSpeakerphoneOn == true
                    
                    if (!isSpeakerOn && targetSpeakerOn) {
                        Log.w(TAG, "监控检测到扬声器被关闭，重新设置")
                        withContext(Dispatchers.Main) {
                            forceSpeakerOn("monitor_fix")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "监控检查失败: ${e.message}")
                }
                
                delay(MONITOR_INTERVAL)
            }
            
            Log.d(TAG, "音频路由监控已停止")
        }
    }

    /**
     * 停止监控
     */
    private fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        targetSpeakerOn = false
        Log.d(TAG, "停止音频路由监控")
    }

    /**
     * 状态转字符串
     */
    private fun stateToString(state: Int): String {
        return when (state) {
            Call.STATE_NEW -> "NEW"
            Call.STATE_DIALING -> "DIALING"
            Call.STATE_CONNECTING -> "CONNECTING"
            Call.STATE_ACTIVE -> "ACTIVE"
            Call.STATE_HOLDING -> "HOLDING"
            Call.STATE_DISCONNECTED -> "DISCONNECTED"
            Call.STATE_PULLING_CALL -> "PULLING"
            else -> "UNKNOWN($state)"
        }
    }

    /**
     * 路由转字符串
     */
    private fun routeToString(route: Int): String {
        return when (route) {
            CallAudioState.ROUTE_EARPIECE -> "EARPIECE"
            CallAudioState.ROUTE_SPEAKER -> "SPEAKER"
            CallAudioState.ROUTE_WIRED_HEADSET -> "WIRED_HEADSET"
            CallAudioState.ROUTE_BLUETOOTH -> "BLUETOOTH"
            CallAudioState.ROUTE_WIRED_OR_EARPIECE -> "WIRED_OR_EARPIECE"
            else -> "UNKNOWN($route)"
        }
    }
}
