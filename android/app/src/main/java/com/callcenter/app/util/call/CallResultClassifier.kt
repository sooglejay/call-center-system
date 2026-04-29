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

        // 第一层阈值配置
        // 注意：语音信箱通常播报 15-30 秒，阈值设置需避免误判
        const val VOICEMAIL_THRESHOLD_MIN = 3000L    // 语音信箱最小阈值：3秒（极短通话）
        const val VOICEMAIL_THRESHOLD_MAX = 10000L   // 语音信箱最大阈值：10秒
        const val CONNECTED_THRESHOLD = 20000L       // 用户接听阈值：20秒（超过此时长认为是真人接听）

        // 响铃相关阈值
        const val QUICK_TRANSFER_THRESHOLD = 5000L   // 快速转接阈值：5秒
        const val NORMAL_ALERTING_MIN = 5000L        // 正常响铃最小时间：5秒
    }

    /**
     * 检查是否启用了关键词检测
     */
    suspend fun isKeywordDetectionEnabled(): Boolean {
        return featureToggleManager.isEnabled(FeatureToggle.AI_KEYWORD_DETECTION)
    }
    
    /**
     * 分类通话结果
     * 
     * @param context 通话上下文信息
     * @return 通话结果
     */
    suspend fun classify(context: CallContext): CallResult {
        Log.d(TAG, "========== 开始分层判断通话结果 ==========")
        Log.d(TAG, "通话上下文: offhookDuration=${context.offhookDuration}ms, " +
                "alertingDuration=${context.alertingDuration}ms, " +
                "hasActiveState=${context.hasActiveState}, " +
                "audioEnergyPattern=${context.audioEnergyPattern}, " +
                "keywordCallType=${context.keywordCallType}, " +
                "detectedKeywords=${context.detectedKeywords}")
        
        // 检查是否启用智能通话结果识别
        val smartDetectionEnabled = featureToggleManager.isEnabled(
            FeatureToggle.SMART_CALL_RESULT_DETECTION
        )
        
        Log.d(TAG, "功能开关状态: smartDetection=$smartDetectionEnabled")
        
        if (!smartDetectionEnabled) {
            // 使用传统判断方式（简单的时长阈值）
            Log.d(TAG, "智能检测未启用，使用传统判断方式")
            return classifyLegacy(context)
        }
        
        // 第一层：时长判断
        Log.d(TAG, "--- 第一层：时长判断 ---")
        val layer1Result = classifyByDuration(context)
        if (layer1Result != null) {
            Log.d(TAG, "第一层判断完成: type=${layer1Result.type}, confidence=${layer1Result.confidence}, reason=${layer1Result.reason}")
            return layer1Result
        }
        Log.d(TAG, "第一层无法确定，进入第二层")
        
        // 第二层：音频能量分析（如果启用）
        val audioAnalysisEnabled = featureToggleManager.isEnabled(
            FeatureToggle.AUDIO_ENERGY_ANALYSIS
        )
        Log.d(TAG, "--- 第二层：音频能量分析 (enabled=$audioAnalysisEnabled, pattern=${context.audioEnergyPattern}) ---")
        
        if (audioAnalysisEnabled && context.audioEnergyPattern != AudioEnergyPattern.UNKNOWN) {
            val layer2Result = classifyByAudioEnergy(context)
            if (layer2Result != null) {
                Log.d(TAG, "第二层判断完成: type=${layer2Result.type}, confidence=${layer2Result.confidence}, reason=${layer2Result.reason}")
                return layer2Result
            }
        } else {
            Log.d(TAG, "第二层跳过: enabled=$audioAnalysisEnabled, pattern=${context.audioEnergyPattern}")
        }
        
        // 第三层：AI关键词识别（如果启用）
        val aiDetectionEnabled = featureToggleManager.isEnabled(
            FeatureToggle.AI_KEYWORD_DETECTION
        )
        Log.d(TAG, "--- 第三层：AI关键词识别 (enabled=$aiDetectionEnabled, keywordCallType=${context.keywordCallType}, keywords=${context.detectedKeywords}) ---")

        // 修改判断条件：检查 keywordCallType 是否为有效值（VOICEMAIL/HUMAN/IVR），或 keywords 不为空
        if (aiDetectionEnabled && context.keywordCallType != KeywordCallType.UNKNOWN) {
            val layer3Result = classifyByKeywords(context)
            if (layer3Result != null) {
                Log.d(TAG, "第三层判断完成: type=${layer3Result.type}, confidence=${layer3Result.confidence}, reason=${layer3Result.reason}")
                return layer3Result
            }
        } else {
            Log.d(TAG, "第三层跳过: enabled=$aiDetectionEnabled, keywordCallType=${context.keywordCallType}")
        }

        // 无法确定，返回 NO_ANSWER（响铃未接听）
        Log.d(TAG, "========== 无法自动判断为已接听或语音信箱，返回 NO_ANSWER（响铃未接听）==========")
        return CallResult.NO_ANSWER.copy(
            confidence = 0.50f,
            reason = "无法自动判断，默认为响铃未接听",
            layer = 3
        )
    }
    
    /**
     * 第一层：基于时长的快速判断
     */
    private fun classifyByDuration(context: CallContext): CallResult? {
        val offhookDuration = context.offhookDuration
        val alertingDuration = context.alertingDuration
        
        // 高置信度判断：极短通话
        if (offhookDuration < VOICEMAIL_THRESHOLD_MIN) {
            return CallResult(
                type = CallResultType.VOICEMAIL,
                confidence = 0.95f,
                reason = "OFFHOOK时长<3秒，极可能是语音信箱",
                layer = 1
            )
        }
        
        // 高置信度判断：较长通话
        if (offhookDuration > CONNECTED_THRESHOLD) {
            return CallResult(
                type = CallResultType.CONNECTED,
                confidence = 0.90f,
                reason = "OFFHOOK时长>20秒，极可能是用户接听",
                layer = 1
            )
        }
        
        // 快速转接特征：响铃很短就接通
        if (alertingDuration < QUICK_TRANSFER_THRESHOLD && 
            alertingDuration > 0 &&
            offhookDuration < VOICEMAIL_THRESHOLD_MAX) {
            return CallResult(
                type = CallResultType.VOICEMAIL,
                confidence = 0.80f,
                reason = "响铃时间<5秒且OFFHOOK<8秒，可能是语音信箱",
                layer = 1
            )
        }
        
        // 正常响铃 + 中等时长：可能是用户接听
        if (alertingDuration >= NORMAL_ALERTING_MIN &&
            offhookDuration >= VOICEMAIL_THRESHOLD_MAX) {
            return CallResult(
                type = CallResultType.CONNECTED,
                confidence = 0.75f,
                reason = "正常响铃时间且OFFHOOK>=8秒，可能是用户接听",
                layer = 1
            )
        }

        // Root 设备精确状态检测
        context.rootDetectedState?.let { state ->
            return when (state) {
                RootCallEndState.VOICEMAIL_DETECTED -> CallResult(
                    type = CallResultType.VOICEMAIL,
                    confidence = 0.95f,
                    reason = "Root检测到语音信箱",
                    layer = 1
                )
                RootCallEndState.NORMAL_CLEARING -> {
                    // 正常挂断，需要结合时长判断
                    if (context.hasActiveState) {
                        CallResult(
                            type = CallResultType.CONNECTED,
                            confidence = 0.90f,
                            reason = "Root检测到ACTIVE状态",
                            layer = 1
                        )
                    } else {
                        null
                    }
                }
                else -> null
            }
        }
        
        // 中间区间：需要进一步判断
        return null
    }
    
    /**
     * 第二层：音频能量分析
     */
    private fun classifyByAudioEnergy(context: CallContext): CallResult? {
        return when (context.audioEnergyPattern) {
            AudioEnergyPattern.STEADY -> CallResult(
                type = CallResultType.VOICEMAIL,
                confidence = 0.85f,
                reason = "音频能量平稳，单向播放特征",
                layer = 2
            )
            AudioEnergyPattern.FLUCTUATING -> CallResult(
                type = CallResultType.CONNECTED,
                confidence = 0.85f,
                reason = "音频能量波动，双向对话特征",
                layer = 2
            )
            AudioEnergyPattern.MODERATE -> {
                // 中等波动，结合其他因素判断
                if (context.offhookDuration > 10000) {
                    CallResult(
                        type = CallResultType.CONNECTED,
                        confidence = 0.70f,
                        reason = "中等能量波动且时长>10秒",
                        layer = 2
                    )
                } else {
                    null
                }
            }
            AudioEnergyPattern.UNKNOWN -> null
        }
    }

    /**
     * 第三层：AI关键词识别
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
        // 使用时长阈值判断
        return when {
            context.offhookDuration < CONNECTED_THRESHOLD -> CallResult(
                type = CallResultType.VOICEMAIL,
                confidence = 0.60f,
                reason = "传统判断：OFFHOOK<${CONNECTED_THRESHOLD/1000}秒",
                layer = 0
            )
            else -> CallResult(
                type = CallResultType.CONNECTED,
                confidence = 0.70f,
                reason = "传统判断：OFFHOOK>=${CONNECTED_THRESHOLD/1000}秒",
                layer = 0
            )
        }
    }
}
