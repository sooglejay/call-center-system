package com.callcenter.app.util

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.callcenter.app.CallCenterApp

/**
 * 用户可见提示工具。
 *
 * 内部系统场景下，关键错误除了日志外，还直接通过 Toast 提示客服/管理员。
 * 为避免连续弹出相同错误，这里做了简单的去重节流。
 */
object UserNotifier {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastMessage: String? = null
    private var lastTimestamp: Long = 0L

    fun showError(message: String) {
        val now = System.currentTimeMillis()
        if (message == lastMessage && now - lastTimestamp < 2500) {
            return
        }

        lastMessage = message
        lastTimestamp = now

        val context = CallCenterApp.instance ?: return
        mainHandler.post {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
}
