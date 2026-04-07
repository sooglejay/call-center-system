package com.callcenter.app.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.callcenter.app.MainActivity

/**
 * App 重启工具类
 */
object AppRestartUtil {

    /**
     * 重启应用
     * @param context 上下文
     * @param delayMillis 延迟时间（毫秒），默认 500ms 让 Toast 有时间显示
     */
    fun restartApp(context: Context, delayMillis: Long = 500) {
        // 显示重启提示
        Toast.makeText(context, "服务器地址已更新，应用即将重启...", Toast.LENGTH_SHORT).show()

        // 延迟重启，让 Toast 有时间显示
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            context.startActivity(intent)

            // 结束当前 Activity
            if (context is Activity) {
                context.finish()
            }

            // 结束进程
            android.os.Process.killProcess(android.os.Process.myPid())
        }, delayMillis)
    }
}
