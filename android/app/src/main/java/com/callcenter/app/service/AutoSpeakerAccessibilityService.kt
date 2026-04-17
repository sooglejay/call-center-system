package com.callcenter.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import com.callcenter.app.util.DebugLogger

/**
 * 自动扬声器无障碍服务
 * 
 * 通过无障碍服务监听通话状态，自动点击扬声器按钮
 * 适用于无法成为默认拨号应用的设备（如 MIUI）
 */
class AutoSpeakerAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoSpeakerA11y"
        
        // 服务是否已激活
        var isServiceEnabled = false
            private set
    }

    private var audioManager: AudioManager? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "========== AccessibilityService 已连接 ==========")
        DebugLogger.log("[AccessibilityService] 服务已连接")
        isServiceEnabled = true
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        val packageName = event.packageName?.toString() ?: return
        
        // 监听电话应用的界面变化
        if (packageName.contains("phone") || 
            packageName.contains("dialer") || 
            packageName.contains("incallui") ||
            packageName.contains("contacts")) {
            
            Log.d(TAG, "检测到电话应用界面: $packageName, eventType: ${event.eventType}")
            
            // 当界面变化时，尝试开启扬声器
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                
                // 检查是否在通话中
                if (isInCall()) {
                    DebugLogger.log("[AccessibilityService] 检测到通话中，尝试开启扬声器")
                    enableSpeaker()
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "AccessibilityService 被中断")
        DebugLogger.log("[AccessibilityService] 服务被中断")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "AccessibilityService 已销毁")
        DebugLogger.log("[AccessibilityService] 服务已销毁")
        isServiceEnabled = false
    }

    /**
     * 检查是否在通话中
     */
    private fun isInCall(): Boolean {
        return try {
            audioManager?.mode == AudioManager.MODE_IN_CALL ||
            audioManager?.mode == AudioManager.MODE_IN_COMMUNICATION
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 开启扬声器
     */
    private fun enableSpeaker() {
        try {
            // 方法1：直接设置 AudioManager
            audioManager?.let { am ->
                am.mode = AudioManager.MODE_IN_CALL
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = true
                
                // Android 12+ 使用 setCommunicationDevice
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val speakerDevice = am.availableCommunicationDevices.firstOrNull {
                        it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    speakerDevice?.let { device ->
                        am.setCommunicationDevice(device)
                        DebugLogger.log("[AccessibilityService] ✓ setCommunicationDevice")
                    }
                }
                
                @Suppress("DEPRECATION")
                if (am.isSpeakerphoneOn) {
                    DebugLogger.log("[AccessibilityService] ✓ 扬声器已开启 (AudioManager)")
                } else {
                    DebugLogger.log("[AccessibilityService] ✗ AudioManager 方式失败，尝试点击界面")
                    // 方法2：尝试点击界面上的扬声器按钮
                    clickSpeakerButton()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "开启扬声器失败: ${e.message}")
            DebugLogger.log("[AccessibilityService] ✗ 开启扬声器失败: ${e.message}")
        }
    }

    /**
     * 尝试点击界面上的扬声器按钮
     */
    private fun clickSpeakerButton() {
        try {
            val rootNode = rootInActiveWindow ?: return
            
            // 查找扬声器相关的按钮
            val speakerKeywords = listOf(
                "speaker", "扬声器", "免提", "扩音", 
                "handsfree", "loud", "speakerphone"
            )
            
            // 递归查找包含关键词的按钮
            findAndClickSpeakerButton(rootNode, speakerKeywords)
            
        } catch (e: Exception) {
            Log.e(TAG, "点击扬声器按钮失败: ${e.message}")
        }
    }

    /**
     * 递归查找并点击扬声器按钮
     */
    private fun findAndClickSpeakerButton(node: AccessibilityNodeInfo, keywords: List<String>): Boolean {
        // 检查当前节点
        val text = node.text?.toString()?.lowercase() ?: ""
        val contentDescription = node.contentDescription?.toString()?.lowercase() ?: ""
        
        for (keyword in keywords) {
            if (text.contains(keyword) || contentDescription.contains(keyword)) {
                // 找到了扬声器按钮，尝试点击
                if (node.isClickable) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    DebugLogger.log("[AccessibilityService] ✓ 点击扬声器按钮成功: $text")
                    return true
                } else {
                    // 如果节点不可点击，尝试点击父节点
                    var parent = node.parent
                    while (parent != null) {
                        if (parent.isClickable) {
                            parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            DebugLogger.log("[AccessibilityService] ✓ 点击父节点成功")
                            return true
                        }
                        parent = parent.parent
                    }
                }
            }
        }
        
        // 递归查找子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                if (findAndClickSpeakerButton(child, keywords)) {
                    return true
                }
            }
        }
        
        return false
    }
}
