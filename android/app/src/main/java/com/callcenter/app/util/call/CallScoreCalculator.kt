package com.callcenter.app.util.call

import android.util.Log
import com.callcenter.app.util.DebugLogger

/**
 * 通话评分结果
 */
data class CallScoreResult(
    val totalScore: Float,          // 总分
    val durationScore: Float,       // 时长得分
    val energyScore: Float,         // 能量得分
    val keywordScore: Float,        // 关键词得分
    val textLengthScore: Float,     // 文本长度得分
    val dialogScore: Float,         // 对话检测得分
    val decision: CallDecision,     // 决策结果
    val reason: String              // 原因说明
)

/**
 * 决策结果
 */
enum class CallDecision {
    HUMAN,          // 真人接听
    VOICEMAIL,      // 语音信箱
    UNCERTAIN       // 不确定，需人工确认
}

/**
 * 通话评分计算器
 * 综合多个维度计算通话状态得分
 */
class CallScoreCalculator {

    companion object {
        private const val TAG = "CallScoreCalculator"

        // 权重配置
        private const val WEIGHT_DURATION = 0.25f      // 时长权重（降低）
        private const val WEIGHT_ENERGY = 0.20f        // 能量权重（降低）
        private const val WEIGHT_KEYWORD = 0.25f       // 关键词权重（降低）
        private const val WEIGHT_TEXT_LENGTH = 0.10f   // 文本长度权重（保持）
        private const val WEIGHT_DIALOG = 0.20f        // 对话检测权重（新增）

        // 决策阈值
        private const val THRESHOLD_HUMAN = 0.75f      // 真人阈值
        private const val THRESHOLD_VOICEMAIL = 0.35f  // 语音信箱阈值
    }

    /**
     * 计算通话评分
     *
     * @param offhookDuration OFFHOOK 持续时长（毫秒）
     * @param averageEnergy 平均能量
     * @param normalizedStdDev 归一化标准差
     * @param keywordCallType 关键词类型
     * @param textLength 识别文本长度
     * @param energySamples 能量样本列表（用于对话检测）
     * @return 评分结果
     */
    fun calculate(
        offhookDuration: Long,
        averageEnergy: Float,
        normalizedStdDev: Float,
        keywordCallType: KeywordCallType?,
        textLength: Int,
        energySamples: List<Float> = emptyList()
    ): CallScoreResult {
        Log.d(TAG, "========== 开始多维度评分 ==========")
        DebugLogger.log("[ScoreCalc] 开始多维度评分")

        // 计算各维度得分
        val durationScore = calculateDurationScore(offhookDuration)
        val energyScore = calculateEnergyScore(averageEnergy, normalizedStdDev)
        val keywordScore = calculateKeywordScore(keywordCallType)
        val textLengthScore = calculateTextLengthScore(textLength)

        // 【新增】对话双向性检测
        val dialogScore = if (energySamples.isNotEmpty()) {
            val dialogDetector = DialogBilateralityDetector()
            val dialogResult = dialogDetector.detect(energySamples)
            DebugLogger.log("[ScoreCalc] 对话检测结果: isDialog=${dialogResult.isDialog}, turns=${dialogResult.dialogTurns}, confidence=${dialogResult.confidence}")
            if (dialogResult.isDialog) {
                dialogResult.confidence
            } else {
                0.2f  // 未检测到对话，给低分
            }
        } else {
            0.3f  // 无数据，给中等偏低分
        }

        // 计算加权总分
        val totalScore = durationScore * WEIGHT_DURATION +
                        energyScore * WEIGHT_ENERGY +
                        keywordScore * WEIGHT_KEYWORD +
                        textLengthScore * WEIGHT_TEXT_LENGTH +
                        dialogScore * WEIGHT_DIALOG

        // 决策
        val decision = when {
            totalScore >= THRESHOLD_HUMAN -> CallDecision.HUMAN
            totalScore <= THRESHOLD_VOICEMAIL -> CallDecision.VOICEMAIL
            else -> CallDecision.UNCERTAIN
        }

        val reason = buildReason(
            decision, totalScore,
            durationScore, energyScore, keywordScore, textLengthScore, dialogScore
        )

        Log.d(TAG, "评分结果: 总分=$totalScore, 决策=$decision")
        DebugLogger.log("[ScoreCalc] 最终评分: 总分=${String.format("%.2f", totalScore)}, 决策=$decision")
        DebugLogger.log("[ScoreCalc] 各维度: 时长=${String.format("%.2f", durationScore)}, 能量=${String.format("%.2f", energyScore)}, 关键词=${String.format("%.2f", keywordScore)}, 文本=${String.format("%.2f", textLengthScore)}, 对话=${String.format("%.2f", dialogScore)}")

        return CallScoreResult(
            totalScore = totalScore,
            durationScore = durationScore,
            energyScore = energyScore,
            keywordScore = keywordScore,
            textLengthScore = textLengthScore,
            dialogScore = dialogScore,
            decision = decision,
            reason = reason
        )
    }

    /**
     * 计算时长得分
     */
    private fun calculateDurationScore(offhookDuration: Long): Float {
        val durationSec = offhookDuration / 1000f
        val score = when {
            offhookDuration < 3000 -> 0.2f   // 极短，可能是语音信箱
            offhookDuration < 10000 -> 0.5f  // 中等，不确定
            offhookDuration < 20000 -> 0.7f  // 较长，倾向真人
            else -> 0.9f                     // 很长，很可能是真人
        }
        DebugLogger.log("[ScoreCalc] 时长得分: ${durationSec}秒 -> $score")
        return score
    }

    /**
     * 计算能量得分
     */
    private fun calculateEnergyScore(averageEnergy: Float, normalizedStdDev: Float): Float {
        val score = when {
            averageEnergy < 20f -> 0.1f      // 静音，录音失败
            averageEnergy < 100f -> 0.3f     // 低能量，可能是语音信箱
            normalizedStdDev > 0.3f -> 0.8f  // 波动大，真人对话特征
            else -> 0.5f                     // 平稳，可能是语音信箱
        }
        DebugLogger.log("[ScoreCalc] 能量得分: avg=$averageEnergy, stdDev=$normalizedStdDev -> $score")
        return score
    }

    /**
     * 计算关键词得分
     */
    private fun calculateKeywordScore(keywordCallType: KeywordCallType?): Float {
        val score = when (keywordCallType) {
            KeywordCallType.VOICEMAIL -> 0.95f  // 高置信度语音信箱
            KeywordCallType.HUMAN -> 0.85f      // 高置信度真人
            KeywordCallType.IVR -> 0.6f         // IVR，可能是企业总机
            KeywordCallType.UNKNOWN -> 0.4f     // 未识别，降权处理
            null -> 0.3f                         // 无数据
        }
        DebugLogger.log("[ScoreCalc] 关键词得分: type=$keywordCallType -> $score")
        return score
    }

    /**
     * 计算文本长度得分
     */
    private fun calculateTextLengthScore(textLength: Int): Float {
        val score = when {
            textLength == 0 -> 0.2f          // 无文本，可能是语音信箱
            textLength < 5 -> 0.4f           // 短文本，不确定
            textLength < 10 -> 0.7f          // 中等长度，倾向真人
            else -> 0.9f                     // 长文本，很可能是真人
        }
        DebugLogger.log("[ScoreCalc] 文本长度得分: length=$textLength -> $score")
        return score
    }

    /**
     * 构建原因说明
     */
    private fun buildReason(
        decision: CallDecision,
        totalScore: Float,
        durationScore: Float,
        energyScore: Float,
        keywordScore: Float,
        textLengthScore: Float,
        dialogScore: Float
    ): String {
        val decisionStr = when (decision) {
            CallDecision.HUMAN -> "真人接听"
            CallDecision.VOICEMAIL -> "语音信箱"
            CallDecision.UNCERTAIN -> "不确定"
        }

        return "决策: $decisionStr (总分=${String.format("%.2f", totalScore)}), " +
               "时长=${String.format("%.2f", durationScore)}, " +
               "能量=${String.format("%.2f", energyScore)}, " +
               "关键词=${String.format("%.2f", keywordScore)}, " +
               "文本=${String.format("%.2f", textLengthScore)}, " +
               "对话=${String.format("%.2f", dialogScore)}"
    }
}
