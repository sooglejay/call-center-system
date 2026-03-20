package com.callcenter.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机启动接收器
 * 用于在设备重启后恢复服务
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // 可以在这里启动后台服务
            // 例如：启动通话监控服务
        }
    }
}
