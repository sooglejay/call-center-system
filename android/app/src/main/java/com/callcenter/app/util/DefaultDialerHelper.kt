package com.callcenter.app.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat

/**
 * 默认拨号应用助手
 * 
 * 用于检查和请求默认拨号应用权限
 * 这是 InCallService 生效的必要条件
 */
object DefaultDialerHelper {
    private const val TAG = "DefaultDialerHelper"

    /**
     * 检查是否是默认拨号应用
     */
    fun isDefaultDialer(context: Context): Boolean {
        return try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            val result = telecomManager?.defaultDialerPackage == context.packageName
            Log.d(TAG, "isDefaultDialer: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "检查默认拨号应用失败: ${e.message}")
            false
        }
    }

    /**
     * 请求成为默认拨号应用
     * 需要 Android 6.0 (API 23) 及以上
     * 
     * @param activity 当前 Activity
     * @param launcher ActivityResultLauncher，用于接收结果
     * @return 是否成功发起请求
     */
    fun requestDefaultDialer(
        activity: Activity,
        launcher: ActivityResultLauncher<Intent>
    ): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ 使用 RoleManager
                val roleManager = activity.getSystemService(android.app.role.RoleManager::class.java)
                if (roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_DIALER)) {
                    if (!roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_DIALER)) {
                        val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_DIALER)
                        launcher.launch(intent)
                        Log.d(TAG, "请求默认拨号应用权限 (RoleManager)")
                        return true
                    }
                }
            }
            
            // Android 6.0 - 9.0 使用 TelecomManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val telecomManager = activity.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
                val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, activity.packageName)
                launcher.launch(intent)
                Log.d(TAG, "请求默认拨号应用权限 (TelecomManager)")
                return true
            }
            
            Log.w(TAG, "系统版本过低，不支持请求默认拨号应用")
            false
        } catch (e: Exception) {
            Log.e(TAG, "请求默认拨号应用失败: ${e.message}")
            false
        }
    }

    /**
     * 打开默认应用设置页面（备用方案）
     */
    fun openDefaultAppsSettings(context: Context) {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ 尝试打开角色管理设置
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "打开默认应用设置失败: ${e.message}")
        }
    }

    /**
     * 获取默认拨号应用的说明文本
     */
    fun getExplanationText(): String {
        return "为了实现自动扩音功能，需要将本应用设置为默认拨号应用。\n\n" +
                "设置后，应用将能够：\n" +
                "• 在通话接通时自动开启免提\n" +
                "• 更稳定地控制通话音频\n\n" +
                "注意：设置后，拨打电话会使用本应用。"
    }
}
