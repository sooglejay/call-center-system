package com.callcenter.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
 * 
 * 优化：如果设备不支持录音，记录标记避免重复查找
 */
class AutoSpeakerAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AutoSpeakerA11y"
        private const val PREFS_NAME = "accessibility_service_prefs"
        private const val KEY_RECORDING_NOT_SUPPORTED = "recording_not_supported"
        
        // 服务是否已激活
        var isServiceEnabled = false
            private set
            
        // 录音按钮是否已点击（防止重复点击）
        private var recordingButtonClicked = false

        // 设备是否支持录音（从SharedPreferences读取）
        private var isRecordingSupported = true
        
        // 重置录音按钮状态（新通话开始时调用）
        fun resetRecordingState() {
            recordingButtonClicked = false
        }

        /**
         * 检查设备是否支持录音
         */
        fun isRecordingSupported(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return !prefs.getBoolean(KEY_RECORDING_NOT_SUPPORTED, false)
        }

        /**
         * 标记设备不支持录音
         */
        private fun markRecordingNotSupported(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_RECORDING_NOT_SUPPORTED, true).apply()
            isRecordingSupported = false
            DebugLogger.log("[AccessibilityService] ⚠️ 已标记设备不支持通话录音，后续将不再查找录音按钮")
        }

        /**
         * 重置录音支持标记（用于设置页面手动重置）
         */
        fun resetRecordingSupportFlag(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(KEY_RECORDING_NOT_SUPPORTED).apply()
            isRecordingSupported = true
            DebugLogger.log("[AccessibilityService] ✓ 已重置录音支持标记，将重新尝试查找录音按钮")
        }
    }

    private var audioManager: AudioManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var sharedPreferences: SharedPreferences

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "========== AccessibilityService 已连接 ==========")
        DebugLogger.log("[AccessibilityService] 服务已连接")
        isServiceEnabled = true
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        
        // 初始化SharedPreferences，读取录音支持状态
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        isRecordingSupported = !sharedPreferences.getBoolean(KEY_RECORDING_NOT_SUPPORTED, false)
        
        if (!isRecordingSupported) {
            DebugLogger.log("[AccessibilityService] ⚠️ 设备已标记为不支持通话录音，将跳过录音按钮查找")
        } else {
            DebugLogger.log("[AccessibilityService] ✓ 设备支持录音，将尝试查找录音按钮")
        }
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
                    
                    // 自动点击录音按钮（仅在设备支持录音且未点击时）
                    if (isRecordingSupported && !recordingButtonClicked) {
                        DebugLogger.log("[AccessibilityService] 尝试自动点击录音按钮")
                        // 延迟一下等待界面加载完成
                        mainHandler.postDelayed({
                            clickRecordingButton()
                        }, 1000)
                    } else if (!isRecordingSupported) {
                        DebugLogger.log("[AccessibilityService] ⏭️ 设备不支持录音，跳过录音按钮查找")
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

            // 查找录音相关的按钮和开关（多语言支持）
            val recordingKeywords = listOf(
                // 简体中文
                "录音", "录制", "通话录音", "开始录音", "录音中",
                "开启通话录音", "开启录音",
                "自动通话录音", "自动录音",
                "自动录制与非通讯录号码的通话",
                // 繁体中文
                "錄音", "錄製", "通話錄音", "開始錄音", "錄音中",
                "開啟通話錄音", "開啟錄音",
                "自動通話錄音", "自動錄音",
                // 英文
                "record", "recording", "call record", "start recording",
                "rec", "voice record", "tape",
                "turn on call recording", "enable call recording",
                "auto call recording", "auto recording",
                "automatically record calls",
                "always record calls",
                // 日文（部分手机可能使用）
                "録音", "通話録音",
                // 韩文
                "녹음"
            )

            // 递归查找包含关键词的按钮或开关
            var found = findAndClickButton(rootNode, recordingKeywords, "录音")

            if (found) {
                recordingButtonClicked = true
                DebugLogger.log("[AccessibilityService] ✓ 录音按钮已点击")
            } else {
                DebugLogger.log("[AccessibilityService] ✗ 未直接找到录音按钮，尝试查找'更多'按钮")

                // 尝试通过描述查找
                found = findAndClickButtonByDescription(rootNode, recordingKeywords, "录音")

                if (!found) {
                    // 尝试点击"更多"按钮展开菜单
                    DebugLogger.log("[AccessibilityService] 尝试点击'更多'按钮展开菜单")
                    val moreButtonClicked = clickMoreButton()

                    if (moreButtonClicked) {
                        // 等待菜单展开
                        DebugLogger.log("[AccessibilityService] 等待菜单展开...")
                        Thread.sleep(500)

                        // 重新获取根节点并查找录音相关控件
                        val newRootNode = rootInActiveWindow
                        if (newRootNode != null) {
                            // 检查是否有地区限制提示
                            if (checkRegionRestriction(newRootNode)) {
                                DebugLogger.log("[AccessibilityService] ⚠️ 检测到地区限制：通话录音服务未在您所在的国家/地区提供")
                                DebugLogger.log("[AccessibilityService] 尝试查找'请求录制通话'选项")
                            }

                            // 尝试查找并开启所有相关的 Switch 开关（Google Pixel 风格）
                            DebugLogger.log("[AccessibilityService] 在展开菜单中查找录音开关")
                            val switchCount = findAllAndClickSwitches(newRootNode, recordingKeywords, "录音相关开关")

                            if (switchCount > 0) {
                                recordingButtonClicked = true
                                DebugLogger.log("[AccessibilityService] ✓ 找到并开启了 $switchCount 个录音开关")
                            }

                            // 再尝试查找普通按钮（备用方案）
                            DebugLogger.log("[AccessibilityService] 在展开菜单中查找录音按钮")
                            found = findAndClickButton(newRootNode, recordingKeywords, "录音")

                            if (found) {
                                recordingButtonClicked = true
                                DebugLogger.log("[AccessibilityService] ✓ 在展开菜单中找到并点击录音按钮")
                            } else if (switchCount == 0) {
                                DebugLogger.log("[AccessibilityService] ✗ 展开菜单后仍未找到录音控件")
                                // 标记设备不支持录音，避免后续重复查找
                                markRecordingNotSupported(this@AutoSpeakerAccessibilityService)
                            }
                        }
                    } else {
                        // 点击"更多"按钮失败，可能不存在更多菜单
                        DebugLogger.log("[AccessibilityService] ✗ 未找到'更多'按钮")
                        // 标记设备不支持录音
                        markRecordingNotSupported(this@AutoSpeakerAccessibilityService)
                    }
                } else {
                    // 通过描述找到了录音按钮，无需标记
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "点击录音按钮失败: ${e.message}")
            DebugLogger.log("[AccessibilityService] ✗ 点击录音按钮失败: ${e.message}")
        }
    }

    /**
     * 检查是否有地区限制提示
     */
    private fun checkRegionRestriction(node: AccessibilityNodeInfo): Boolean {
        val restrictionKeywords = listOf(
            // 简体中文
            "未在您所在的国家", "未在您所在的地区", "地区提供", "国家提供",
            // 英文
            "not available in your country", "not available in your region",
            "not available in your area"
        )

        val text = node.text?.toString()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""

        for (keyword in restrictionKeywords) {
            if (text.contains(keyword.lowercase()) || contentDesc.contains(keyword.lowercase())) {
                return true
            }
        }

        // 递归检查子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null && checkRegionRestriction(child)) {
                return true
            }
        }

        return false
    }

    /**
     * 查找并点击 Switch 开关（支持 Google Pixel 风格的开关）
     */
    private fun findAndClickSwitch(node: AccessibilityNodeInfo, keywords: List<String>, switchName: String): Boolean {
        // 检查当前节点
        val text = node.text?.toString()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        val className = node.className?.toString() ?: ""

        // 检查是否是 Switch 控件
        val isSwitch = className.contains("Switch", ignoreCase = true) ||
                       node.isCheckable // Switch 通常可以勾选

        for (keyword in keywords) {
            if (text.contains(keyword.lowercase()) || contentDesc.contains(keyword.lowercase())) {
                if (isSwitch) {
                    // 检查是否已开启
                    if (!node.isChecked) {
                        val clicked = performClick(node)
                        if (clicked) {
                            DebugLogger.log("[AccessibilityService] ✓ 点击${switchName}开关成功: text='$text' desc='$contentDesc'")
                            return true
                        }
                    } else {
                        DebugLogger.log("[AccessibilityService] ✓ ${switchName}开关已开启，无需点击")
                        return true
                    }
                } else {
                    // 不是 Switch，但包含关键词，尝试点击
                    val clicked = performClick(node)
                    if (clicked) {
                        DebugLogger.log("[AccessibilityService] ✓ 点击${switchName}成功: text='$text' desc='$contentDesc'")
                        return true
                    }
                }
            }
        }

        // 递归查找子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                if (findAndClickSwitch(child, keywords, switchName)) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * 查找并开启所有相关的 Switch 开关（Google Pixel 可能有多个开关）
     */
    private fun findAllAndClickSwitches(node: AccessibilityNodeInfo, keywords: List<String>, switchName: String): Int {
        return findAllAndClickSwitchesInternal(node, keywords, switchName, mutableSetOf(), mutableSetOf(), 0)
    }

    /**
     * 递归查找并开启所有 Switch 开关的内部方法
     */
    private fun findAllAndClickSwitchesInternal(
        node: AccessibilityNodeInfo,
        keywords: List<String>,
        switchName: String,
        visitedNodes: MutableSet<String>,
        clickedTexts: MutableSet<String>,
        count: Int
    ): Int {
        var currentCount = count

        // 检查当前节点
        val text = node.text?.toString()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        val className = node.className?.toString() ?: ""

        // 避免重复处理相同的文本
        val nodeKey = text + contentDesc
        if (nodeKey.isNotBlank() && nodeKey in clickedTexts) {
            return currentCount
        }

        // 检查是否是 Switch 控件
        val isSwitch = className.contains("Switch", ignoreCase = true) ||
                       node.isCheckable

        for (keyword in keywords) {
            if (text.contains(keyword.lowercase()) || contentDesc.contains(keyword.lowercase())) {
                if (isSwitch) {
                    // 检查是否已开启
                    if (!node.isChecked) {
                        val clicked = performClick(node)
                        if (clicked) {
                            currentCount++
                            clickedTexts.add(nodeKey)
                            DebugLogger.log("[AccessibilityService] ✓ 点击${switchName}开关成功 (#$currentCount): text='$text' desc='$contentDesc'")
                            // 短暂延迟，避免点击过快
                            Thread.sleep(100)
                        }
                    } else {
                        DebugLogger.log("[AccessibilityService] ✓ ${switchName}开关已开启，无需点击: text='$text'")
                        currentCount++
                        clickedTexts.add(nodeKey)
                    }
                }
                break  // 匹配到一个关键词就够了
            }
        }

        // 递归查找子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                currentCount = findAllAndClickSwitchesInternal(child, keywords, switchName, visitedNodes, clickedTexts, currentCount)
            }
        }

        return currentCount
    }

    /**
     * 尝试点击"更多"按钮展开菜单
     */
    private fun clickMoreButton(): Boolean {
        try {
            val rootNode = rootInActiveWindow ?: return false

            // 查找"更多"按钮的关键词（多语言支持）
            val moreKeywords = listOf(
                // 简体中文
                "更多", "展开", "菜单", "其他选项",
                // 繁体中文
                "更多", "展開", "選單", "其他選項",
                // 英文
                "more", "menu", "expand", "overflow", "options",
                "more options", "additional",
                // 符号
                "⋯", "⋮", "☰", "•••", "..."
            )

            // 先尝试通过文本查找
            var found = findAndClickButton(rootNode, moreKeywords, "更多")

            if (!found) {
                // 尝试通过内容描述查找
                found = findAndClickButtonByDescription(rootNode, moreKeywords, "更多")
            }

            if (!found) {
                // 尝试通过视图ID查找（Google Dialer 常见的ID）
                val moreButtonIds = listOf(
                    "com.google.android.dialer:id/more_options",
                    "com.google.android.dialer:id/overflow",
                    "com.android.dialer:id/more_options",
                    "com.android.dialer:id/overflow",
                    "android:id/more"
                )

                found = findAndClickButtonById(rootNode, moreButtonIds, "更多")
            }

            if (found) {
                DebugLogger.log("[AccessibilityService] ✓ '更多'按钮已点击")
            } else {
                DebugLogger.log("[AccessibilityService] ✗ 未找到'更多'按钮")
            }

            return found

        } catch (e: Exception) {
            Log.e(TAG, "点击'更多'按钮失败: ${e.message}")
            DebugLogger.log("[AccessibilityService] ✗ 点击'更多'按钮失败: ${e.message}")
            return false
        }
    }

    /**
     * 通过ID查找并点击按钮
     */
    private fun findAndClickButtonById(node: AccessibilityNodeInfo, ids: List<String>, buttonName: String): Boolean {
        val nodeId = node.viewIdResourceName ?: ""

        for (id in ids) {
            if (nodeId.contains(id, ignoreCase = true)) {
                val clicked = performClick(node)
                if (clicked) {
                    DebugLogger.log("[AccessibilityService] ✓ 通过ID点击${buttonName}按钮成功: id='$nodeId'")
                    return true
                }
            }
        }

        // 递归查找子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                if (findAndClickButtonById(child, ids, buttonName)) {
                    return true
                }
            }
        }

        return false
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
