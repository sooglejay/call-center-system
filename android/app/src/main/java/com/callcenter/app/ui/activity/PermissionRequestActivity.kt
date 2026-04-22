package com.callcenter.app.ui.activity

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.callcenter.app.util.DebugLogger

/**
 * 透明权限请求 Activity
 * 用于在 Service 中请求运行时权限
 */
class PermissionRequestActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PERMISSIONS = "extra_permissions"
        const val EXTRA_REQUEST_CODE = "extra_request_code"
        const val EXTRA_SHOW_RATIONALE = "extra_show_rationale"

        private const val TAG = "PermissionRequest"

        /**
         * 创建启动 Intent
         */
        fun createIntent(
            context: Context,
            permissions: Array<String>,
            requestCode: Int = 100,
            showRationale: Boolean = true
        ): Intent {
            return Intent(context, PermissionRequestActivity::class.java).apply {
                putExtra(EXTRA_PERMISSIONS, permissions)
                putExtra(EXTRA_REQUEST_CODE, requestCode)
                putExtra(EXTRA_SHOW_RATIONALE, showRationale)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        /**
         * 检查是否有录音权限
         */
        fun hasRecordAudioPermission(context: Context): Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        DebugLogger.log("[PermissionRequest] 权限请求结果: $permissions")

        val allGranted = permissions.values.all { it }
        val deniedPermissions = permissions.filter { !it.value }.keys

        if (allGranted) {
            DebugLogger.log("[PermissionRequest] ✓ 所有权限已授予")
            Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show()
        } else {
            DebugLogger.log("[PermissionRequest] ✗ 部分权限被拒绝: $deniedPermissions")

            // 检查是否需要引导用户去设置
            val shouldShowRationale = deniedPermissions.any {
                shouldShowRequestPermissionRationale(it)
            }

            if (!shouldShowRationale) {
                // 用户选择了"不再询问"，需要引导去设置
                DebugLogger.log("[PermissionRequest] 用户选择了不再询问，引导去设置")
                Toast.makeText(
                    this,
                    "请在设置中手动开启权限",
                    Toast.LENGTH_LONG
                ).show()

                // 打开应用设置页面
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } else {
                Toast.makeText(
                    this,
                    "权限被拒绝，部分功能可能无法使用",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        // 关闭 Activity
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 透明背景
        window?.setBackgroundDrawableResource(android.R.color.transparent)

        val permissions = intent.getStringArrayExtra(EXTRA_PERMISSIONS)
        val showRationale = intent.getBooleanExtra(EXTRA_SHOW_RATIONALE, true)

        if (permissions.isNullOrEmpty()) {
            DebugLogger.log("[PermissionRequest] 无权限需要请求，关闭")
            finish()
            return
        }

        DebugLogger.log("[PermissionRequest] 请求权限: ${permissions.joinToString()}")

        // 过滤出未授权的权限
        val notGrantedPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (notGrantedPermissions.isEmpty()) {
            DebugLogger.log("[PermissionRequest] 所有权限已授予，关闭")
            finish()
            return
        }

        // 显示说明（可选）
        if (showRationale) {
            val message = getPermissionRationaleMessage(notGrantedPermissions)
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }

        // 请求权限
        permissionLauncher.launch(notGrantedPermissions)
    }

    /**
     * 获取权限说明消息
     */
    private fun getPermissionRationaleMessage(permissions: Array<String>): String {
        return when {
            permissions.contains(Manifest.permission.RECORD_AUDIO) -> {
                "需要录音权限来识别语音信箱"
            }
            else -> "需要相关权限才能使用完整功能"
        }
    }
}
