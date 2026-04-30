package com.callcenter.app.util.call

import android.util.Log
import com.callcenter.app.data.local.preferences.FeatureToggle
import com.callcenter.app.data.local.preferences.FeatureToggleManager
import kotlinx.coroutines.flow.first

/**
 * 通话上下文信息
 * 用于综合判断通话结果
 */
data class CallContext(
    /** 拨号到接通(OFFHOOK)的时间（毫秒） */
    val dialToOffhookTime: Long = 0,

    /** 响铃时长（毫秒） */
    val alertingDuration: Long = 0,

    /** OFFHOOK 持续时长（毫秒） */
    val offhookDuration: Long = 0,

    /** 总通话时长（毫秒） */
    val totalDuration: Long = 0,

    /** 是否检测到 ACTIVE 状态 */
    val hasActiveState: Boolean = false,

    /** 是否检测到 ALERTING 状态 */
    val hasAlertingState: Boolean = false,

    /** 挂断方 */
    val hangupInitiator: HangupInitiator = HangupInitiator.UNKNOWN,

    /** 音频能量模式（可选，需要录音权限） */
    val audioEnergyPattern: AudioEnergyPattern = AudioEnergyPattern.UNKNOWN,

    /** Root 检测到的精确状态（可选） */
    val rootDetectedState: RootCallEndState? = null,

    /** AI关键词检测到的关键词列表（第三层） */
    val detectedKeywords: List<String> = emptyList(),

    /** AI关键词检测到的通话类型 */
    val keywordCallType: KeywordCallType? = null,

    /** 【新增】平均能量 */
    val averageEnergy: Float = 0f,

    /** 【新增】归一化标准差 */
    val normalizedStdDev: Float = 0f,

    /** 【新增】识别文本长度 */
    val textLength: Int = 0,

    /** 【新增】能量样本列表（用于对话检测） */
    val energySamples: List<Float> = emptyList()
)

/**
 * 挂断方
 */
enum class HangupInitiator {
    LOCAL,      // 本地挂断
    REMOTE,     // 对方挂断
    NETWORK,    // 网络断开
    TIMEOUT,    // 超时
    UNKNOWN
}

/**
 * 音频能量模式
 */
enum class AudioEnergyPattern {
    STEADY,        // 平稳（语音信箱特征）
    FLUCTUATING,   // 波动（对话特征）
    MODERATE,      // 中等
    UNKNOWN
}

/**
 * Root 检测到的精确通话结束状态
 */
enum class RootCallEndState {
    NORMAL_CLEARING,      // 正常挂断
    USER_BUSY,            // 用户忙线
    NO_ANSWER,            // 无人接听
    CALL_REJECTED,        // 对方拒接
    POWER_OFF,            // 对方关机
    VOICEMAIL_DETECTED,   // 检测到语音信箱
    NETWORK_ERROR         // 网络错误
}

/**
 * 通话结果
 */
data class CallResult(
    val type: CallResultType,
    val confidence: Float,
    val reason: String,
    val layer: Int  // 判断层级
) {
    companion object {
        val CONNECTED = CallResult(CallResultType.CONNECTED, 0f, "", 0)
        val VOICEMAIL = CallResult(CallResultType.VOICEMAIL, 0f, "", 0)
        val NO_ANSWER = CallResult(CallResultType.NO_ANSWER, 0f, "响铃未接听", 0)
    }
}

enum class CallResultType {
    CONNECTED,   // 真人已接听
    NO_ANSWER,   // 响铃未接听（包括对方正忙、对方拒接、无人接听、对方关机等）
    VOICEMAIL    // 语音信箱播报
}

/**
 * 通话结果分类器
 * 
 * 使用分层判断策略：
 * - 第一层：基于时长的快速判断（无额外权限）
 * - 第二层：音频能量分析（需要录音权限）
 * - 第三层：AI关键词识别（需要录音权限和额外资源）
 * 
 * 每一层都可以通过功能开关独立控制
 */
class CallResultClassifier(
    private val featureToggleManager: FeatureToggleManager
) {
    companion object {
        private const val TAG = "CallResultClassifier"
    }

    /**
     * 检查是否启用了录音识别
     */
    suspend fun isKeywordDetectionEnabled(): Boolean {
        return featureToggleManager.isEnabled(FeatureToggle.VOICE_RECOGNITION_DETECTION)
    }
    
    /**
     * 分类通话结果
     * 
     * 使用多维度综合评分判断通话类型
     * 
     * @param context 通话上下文信息
     * @return 通话结果
     */
    suspend fun classify(context: CallContext): CallResult {
        Log.d(TAG, "========== 开始多维度评分判断 ==========")
        Log.d(TAG, "通话上下文: offhookDuration=${context.offhookDuration}ms, " +
                "keywordCallType=${context.keywordCallType}, " +
                "averageEnergy=${context.averageEnergy}, " +
                "textLength=${context.textLength}")
        
        // 检查是否启用录音识别
        val voiceRecognitionEnabled = featureToggleManager.isEnabled(
            FeatureToggle.VOICE_RECOGNITION_DETECTION
        )
        
        Log.d(TAG, "功能开关状态: voiceRecognition=$voiceRecognitionEnabled")
        
        if (!voiceRecognitionEnabled) {
            // 使用传统判断方式（简单的时长阈值）
            Log.d(TAG, "录音识别未启用，使用传统判断方式")
            return classifyLegacy(context)
        }
        
        // 检查是否已进入 OFFHOOK 状态
        val hasOffhook = context.offhookDuration > 0
        
        if (!hasOffhook) {
            // 没有 OFFHOOK，确实是响铃未接听
            Log.d(TAG, "========== 未进入OFFHOOK状态，返回 NO_ANSWER（响铃未接听）==========")
            return CallResult.NO_ANSWER.copy(
                confidence = 0.50f,
                reason = "未进入OFFHOOK状态，响铃未接听",
                layer = 1
            )
        }
        
        // 【新增】使用多维度评分系统
        Log.d(TAG, "--- 开始多维度评分 ---")
        val scoreCalculator = CallScoreCalculator()
        val scoreResult = scoreCalculator.calculate(
            offhookDuration = context.offhookDuration,
            averageEnergy = context.averageEnergy,
            normalizedStdDev = context.normalizedStdDev,
            keywordCallType = context.keywordCallType,
            textLength = context.textLength,
            energySamples = context.energySamples
        )
        
        Log.d(TAG, "评分结果: 总分=${scoreResult.totalScore}, 决策=${scoreResult.decision}")
        
        return when (scoreResult.decision) {
            CallDecision.HUMAN -> CallResult(
                type = CallResultType.CONNECTED,
                confidence = scoreResult.totalScore,
                reason = scoreResult.reason,
                layer = 3
            )
            CallDecision.VOICEMAIL -> CallResult(
                type = CallResultType.VOICEMAIL,
                confidence = 1f - scoreResult.totalScore,
                reason = scoreResult.reason,
                layer = 3
            )
            CallDecision.UNCERTAIN -> {
                // 不确定时，使用降级策略
                Log.d(TAG, "评分不确定，使用降级策略")
                classifyFallback(context)
            }
        }
    }
    
    /**
     * 降级判断：当评分不确定时使用
     */
    private fun classifyFallback(context: CallContext): CallResult {
        Log.d(TAG, "使用降级判断策略")
        
        // 降级策略1：使用通话时长
        if (context.offhookDuration > 20000) {
            return CallResult(
                type = CallResultType.CONNECTED,
                confidence = 0.65f,
                reason = "评分不确定，降级判断：通话时长>20秒",
                layer = 1
            )
        }
        
        // 降级策略2：使用挂断方信息
        if (context.hangupInitiator == HangupInitiator.LOCAL) {
            return CallResult(
                type = CallResultType.CONNECTED,
                confidence = 0.60f,
                reason = "评分不确定，降级判断：本地挂断",
                layer = 1
            )
        }
        
        // 降级策略3：默认判断
        return CallResult(
            type = CallResultType.VOICEMAIL,
            confidence = 0.50f,
            reason = "评分不确定，降级判断：默认为语音信箱",
            layer = 1
        )
    }
    
    /**
     * 录音识别判断
     */
    private fun classifyByKeywords(context: CallContext): CallResult? {
        val keywordCallType = context.keywordCallType ?: return null
        val keywords = context.detectedKeywords

        return when (keywordCallType) {
            KeywordCallType.VOICEMAIL -> CallResult(
                type = CallResultType.VOICEMAIL,
                confidence = 0.90f,
                reason = "检测到语音信箱关键词: ${keywords.joinToString()}",
                layer = 3
            )
            KeywordCallType.HUMAN -> CallResult(
                type = CallResultType.CONNECTED,
                confidence = 0.85f,
                reason = if (keywords.isNotEmpty()) "检测到真人接听关键词: ${keywords.joinToString()}" else "识别文本长度>5，判定为真人接听",
                layer = 3
            )
            KeywordCallType.IVR -> CallResult(
                // IVR语音导航，可能是企业总机，需要进一步确认
                // 但通常IVR意味着不是语音信箱
                type = CallResultType.CONNECTED,
                confidence = 0.70f,
                reason = "检测到IVR语音导航关键词: ${keywords.joinToString()}",
                layer = 3
            )
            KeywordCallType.UNKNOWN -> null
        }
    }

    /**
     * 传统判断方式（功能开关关闭时使用）
     */
    private fun classifyLegacy(context: CallContext): CallResult {
        // 使用时长阈值判断（默认20秒）
        val thresholdMs = 20000L
        return when {
            context.offhookDuration < thresholdMs -> CallResult(
                type = CallResultType.VOICEMAIL,
                confidence = 0.60f,
                reason = "传统判断：OFFHOOK<20秒",
                layer = 0
            )
            else -> CallResult(
                type = CallResultType.CONNECTED,
                confidence = 0.70f,
                reason = "传统判断：OFFHOOK>=20秒",
                layer = 0
            )
        }
    }
}
