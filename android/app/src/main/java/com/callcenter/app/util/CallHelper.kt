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

fun applyMaxSpeakerVolume(audioManager: AudioManager, tag: String, reason: String) {
    try {
        val voiceMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
        val voiceCurrent = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
        if (voiceCurrent < voiceMax) {
            audioManager.setStreamVolume(
                AudioManager.STREAM_VOICE_CALL,
                voiceMax,
                AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE
            )
        }
        Log.d(tag, "[$reason] 通话音量已拉满: $voiceCurrent -> $voiceMax")

        val musicMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val musicCurrent = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (musicCurrent < musicMax) {
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                musicMax,
                AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE
            )
        }
        Log.d(tag, "[$reason] 媒体音量兜底拉满: $musicCurrent -> $musicMax")
    } catch (e: Exception) {
        Log.e(tag, "[$reason] 拉满音量失败: ${e.message}")
    }
}

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

            Log.d(TAG, "========== makeCall ==========")
            Log.d(TAG, "PhoneNumber: $phoneNumber")
            Log.d(TAG, "Intent: ${intent.action}")
            Log.d(TAG, "Data: ${intent.data}")

            // 检查拨号权限
            if (android.content.pm.PackageManager.PERMISSION_GRANTED !=
                context.checkSelfPermission(android.Manifest.permission.CALL_PHONE)
            ) {
                Log.e(TAG, "缺少拨号权限 (CALL_PHONE)")
                throw SecurityException("缺少拨号权限")
            }

            // 直接使用 Intent.ACTION_CALL，这是最兼容的方式
            context.startActivity(intent)
            Log.d(TAG, "✓ 使用 Intent.ACTION_CALL 拨号成功")

            // 启动扬声器服务（17 次重试）
            enableSpeakerWithRetry(17)

        } catch (e: SecurityException) {
            Log.e(TAG, "✗ 缺少拨号权限: ${e.message}")
            e.printStackTrace()
            throw e
        } catch (e: android.content.ActivityNotFoundException) {
            Log.e(TAG, "✗ 未找到拨号应用: ${e.message}")
            e.printStackTrace()
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "✗ 拨号失败: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    /**
     * 启用扬声器（17 次重试）
     * 间隔：50ms、100ms、150ms... 直到 850ms
     */
    private fun enableSpeakerWithRetry(maxRetries: Int = 17) {
        Log.d(TAG, "开始启用扬声器（重试次数: $maxRetries）")

        val handler = android.os.Handler(android.os.Looper.getMainLooper())

        // 启动持续监控
        CallStateMonitorService.startMonitoring(context)

        // 立即尝试一次
        enableSpeakerWithMultiPhase("retry_0")

        // 递归重试函数
        fun scheduleRetry(attempt: Int) {
            if (attempt >= maxRetries) {
                Log.w(TAG, "已达到最大重试次数 ($maxRetries)，停止重试")
                return
            }

            val delay = (attempt + 1) * 50L
            handler.postDelayed({
                Log.d(TAG, "第 $attempt/$maxRetries 次尝试启用扬声器（延迟: ${delay}ms）")
                enableSpeakerWithMultiPhase("retry_$attempt")
                scheduleRetry(attempt + 1)
            }, delay)
        }

        // 开始重试
        scheduleRetry(1)
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
                applyMaxSpeakerVolume(audioManager, TAG, reason)
            } catch (e: Exception) {
                Log.e(TAG, "[$reason] Phase 2 失败: ${e.message}")
            }

            // Phase 3: 使用反射调用 AudioSystem.setForceUse
            run {
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
                            return@run
                        }
                    }

                    setForceUse.invoke(null, 0, 1)  // FOR_COMMUNICATION, FORCE_SPEAKER
                    setForceUse.invoke(null, 1, 1)  // FOR_MEDIA, FORCE_SPEAKER
                    Log.d(TAG, "[$reason] Phase 3: AudioSystem.setForceUse 已执行")
                } catch (e: Throwable) {
                    Log.w(TAG, "[$reason] Phase 3 失败: ${e.message}")
                }
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
