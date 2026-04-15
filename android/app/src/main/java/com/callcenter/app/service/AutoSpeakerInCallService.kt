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

/**
 * 默认拨号应用场景下的自动免提服务。
 *
 * 当系统把外呼/通话交给本应用的 InCallService 后，
 * 这里可以比普通 AudioManager 更稳定地控制通话音频路由。
 *
 * 一次性成功原理：
 * 1. 在 onCallAdded 时立即设置音频路由
 * 2. 使用 AudioSystem.setForceUse 强制路由到扬声器
 * 3. Root 权限下使用系统级命令确保设置生效
 */
class AutoSpeakerInCallService : InCallService() {

    companion object {
        private const val TAG = "AutoSpeakerInCall"
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isSpeakerAlreadySet = false

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            Log.d(TAG, "onStateChanged: $state")

            // 在每个关键状态都确认一次扬声器状态
            when (state) {
                Call.STATE_DIALING -> {
                    Log.d(TAG, "拨号中 - 确认扬声器")
                    ensureSpeakerOn("state_dialing")
                }
                Call.STATE_CONNECTING -> {
                    Log.d(TAG, "连接中 - 确认扬声器")
                    ensureSpeakerOn("state_connecting")
                }
                Call.STATE_ACTIVE -> {
                    Log.d(TAG, "通话接通 - 确认扬声器")
                    ensureSpeakerOn("state_active")
                }
            }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "onCallAdded")
        call.registerCallback(callCallback)

        // 一次性设置：在通话添加时立即设置扬声器
        // 使用系统级强制命令，不需要重试
        forceSpeakerOnce("on_call_added")
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)
        isSpeakerAlreadySet = false
        Log.d(TAG, "onCallRemoved")
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        Log.d(TAG, "onCallAudioStateChanged: route=${audioState?.route}, supported=${audioState?.supportedRouteMask}")

        // 如果音频路由不是扬声器，立即重新设置
        if (audioState?.route != CallAudioState.ROUTE_SPEAKER) {
            Log.w(TAG, "音频路由被重置，立即恢复到扬声器")
            setAudioRoute(CallAudioState.ROUTE_SPEAKER)
            ensureSpeakerOn("audio_state_changed")
        }
    }

    /**
     * 一次性强制设置扬声器（无重试）
     *
     * 原理：
     * 1. 使用 InCallService 的 setAudioRoute API（最高优先级）
     * 2. 使用 AudioSystem.setForceUse 强制系统级音频路由
     * 3. Root 设备使用系统命令直接修改音频路由
     *
     * 这种方法不需要重试，因为：
     * - InCallService 的 setAudioRoute 在音频路由建立的最早期执行
     * - AudioSystem.setForceUse 是系统级 API，无法被应用重置
     * - Root 命令直接操作音频子系统
     */
    private fun forceSpeakerOnce(reason: String) {
        if (isSpeakerAlreadySet) {
            Log.d(TAG, "[$reason] 扬声器已设置，跳过")
            return
        }

        Log.d(TAG, "[$reason] 开始一次性强制设置扬声器...")
        try {
            // 1. 使用 InCallService 的 setAudioRoute API（优先级最高）
            setAudioRoute(CallAudioState.ROUTE_SPEAKER)
            Log.d(TAG, "[$reason] setAudioRoute(ROUTE_SPEAKER) 已执行")

            // 2. 设置音频模式
            val audioManager = getSystemService(AudioManager::class.java)
            audioManager?.mode = AudioManager.MODE_IN_CALL
            Log.d(TAG, "[$reason] audioManager.mode = MODE_IN_CALL")

            // 3. 强制使用 AudioSystem.setForceUse（系统级 API）
            // 这个 API 的设置会强制音频路由到扬声器，应用无法通过 AudioManager 重置
            forceAudioSystemSpeaker(reason)

            // 4. 设置通信设备（Android 12+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && audioManager != null) {
                val speakerDevice = audioManager.availableCommunicationDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                }
                if (speakerDevice != null) {
                    val routed = audioManager.setCommunicationDevice(speakerDevice)
                    Log.d(TAG, "[$reason] setCommunicationDevice = $routed")
                }
            }

            // 5. 设置 AudioManager 的扬声器标志（作为辅助）
            @Suppress("DEPRECATION")
            audioManager?.isSpeakerphoneOn = true
            Log.d(TAG, "[$reason] audioManager.isSpeakerphoneOn = true")

            // 6. Root 设备使用系统命令直接修改音频路由
            if (RootUtil.isDeviceRooted()) {
                forceSpeakerWithRoot(reason)
            }

            // 7. 验证设置结果
            @Suppress("DEPRECATION")
            val isSpeakerOn = audioManager?.isSpeakerphoneOn == true
            if (isSpeakerOn) {
                isSpeakerAlreadySet = true
                Log.d(TAG, "[$reason] ✓ 一次性设置成功！扬声器已开启")
            } else {
                Log.e(TAG, "[$reason] ✗ 一次性设置失败，需要启用持续监控")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "[$reason] 一次性设置失败: ${e.message}", e)
        }
    }

    /**
     * 确认扬声器状态（如果未开启则重新开启）
     */
    private fun ensureSpeakerOn(reason: String) {
        try {
            val audioManager = getSystemService(AudioManager::class.java)
            @Suppress("DEPRECATION")
            val isSpeakerOn = audioManager?.isSpeakerphoneOn == true

            if (!isSpeakerOn) {
                Log.w(TAG, "[$reason] 检测到扬声器未开启，重新设置")
                forceSpeakerOnce("$reason#retry")
            } else {
                Log.d(TAG, "[$reason] 扬声器状态正常")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[$reason] 检查扬声器状态失败: ${e.message}")
        }
    }

    /**
     * 使用 AudioSystem.setForceUse 强制系统级音频路由
     *
     * 这是 Android 的隐藏 API，可以直接控制音频子系统的路由策略
     * FOR_COMMUNICATION = 0: 通话场景
     * FOR_MEDIA = 1: 媒体场景
     * FORCE_SPEAKER = 1: 强制使用扬声器
     */
    private fun forceAudioSystemSpeaker(reason: String) {
        try {
            val audioSystemClass = Class.forName("android.media.AudioSystem")
            val setForceUse = audioSystemClass.getDeclaredMethod(
                "setForceUse",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )

            // 强制通话场景使用扬声器
            setForceUse.invoke(null, 0, 1)  // FOR_COMMUNICATION, FORCE_SPEAKER
            // 强制媒体场景使用扬声器（防止某些场景下的冲突）
            setForceUse.invoke(null, 1, 1)  // FOR_MEDIA, FORCE_SPEAKER

            Log.d(TAG, "[$reason] AudioSystem.setForceUse 已执行（通话+媒体场景）")
        } catch (e: Throwable) {
            Log.w(TAG, "[$reason] AudioSystem.setForceUse 调用失败: ${e.message}")
        }
    }

    /**
     * Root 设备使用系统命令强制设置扬声器
     */
    private fun forceSpeakerWithRoot(reason: String) {
        try {
            val commands = listOf(
                // 强制音频路由
                "cmd audio set-force-use communication speaker",
                "cmd audio set-force-use media speaker",
                // 设置音频模式
                "cmd audio set-mode in_call",
                // 开启扬声器
                "cmd audio set-speakerphone-on true",
                // 旧版兼容
                "service call audio 2 i32 3",  // set mode to in_call
                "service call audio 24 i32 1 i32 0",  // set speaker on
                // 系统设置
                "settings put system speakerphone_on 1",
                "settings put global audio_speaker_when_in_call 1"
            )

            val result = RootUtil.executeMultipleWithRoot(commands)
            Log.d(TAG, "[$reason] Root 强制设置结果: ${result.success}")
            if (result.success) {
                Log.d(TAG, "[$reason] Root 命令执行成功")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[$reason] Root 强制设置失败: ${e.message}")
        }
    }
}
