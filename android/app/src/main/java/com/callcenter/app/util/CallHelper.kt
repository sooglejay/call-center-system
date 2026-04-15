package com.callcenter.app.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.callcenter.app.service.CallStateMonitorService
import com.callcenter.app.util.RootUtil
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 电话拨打工具类
 */
@Singleton
class CallHelper @Inject constructor(
    private val context: Context
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "CallHelper"
        // 增强重试频率：更密集的前期重试 + 持续的后期重试
        private val IMMEDIATE_SPEAKER_RETRY_DELAYS = listOf(
            0L, 100L, 250L, 450L, 700L, 1000L, 1400L, 1900L,
            2500L, 3200L, 4000L, 5000L, 6000L, 7500L, 9000L, 11000L, 13000L
        )
    }

    /**
     * 检查是否有拨打电话权限
     */
    fun hasCallPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 拨打电话
     * @param phoneNumber 电话号码
     * @param directCall 是否直接拨打（需要 CALL_PHONE 权限）
     * @param autoSpeaker 是否自动开启免提（默认开启）
     * @param speakerDelayMs 开启免提的延迟时间（毫秒）
     */
    fun makeCall(
        phoneNumber: String,
        directCall: Boolean = true,
        autoSpeaker: Boolean = true,
        speakerDelayMs: Long = CallStateMonitorService.DEFAULT_SPEAKER_DELAY
    ) {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")

        if (directCall && hasCallPermission()) {
            if (autoSpeaker) {
                // 在真正发起外呼前先启动监听，并立即做一轮预热，尽量抢在系统电话 App 重置音频路由之前。
                CallStateMonitorService.startService(
                    context,
                    autoSpeaker = true,
                    delayMs = speakerDelayMs
                )
                startImmediateSpeakerBoost("before_action_call")
            }

            // 直接拨打
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$cleanNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)

            // 已真正发起外呼后再启动监听与强制免提逻辑
            if (autoSpeaker) {
                startImmediateSpeakerBoost("after_action_call")
                CallStateMonitorService.startService(
                    context,
                    autoSpeaker = true,
                    delayMs = speakerDelayMs
                )
                android.util.Log.d(TAG, "已发起外呼，启动自动免提监听服务")
            }
        } else {
            // 跳转到拨号界面
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$cleanNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * 拨打电话（简化版，默认自动开启免提）
     */
    fun makeCallWithAutoSpeaker(phoneNumber: String) {
        makeCall(phoneNumber, directCall = true, autoSpeaker = true)
    }

    /**
     * 获取当前通话状态
     */
    fun getCallState(): Int {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return telephonyManager.callState
    }

    /**
     * 检查是否正在通话
     */
    fun isInCall(): Boolean {
        return getCallState() != TelephonyManager.CALL_STATE_IDLE
    }

    /**
     * 打开扬声器（免提）
     * 需要在通话接通后调用
     */
    fun enableSpeakerphone(): Boolean {
        return try {
            forceEnableSpeakerphone("manual_enable")
            android.util.Log.d(TAG, "扬声器已打开")
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "打开扬声器失败: ${e.message}")
            false
        }
    }

    /**
     * 关闭扬声器
     */
    fun disableSpeakerphone(): Boolean {
        return try {
            audioManager.isSpeakerphoneOn = false
            android.util.Log.d(TAG, "扬声器已关闭")
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "关闭扬声器失败: ${e.message}")
            false
        }
    }

    private fun startImmediateSpeakerBoost(reason: String) {
        IMMEDIATE_SPEAKER_RETRY_DELAYS.forEachIndexed { index, delayMs ->
            handler.postDelayed(
                {
                    forceEnableSpeakerphone("$reason#$index")
                },
                delayMs
            )
        }
    }

    private fun forceEnableSpeakerphone(reason: String) {
        try {
            audioManager.mode = AudioManager.MODE_IN_CALL
            forceSpeakerUsingHiddenAudioSystem(reason)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val speakerDevice = audioManager.availableCommunicationDevices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                }
                if (speakerDevice != null) {
                    val routed = audioManager.setCommunicationDevice(speakerDevice)
                    android.util.Log.d(TAG, "[$reason] setCommunicationDevice=$routed")
                }
            }

            @Suppress("DEPRECATION")
            run {
                audioManager.isSpeakerphoneOn = true
            }

            if (RootUtil.isDeviceRooted()) {
                RootUtil.forceEnableSpeaker()
            }

            handler.postDelayed(
                {
                    try {
                        audioManager.mode = AudioManager.MODE_IN_CALL
                        @Suppress("DEPRECATION")
                        run {
                            if (!audioManager.isSpeakerphoneOn) {
                                audioManager.isSpeakerphoneOn = true
                            }
                        }
                    } catch (retryError: Exception) {
                        android.util.Log.e(TAG, "[$reason] 二次开启扬声器失败: ${retryError.message}")
                    }
                },
                150L
            )

            android.util.Log.d(TAG, "[$reason] 已执行主动外呼免提增强")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "[$reason] 强制开启扬声器失败: ${e.message}")
        }
    }

    private fun forceSpeakerUsingHiddenAudioSystem(reason: String) {
        try {
            val audioSystemClass = Class.forName("android.media.AudioSystem")
            val setForceUse = audioSystemClass.getDeclaredMethod(
                "setForceUse",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )

            // FOR_COMMUNICATION = 0, FOR_MEDIA = 1, FORCE_SPEAKER = 1
            setForceUse.invoke(null, 0, 1)
            setForceUse.invoke(null, 1, 1)
            android.util.Log.d(TAG, "[$reason] AudioSystem.setForceUse(FOR_COMMUNICATION/FOR_MEDIA, FORCE_SPEAKER) 已执行")
        } catch (e: Throwable) {
            android.util.Log.w(TAG, "[$reason] AudioSystem.setForceUse 调用失败: ${e.message}")
        }
    }

    /**
     * 检查扬声器状态
     */
    fun isSpeakerphoneOn(): Boolean {
        return audioManager.isSpeakerphoneOn
    }

    /**
     * 挂断电话（需要系统权限，普通应用无法实现）
     * 注意：普通应用无法挂断电话，只能通过用户手动挂断
     */
    fun endCall(): Boolean {
        Toast.makeText(context, "请手动挂断电话", Toast.LENGTH_SHORT).show()
        return false
    }

    /**
     * 格式化电话号码显示
     */
    fun formatPhoneNumber(phoneNumber: String): String {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9]"), "")
        return when {
            cleanNumber.length == 11 -> {
                // 中国手机号格式：138 0000 0000
                "${cleanNumber.substring(0, 3)} ${cleanNumber.substring(3, 7)} ${cleanNumber.substring(7)}"
            }
            cleanNumber.length > 11 -> {
                // 国际号码
                "+$cleanNumber"
            }
            else -> cleanNumber
        }
    }

    /**
     * 验证电话号码格式
     */
    fun isValidPhoneNumber(phoneNumber: String): Boolean {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9]"), "")
        return cleanNumber.length >= 7 && cleanNumber.length <= 15
    }
}
