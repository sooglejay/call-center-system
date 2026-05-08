package com.callcenter.app.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import com.callcenter.app.util.DebugLogger
import com.callcenter.app.service.AutoSpeakerAccessibilityService

/**
 * 无障碍开启引导 Activity（透明弹窗）。
 * 用于在 Service 中引导用户开启无障碍服务以保证自动免提稳定生效。
 */
class AccessibilityGuideActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_SOURCE = "extra_source"

        fun createIntent(context: Context, source: String? = null): Intent {
            return Intent(context, AccessibilityGuideActivity::class.java).apply {
                putExtra(EXTRA_SOURCE, source)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 透明背景
        window?.setBackgroundDrawableResource(android.R.color.transparent)

        val source = intent.getStringExtra(EXTRA_SOURCE)
        DebugLogger.log("[AccessibilityGuide] 引导页面打开: source=$source")

        // 已开启则直接退出
        if (AutoSpeakerAccessibilityService.isServiceEnabled) {
            DebugLogger.log("[AccessibilityGuide] 无障碍已开启，关闭引导")
            finish()
            return
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("需要开启无障碍服务")
            .setMessage(
                "检测到未开启无障碍服务，自动免提可能无法稳定开启，影响录音与接通识别。\n\n" +
                    "是否现在去系统设置开启？\n\n" +
                    "路径参考：设置 → 无障碍 → 已安装/已下载服务 → 智能呼叫中心 → 开启"
            )
            .setCancelable(true)
            .setPositiveButton("去开启") { _, _ ->
                runCatching {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }.onFailure { e ->
                    DebugLogger.log("[AccessibilityGuide] ✗ 打开无障碍设置失败: ${e.message}")
                }
                finish()
            }
            .setNegativeButton("暂不") { _, _ ->
                finish()
            }
            .setOnCancelListener {
                finish()
            }
            .show()
    }
}

