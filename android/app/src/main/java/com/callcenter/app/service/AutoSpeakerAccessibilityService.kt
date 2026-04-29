package com.callcenter.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import com.callcenter.app.util.DebugLogger

/**
 * 自动扬声器与录音无障碍服务
 * 
 * 通过无障碍服务监听通话状态，自动：
 * 1. 开启扬声器
 * 2. 点击录音按钮（启动通话录音）
 * 
 * 适用于无法成为默认拨号应用的设备（如 MIUI、Google Pixel）
 */
class AutoSpeakerAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoSpeakerA11y"
        
        // 服务是否已激活
        var isServiceEnabled = false
            private set
            
        // 录音按钮是否已点击（防止重复点击）
        private var recordingButtonClicked = false
        
        // 重置录音按钮状态（新通话开始时调用）
        fun resetRecordingState() {
            recordingButtonClicked = false
        }
    }

    private var audioManager: AudioManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())

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
            packageName.contains("contacts") ||
            packageName.contains("com.google.android.dialer")) {
            
            Log.d(TAG, "检测到电话应用界面: $packageName, eventType: ${event.eventType}")
            
            // 当界面变化时，尝试开启扬声器和录音
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                
                // 检查是否在通话中
                if (isInCall()) {
                    DebugLogger.log("[AccessibilityService] 检测到通话中，尝试开启扬声器")
                    enableSpeaker()
                    
                    // 自动点击录音按钮
                    if (!recordingButtonClicked) {
                        DebugLogger.log("[AccessibilityService] 尝试自动点击录音按钮")
                        // 延迟一下等待界面加载完成
                        mainHandler.postDelayed({
                            clickRecordingButton()
                        }, 1000)
                    }
                } else {
                    // 不在通话中，重置录音状态
                    recordingButtonClicked = false
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
            
            // 查找扬声器相关的按钮（多语言支持）
            val speakerKeywords = listOf(
                // 简体中文
                "扬声器", "免提", "扩音", "喇叭",
                // 繁体中文
                "揚聲器", "免提", "擴音", "喇叭",
                // 英文
                "speaker", "handsfree", "loud", "speakerphone"
            )
            
            // 递归查找包含关键词的按钮
            findAndClickButton(rootNode, speakerKeywords, "扬声器")
            
        } catch (e: Exception) {
            Log.e(TAG, "点击扬声器按钮失败: ${e.message}")
        }
    }
    
    /**
     * 尝试点击界面上的录音按钮
     */
    private fun clickRecordingButton() {
        try {
            val rootNode = rootInActiveWindow
            if (rootNode == null) {
                DebugLogger.log("[AccessibilityService] ✗ 无法获取根节点")
                return
            }
            
            DebugLogger.log("[AccessibilityService] 开始查找录音按钮...")
            dumpNodeHierarchy(rootNode, 0)  // 调试：打印节点层级
            
            // 查找录音相关的按钮（多语言支持）
            val recordingKeywords = listOf(
                // 简体中文
                "录音", "录制", "通话录音", "开始录音", "录音中",
                // 繁体中文
                "錄音", "錄製", "通話錄音", "開始錄音", "錄音中",
                // 英文
                "record", "recording", "call record", "start recording",
                "rec", "voice record", "tape",
                // 日文（部分手机可能使用）
                "録音", "通話録音",
                // 韩文
                "녹음"
            )
            
            // 递归查找包含关键词的按钮
            val found = findAndClickButton(rootNode, recordingKeywords, "录音")
            
            if (found) {
                recordingButtonClicked = true
                DebugLogger.log("[AccessibilityService] ✓ 录音按钮已点击")
            } else {
                DebugLogger.log("[AccessibilityService] ✗ 未找到录音按钮")
                // 尝试通过描述查找
                findAndClickButtonByDescription(rootNode, recordingKeywords, "录音")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "点击录音按钮失败: ${e.message}")
            DebugLogger.log("[AccessibilityService] ✗ 点击录音按钮失败: ${e.message}")
        }
    }
    
    /**
     * 打印节点层级（调试用）
     */
    private fun dumpNodeHierarchy(node: AccessibilityNodeInfo, depth: Int) {
        if (depth > 5) return  // 限制深度
        
        val indent = "  ".repeat(depth)
        val text = node.text?.toString()?.take(30) ?: ""
        val contentDesc = node.contentDescription?.toString()?.take(30) ?: ""
        val className = node.className?.toString()?.substringAfterLast(".") ?: ""
        
        if (text.isNotBlank() || contentDesc.isNotBlank()) {
            Log.d(TAG, "$indent [$className] text='$text' desc='$contentDesc' clickable=${node.isClickable}")
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                dumpNodeHierarchy(child, depth + 1)
            }
        }
    }

    /**
     * 递归查找并点击按钮
     */
    private fun findAndClickButton(node: AccessibilityNodeInfo, keywords: List<String>, buttonName: String): Boolean {
        // 检查当前节点
        val text = node.text?.toString()?.lowercase() ?: ""
        val contentDescription = node.contentDescription?.toString()?.lowercase() ?: ""
        
        for (keyword in keywords) {
            if (text.contains(keyword.lowercase()) || contentDescription.contains(keyword.lowercase())) {
                // 找到了按钮，尝试点击
                val clicked = performClick(node)
                if (clicked) {
                    DebugLogger.log("[AccessibilityService] ✓ 点击${buttonName}按钮成功: text='$text' desc='$contentDescription'")
                    return true
                }
            }
        }
        
        // 递归查找子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                if (findAndClickButton(child, keywords, buttonName)) {
                    return true
                }
            }
        }
        
        return false
    }
    
    /**
     * 通过描述查找并点击按钮
     */
    private fun findAndClickButtonByDescription(node: AccessibilityNodeInfo, keywords: List<String>, buttonName: String): Boolean {
        val contentDescription = node.contentDescription?.toString()?.lowercase() ?: ""
        
        for (keyword in keywords) {
            if (contentDescription.contains(keyword.lowercase())) {
                val clicked = performClick(node)
                if (clicked) {
                    DebugLogger.log("[AccessibilityService] ✓ 通过描述点击${buttonName}按钮成功: desc='$contentDescription'")
                    return true
                }
            }
        }
        
        // 递归查找子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                if (findAndClickButtonByDescription(child, keywords, buttonName)) {
                    return true
                }
            }
        }
        
        return false
    }
    
    /**
     * 执行点击操作
     */
    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        // 如果节点可点击，直接点击
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        
        // 否则尝试点击父节点
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) {
                return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            parent = parent.parent
        }
        
        return false
    }
}
