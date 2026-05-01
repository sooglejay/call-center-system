package com.callcenter.app.service

import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log
import com.callcenter.app.util.DebugLogger
import com.callcenter.app.util.RootUtil
import kotlinx.coroutines.*

/**
 * 自动扬声器 InCallService
 */
class AutoSpeakerInCallService : InCallService() {

    companion object {
        private const val TAG = "AutoSpeaker"
        private const val MONITOR_INTERVAL = 500L
        private const val MAX_MONITOR_DURATION = 600_000L
        
        var isServiceActive = false
            private set
        
        // 当前通话实例，用于挂断
        private var activeCall: Call? = null
        
        /**
         * 挂断当前通话
         * @return 是否成功发起挂断请求
         */
        fun disconnectCurrentCall(): Boolean {
            val call = activeCall
            if (call == null) {
                DebugLogger.log("[InCallService] ✗ 没有活跃的通话可挂断")
                return false
            }
            
            return try {
                call.disconnect()
                DebugLogger.log("[InCallService] ✓ 已发送挂断命令")
                true
            } catch (e: Exception) {
                DebugLogger.log("[InCallService] ✗ 挂断失败: ${e.message}")
                false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "========== InCallService onCreate ==========")
        DebugLogger.log("[InCallService] onCreate - 服务已创建")
        isServiceActive = true
    }

    override fun onDestroy() {
        Log.d(TAG, "========== InCallService onDestroy ==========")
        DebugLogger.log("[InCallService] onDestroy - 服务已销毁")
        isServiceActive = false
        stopMonitoring()
        serviceScope.cancel()
        super.onDestroy()
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var monitorJob: Job? = null
    private var currentCall: Call? = null
    private var targetSpeakerOn = true
    private var lastRoute: Int = CallAudioState.ROUTE_EARPIECE

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            val stateStr = stateToString(state)
            Log.d(TAG, "Call state changed: $stateStr")
            DebugLogger.log("[InCallService] 通话状态变化: $stateStr")
            
            when (state) {
                Call.STATE_DIALING -> forceSpeakerOn("state_dialing")
                Call.STATE_CONNECTING -> forceSpeakerOn("state_connecting")
                Call.STATE_ACTIVE -> {
                    forceSpeakerOn("state_active")
                    startMonitoring()
                }
                Call.STATE_DISCONNECTED -> stopMonitoring()
            }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "========== onCallAdded ==========")
        DebugLogger.log("[InCallService] onCallAdded - 有新通话加入")
        
        currentCall = call
        activeCall = call  // 保存到静态变量，供挂断使用
        call.registerCallback(callCallback)
        targetSpeakerOn = true
        forceSpeakerOn("on_call_added")
    }

    override fun onCallRemoved(call: Call) {
        Log.d(TAG, "========== onCallRemoved ==========")
        DebugLogger.log("[InCallService] onCallRemoved - 通话已移除")
        
        call.unregisterCallback(callCallback)
        currentCall = null
        activeCall = null  // 清除静态变量
        stopMonitoring()
        super.onCallRemoved(call)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        
        val route = audioState?.route ?: CallAudioState.ROUTE_EARPIECE
        val routeStr = routeToString(route)
        Log.d(TAG, "onCallAudioStateChanged: route=$routeStr")
        DebugLogger.log("[InCallService] 音频路由变化: $routeStr")
        
        if (targetSpeakerOn && route != CallAudioState.ROUTE_SPEAKER) {
            Log.w(TAG, "⚠️ 检测到音频路由被系统重置！")
            DebugLogger.log("[InCallService] ⚠️ 音频路由被系统重置，尝试修复")
            forceSpeakerOn("audio_reset_detected")
        }
        
        lastRoute = route
    }

    private fun forceSpeakerOn(reason: String) {
        DebugLogger.log("[InCallService] forceSpeakerOn - 原因: $reason")
        
        try {
            setAudioRoute(CallAudioState.ROUTE_SPEAKER)
            DebugLogger.log("[InCallService] ✓ setAudioRoute(ROUTE_SPEAKER)")

            forceAudioSystemSpeaker(reason)

            val audioManager = getSystemService(AudioManager::class.java)
            audioManager?.let { am ->
                am.mode = AudioManager.MODE_IN_CALL
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = true
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val speakerDevice = am.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    speakerDevice?.let { device ->
                        am.setCommunicationDevice(device)
                        DebugLogger.log("[InCallService] ✓ setCommunicationDevice")
                    }
                }
                
                val maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVolume, 0)
            }

            if (RootUtil.isDeviceRooted()) {
                forceSpeakerWithRoot(reason)
            }

            verifySpeakerState(reason)

        } catch (e: Exception) {
            Log.e(TAG, "[$reason] 强制开启扬声器失败: ${e.message}", e)
            DebugLogger.log("[InCallService] ✗ 强制开启扬声器失败: ${e.message}")
        }
    }

    private fun forceAudioSystemSpeaker(reason: String) {
        try {
            val audioSystemClass = Class.forName("android.media.AudioSystem")
            val setForceUse = audioSystemClass.getDeclaredMethod(
                "setForceUse",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            setForceUse.invoke(null, 0, 1)
            setForceUse.invoke(null, 1, 1)
            setForceUse.invoke(null, 2, 1)
            setForceUse.invoke(null, 3, 1)
            DebugLogger.log("[InCallService] ✓ AudioSystem.setForceUse")
        } catch (e: Throwable) {
            DebugLogger.log("[InCallService] AudioSystem.setForceUse 失败: ${e.message}")
        }
    }

    private fun forceSpeakerWithRoot(reason: String) {
        try {
            val commands = listOf(
                "cmd audio set-force-use communication speaker",
                "cmd audio set-force-use media speaker"
            )
            val result = RootUtil.executeMultipleWithRoot(commands)
            if (result.success) {
                DebugLogger.log("[InCallService] ✓ Root 命令执行成功")
            }
        } catch (e: Exception) {
            DebugLogger.log("[InCallService] Root 命令失败: ${e.message}")
        }
    }

    private fun verifySpeakerState(reason: String) {
        try {
            val audioManager = getSystemService(AudioManager::class.java)
            @Suppress("DEPRECATION")
            val isSpeakerOn = audioManager?.isSpeakerphoneOn == true
            val mode = audioManager?.mode ?: -1
            
            if (isSpeakerOn) {
                DebugLogger.log("[InCallService] ✓✓✓ 扬声器已成功开启 (mode=$mode)")
            } else {
                DebugLogger.log("[InCallService] ✗✗✗ 扬声器开启失败！(mode=$mode)")
            }
        } catch (e: Exception) {
            DebugLogger.log("[InCallService] 验证扬声器状态失败: ${e.message}")
        }
    }

    private fun startMonitoring() {
        if (monitorJob?.isActive == true) return
        DebugLogger.log("[InCallService] 启动音频路由监控")
        
        monitorJob = serviceScope.launch {
            val startTime = System.currentTimeMillis()
            
            while (isActive && targetSpeakerOn) {
                if (System.currentTimeMillis() - startTime > MAX_MONITOR_DURATION) break
                
                try {
                    val audioManager = getSystemService(AudioManager::class.java)
                    @Suppress("DEPRECATION")
                    val isSpeakerOn = audioManager?.isSpeakerphoneOn == true
                    
                    if (!isSpeakerOn && targetSpeakerOn) {
                        DebugLogger.log("[InCallService] 监控检测到扬声器被关闭，重新设置")
                        withContext(Dispatchers.Main) {
                            forceSpeakerOn("monitor_fix")
                        }
                    }
                } catch (e: Exception) {
                    DebugLogger.log("[InCallService] 监控检查失败: ${e.message}")
                }
                
                delay(MONITOR_INTERVAL)
            }
        }
    }

    private fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        targetSpeakerOn = false
        DebugLogger.log("[InCallService] 停止音频路由监控")
    }

    private fun stateToString(state: Int): String = when (state) {
        Call.STATE_NEW -> "NEW"
        Call.STATE_DIALING -> "DIALING"
        Call.STATE_CONNECTING -> "CONNECTING"
        Call.STATE_ACTIVE -> "ACTIVE"
        Call.STATE_HOLDING -> "HOLDING"
        Call.STATE_DISCONNECTED -> "DISCONNECTED"
        else -> "UNKNOWN($state)"
    }

    private fun routeToString(route: Int): String = when (route) {
        CallAudioState.ROUTE_EARPIECE -> "EARPIECE"
        CallAudioState.ROUTE_SPEAKER -> "SPEAKER"
        CallAudioState.ROUTE_WIRED_HEADSET -> "WIRED_HEADSET"
        CallAudioState.ROUTE_BLUETOOTH -> "BLUETOOTH"
        else -> "UNKNOWN($route)"
    }
}
