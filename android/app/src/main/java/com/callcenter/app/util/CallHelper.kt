package com.callcenter.app.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.core.content.ContextCompat
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
     */
    fun makeCall(phoneNumber: String, directCall: Boolean = true) {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        
        if (directCall && hasCallPermission()) {
            // 直接拨打
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$cleanNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
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
            audioManager.mode = AudioManager.MODE_IN_CALL
            audioManager.isSpeakerphoneOn = true
            android.util.Log.d("CallHelper", "扬声器已打开")
            true
        } catch (e: Exception) {
            android.util.Log.e("CallHelper", "打开扬声器失败: ${e.message}")
            false
        }
    }

    /**
     * 关闭扬声器
     */
    fun disableSpeakerphone(): Boolean {
        return try {
            audioManager.isSpeakerphoneOn = false
            android.util.Log.d("CallHelper", "扬声器已关闭")
            true
        } catch (e: Exception) {
            android.util.Log.e("CallHelper", "关闭扬声器失败: ${e.message}")
            false
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
