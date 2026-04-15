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
 */
class AutoSpeakerInCallService : InCallService() {

    companion object {
        private const val TAG = "AutoSpeakerInCall"
        // 增强重试频率
        private val ROUTE_RETRY_DELAYS = listOf(
            0L, 100L, 250L, 450L, 700L, 1000L, 1500L, 2200L, 3000L, 4000L, 5500L, 7500L, 10000L
        )
    }

    private val handler = Handler(Looper.getMainLooper())

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            Log.d(TAG, "onStateChanged: $state")
            if (state == Call.STATE_DIALING || state == Call.STATE_CONNECTING || state == Call.STATE_ACTIVE) {
                boostSpeakerRoute("call_state_$state")
            }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "onCallAdded")
        call.registerCallback(callCallback)
        boostSpeakerRoute("on_call_added")
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)
        Log.d(TAG, "onCallRemoved")
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        Log.d(TAG, "onCallAudioStateChanged: route=${audioState?.route}")
        if (audioState?.route != CallAudioState.ROUTE_SPEAKER) {
            boostSpeakerRoute("audio_state_changed")
        }
    }

    private fun boostSpeakerRoute(reason: String) {
        ROUTE_RETRY_DELAYS.forEachIndexed { index, delayMs ->
            handler.postDelayed(
                {
                    forceSpeakerRoute("$reason#$index")
                },
                delayMs
            )
        }
    }

    private fun forceSpeakerRoute(reason: String) {
        try {
            setAudioRoute(CallAudioState.ROUTE_SPEAKER)

            val audioManager = getSystemService(AudioManager::class.java)
            audioManager?.mode = AudioManager.MODE_IN_CALL

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && audioManager != null) {
                val speakerDevice = audioManager.availableCommunicationDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                }
                if (speakerDevice != null) {
                    val routed = audioManager.setCommunicationDevice(speakerDevice)
                    Log.d(TAG, "[$reason] setCommunicationDevice=$routed")
                }
            }

            @Suppress("DEPRECATION")
            audioManager?.isSpeakerphoneOn = true

            if (RootUtil.isDeviceRooted()) {
                RootUtil.forceEnableSpeaker()
            }

            Log.d(TAG, "[$reason] 已强制切换到扬声器")
        } catch (e: Throwable) {
            Log.e(TAG, "[$reason] 切换扬声器失败: ${e.message}")
        }
    }
}
