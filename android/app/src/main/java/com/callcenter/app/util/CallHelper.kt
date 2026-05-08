package com.callcenter.app.util

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.util.Log
import com.callcenter.app.util.DebugLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 呼叫辅助工具类
 * 负责发起电话拨号和通话音频控制
 * 
 * 扬声器启用策略：
 * - 多阶段启用（6层防护）
 * - 协程重试机制（非阻塞）
 * - 非 Root 设备也能正常工作
 */
@Singleton
class CallHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "CallHelper"
        private const val MAX_RETRIES = 20          // 最大重试次数
        private const val RETRY_DELAY = 100L        // 重试间隔 100ms
        private const val TOTAL_TIMEOUT = 5000L     // 总超时 5秒
    }

    private val audioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private val telecomManager by lazy {
        context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
    }

    /**
     * 拨打电话
     * 根据是否是默认拨号应用选择不同的拨号方式
     */
    fun makeCall(phoneNumber: String) {
        try {
            val uri = Uri.parse("tel:$phoneNumber")

            Log.d(TAG, "makeCall: $phoneNumber")
            DebugLogger.log("[CallHelper] makeCall: $phoneNumber")

            // 检查拨号权限
            if (android.content.pm.PackageManager.PERMISSION_GRANTED !=
                context.checkSelfPermission(android.Manifest.permission.CALL_PHONE)
            ) {
                Log.e(TAG, "缺少拨号权限 (CALL_PHONE)")
                DebugLogger.log("[CallHelper] ✗ 缺少拨号权限")
                throw SecurityException("缺少拨号权限")
            }

            // 检查是否是默认拨号应用
            val isDefaultDialer = telecomManager.defaultDialerPackage == context.packageName
            Log.d(TAG, "isDefaultDialer: $isDefaultDialer")
            DebugLogger.log("[CallHelper] isDefaultDialer: $isDefaultDialer")

            if (isDefaultDialer) {
                // 是默认拨号应用：使用 TelecomManager.placeCall()
                // 这会触发系统的电话服务，使用 SIM 卡拨号
                Log.d(TAG, "使用 TelecomManager.placeCall() 拨号")
                DebugLogger.log("[CallHelper] 使用 TelecomManager.placeCall() 拨号")
                
                // 获取 SIM 卡 PhoneAccount
                val phoneAccounts = telecomManager.callCapablePhoneAccounts
                val simAccount = phoneAccounts?.firstOrNull()
                
                if (simAccount != null) {
                    Log.d(TAG, "使用 SIM PhoneAccount: $simAccount")
                    DebugLogger.log("[CallHelper] 使用 SIM PhoneAccount: $simAccount")
                    val extras = Bundle()
                    extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, simAccount)
                    telecomManager.placeCall(uri, extras)
                } else {
                    // 没有指定 PhoneAccount，让系统选择
                    Log.d(TAG, "未指定 PhoneAccount，让系统选择")
                    DebugLogger.log("[CallHelper] 未指定 PhoneAccount，让系统选择")
                    telecomManager.placeCall(uri, null)
                }
            } else {
                // 不是默认拨号应用：使用 Intent.ACTION_CALL
                Log.d(TAG, "使用 Intent.ACTION_CALL 拨号")
                DebugLogger.log("[CallHelper] 使用 Intent.ACTION_CALL 拨号")
                val intent = Intent(Intent.ACTION_CALL).apply {
                    data = uri
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }

            Log.d(TAG, "拨号成功")
            DebugLogger.log("[CallHelper] ✓ 拨号请求已发送")

        } catch (e: SecurityException) {
            Log.e(TAG, "缺少拨号权限: ${e.message}")
            DebugLogger.log("[CallHelper] ✗ 缺少拨号权限: ${e.message}")
            throw e
        } catch (e: android.content.ActivityNotFoundException) {
            Log.e(TAG, "未找到拨号应用: ${e.message}")
            DebugLogger.log("[CallHelper] ✗ 未找到拨号应用: ${e.message}")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "拨号失败: ${e.message}")
            DebugLogger.log("[CallHelper] ✗ 拨号失败: ${e.message}")
            throw e
        }
    }

    /**
     * 开启免提（扬声器）- 挂起函数版本
     * 使用协程重试，阻塞直到成功或超时
     * 
     * @return 是否成功开启
     */
    suspend fun enableSpeakerphoneAsync(): Boolean {
        Log.d(TAG, "========== enableSpeakerphoneAsync 开始 ==========")
        
        val startTime = System.currentTimeMillis()
        var retryCount = 0
        
        while (retryCount < MAX_RETRIES) {
            retryCount++
            
            // 检查是否超时
            if (System.currentTimeMillis() - startTime > TOTAL_TIMEOUT) {
                Log.w(TAG, "已超过总超时时间 ${TOTAL_TIMEOUT}ms，停止重试")
                break
            }
            
            Log.d(TAG, "第 $retryCount 次尝试启用扬声器")
            
            // 执行多阶段启用
            enableSpeakerMultiPhase("attempt_$retryCount")
            
            // 验证结果
            if (checkSpeakerStatus()) {
                Log.d(TAG, "✓ 扬声器在第 $retryCount 次尝试后启用成功")
                return true
            }
            
            // 等待后重试
            delay(RETRY_DELAY)
        }
        
        Log.e(TAG, "✗ 扬声器在 $retryCount 次尝试后仍失败")
        return false
    }

    /**
     * 开启免提（扬声器）- 同步版本
     * 立即返回，不等待结果
     */
    fun enableSpeakerphone(): Boolean {
        Log.d(TAG, "enableSpeakerphone (同步版本)")
        enableSpeakerMultiPhase("sync")
        return checkSpeakerStatus()
    }

    /**
     * 多阶段启用扬声器（6层防护）
     * 非 Root 设备也能正常工作
     */
    private fun enableSpeakerMultiPhase(reason: String) {
        try {
            // Phase 1: 设置音频模式为通话模式
            try {
                // 优先 MODE_IN_COMMUNICATION（更符合 setCommunicationDevice 语义，部分机型更稳定）
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                Log.d(TAG, "[$reason] Phase 1: MODE_IN_COMMUNICATION")
            } catch (e: Exception) {
                Log.e(TAG, "[$reason] Phase 1 失败: ${e.message}")
            }

            // 兼容：部分 ROM 仍需要 MODE_IN_CALL 才能切路由
            try {
                audioManager.mode = AudioManager.MODE_IN_CALL
                Log.d(TAG, "[$reason] Phase 1b: MODE_IN_CALL")
            } catch (_: Exception) {
            }

            // Phase 2: 使用 AudioManager API 设置扬声器
            try {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = true
                Log.d(TAG, "[$reason] Phase 2: isSpeakerphoneOn = true")
            } catch (e: Exception) {
                Log.e(TAG, "[$reason] Phase 2 失败: ${e.message}")
            }

            // Phase 3: 设置通话音量到最大
            try {
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
                if (currentVolume < maxVolume) {
                    audioManager.setStreamVolume(
                        AudioManager.STREAM_VOICE_CALL,
                        maxVolume,
                        AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE
                    )
                }
                Log.d(TAG, "[$reason] Phase 3: 音量 $currentVolume -> $maxVolume")
            } catch (e: Exception) {
                Log.e(TAG, "[$reason] Phase 3 失败: ${e.message}")
            }

            // Phase 4: 反射调用 AudioSystem.setForceUse（关键！）
            try {
                val audioSystemClass = Class.forName("android.media.AudioSystem")
                val setForceUse = audioSystemClass.getDeclaredMethod(
                    "setForceUse",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                setForceUse.isAccessible = true
                // FOR_COMMUNICATION = 0, FORCE_SPEAKER = 1
                setForceUse.invoke(null, 0, 1)
                // FOR_MEDIA = 1, FORCE_SPEAKER = 1  
                setForceUse.invoke(null, 1, 1)
                Log.d(TAG, "[$reason] Phase 4: AudioSystem.setForceUse 成功")
            } catch (e: Throwable) {
                Log.w(TAG, "[$reason] Phase 4 失败: ${e.message}")
            }

            // Phase 5: Android 12+ 使用 setCommunicationDevice
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    audioManager.clearCommunicationDevice()
                    val speakerDevice = audioManager.availableCommunicationDevices.firstOrNull {
                        it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    if (speakerDevice != null) {
                        val result = audioManager.setCommunicationDevice(speakerDevice)
                        Log.d(TAG, "[$reason] Phase 5: setCommunicationDevice = $result")
                    } else {
                        Log.w(TAG, "[$reason] Phase 5: 未找到扬声器设备")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "[$reason] Phase 5 失败: ${e.message}")
                }
            }

            // Phase 6: Root 设备使用系统命令（可选）
            try {
                if (RootUtil.isDeviceRooted()) {
                    val commands = listOf(
                        "cmd audio set-force-use communication speaker",
                        "cmd audio set-force-use media speaker"
                    )
                    RootUtil.executeMultipleWithRoot(commands)
                    Log.d(TAG, "[$reason] Phase 6: Root 命令已执行")
                }
            } catch (e: Exception) {
                Log.e(TAG, "[$reason] Phase 6 失败: ${e.message}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "[$reason] 启用扬声器异常: ${e.message}")
        }
    }

    /**
     * 检查扬声器状态
     */
    private fun checkSpeakerStatus(): Boolean {
        return try {
            @Suppress("DEPRECATION")
            val isSpeakerOn = audioManager.isSpeakerphoneOn
            val mode = audioManager.mode

            // Android 12+：isSpeakerphoneOn 在某些机型/ROM 下可能永远为 false，应结合 communicationDevice 判断
            val commDeviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.communicationDevice?.type
            } else {
                null
            }

            val commIsSpeaker = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) &&
                (commDeviceType == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)

            Log.d(
                TAG,
                "扬声器状态: isSpeakerphoneOn=$isSpeakerOn, mode=$mode, commDeviceType=${commDeviceType ?: "NA"}, commIsSpeaker=$commIsSpeaker"
            )

            isSpeakerOn || commIsSpeaker
        } catch (e: Exception) {
            Log.e(TAG, "检查扬声器状态失败: ${e.message}")
            false
        }
    }

    /**
     * 关闭免提（扬声器），恢复听筒
     */
    fun disableSpeakerphone() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
            
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
            
            // 恢复正常音频模式
            audioManager.mode = AudioManager.MODE_NORMAL
            
            Log.d(TAG, "disableSpeakerphone: 扬声器已关闭")
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
