package com.callcenter.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.callcenter.app.service.AutoDialService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 电话状态接收器
 * 监听来电和去电状态
 */
class PhoneStateReceiver : BroadcastReceiver() {

    companion object {
        var lastState = TelephonyManager.CALL_STATE_IDLE
        var lastPhoneNumber: String? = null
        var callStartTime: Long = 0
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        @Suppress("DEPRECATION")
        val phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                // 来电响铃
                lastPhoneNumber = phoneNumber
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // 电话接通（来电或去电）
                callStartTime = System.currentTimeMillis()
                
                // 如果正在自动拨号，记录状态
                if (AutoDialService.isRunning.value) {
                    // 可以在这里通知 UI 更新状态
                }
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                // 电话挂断
                val callDuration = if (callStartTime > 0) {
                    (System.currentTimeMillis() - callStartTime) / 1000
                } else 0

                // 保存通话记录
                if (lastPhoneNumber != null && callDuration > 0) {
                    saveCallRecord(
                        context = context,
                        phoneNumber = lastPhoneNumber!!,
                        duration = callDuration.toInt()
                    )
                }

                // 重置状态
                callStartTime = 0
                lastPhoneNumber = null
            }
        }

        lastState = when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
            TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
            else -> TelephonyManager.CALL_STATE_IDLE
        }
    }

    private fun saveCallRecord(context: Context, phoneNumber: String, duration: Int) {
        // TODO: 保存通话记录到数据库
        // 这需要在后台协程中执行
        CoroutineScope(Dispatchers.IO).launch {
            // 保存到本地数据库
        }
    }
}
