package com.callcenter.app.util

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 呼叫辅助工具类
 * 负责发起电话拨号和通话音频控制
 */
@Singleton
class CallHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "CallHelper"
    }

    private val audioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    /**
     * 拨打电话
     */
    fun makeCall(phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            Log.d(TAG, "makeCall: $phoneNumber")

            // 检查拨号权限
            if (android.content.pm.PackageManager.PERMISSION_GRANTED !=
                context.checkSelfPermission(android.Manifest.permission.CALL_PHONE)
            ) {
                Log.e(TAG, "缺少拨号权限 (CALL_PHONE)")
                throw SecurityException("缺少拨号权限")
            }

            context.startActivity(intent)
            Log.d(TAG, "拨号成功")

        } catch (e: SecurityException) {
            Log.e(TAG, "缺少拨号权限: ${e.message}")
            throw e
        } catch (e: android.content.ActivityNotFoundException) {
            Log.e(TAG, "未找到拨号应用: ${e.message}")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "拨号失败: ${e.message}")
            throw e
        }
    }

    /**
     * 开启免提（扬声器）
     * 需要在电话接通后调用
     */
    fun enableSpeakerphone(): Boolean {
        return try {
            // 关键：先设置音频模式为通话模式
            audioManager.mode = AudioManager.MODE_IN_CALL

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ 使用 setCommunicationDevice
                val devices = audioManager.availableCommunicationDevices
                val speakerDevice = devices.firstOrNull {
                    it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                }
                if (speakerDevice != null) {
                    val result = audioManager.setCommunicationDevice(speakerDevice)
                    Log.d(TAG, "enableSpeakerphone (API 31+): setCommunicationDevice result=$result")
                    if (result) {
                        applyMaxSpeakerVolume()
                        return true
                    }
                }
                // 如果新API失败，回退到旧方法
                Log.w(TAG, "enableSpeakerphone: 新API失败，回退到 setSpeakerphoneOn")
            }

            // Android 11 及以下，或新API失败时使用 setSpeakerphoneOn
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true
            applyMaxSpeakerVolume()
            Log.d(TAG, "enableSpeakerphone (legacy): isSpeakerphoneOn=${audioManager.isSpeakerphoneOn}")
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn
        } catch (e: Exception) {
            Log.e(TAG, "enableSpeakerphone 失败: ${e.message}", e)
            false
        }
    }

    /**
     * 设置最大扬声器音量
     */
    private fun applyMaxSpeakerVolume() {
        try {
            // 设置通话音量到最大
            val voiceMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            val voiceCurrent = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
            if (voiceCurrent < voiceMax) {
                audioManager.setStreamVolume(
                    AudioManager.STREAM_VOICE_CALL,
                    voiceMax,
                    AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE
                )
            }
            Log.d(TAG, "通话音量: $voiceCurrent -> $voiceMax")

            // 设置媒体音量到最大（兜底）
            val musicMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val musicCurrent = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (musicCurrent < musicMax) {
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    musicMax,
                    AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE
                )
            }
            Log.d(TAG, "媒体音量: $musicCurrent -> $musicMax")
        } catch (e: Exception) {
            Log.e(TAG, "设置音量失败: ${e.message}")
        }
    }

    /**
     * 关闭免提（扬声器），恢复听筒
     */
    fun disableSpeakerphone() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
                Log.d(TAG, "disableSpeakerphone: clearCommunicationDevice")
            } else {
                audioManager.isSpeakerphoneOn = false
                Log.d(TAG, "disableSpeakerphone: isSpeakerphoneOn=false")
            }
        } catch (e: Exception) {
            Log.e(TAG, "disableSpeakerphone 失败: ${e.message}", e)
        }
    }

    /**
     * 检查是否在通话中
     */
    fun isInCall(): Boolean {
        return try {
            audioManager.mode == AudioManager.MODE_IN_CALL ||
                    audioManager.mode == AudioManager.MODE_IN_COMMUNICATION
        } catch (e: Exception) {
            Log.e(TAG, "检查通话状态失败: ${e.message}")
            false
        }
    }
}
