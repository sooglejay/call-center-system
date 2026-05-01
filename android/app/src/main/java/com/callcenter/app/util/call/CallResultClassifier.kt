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
    val keywordCallType: KeywordCallType? = null
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
    INTERMITTENT,  // 间歇性（单方说话特征，如真人应答等待回应）
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
     * 只使用录音识别结果判断通话类型
     * 
     * @param context 通话上下文信息
     * @return 通话结果
     */
    suspend fun classify(context: CallContext): CallResult {
        Log.d(TAG, "========== 开始判断通话结果 ==========")
        Log.d(TAG, "通话上下文: offhookDuration=${context.offhookDuration}ms, " +
                "keywordCallType=${context.keywordCallType}, " +
                "detectedKeywords=${context.detectedKeywords}")
        
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
        
        // 使用录音识别结果判断
        Log.d(TAG, "--- 录音识别判断 (keywordCallType=${context.keywordCallType}, keywords=${context.detectedKeywords}) ---")

        // 检查是否已进入 OFFHOOK 状态
        val hasOffhook = context.offhookDuration > 0

        // 检查 keywordCallType 是否为有效值（VOICEMAIL/HUMAN）
        if (context.keywordCallType != KeywordCallType.UNKNOWN) {
            val result = classifyByKeywords(context)
            if (result != null) {
                Log.d(TAG, "录音识别判断完成: type=${result.type}, confidence=${result.confidence}, reason=${result.reason}")
                return result
            }
        } else {
            Log.d(TAG, "录音识别跳过: keywordCallType=${context.keywordCallType}")
        }

        // 根据 OFFHOOK 状态返回默认结果
        if (hasOffhook) {
            // 【修复】OFFHOOK 状态下，结合多维度信息判断
            Log.d(TAG, "--- 录音识别未返回有效结果，尝试多维度降级判断 ---")

            // 多维度综合判断（结合通话时长和能量模式）
            val duration = context.offhookDuration
            val energyPattern = context.audioEnergyPattern

            // 判断逻辑：
            // 1. 通话时长 > 20秒：很可能是真人接听（语音信箱通常不会播报这么久）
            // 2. 通话时长 10-20秒 + 能量FLUCTUATING：可能是真人，但置信度较低
            // 3. 通话时长 5-20秒 + 能量INTERMITTENT：可能是真人应答等待回应
            // 4. 能量STEADY：很可能是语音信箱
            // 5. 其他情况：默认语音信箱

            when {
                duration > 20000 -> {
                    // 通话时长超过20秒，很可能是真人接听
                    Log.d(TAG, "========== 多维度判断：通话时长>${duration/1000}秒 → 已接听 ==========")
                    return CallResult(
                        type = CallResultType.CONNECTED,
                        confidence = 0.80f,
                        reason = "OFFHOOK状态，通话时长超过20秒（${duration/1000}秒），判定为真人接听",
                        layer = 1  // 时长判断（第一层）
                    )
                }

                duration in 10000..20000 && energyPattern == AudioEnergyPattern.FLUCTUATING -> {
                    // 通话时长10-20秒，能量波动，可能是真人，但置信度中等
                    Log.d(TAG, "========== 多维度判断：通话时长${duration/1000}秒+FLUCTUATING → 可能已接听 ==========")
                    return CallResult(
                        type = CallResultType.CONNECTED,
                        confidence = 0.65f,  // 中等置信度
                        reason = "OFFHOOK状态，通话时长${duration/1000}秒+能量波动，可能是真人接听",
                        layer = 2  // 能量分析（第二层）
                    )
                }

                duration in 5000..20000 && energyPattern == AudioEnergyPattern.INTERMITTENT -> {
                    // 【新增】通话时长5-20秒，间歇性说话，可能是真人应答等待回应
                    Log.d(TAG, "========== 多维度判断：通话时长${duration/1000}秒+INTERMITTENT → 可能已接听 ==========")
                    return CallResult(
                        type = CallResultType.CONNECTED,
                        confidence = 0.68f,  // 中等置信度
                        reason = "OFFHOOK状态，通话时长${duration/1000}秒+间歇性说话模式，可能是真人接听等待回应",
                        layer = 2
                    )
                }

                energyPattern == AudioEnergyPattern.STEADY -> {
                    // 能量平稳，单向播报特征，判定为语音信箱
                    Log.d(TAG, "========== 多维度判断：STEADY → 语音信箱 ==========")
                    return CallResult(
                        type = CallResultType.VOICEMAIL,
                        confidence = 0.75f,
                        reason = "OFFHOOK状态，能量分析显示单向播报特征（STEADY），判定为语音信箱",
                        layer = 2
                    )
                }

                duration in 5000..10000 && energyPattern == AudioEnergyPattern.FLUCTUATING -> {
                    // 通话时长5-10秒，能量波动，可能是真人或语音信箱+噪音，置信度较低
                    Log.d(TAG, "========== 多维度判断：通话时长${duration/1000}秒+FLUCTUATING（短时）→ 可能语音信箱 ==========")
                    return CallResult(
                        type = CallResultType.VOICEMAIL,
                        confidence = 0.60f,
                        reason = "OFFHOOK状态，通话时长较短（${duration/1000}秒）+能量波动，可能是语音信箱或嘈杂环境",
                        layer = 2
                    )
                }

                else -> {
                    // 其他情况，默认语音信箱
                    Log.d(TAG, "========== 多维度判断：默认 VOICEMAIL（语音信箱）==========")
                    return CallResult(
                        type = CallResultType.VOICEMAIL,
                        confidence = 0.55f,
                        reason = "OFFHOOK状态下未识别到语音文本，能量分析结果=${energyPattern}，通话时长=${duration/1000}秒，默认为语音信箱",
                        layer = 1
                    )
                }
            }
        } else {
            // 没有 OFFHOOK，确实是响铃未接听
            Log.d(TAG, "========== 未进入OFFHOOK状态，返回 NO_ANSWER（响铃未接听）==========")
            return CallResult.NO_ANSWER.copy(
                confidence = 0.50f,
                reason = "未进入OFFHOOK状态，响铃未接听",
                layer = 1
            )
        }
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
