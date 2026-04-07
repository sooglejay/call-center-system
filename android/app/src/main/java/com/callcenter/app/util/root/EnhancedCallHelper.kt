package com.callcenter.app.util.root

import android.content.Context
import android.telephony.TelephonyManager
import android.util.Log
import com.callcenter.app.util.CallHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 增强版通话助手
 * 集成 Root 状态检测，在 Root 手机上使用更精确的通话状态
 */
class EnhancedCallHelper(context: Context) {

    companion object {
        private const val TAG = "EnhancedCallHelper"
    }

    private val context = context.applicationContext
    private val callHelper = CallHelper(context)
    private val rootDetector = RootCallStateDetector()

    // 是否使用 Root 检测
    private val useRootDetection: Boolean = RootUtils.hasRootPermission()

    // 当前通话状态 - 使用 StateFlow 支持观察
    private val _currentCallState = MutableStateFlow(RootCallState.IDLE)
    val currentCallState: StateFlow<RootCallState> = _currentCallState.asStateFlow()

    // 通话统计
    private var callStats = CallStats()

    init {
        Log.d(TAG, "Root 检测可用: $useRootDetection")

        if (useRootDetection) {
            setupRootDetection()
        }
    }

    /**
     * 设置 Root 检测
     */
    private fun setupRootDetection() {
        rootDetector.addListener(object : RootPhoneStateListener {
            override fun onDialing(number: String?) {
                Log.d(TAG, "Root 检测: 拨号中 - $number")
                _currentCallState.value = RootCallState.DIALING
                callStats.dialStartTime = System.currentTimeMillis()
                callStats.number = number
            }

            override fun onAlerting(number: String?) {
                Log.d(TAG, "Root 检测: 对方响铃 - $number")
                _currentCallState.value = RootCallState.ALERTING
            }

            override fun onActive(number: String?, setupTime: Long) {
                Log.d(TAG, "Root 检测: 通话接通 - $number, 接通耗时: ${setupTime}ms")
                _currentCallState.value = RootCallState.ACTIVE
                callStats.connectTime = System.currentTimeMillis()
                callStats.setupDuration = setupTime
            }

            override fun onHolding() {
                Log.d(TAG, "Root 检测: 通话保持")
                _currentCallState.value = RootCallState.HOLDING
            }

            override fun onDisconnecting() {
                Log.d(TAG, "Root 检测: 正在挂断")
                _currentCallState.value = RootCallState.DISCONNECTING
            }

            override fun onIdle(duration: Long) {
                Log.d(TAG, "Root 检测: 通话结束, 时长: ${duration}ms")
                _currentCallState.value = RootCallState.IDLE
                callStats.callDuration = duration

                // 记录通话统计
                if (duration > 0) {
                    logCallStats()
                }
            }

            override fun onStateChanged(oldState: RootCallState, newState: RootCallState) {
                Log.d(TAG, "状态变更: $oldState -> $newState")
            }

            override fun onError(message: String) {
                Log.e(TAG, "Root 检测错误: $message")
            }
        })

        rootDetector.startDetection()
    }

    /**
     * 拨打电话
     */
    fun makeCall(phone: String, directCall: Boolean = false) {
        callHelper.makeCall(phone, directCall)
    }

    /**
     * 获取当前通话状态
     */
    fun getCurrentCallState(): RootCallState {
        return if (useRootDetection) {
            _currentCallState.value
        } else {
            // 回退到普通 TelephonyManager 检测
            convertToRootState(callHelper.getCallState())
        }
    }

    /**
     * 获取当前通话号码
     */
    fun getCurrentNumber(): String? {
        return if (useRootDetection) {
            rootDetector.getCurrentNumber()
        } else {
            null
        }
    }

    /**
     * 获取当前通话时长
     */
    fun getCurrentCallDuration(): Long {
        return if (useRootDetection) {
            rootDetector.getCurrentCallDuration()
        } else {
            0
        }
    }

    /**
     * 是否正在通话中（已接通）
     */
    fun isInActiveCall(): Boolean {
        return getCurrentCallState() == RootCallState.ACTIVE
    }

    /**
     * 是否正在拨号或响铃
     */
    fun isDialingOrAlerting(): Boolean {
        val state = getCurrentCallState()
        return state == RootCallState.DIALING || state == RootCallState.ALERTING
    }

    /**
     * 获取通话统计信息
     */
    fun getCallStats(): CallStats {
        return callStats.copy()
    }

    /**
     * 重置通话统计
     */
    fun resetCallStats() {
        callStats = CallStats()
    }

    /**
     * 释放资源
     */
    fun release() {
        if (useRootDetection) {
            rootDetector.release()
        }
    }

    /**
     * 记录通话统计
     */
    private fun logCallStats() {
        Log.i(TAG, "=== 通话统计 ===")
        Log.i(TAG, "号码: ${callStats.number}")
        Log.i(TAG, "拨号耗时: ${callStats.setupDuration}ms")
        Log.i(TAG, "通话时长: ${callStats.callDuration}ms")
        Log.i(TAG, "==============")
    }

    /**
     * 将 TelephonyManager 状态转换为 RootCallState
     */
    private fun convertToRootState(state: Int): RootCallState {
        return when (state) {
            TelephonyManager.CALL_STATE_IDLE -> RootCallState.IDLE
            TelephonyManager.CALL_STATE_RINGING -> RootCallState.ALERTING
            TelephonyManager.CALL_STATE_OFFHOOK -> RootCallState.ACTIVE
            else -> RootCallState.IDLE
        }
    }
}

/**
 * 通话统计数据类
 */
data class CallStats(
    var number: String? = null,
    var dialStartTime: Long = 0,
    var connectTime: Long = 0,
    var setupDuration: Long = 0,      // 拨号到接通耗时
    var callDuration: Long = 0        // 通话时长
) {
    /**
     * 获取格式化的通话时长
     */
    fun getFormattedDuration(): String {
        val seconds = callDuration / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        return String.format("%02d:%02d:%02d", hours, minutes % 60, seconds % 60)
    }

    /**
     * 获取格式化的接通耗时
     */
    fun getFormattedSetupTime(): String {
        return when {
            setupDuration < 1000 -> "${setupDuration}ms"
            setupDuration < 60000 -> "${setupDuration / 1000}s"
            else -> "${setupDuration / 60000}m ${(setupDuration % 60000) / 1000}s"
        }
    }
}
