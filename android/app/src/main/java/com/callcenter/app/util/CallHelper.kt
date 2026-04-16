package com.callcenter.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 呼叫辅助工具类
 * 负责发起电话拨号
 */
@Singleton
class CallHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "CallHelper"
    }

    /**
     * 拨打电话
     */
    fun makeCall(phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            Log.d(TAG, "makeCall: $phoneNumber")

            // 检查拨号权限
            if (android.content.pm.PackageManager.PERMISSION_GRANTED !=
                context.checkSelfPermission(android.Manifest.permission.CALL_PHONE)
            ) {
                Log.e(TAG, "缺少拨号权限 (CALL_PHONE)")
                throw SecurityException("缺少拨号权限")
            }

            context.startActivity(intent)
            Log.d(TAG, "拨号成功")

        } catch (e: SecurityException) {
            Log.e(TAG, "缺少拨号权限: ${e.message}")
            throw e
        } catch (e: android.content.ActivityNotFoundException) {
            Log.e(TAG, "未找到拨号应用: ${e.message}")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "拨号失败: ${e.message}")
            throw e
        }
    }

    /**
     * 检查是否在通话中
     */
    fun isInCall(): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            audioManager.mode == android.media.AudioManager.MODE_IN_CALL
        } catch (e: Exception) {
            Log.e(TAG, "检查通话状态失败: ${e.message}")
            false
        }
    }
}
