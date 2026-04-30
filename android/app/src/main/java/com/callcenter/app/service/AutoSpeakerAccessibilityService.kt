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
 * 3. 挂断电话（检测到语音信箱时）
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
        
        // 服务实例
        private var instance: AutoSpeakerAccessibilityService? = null
        
        // 挂断按钮的文案关键词（content-desc / text 都会匹配）
        private val HANGUP_KEYWORDS = listOf(
            "挂断", "结束通话", "结束", "拒接",
            "End call", "End", "Hang up", "Decline"
        )
        
        // 挂断按钮常见 viewId（去掉包名前缀，做后缀匹配）
        private val HANGUP_VIEW_ID_SUFFIXES = listOf(
            "endButton", "end_call_button", "btn_endcall",
            "btn_end_call", "btn_hangup", "end_call", "hangup_button",
            "floating_end_call_action_button"
        )
        
        // 重置录音按钮状态（新通话开始时调用）
        fun resetRecordingState() {
            recordingButtonClicked = false
        }
        
        /**
         * 通过无障碍服务挂断电话
         * @param maxRetry 重试次数（rootInActiveWindow 可能瞬时为 null）
         * @param intervalMs 每次重试间隔
         * @return 是否成功点击
         */
        fun hangupCall(maxRetry: Int = 5, intervalMs: Long = 150L): Boolean {
            val service = instance ?: return false
            return service.performHangup(maxRetry, intervalMs)
        }
    }

    private var audioManager: AudioManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "========== AccessibilityService 已连接 ==========")
        DebugLogger.log("[AccessibilityService] 服务已连接")
        isServiceEnabled = true
        instance = this
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
                    enableSpeaker()
                    
                    // 自动点击录音按钮
                    if (!recordingButtonClicked) {
                        DebugLogger.log("[AccessibilityService] 尝试自动点击录音按钮")
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
        instance = null
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
                    }
                }
                
                @Suppress("DEPRECATION")
                if (am.isSpeakerphoneOn) {
                    DebugLogger.log("[AccessibilityService] ✓ 扬声器已开启")
                } else {
                    clickSpeakerButton()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "开启扬声器失败: ${e.message}")
        }
    }

    /**
     * 尝试点击界面上的扬声器按钮
     */
    private fun clickSpeakerButton() {
        try {
            val rootNode = rootInActiveWindow ?: return
            
            val speakerKeywords = listOf(
                "扬声器", "免提", "扩音", "喇叭",
                "揚聲器", "免提", "擴音", "喇叭",
                "speaker", "handsfree", "loud", "speakerphone"
            )
            
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
            
            val recordingKeywords = listOf(
                "录音", "录制", "通话录音", "开始录音", "录音中",
                "錄音", "錄製", "通話錄音", "開始錄音", "錄音中",
                "record", "recording", "call record", "start recording",
                "rec", "voice record", "tape",
                "録音", "通話録音",
                "녹음"
            )
            
            val found = findAndClickButton(rootNode, recordingKeywords, "录音")
            
            if (found) {
                recordingButtonClicked = true
                DebugLogger.log("[AccessibilityService] ✓ 录音按钮已点击")
            } else {
                DebugLogger.log("[AccessibilityService] ✗ 未找到录音按钮")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "点击录音按钮失败: ${e.message}")
        }
    }

    // ==================== 挂断电话相关方法 ====================

    /**
     * 程序化挂断当前通话
     */
    private fun performHangup(maxRetry: Int = 5, intervalMs: Long = 150L): Boolean {
        DebugLogger.log("[AccessibilityService] 开始执行挂断，最大重试次数: $maxRetry")
        
        repeat(maxRetry) { attempt ->
            val clicked = tryHangupOnce()
            if (clicked) {
                Log.i(TAG, "performHangup success on attempt ${attempt + 1}")
                DebugLogger.log("[AccessibilityService] ✓ 挂断成功，第 ${attempt + 1} 次尝试")
                return true
            }
            Log.d(TAG, "performHangup attempt ${attempt + 1} failed, retrying...")
            try { Thread.sleep(intervalMs) } catch (_: InterruptedException) {}
        }
        
        Log.w(TAG, "performHangup all attempts failed")
        DebugLogger.log("[AccessibilityService] ✗ 所有挂断尝试失败")
        return false
    }

    private fun tryHangupOnce(): Boolean {
        // 优先拿 active window，其次遍历 windows（分屏/悬浮窗场景）
        val roots = collectRootNodes()
        if (roots.isEmpty()) {
            Log.d(TAG, "no root node available")
            return false
        }
        
        for (root in roots) {
            val btn = findHangupNode(root) ?: continue
            Log.i(TAG, "found hangup node: viewId=${btn.viewIdResourceName}, " +
                       "desc=${btn.contentDescription}, text=${btn.text}")
            DebugLogger.log("[AccessibilityService] 找到挂断按钮: id=${btn.viewIdResourceName}, desc=${btn.contentDescription}")
            
            if (clickNode(btn)) return true
        }
        return false
    }

    /** 收集所有候选根节点 */
    private fun collectRootNodes(): List<AccessibilityNodeInfo> {
        val list = mutableListOf<AccessibilityNodeInfo>()
        rootInActiveWindow?.let { list.add(it) }
        
        // Android 5.0+，能拿到所有可交互窗口
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                windows?.forEach { w ->
                    w.root?.let { r -> if (list.none { it == r }) list.add(r) }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "windows API failed: $e")
        }
        return list
    }

    /** 递归查找挂断按钮 */
    private fun findHangupNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // 策略 A：按 viewId 后缀匹配（快）
        for (suffix in HANGUP_VIEW_ID_SUFFIXES) {
            val nodes = findByViewIdSuffix(root, suffix)
            if (nodes.isNotEmpty()) return nodes.first()
        }
        
        // 策略 B：按 content-description / text 关键字（最稳）
        return findByKeyword(root)
    }

    private fun findByViewIdSuffix(root: AccessibilityNodeInfo, suffix: String): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        traverse(root) { node ->
            val vid = node.viewIdResourceName ?: return@traverse false
            if (vid.endsWith(":id/$suffix") || vid.endsWith("/$suffix")) {
                result.add(node)
            }
            false
        }
        return result
    }

    private fun findByKeyword(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var found: AccessibilityNodeInfo? = null
        traverse(root) { node ->
            val desc = node.contentDescription?.toString().orEmpty()
            val text = node.text?.toString().orEmpty()
            val hit = HANGUP_KEYWORDS.any { kw ->
                desc.equals(kw, ignoreCase = true) || 
                text.equals(kw, ignoreCase = true) ||
                // 对"挂断" / "结束通话"这种精确文案可以放宽到 contains
                (kw.length >= 2 && (desc.contains(kw) || text.contains(kw)))
            }
            if (hit) {
                // 排除"接听"、"应答"之类的误匹配
                val negative = listOf("接听", "接受", "应答", "Answer", "Accept")
                if (negative.none { desc.contains(it) || text.contains(it) }) {
                    found = node
                    return@traverse true  // 找到就停
                }
            }
            false
        }
        return found
    }

    /** 深度优先遍历，predicate 返回 true 则提前停止 */
    private fun traverse(node: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): Boolean {
        if (node == null) return false
        if (predicate(node)) return true
        for (i in 0 until node.childCount) {
            if (traverse(node.getChild(i), predicate)) return true
        }
        return false
    }

    /** 尝试点击节点，不可点击则向上找可点击的父节点 */
    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var target: AccessibilityNodeInfo? = node
        var hop = 0
        while (target != null && !target.isClickable && hop < 5) {
            target = target.parent
            hop++
        }
        if (target == null) {
            Log.w(TAG, "no clickable ancestor")
            return false
        }
        val ok = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        Log.i(TAG, "performAction CLICK result=$ok on ${target.viewIdResourceName}")
        return ok
    }

    // ==================== 通用按钮查找方法 ====================

    /**
     * 递归查找并点击按钮
     */
    private fun findAndClickButton(node: AccessibilityNodeInfo, keywords: List<String>, buttonName: String): Boolean {
        val text = node.text?.toString()?.lowercase() ?: ""
        val contentDescription = node.contentDescription?.toString()?.lowercase() ?: ""
        
        for (keyword in keywords) {
            if (text.contains(keyword.lowercase()) || contentDescription.contains(keyword.lowercase())) {
                if (performClick(node)) {
                    DebugLogger.log("[AccessibilityService] ✓ 点击${buttonName}按钮成功: text='$text'")
                    return true
                }
            }
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null && findAndClickButton(child, keywords, buttonName)) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * 执行点击操作
     */
    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        
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
