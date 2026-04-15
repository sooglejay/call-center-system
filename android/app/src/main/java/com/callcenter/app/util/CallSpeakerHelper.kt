package com.callcenter.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.telephony.TelephonyManager
import android.telephony.TelephonyCallback
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * 轻量级通话自动免提助手
 * 使用 TelephonyCallback 监听通话状态，在通话接通时自动开启扬声器
 */
class CallSpeakerHelper(private val context: Context) {

    companion object {
        private const val TAG = "CallSpeakerHelper"
    }

    private val telephonyManager = context.getSystemService(TelephonyManager::class.java)
    private val audioManager = context.getSystemService(AudioManager::class.java)

    private val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) {
            Log.d(TAG, "通话状态变更: $state")
            
            if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
                Log.d(TAG, "通话已接通，自动开启扬声器")
                enableSpeaker()
            }
        }
    }

    private var isListening = false

    /**
     * 启动通话状态监听
     */
    fun start() {
        if (isListening) {
            Log.w(TAG, "已经在监听中，无需重复启动")
            return
        }

        // 检查必需权限
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "缺少必需权限，无法启动监听")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                telephonyManager?.registerTelephonyCallback(context.mainExecutor, callback)
                isListening = true
                Log.d(TAG, "通话状态监听已启动")
            } catch (e: Exception) {
                Log.e(TAG, "启动监听失败: ${e.message}")
            }
        } else {
            Log.w(TAG, "Android 版本过低，不支持 TelephonyCallback (需要 API 31+)")
        }
    }

    /**
     * 停止通话状态监听
     */
    fun stop() {
        if (!isListening) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                telephonyManager?.unregisterTelephonyCallback(callback)
                isListening = false
                Log.d(TAG, "通话状态监听已停止")
            } catch (e: Exception) {
                Log.e(TAG, "停止监听失败: ${e.message}")
            }
        }
    }

    /**
     * 手动开启扬声器
     */
    fun enableSpeaker() {
        try {
            audioManager?.mode = AudioManager.MODE_IN_CALL
            @Suppress("DEPRECATION")
            audioManager?.isSpeakerphoneOn = true
            
            // 设置最大音量
            applyMaxSpeakerVolume(audioManager, TAG, "manual")
            
            Log.d(TAG, "扬声器已开启")
        } catch (e: Exception) {
            Log.e(TAG, "开启扬声器失败: ${e.message}")
        }
    }

    /**
     * 检查是否已开启扬声器
     */
    @Suppress("DEPRECATION")
    fun isSpeakerEnabled(): Boolean {
        return audioManager?.isSpeakerphoneOn == true
    }

    /**
     * 检查必需权限
     */
    private fun hasRequiredPermissions(): Boolean {
        val permissions = listOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE
        )

        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 获取当前监听状态
     */
    fun isListening(): Boolean = isListening
}
