package com.callcenter.app.util.root

import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Root 手机专用通话状态检测器
 * 通过读取 RIL (Radio Interface Layer) 日志获取更精确的通话状态
 *
 * 支持的精确状态：
 * - IDLE: 空闲
 * - DIALING: 拨号中
 * - ALERTING: 对方响铃中
 * - ACTIVE: 通话已接通
 * - HOLDING: 通话保持中
 * - DISCONNECTING: 正在挂断
 * - DISCONNECTED: 已挂断
 */
class RootCallStateDetector {

    companion object {
        private const val TAG = "RootCallStateDetector"

        // RIL 日志中的关键标识
        private val CALL_STATE_PATTERNS = mapOf(
            // 拨号开始
            "DIALING" to listOf(
                "RILJ.*DIAL",
                "RILJ.*dial",
                "GsmCallTracker.*dial",
                "ImsPhoneCallTracker.*dial"
            ),
            // 对方响铃
            "ALERTING" to listOf(
                "RILJ.*ALERTING",
                "RILJ.*alerting",
                "GsmCallTracker.*ALERTING",
                "CallState.*ALERTING"
            ),
            // 通话接通
            "ACTIVE" to listOf(
                "RILJ.*ACTIVE",
                "RILJ.*active",
                "GsmCallTracker.*ACTIVE",
                "CallState.*ACTIVE",
                "onCallConnected"
            ),
            // 通话保持
            "HOLDING" to listOf(
                "RILJ.*HOLDING",
                "RILJ.*holding",
                "GsmCallTracker.*HOLDING"
            ),
            // 正在挂断
            "DISCONNECTING" to listOf(
                "RILJ.*HANGUP",
                "RILJ.*hangup",
                "GsmCallTracker.*hangup",
                "ImsPhoneCallTracker.*hangup"
            ),
            // 已挂断/空闲
            "IDLE" to listOf(
                "RILJ.*IDLE",
                "RILJ.*idle",
                "GsmCallTracker.*IDLE",
                "CallState.*IDLE",
                "onCallDisconnected",
                "onCallEnded"
            )
        )

        // 通话时长相关模式
        private val CALL_DURATION_PATTERNS = listOf(
            "RILJ.*callDuration",
            "RILJ.*duration",
            "GsmCallTracker.*duration"
        )

        // 对方号码相关模式
        private val CALL_NUMBER_PATTERNS = listOf(
            "RILJ.*number",
            "RILJ.*getNumber",
            "GsmCallTracker.*number"
        )
    }

    // 当前通话状态
    private var currentState: RootCallState = RootCallState.IDLE

    // 通话开始时间
    private var callStartTime: Long = 0

    // 通话接通时间
    private var callConnectedTime: Long = 0

    // 当前通话号码
    private var currentNumber: String? = null

    // 监听器列表
    private val listeners = mutableListOf<RootPhoneStateListener>()

    // 检测协程
    private var detectionJob: Job? = null

    // 协程作用域
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 是否正在检测
    var isDetecting = false
        private set

    /**
     * 开始检测通话状态
     */
    fun startDetection() {
        if (isDetecting) return

        if (!RootUtils.hasRootPermission()) {
            Log.e(TAG, "没有 Root 权限，无法启动检测")
            notifyError("需要 Root 权限才能使用此功能")
            return
        }

        isDetecting = true
        Log.d(TAG, "开始 Root 通话状态检测")

        detectionJob = scope.launch {
            monitorRadioLogs()
        }
    }

    /**
     * 停止检测
     */
    fun stopDetection() {
        isDetecting = false
        detectionJob?.cancel()
        detectionJob = null
        Log.d(TAG, "停止 Root 通话状态检测")
    }

    /**
     * 添加状态监听器
     */
    fun addListener(listener: RootPhoneStateListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    /**
     * 移除状态监听器
     */
    fun removeListener(listener: RootPhoneStateListener) {
        listeners.remove(listener)
    }

    /**
     * 获取当前通话状态
     */
    fun getCurrentState(): RootCallState = currentState

    /**
     * 获取当前通话号码
     */
    fun getCurrentNumber(): String? = currentNumber

    /**
     * 获取当前通话时长（毫秒）
     * 只有在 ACTIVE 状态下才有意义
     */
    fun getCurrentCallDuration(): Long {
        return if (currentState == RootCallState.ACTIVE && callConnectedTime > 0) {
            System.currentTimeMillis() - callConnectedTime
        } else {
            0
        }
    }

    /**
     * 监控 RIL 日志
     */
    private suspend fun monitorRadioLogs() {
        withContext(Dispatchers.IO) {
            try {
                // 使用 logcat 读取 radio 缓冲区
                val process = Runtime.getRuntime().exec(
                    arrayOf("su", "-c", "logcat -b radio -v time RILJ:D GsmCallTracker:D ImsPhoneCallTracker:D *:S")
                )

                val reader = BufferedReader(InputStreamReader(process.inputStream))

                while (isDetecting) {
                    val line = reader.readLine() ?: break
                    parseLogLine(line)
                }

                reader.close()
                process.destroy()
            } catch (e: Exception) {
                Log.e(TAG, "监控日志失败: ${e.message}")
//                notifyError("监控失败: ${e.message}")
            }
        }
    }

    /**
     * 解析日志行
     */
    private fun parseLogLine(line: String) {
        // 提取号码
        extractNumber(line)

        // 匹配状态
        for ((state, patterns) in CALL_STATE_PATTERNS) {
            for (pattern in patterns) {
                if (line.contains(Regex(pattern))) {
                    val newState = RootCallState.valueOf(state)
                    if (newState != currentState) {
                        handleStateChange(newState, line)
                    }
                    return
                }
            }
        }
    }

    /**
     * 从日志中提取号码
     */
    private fun extractNumber(line: String) {
        // 匹配各种号码格式
        val numberPatterns = listOf(
            Regex("number[=:]([^,\\s]+)"),
            Regex("dialing[^0-9]*([0-9+]+)"),
            Regex("call[^0-9]*([0-9+]{7,})")
        )

        for (pattern in numberPatterns) {
            pattern.find(line)?.groupValues?.get(1)?.let { number ->
                if (number.length >= 7) {
                    currentNumber = number
                    Log.d(TAG, "提取到号码: $number")
                    return
                }
            }
        }
    }

    /**
     * 处理状态变更
     */
    private fun handleStateChange(newState: RootCallState, logLine: String) {
        val oldState = currentState
        currentState = newState

        Log.d(TAG, "通话状态变更: $oldState -> $newState, 日志: $logLine")

        when (newState) {
            RootCallState.DIALING -> {
                callStartTime = System.currentTimeMillis()
                callConnectedTime = 0
                notifyDialing(currentNumber)
            }
            RootCallState.ALERTING -> {
                notifyAlerting(currentNumber)
            }
            RootCallState.ACTIVE -> {
                callConnectedTime = System.currentTimeMillis()
                val setupTime = callConnectedTime - callStartTime
                notifyActive(currentNumber, setupTime)
            }
            RootCallState.HOLDING -> {
                notifyHolding()
            }
            RootCallState.DISCONNECTING -> {
                notifyDisconnecting()
            }
            RootCallState.IDLE -> {
                val duration = if (callConnectedTime > 0) {
                    System.currentTimeMillis() - callConnectedTime
                } else 0
                notifyIdle(duration)
                // 重置状态
                currentNumber = null
                callConnectedTime = 0
            }
            else -> {}
        }

        // 通知所有监听器状态变更
        notifyStateChanged(oldState, newState)
    }

    /**
     * 通知拨号中
     */
    private fun notifyDialing(number: String?) {
        listeners.forEach { it.onDialing(number) }
    }

    /**
     * 通知对方响铃
     */
    private fun notifyAlerting(number: String?) {
        listeners.forEach { it.onAlerting(number) }
    }

    /**
     * 通知通话接通
     */
    private fun notifyActive(number: String?, setupTime: Long) {
        listeners.forEach { it.onActive(number, setupTime) }
    }

    /**
     * 通知通话保持
     */
    private fun notifyHolding() {
        listeners.forEach { it.onHolding() }
    }

    /**
     * 通知正在挂断
     */
    private fun notifyDisconnecting() {
        listeners.forEach { it.onDisconnecting() }
    }

    /**
     * 通知空闲/挂断
     */
    private fun notifyIdle(duration: Long) {
        listeners.forEach { it.onIdle(duration) }
    }

    /**
     * 通知状态变更
     */
    private fun notifyStateChanged(oldState: RootCallState, newState: RootCallState) {
        listeners.forEach { it.onStateChanged(oldState, newState) }
    }

    /**
     * 通知错误
     */
    private fun notifyError(message: String) {
        listeners.forEach { it.onError(message) }
    }

    /**
     * 释放资源
     */
    fun release() {
        stopDetection()
        listeners.clear()
        scope.cancel()
    }
}

/**
 * Root 通话状态枚举
 * 比普通 TelephonyManager 更精确，包含更多实际通话状态
 */
enum class RootCallState {
    // ========== 基础状态 ==========
    IDLE,                   // 空闲
    DIALING,                // 拨号中
    ALERTING,               // 对方响铃中
    ACTIVE,                 // 通话已接通
    HOLDING,                // 通话保持中
    DISCONNECTING,          // 正在挂断
    DISCONNECTED,           // 已挂断
    
    // ========== 呼叫结果状态 ==========
    CONNECTED,              // 已接听（通话成功建立）
    MISSED,                 // 未接听（响铃未接）
    BUSY,                   // 对方忙线中
    NO_ANSWER,              // 无人接听
    REJECTED,               // 对方拒接
    
    // ========== 网络/运营商状态 ==========
    NETWORK_UNAVAILABLE,    // 网络不可用
    OUT_OF_SERVICE,         // 无服务
    SIGNAL_WEAK,            // 信号弱
    
    // ========== 号码状态 ==========
    INVALID_NUMBER,         // 号码无效/空号
    NUMBER_NOT_IN_SERVICE,  // 号码已停机
    POWER_OFF,              // 对方已关机
    
    // ========== 语音信箱/提示音 ==========
    VOICEMAIL,              // 语音信箱
    CALL_FORWARDING,        // 呼叫转移
    IVR_PROMPT,             // IVR语音导航（如"您好，欢迎致电..."）
    
    // ========== 通话中状态 ==========
    ONHOLD,                 // 通话保持
    CONFERENCE,             // 会议通话中
    MUTED,                  // 静音中
    
    // ========== 异常状态 ==========
    ERROR,                  // 呼叫错误
    TIMEOUT,                // 呼叫超时
    CALL_FAILED,            // 呼叫失败
    UNKNOWN                 // 未知状态
}

/**
 * 通话状态显示信息
 */
data class CallStateDisplayInfo(
    val displayName: String,        // 显示名称
    val color: androidx.compose.ui.graphics.Color,  // 状态颜色
    val description: String,        // 状态描述
    val isSuccess: Boolean?,        // 是否成功状态（true=成功，false=失败，null=进行中）
    val icon: String                // 图标标识
)

/**
 * 获取通话状态的显示信息
 */
fun RootCallState.getDisplayInfo(): CallStateDisplayInfo {
    return when (this) {
        // 基础状态
        RootCallState.IDLE -> CallStateDisplayInfo(
            "空闲", 
            androidx.compose.ui.graphics.Color(0xFF9E9E9E), 
            "等待拨打", 
            null, 
            "📞"
        )
        RootCallState.DIALING -> CallStateDisplayInfo(
            "拨号中", 
            androidx.compose.ui.graphics.Color(0xFF2196F3), 
            "正在拨打电话", 
            null, 
            "📱"
        )
        RootCallState.ALERTING -> CallStateDisplayInfo(
            "对方响铃中", 
            androidx.compose.ui.graphics.Color(0xFFFF9800), 
            "等待对方接听", 
            null, 
            "🔔"
        )
        RootCallState.ACTIVE -> CallStateDisplayInfo(
            "通话中", 
            androidx.compose.ui.graphics.Color(0xFF4CAF50), 
            "通话已接通", 
            true, 
            "🎧"
        )
        RootCallState.HOLDING -> CallStateDisplayInfo(
            "通话保持", 
            androidx.compose.ui.graphics.Color(0xFF9C27B0), 
            "通话被保持", 
            null, 
            "⏸️"
        )
        RootCallState.DISCONNECTING -> CallStateDisplayInfo(
            "正在挂断", 
            androidx.compose.ui.graphics.Color(0xFFE91E63), 
            "通话即将结束", 
            null, 
            "📵"
        )
        RootCallState.DISCONNECTED -> CallStateDisplayInfo(
            "已挂断", 
            androidx.compose.ui.graphics.Color(0xFF757575), 
            "通话已结束", 
            null, 
            "🔚"
        )
        
        // 呼叫结果状态
        RootCallState.CONNECTED -> CallStateDisplayInfo(
            "已接听", 
            androidx.compose.ui.graphics.Color(0xFF4CAF50), 
            "通话成功建立", 
            true, 
            "✅"
        )
        RootCallState.MISSED -> CallStateDisplayInfo(
            "未接听", 
            androidx.compose.ui.graphics.Color(0xFFFF5722), 
            "响铃但未接听", 
            false, 
            "❌"
        )
        RootCallState.BUSY -> CallStateDisplayInfo(
            "对方忙线", 
            androidx.compose.ui.graphics.Color(0xFFFF9800), 
            "对方正在通话中", 
            false, 
            "📵"
        )
        RootCallState.NO_ANSWER -> CallStateDisplayInfo(
            "无人接听", 
            androidx.compose.ui.graphics.Color(0xFFFF9800), 
            "响铃超时无人接听", 
            false, 
            "⏰"
        )
        RootCallState.REJECTED -> CallStateDisplayInfo(
            "对方拒接", 
            androidx.compose.ui.graphics.Color(0xFFE91E63), 
            "对方主动挂断", 
            false, 
            "🚫"
        )
        
        // 网络/运营商状态
        RootCallState.NETWORK_UNAVAILABLE -> CallStateDisplayInfo(
            "网络不可用", 
            androidx.compose.ui.graphics.Color(0xFF795548), 
            "无法连接到网络", 
            false, 
            "📡"
        )
        RootCallState.OUT_OF_SERVICE -> CallStateDisplayInfo(
            "无服务", 
            androidx.compose.ui.graphics.Color(0xFF795548), 
            "当前无网络服务", 
            false, 
            "📵"
        )
        RootCallState.SIGNAL_WEAK -> CallStateDisplayInfo(
            "信号弱", 
            androidx.compose.ui.graphics.Color(0xFFFFC107), 
            "网络信号不稳定", 
            null, 
            "📶"
        )
        
        // 号码状态
        RootCallState.INVALID_NUMBER -> CallStateDisplayInfo(
            "号码无效", 
            androidx.compose.ui.graphics.Color(0xFF607D8B), 
            "号码格式错误或不存在", 
            false, 
            "⚠️"
        )
        RootCallState.NUMBER_NOT_IN_SERVICE -> CallStateDisplayInfo(
            "号码已停机", 
            androidx.compose.ui.graphics.Color(0xFF607D8B), 
            "该号码已停机或注销", 
            false, 
            "🚫"
        )
        RootCallState.POWER_OFF -> CallStateDisplayInfo(
            "对方关机", 
            androidx.compose.ui.graphics.Color(0xFF607D8B), 
            "对方手机已关机", 
            false, 
            "📴"
        )
        
        // 语音信箱/提示音
        RootCallState.VOICEMAIL -> CallStateDisplayInfo(
            "语音信箱", 
            androidx.compose.ui.graphics.Color(0xFF2196F3), 
            "已转接语音信箱", 
            false, 
            "📮"
        )
        RootCallState.CALL_FORWARDING -> CallStateDisplayInfo(
            "呼叫转移", 
            androidx.compose.ui.graphics.Color(0xFF2196F3), 
            "电话被转移至其他号码", 
            null, 
            "↪️"
        )
        RootCallState.IVR_PROMPT -> CallStateDisplayInfo(
            "IVR语音导航", 
            androidx.compose.ui.graphics.Color(0xFF00BCD4), 
            "正在播放语音提示", 
            null, 
            "🔊"
        )
        
        // 通话中状态
        RootCallState.ONHOLD -> CallStateDisplayInfo(
            "通话保持", 
            androidx.compose.ui.graphics.Color(0xFF9C27B0), 
            "通话被保持中", 
            null, 
            "⏸️"
        )
        RootCallState.CONFERENCE -> CallStateDisplayInfo(
            "会议通话", 
            androidx.compose.ui.graphics.Color(0xFF673AB7), 
            "多方通话中", 
            true, 
            "👥"
        )
        RootCallState.MUTED -> CallStateDisplayInfo(
            "静音中", 
            androidx.compose.ui.graphics.Color(0xFF9E9E9E), 
            "当前处于静音状态", 
            null, 
            "🔇"
        )
        
        // 异常状态
        RootCallState.ERROR -> CallStateDisplayInfo(
            "呼叫错误", 
            androidx.compose.ui.graphics.Color(0xFFF44336), 
            "发生未知错误", 
            false, 
            "❌"
        )
        RootCallState.TIMEOUT -> CallStateDisplayInfo(
            "呼叫超时", 
            androidx.compose.ui.graphics.Color(0xFFFF5722), 
            "呼叫等待超时", 
            false, 
            "⏱️"
        )
        RootCallState.CALL_FAILED -> CallStateDisplayInfo(
            "呼叫失败", 
            androidx.compose.ui.graphics.Color(0xFFF44336), 
            "呼叫未能完成", 
            false, 
            "❌"
        )
        RootCallState.UNKNOWN -> CallStateDisplayInfo(
            "未知状态", 
            androidx.compose.ui.graphics.Color(0xFF9E9E9E), 
            "无法识别的通话状态", 
            null, 
            "❓"
        )
    }
}

/**
 * Root 通话状态监听器接口
 */
interface RootPhoneStateListener {
    /**
     * 拨号中
     */
    fun onDialing(number: String?) {}

    /**
     * 对方响铃中
     */
    fun onAlerting(number: String?) {}

    /**
     * 通话已接通
     * @param setupTime 拨号到接通的耗时（毫秒）
     */
    fun onActive(number: String?, setupTime: Long) {}

    /**
     * 通话保持中
     */
    fun onHolding() {}

    /**
     * 正在挂断
     */
    fun onDisconnecting() {}

    /**
     * 通话结束/空闲
     * @param duration 通话时长（毫秒），如果未接通则为 0
     */
    fun onIdle(duration: Long) {}

    /**
     * 状态变更
     */
    fun onStateChanged(oldState: RootCallState, newState: RootCallState) {}

    /**
     * 发生错误
     */
    fun onError(message: String) {}
}
