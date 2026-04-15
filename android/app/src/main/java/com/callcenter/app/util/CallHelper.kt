package com.callcenter.app.util

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.callcenter.app.service.CallStateMonitorService
import com.callcenter.app.service.CallStateMonitorService.Companion.forceSpeaker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 呼叫辅助工具类（改进版）
 *
 * 改进点：
 * 1. 拨号后自动启用扬声器（17 次重试）
 * 2. 多阶段启用策略（6 层防护）
 * 3. 支持持续监控和自动修复
 * 4. 更详细的日志输出
 */
@Singleton
class CallHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "CallHelper"
    }

    /**
     * 拨打电话并自动启用扬声器
     */
    fun makeCall(phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            // 检查是否是默认拨号应用
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            val isDefaultDialer = telecomManager?.defaultDialerPackage == context.packageName

            Log.d(TAG, "========== makeCall ==========")
            Log.d(TAG, "PhoneNumber: $phoneNumber")
            Log.d(TAG, "IsDefaultDialer: $isDefaultDialer")

            // 如果是默认拨号应用，使用 TelecomManager API（兼容性更好）
            if (isDefaultDialer && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                makeCallWithTelecomManager(phoneNumber)
            } else {
                // 否则使用标准 Intent
                context.startActivity(intent)
            }

            // 启动扬声器服务（17 次重试）
            enableSpeakerWithRetry(17)

        } catch (e: SecurityException) {
            Log.e(TAG, "缺少拨号权限: ${e.message}")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "拨号失败: ${e.message}")
            throw e
        }
    }

    /**
     * 使用 TelecomManager 拨号（Android 6.0+）
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun makeCallWithTelecomManager(phoneNumber: String) {
        try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val phoneAccountHandle = telecomManager.phoneAccountsForUser(
                android.os.Process.myUserHandle()
            ).firstOrNull()

            if (phoneAccountHandle != null) {
                val uri = Uri.fromParts("tel", phoneNumber, null)
                telecomManager.placeCall(uri, null)
                Log.d(TAG, "使用 TelecomManager 拨号成功")
            } else {
                // 降级到 Intent
                val intent = Intent(Intent.ACTION_CALL).apply {
                    data = Uri.parse("tel:$phoneNumber")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                Log.d(TAG, "降级到 Intent 拨号")
            }
        } catch (e: Exception) {
            Log.e(TAG, "TelecomManager 拨号失败: ${e.message}")
            // 降级到 Intent
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    /**
     * 启用扬声器（17 次重试）
     * 间隔：50ms、100ms、150ms... 直到 850ms
     */
    private fun enableSpeakerWithRetry(maxRetries: Int = 17) {
        Log.d(TAG, "开始启用扬声器（重试次数: $maxRetries）")

        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var retryCount = 0

        fun tryEnable() {
            if (retryCount >= maxRetries) {
                Log.w(TAG, "已达到最大重试次数 ($maxRetries)，停止重试")
                return
            }

            val delay = (retryCount + 1) * 50L  // 50ms, 100ms, 150ms, ..., 850ms
            retryCount++

            handler.postDelayed({
                Log.d(TAG, "第 $retryCount/$maxRetries 次尝试启用扬声器（延迟: ${delay}ms）")
                enableSpeakerWithMultiPhase("retry_$retryCount")

                // 如果重试次数未达到最大值，继续重试
                if (retryCount < maxRetries) {
                    tryEnable()
                }
            }, delay)
        }

        // 启动持续监控
        CallStateMonitorService.startMonitoring(context)

        // 立即尝试一次
        enableSpeakerWithMultiPhase("retry_0")

        // 开始重试
        tryEnable()
    }

    /**
     * 多阶段启用扬声器（6 层防护）
     */
    private fun enableSpeakerWithMultiPhase(reason: String) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            Log.d(TAG, "[$reason] 开始多阶段启用扬声器...")

            // Phase 1: 设置音频模式
            try {
                audioManager.mode = AudioManager.MODE_IN_CALL
                Log.d(TAG, "[$reason] Phase 1: 音频模式已设置为 MODE_IN_CALL")
            } catch (e: Exception) {
                Log.e(TAG, "[$reason] Phase 1 失败: ${e.message}")
            }

            // Phase 2: 使用 AudioManager API
            try {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = true
                Log.d(TAG, "[$reason] Phase 2: audioManager.isSpeakerphoneOn = true")

                // 设置音量到最大
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVolume, 0)
                Log.d(TAG, "[$reason] 通话音量: $maxVolume")
            } catch (e: Exception) {
                Log.e(TAG, "[$reason] Phase 2 失败: ${e.message}")
            }

            // Phase 3: 使用反射调用 AudioSystem.setForceUse
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
                        Log.w(TAG, "[$reason] Phase 3: 无法找到 setForceUse 方法")
                        return@try
                    }
                }

                setForceUse.invoke(null, 0, 1)  // FOR_COMMUNICATION, FORCE_SPEAKER
                setForceUse.invoke(null, 1, 1)  // FOR_MEDIA, FORCE_SPEAKER
                Log.d(TAG, "[$reason] Phase 3: AudioSystem.setForceUse 已执行")
            } catch (e: Throwable) {
                Log.w(TAG, "[$reason] Phase 3 失败: ${e.message}")
            }

            // Phase 4: Android 12+ 使用 setCommunicationDevice
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    audioManager.clearCommunicationDevice()
                    val availableDevices = audioManager.availableCommunicationDevices
                    val speakerDevice = availableDevices.firstOrNull {
                        it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    if (speakerDevice != null) {
                        val routed = audioManager.setCommunicationDevice(speakerDevice)
                        Log.d(TAG, "[$reason] Phase 4: setCommunicationDevice = $routed")
                    } else {
                        Log.w(TAG, "[$reason] Phase 4: 未找到扬声器设备")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "[$reason] Phase 4 失败: ${e.message}")
                }
            }

            // Phase 5: Root 设备使用系统命令
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
                    val result = RootUtil.executeMultipleWithRoot(commands)
                    Log.d(TAG, "[$reason] Phase 5: Root 命令执行结果: ${result.success}")
                } catch (e: Exception) {
                    Log.e(TAG, "[$reason] Phase 5 失败: ${e.message}")
                }
            }

            // Phase 6: 验证结果
            @Suppress("DEPRECATION")
            val isSuccess = audioManager.isSpeakerphoneOn
            val mode = audioManager.mode
            Log.d(TAG, "[$reason] Phase 6: 验证结果: isSpeakerphoneOn=$isSuccess, mode=$mode")

            if (isSuccess) {
                Log.d(TAG, "[$reason] ✓ 扬声器启用成功！")
            } else {
                Log.e(TAG, "[$reason] ✗ 扬声器启用失败！")
            }

        } catch (e: Exception) {
            Log.e(TAG, "[$reason] 启用扬声器失败: ${e.message}")
        }
    }

    /**
     * 强制启用扬声器
     */
    fun forceEnableSpeaker() {
        Log.d(TAG, "强制启用扬声器")
        forceSpeaker(context, true)
    }

    /**
     * 禁用扬声器
     */
    fun disableSpeaker() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
            Log.d(TAG, "扬声器已禁用")

            // 停止监控
            CallStateMonitorService.stopMonitoring(context)
        } catch (e: Exception) {
            Log.e(TAG, "禁用扬声器失败: ${e.message}")
        }
    }

    /**
     * 检查是否在通话中
     */
    fun isInCall(): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.mode == AudioManager.MODE_IN_CALL
        } catch (e: Exception) {
            Log.e(TAG, "检查通话状态失败: ${e.message}")
            false
        }
    }
}
