package com.callcenter.app.service

import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log
import com.callcenter.app.util.RootUtil
import com.callcenter.app.util.applyMaxSpeakerVolume
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 默认拨号应用场景下的自动免提服务（改进版）
 *
 * 改进点：
 * 1. 多阶段设置策略，适应不同 Android 版本
 * 2. 检查路由支持，避免设置不支持的设备
 * 3. 延迟设置，等待 CallAudioState 就绪
 * 4. 更详细的日志和验证
 * 5. 兼容 Android 12+ 的 setCommunicationDevice API
 *
 * 参考资料：
 * - https://developer.android.com/develop/connectivity/bluetooth/ble-audio/telecom-api-managed-calls
 * - https://cloud.tencent.com/developer/ask/sof/106927783
 * - https://blog.csdn.net/renshuguo123723/article/details/86633524
 */
class AutoSpeakerInCallService : InCallService() {

    companion object {
        private const val TAG = "AutoSpeakerInCall"

        // 音频路由常量 (AudioSystem.setForceUse)
        private const val FOR_COMMUNICATION = 0
        private const val FOR_MEDIA = 1
        private const val FOR_RINGING = 2
        private const val FORCE_SPEAKER = 1
        private const val FORCE_NONE = 0
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isSpeakerEnabled = AtomicBoolean(false)
    private var lastCallState = Call.STATE_NEW
    private var currentCall: Call? = null

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        currentCall = call
        Log.d(TAG, "========== onCallAdded ==========")
        Log.d(TAG, "Call: $call, State: ${call.state}, Details: ${call.details}")
        Log.d(TAG, "Call: $call, State: ${call.state}, Details: ${call.details}")

        // 注册状态回调
        call.registerCallback(callCallback)

        // 延迟 100ms，等待 CallAudioState 就绪
        handler.postDelayed({
            enableSpeakerWithMultiPhase("delayed_on_call_added")
        }, 100)

        // 再次延迟 500ms，确保在拨号阶段生效
        handler.postDelayed({
            enableSpeakerWithMultiPhase("delayed_dialing_phase")
        }, 500)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)
        if (currentCall == call) {
            currentCall = null
        }
        isSpeakerEnabled.set(false)
        Log.d(TAG, "========== onCallRemoved ==========")
    }

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            lastCallState = state

            Log.d(TAG, "========== onStateChanged ==========")
            Log.d(TAG, "State: $state, Call: $call")

            when (state) {
                Call.STATE_DIALING -> {
                    Log.d(TAG, "拨号中 - 启用扬声器")
                    enableSpeakerWithMultiPhase("state_dialing")
                }
                Call.STATE_CONNECTING -> {
                    Log.d(TAG, "连接中 - 启用扬声器")
                    enableSpeakerWithMultiPhase("state_connecting")
                }
                Call.STATE_ACTIVE -> {
                    Log.d(TAG, "通话接通 - 启用扬声器")
                    enableSpeakerWithMultiPhase("state_active")
                }
            }
        }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        Log.d(TAG, "========== onCallAudioStateChanged ==========")
        Log.d(TAG, "AudioState: $audioState")

        // 检查是否支持扬声器路由
        val supportsSpeaker = (audioState?.supportedRouteMask ?: 0) and CallAudioState.ROUTE_SPEAKER != 0
        Log.d(TAG, "Supports Speaker: $supportsSpeaker")

        // 如果支持扬声器但当前不是扬声器路由，则设置
        if (supportsSpeaker && audioState?.route != CallAudioState.ROUTE_SPEAKER) {
            Log.w(TAG, "音频路由不是扬声器，正在切换...")
            // 先尝试使用 InCallService API
            setAudioRoute(CallAudioState.ROUTE_SPEAKER)
            // 然后使用底层 API 强制设置
            enableSpeakerWithMultiPhase("audio_state_change")
        }
    }

    /**
     * 多阶段启用扬声器
     * 按照优先级顺序尝试不同的方法
     */
    private fun enableSpeakerWithMultiPhase(reason: String) {
        Log.d(TAG, "[$reason] 开始多阶段启用扬声器...")

        val audioManager = getSystemService(AudioManager::class.java)
        if (audioManager == null) {
            Log.e(TAG, "[$reason] AudioManager is null")
            return
        }

        // Phase 1: 检查并设置音频模式
        setAudioMode(audioManager, reason)

        // Phase 2: 尝试使用 InCallService API
        trySetAudioRoute(reason)

        // Phase 3: 使用 AudioSystem.setForceUse（系统级强制）
        forceAudioSystemToSpeaker(reason)

        // Phase 4: Android 12+ 使用 setCommunicationDevice
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setCommunicationDeviceToSpeaker(audioManager, reason)
        }

        // Phase 5: 使用 AudioManager API（辅助）
        setSpeakerphoneOn(audioManager, reason)

        // Phase 6: Root 设备使用系统命令
        if (RootUtil.isDeviceRooted()) {
            forceSpeakerWithRoot(reason)
        }

        // 验证结果
        val success = verifySpeakerEnabled(audioManager, reason)
        if (success) {
            isSpeakerEnabled.set(true)
            Log.d(TAG, "[$reason] ✓ 多阶段启用扬声器成功！")
        } else {
            Log.e(TAG, "[$reason] ✗ 多阶段启用扬声器失败！")
        }
    }

    /**
     * Phase 1: 设置音频模式
     */
    private fun setAudioMode(audioManager: AudioManager, reason: String) {
        try {
            val currentMode = audioManager.mode
            Log.d(TAG, "[$reason] 当前音频模式: $currentMode")

            // 尝试设置为通话模式
            audioManager.mode = AudioManager.MODE_IN_CALL
            Log.d(TAG, "[$reason] 音频模式已设置为 MODE_IN_CALL")
        } catch (e: Exception) {
            Log.e(TAG, "[$reason] 设置音频模式失败: ${e.message}")
        }
    }

    /**
     * Phase 2: 尝试使用 InCallService API
     */
    private fun trySetAudioRoute(reason: String) {
        try {
            setAudioRoute(CallAudioState.ROUTE_SPEAKER)
            Log.d(TAG, "[$reason] setAudioRoute(ROUTE_SPEAKER) 已执行")

            // 等待设置生效
            handler.postDelayed({
                Log.d(TAG, "[$reason] setAudioRoute 已执行")
            }, 50)
        } catch (e: Exception) {
            Log.e(TAG, "[$reason] setAudioRoute 失败: ${e.message}")
        }
    }

    /**
     * Phase 3: 使用 AudioSystem.setForceUse 强制系统级音频路由
     */
    private fun forceAudioSystemToSpeaker(reason: String) {
        try {
            val audioSystemClass = Class.forName("android.media.AudioSystem")

            // 尝试获取 setForceUse 方法
            val setForceUse = try {
                audioSystemClass.getDeclaredMethod(
                    "setForceUse",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
            } catch (e: NoSuchMethodException) {
                // 尝试其他方法签名
                try {
                    audioSystemClass.getDeclaredMethod(
                        "setForceUse",
                        String::class.java,
                        Int::class.javaPrimitiveType
                    )
                } catch (e2: Exception) {
                    Log.w(TAG, "[$reason] 无法找到 setForceUse 方法")
                    return
                }
            }

            // 强制通话场景使用扬声器
            setForceUse.invoke(null, FOR_COMMUNICATION, FORCE_SPEAKER)
            Log.d(TAG, "[$reason] AudioSystem.setForceUse(FOR_COMMUNICATION, FORCE_SPEAKER) 已执行")

            // 强制媒体场景使用扬声器（防止某些场景下的冲突）
            setForceUse.invoke(null, FOR_MEDIA, FORCE_SPEAKER)
            Log.d(TAG, "[$reason] AudioSystem.setForceUse(FOR_MEDIA, FORCE_SPEAKER) 已执行")

            // 强制响铃场景使用扬声器（确保来电也能外放）
            setForceUse.invoke(null, FOR_RINGING, FORCE_SPEAKER)
            Log.d(TAG, "[$reason] AudioSystem.setForceUse(FOR_RINGING, FORCE_SPEAKER) 已执行")

        } catch (e: Throwable) {
            Log.w(TAG, "[$reason] AudioSystem.setForceUse 调用失败: ${e.message}")
        }
    }

    /**
     * Phase 4: Android 12+ 使用 setCommunicationDevice
     */
    private fun setCommunicationDeviceToSpeaker(audioManager: AudioManager, reason: String) {
        try {
            // 先清除当前设备
            audioManager.clearCommunicationDevice()

            // 查找内置扬声器设备
            val availableDevices = audioManager.availableCommunicationDevices
            Log.d(TAG, "[$reason] 可用的通信设备: ${availableDevices.size}")
            for (device in availableDevices) {
                Log.d(TAG, "  - Device: ${device.type} (${getDeviceTypeName(device.type)})")
            }

            val speakerDevice = availableDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            }

            if (speakerDevice != null) {
                val routed = audioManager.setCommunicationDevice(speakerDevice)
                Log.d(TAG, "[$reason] setCommunicationDevice(TYPE_BUILTIN_SPEAKER) = $routed")

                // 验证设置
                val currentDevice = audioManager.communicationDevice
                Log.d(TAG, "[$reason] 当前通信设备: ${currentDevice?.type}")
            } else {
                Log.w(TAG, "[$reason] 未找到内置扬声器设备")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[$reason] setCommunicationDevice 失败: ${e.message}")
        }
    }

    /**
     * Phase 5: 使用 AudioManager API（辅助）
     */
    private fun setSpeakerphoneOn(audioManager: AudioManager, reason: String) {
        try {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true
            Log.d(TAG, "[$reason] audioManager.isSpeakerphoneOn = true")

            applyMaxSpeakerVolume(audioManager, TAG, reason)
        } catch (e: Exception) {
            Log.e(TAG, "[$reason] setSpeakerphoneOn 失败: ${e.message}")
        }
    }

    /**
     * Phase 6: Root 设备使用系统命令
     */
    private fun forceSpeakerWithRoot(reason: String) {
        try {
            val commands = listOf(
                // 强制音频路由到扬声器
                "cmd audio set-force-use communication speaker",
                "cmd audio set-force-use media speaker",
                "cmd audio set-force-use ring speaker",
                // 设置音频模式
                "cmd audio set-mode in_call",
                // 开启扬声器
                "cmd audio set-speakerphone-on true",
                // 旧版 service call audio
                "service call audio 2 i32 3",      // set mode to in_call
                "service call audio 24 i32 1 i32 0", // set speaker on
                "service call audio 28 i32 1",      // set speakerphone on
                // 系统设置
                "settings put system speakerphone_on 1",
                "settings put global audio_speaker_when_in_call 1"
            )

            val result = RootUtil.executeMultipleWithRoot(commands)
            Log.d(TAG, "[$reason] Root 命令执行结果: ${result.success}")
            if (result.output.isNotBlank()) {
                Log.d(TAG, "[$reason] Root 输出: ${result.output}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[$reason] Root 强制设置失败: ${e.message}")
        }
    }

    /**
     * 验证扬声器是否启用
     */
    private fun verifySpeakerEnabled(audioManager: AudioManager, reason: String): Boolean {
        try {
            // 检查 AudioManager 的状态
            @Suppress("DEPRECATION")
            val isSpeakerOn = audioManager.isSpeakerphoneOn
            val mode = audioManager.mode
            val streamVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)

            Log.d(TAG, "[$reason] 验证结果:")
            Log.d(TAG, "  - isSpeakerphoneOn: $isSpeakerOn")
            Log.d(TAG, "  - mode: $mode")
            Log.d(TAG, "  - 通话音量: $streamVolume / $maxVolume")

            // 检查通信设备（Android 12+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val commDevice = audioManager.communicationDevice
                Log.d(TAG, "  - communicationDevice: ${commDevice?.type}")
            }

            return isSpeakerOn
        } catch (e: Exception) {
            Log.e(TAG, "[$reason] 验证失败: ${e.message}")
            return false
        }
    }

    /**
     * 获取设备类型名称
     */
    private fun getDeviceTypeName(type: Int): String {
        return when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "听筒"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "扬声器"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "有线耳机"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "蓝牙耳机"
            AudioDeviceInfo.TYPE_BLE_HEADSET -> "蓝牙耳机 (BLE)"
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "麦克风"
            else -> "未知 ($type)"
        }
    }
}
