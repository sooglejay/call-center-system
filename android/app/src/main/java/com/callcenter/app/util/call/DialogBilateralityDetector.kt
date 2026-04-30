package com.callcenter.app.util.call

import android.util.Log

/**
 * 对话双向性检测器
 *
 * 核心原理：
 * - 语音信箱：单向播报，能量平稳
 * - 真人接听：双向对话，能量交替
 *
 * 检测方式：
 * 1. 能量交替检测：识别能量的交替波动
 * 2. 对话轮次识别：统计说话轮次
 * 3. 响应时间检测：检测客服说话后的响应
 */
class DialogBilateralityDetector {

    companion object {
        private const val TAG = "DialogBilaterality"

        // 能量阈值
        private const val SPEECH_ENERGY_THRESHOLD = 50f       // 说话能量阈值
        private const val SILENCE_ENERGY_THRESHOLD = 20f      // 静音能量阈值

        // 时间阈值
        private const val MIN_SPEECH_DURATION = 500L    // 最小说话时长 500ms
        private const val MAX_SPEECH_DURATION = 10000L  // 最大说话时长 10秒
        private const val MIN_SILENCE_BETWEEN = 300L    // 两段说话间的最小静音 300ms
        private const val DIALOG_TIMEOUT = 5000L        // 对话超时 5秒

        // 对话轮次阈值
        private const val MIN_DIALOG_TURNS = 2          // 最少对话轮次（一问一答）
    }

    /**
     * 对话段
     */
    data class SpeechSegment(
        val startTime: Long,
        val endTime: Long,
        val averageEnergy: Float,
        val isSpeakerSide: Boolean  // true=客服方, false=对方
    )

    /**
     * 检测结果
     */
    data class DialogResult(
        val isDialog: Boolean,              // 是否为对话
        val confidence: Float,              // 置信度
        val dialogTurns: Int,               // 对话轮次
        val speechSegments: List<SpeechSegment>,  // 说话段列表
        val reason: String                  // 原因说明
    )

    /**
     * 检测对话双向性
     *
     * @param energySamples 能量样本列表（每100ms一个样本）
     * @param sampleInterval 样本间隔（毫秒）
     * @return 检测结果
     */
    fun detect(
        energySamples: List<Float>,
        sampleInterval: Long = 100L
    ): DialogResult {
        Log.d(TAG, "开始检测对话双向性: 样本数=${energySamples.size}")

        if (energySamples.size < 10) {
            Log.w(TAG, "样本数量不足: ${energySamples.size} < 10")
            return DialogResult(
                isDialog = false,
                confidence = 0f,
                dialogTurns = 0,
                speechSegments = emptyList(),
                reason = "样本数量不足"
            )
        }

        // 1. 识别说话段
        val segments = identifySpeechSegments(energySamples, sampleInterval)
        Log.d(TAG, "识别到 ${segments.size} 个说话段")

        // 2. 检测对话轮次
        val dialogTurns = countDialogTurns(segments)
        Log.d(TAG, "检测到 $dialogTurns 轮对话")

        // 3. 判断是否为对话
        val isDialog = dialogTurns >= MIN_DIALOG_TURNS

        // 4. 计算置信度
        val confidence = calculateDialogConfidence(dialogTurns, segments)

        val reason = if (isDialog) {
            "检测到 $dialogTurns 轮对话，判定为真人接听"
        } else {
            "对话轮次不足($dialogTurns < $MIN_DIALOG_TURNS)，可能是语音信箱"
        }

        Log.d(TAG, "检测结果: isDialog=$isDialog, confidence=$confidence, turns=$dialogTurns")

        return DialogResult(
            isDialog = isDialog,
            confidence = confidence,
            dialogTurns = dialogTurns,
            speechSegments = segments,
            reason = reason
        )
    }

    /**
     * 识别说话段
     */
    private fun identifySpeechSegments(
        energySamples: List<Float>,
        sampleInterval: Long
    ): List<SpeechSegment> {
        val segments = mutableListOf<SpeechSegment>()
        var inSpeech = false
        var speechStart = 0
        var speechEnergy = 0f
        var speechCount = 0

        for (i in energySamples.indices) {
            val energy = energySamples[i]

            if (!inSpeech && energy > SPEECH_ENERGY_THRESHOLD) {
                // 开始说话
                inSpeech = true
                speechStart = i
                speechEnergy = energy
                speechCount = 1
            } else if (inSpeech) {
                if (energy > SILENCE_ENERGY_THRESHOLD) {
                    // 继续说话
                    speechEnergy += energy
                    speechCount++
                } else {
                    // 可能是静音，检查持续时间
                    var silenceCount = 0
                    var j = i
                    while (j < energySamples.size && energySamples[j] <= SILENCE_ENERGY_THRESHOLD) {
                        silenceCount++
                        j++
                    }

                    val silenceDuration = silenceCount * sampleInterval
                    if (silenceDuration >= MIN_SILENCE_BETWEEN) {
                        // 确认说话段结束
                        val speechDuration = (i - speechStart) * sampleInterval
                        if (speechDuration >= MIN_SPEECH_DURATION) {
                            segments.add(
                                SpeechSegment(
                                    startTime = speechStart * sampleInterval,
                                    endTime = i * sampleInterval,
                                    averageEnergy = speechEnergy / speechCount,
                                    isSpeakerSide = false  // 简化处理，暂不区分
                                )
                            )
                        }
                        inSpeech = false
                    }
                }
            }
        }

        // 处理最后一个说话段
        if (inSpeech) {
            val speechDuration = (energySamples.size - speechStart) * sampleInterval
            if (speechDuration >= MIN_SPEECH_DURATION) {
                segments.add(
                    SpeechSegment(
                        startTime = speechStart * sampleInterval,
                        endTime = energySamples.size * sampleInterval,
                        averageEnergy = speechEnergy / speechCount,
                        isSpeakerSide = false
                    )
                )
            }
        }

        return segments
    }

    /**
     * 统计对话轮次
     *
     * 对话轮次定义：两段说话之间有明显的静音间隔
     */
    private fun countDialogTurns(segments: List<SpeechSegment>): Int {
        if (segments.size < 2) return 0

        var turns = 0
        for (i in 1 until segments.size) {
            val gap = segments[i].startTime - segments[i - 1].endTime
            if (gap >= MIN_SILENCE_BETWEEN) {
                turns++
            }
        }

        return turns
    }

    /**
     * 计算对话置信度
     */
    private fun calculateDialogConfidence(
        dialogTurns: Int,
        segments: List<SpeechSegment>
    ): Float {
        if (dialogTurns < MIN_DIALOG_TURNS) return 0.3f

        // 轮次越多，置信度越高
        val turnScore = minOf(0.6f, dialogTurns * 0.15f)

        // 能量波动越大，置信度越高
        val energyVariation = if (segments.size >= 2) {
            val energies = segments.map { it.averageEnergy }
            val avgEnergy = energies.average().toFloat()
            val stdDev = Math.sqrt(
                energies.map { (it - avgEnergy) * (it - avgEnergy) }.average()
            ).toFloat()
            val normalizedStdDev = if (avgEnergy > 0) stdDev / avgEnergy else 0f
            minOf(0.3f, normalizedStdDev * 0.5f)
        } else {
            0f
        }

        return minOf(0.95f, 0.1f + turnScore + energyVariation)
    }
}
